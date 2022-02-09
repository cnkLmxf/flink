/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.registration;

import org.apache.flink.runtime.rpc.RpcGateway;

import org.slf4j.Logger;

import java.io.Serializable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * This utility class implements the basis of RPC connecting from one component to another
 * component, for example the RPC connection from TaskExecutor to ResourceManager. This {@code
 * RegisteredRpcConnection} implements registration and get target gateway.
 * 该实用程序类实现了从一个组件到另一个组件的 RPC 连接的基础，例如从 TaskExecutor 到 ResourceManager 的 RPC 连接。
 * 这个 {@code RegisteredRpcConnection} 实现注册和获取目标网关。
 *
 * <p>The registration gives access to a future that is completed upon successful registration. The
 * RPC connection can be closed, for example when the target where it tries to register at looses
 * leader status.
 * 注册可以访问在成功注册后完成的未来。 RPC 连接可以关闭，例如当它尝试注册的目标失去领导者状态时。
 *
 * @param <F> The type of the fencing token
 * @param <G> The type of the gateway to connect to.
 * @param <S> The type of the successful registration responses.
 * @param <R> The type of the registration rejection responses.
 */
public abstract class RegisteredRpcConnection<
        F extends Serializable,
        G extends RpcGateway,
        S extends RegistrationResponse.Success,
        R extends RegistrationResponse.Rejection> {

    private static final AtomicReferenceFieldUpdater<RegisteredRpcConnection, RetryingRegistration>
            REGISTRATION_UPDATER =
                    AtomicReferenceFieldUpdater.newUpdater(
                            RegisteredRpcConnection.class,
                            RetryingRegistration.class,
                            "pendingRegistration");

    /** The logger for all log messages of this class. */
    protected final Logger log;

    /** The fencing token fo the remote component.
     * 远程组件的隔离令牌。
     * */
    private final F fencingToken;

    /** The target component Address, for example the ResourceManager Address.
     * 目标组件地址，例如 ResourceManager 地址。
     * */
    private final String targetAddress;

    /**
     * Execution context to be used to execute the on complete action of the
     * ResourceManagerRegistration.
     * 用于执行 ResourceManagerRegistration 的完成操作的执行上下文。
     */
    private final Executor executor;

    /** The Registration of this RPC connection. */
    private volatile RetryingRegistration<F, G, S, R> pendingRegistration;

    /** The gateway to register, it's null until the registration is completed.
     * 注册网关，注册完成前为空。
     * */
    private volatile G targetGateway;

    /** Flag indicating that the RPC connection is closed.
     * 指示 RPC 连接已关闭的标志。
     * */
    private volatile boolean closed;

    // ------------------------------------------------------------------------

    public RegisteredRpcConnection(
            Logger log, String targetAddress, F fencingToken, Executor executor) {
        this.log = checkNotNull(log);
        this.targetAddress = checkNotNull(targetAddress);
        this.fencingToken = checkNotNull(fencingToken);
        this.executor = checkNotNull(executor);
    }

    // ------------------------------------------------------------------------
    //  Life cycle
    // ------------------------------------------------------------------------

    public void start() {
        checkState(!closed, "The RPC connection is already closed");
        checkState(
                !isConnected() && pendingRegistration == null,
                "The RPC connection is already started");

        final RetryingRegistration<F, G, S, R> newRegistration = createNewRegistration();

        if (REGISTRATION_UPDATER.compareAndSet(this, null, newRegistration)) {
            newRegistration.startRegistration();
        } else {
            // concurrent start operation
            newRegistration.cancel();
        }
    }

    /**
     * Tries to reconnect to the {@link #targetAddress} by cancelling the pending registration and
     * starting a new pending registration.
     * 尝试通过取消挂起的注册并开始新的挂起注册来重新连接到 {@link #targetAddress}。
     *
     * @return {@code false} if the connection has been closed or a concurrent modification has
     *     happened; otherwise {@code true}
     */
    public boolean tryReconnect() {
        checkState(isConnected(), "Cannot reconnect to an unknown destination.");

        if (closed) {
            return false;
        } else {
            final RetryingRegistration<F, G, S, R> currentPendingRegistration = pendingRegistration;

            if (currentPendingRegistration != null) {
                currentPendingRegistration.cancel();
            }

            final RetryingRegistration<F, G, S, R> newRegistration = createNewRegistration();

            if (REGISTRATION_UPDATER.compareAndSet(
                    this, currentPendingRegistration, newRegistration)) {
                newRegistration.startRegistration();
            } else {
                // concurrent modification
                newRegistration.cancel();
                return false;
            }

            // double check for concurrent close operations
            if (closed) {
                newRegistration.cancel();

                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * This method generate a specific Registration, for example TaskExecutor Registration at the
     * ResourceManager.
     * 该方法生成一个特定的 Registration，例如 ResourceManager 上的 TaskExecutor Registration。
     */
    protected abstract RetryingRegistration<F, G, S, R> generateRegistration();

    /** This method handle the Registration Response.
     * 此方法处理注册响应。
     * */
    protected abstract void onRegistrationSuccess(S success);

    /**
     * This method handles the Registration rejection.
     * 此方法处理注册拒绝。
     *
     * @param rejection rejection containing additional information about the rejection
     */
    protected abstract void onRegistrationRejection(R rejection);

    /** This method handle the Registration failure.
     * 此方法处理注册失败。
     * */
    protected abstract void onRegistrationFailure(Throwable failure);

    /** Close connection. */
    public void close() {
        closed = true;

        // make sure we do not keep re-trying forever
        if (pendingRegistration != null) {
            pendingRegistration.cancel();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------------
    //  Properties
    // ------------------------------------------------------------------------

    public F getTargetLeaderId() {
        return fencingToken;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    /** Gets the RegisteredGateway. This returns null until the registration is completed. */
    public G getTargetGateway() {
        return targetGateway;
    }

    public boolean isConnected() {
        return targetGateway != null;
    }

    // ------------------------------------------------------------------------

    @Override
    public String toString() {
        String connectionInfo =
                "(ADDRESS: " + targetAddress + " FENCINGTOKEN: " + fencingToken + ")";

        if (isConnected()) {
            connectionInfo =
                    "RPC connection to "
                            + targetGateway.getClass().getSimpleName()
                            + " "
                            + connectionInfo;
        } else {
            connectionInfo = "RPC connection to " + connectionInfo;
        }

        if (isClosed()) {
            connectionInfo += " is closed";
        } else if (isConnected()) {
            connectionInfo += " is established";
        } else {
            connectionInfo += " is connecting";
        }

        return connectionInfo;
    }

    // ------------------------------------------------------------------------
    //  Internal methods
    // ------------------------------------------------------------------------

    private RetryingRegistration<F, G, S, R> createNewRegistration() {
        RetryingRegistration<F, G, S, R> newRegistration = checkNotNull(generateRegistration());

        CompletableFuture<RetryingRegistration.RetryingRegistrationResult<G, S, R>> future =
                newRegistration.getFuture();

        future.whenCompleteAsync(
                (RetryingRegistration.RetryingRegistrationResult<G, S, R> result,
                        Throwable failure) -> {
                    if (failure != null) {
                        if (failure instanceof CancellationException) {
                            // we ignore cancellation exceptions because they originate from
                            // cancelling
                            // the RetryingRegistration
                            log.debug(
                                    "Retrying registration towards {} was cancelled.",
                                    targetAddress);
                        } else {
                            // this future should only ever fail if there is a bug, not if the
                            // registration is declined
                            onRegistrationFailure(failure);
                        }
                    } else {
                        if (result.isSuccess()) {
                            targetGateway = result.getGateway();
                            onRegistrationSuccess(result.getSuccess());
                        } else if (result.isRejection()) {
                            onRegistrationRejection(result.getRejection());
                        } else {
                            throw new IllegalArgumentException(
                                    String.format(
                                            "Unknown retrying registration response: %s.", result));
                        }
                    }
                },
                executor);

        return newRegistration;
    }
}
