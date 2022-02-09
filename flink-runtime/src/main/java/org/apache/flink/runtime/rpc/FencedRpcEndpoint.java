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

package org.apache.flink.runtime.rpc;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Base class for fenced {@link RpcEndpoint}. A fenced rpc endpoint expects all rpc messages being
 * enriched with fencing tokens. Furthermore, the rpc endpoint has its own fencing token assigned.
 * The rpc is then only executed if the attached fencing token equals the endpoint's own token.
 * 围栏 {@link RpcEndpoint} 的基类。 受防护的 rpc 端点期望所有 rpc 消息都使用防护令牌进行丰富。
 * 此外，rpc 端点分配了自己的防护令牌。 仅当附加的防护令牌等于端点自己的令牌时，才会执行 rpc。
 *
 * @param <F> type of the fencing token
 */
public abstract class FencedRpcEndpoint<F extends Serializable> extends RpcEndpoint {

    private final UnfencedMainThreadExecutor unfencedMainThreadExecutor;
    private volatile F fencingToken;
    private volatile MainThreadExecutor fencedMainThreadExecutor;

    protected FencedRpcEndpoint(
            RpcService rpcService, String endpointId, @Nullable F fencingToken) {
        super(rpcService, endpointId);

        Preconditions.checkArgument(
                rpcServer instanceof FencedMainThreadExecutable,
                "The rpcServer must be of type %s.",
                FencedMainThreadExecutable.class.getSimpleName());

        // no fencing token == no leadership
        this.fencingToken = fencingToken;
        this.unfencedMainThreadExecutor =
                new UnfencedMainThreadExecutor((FencedMainThreadExecutable) rpcServer);
        this.fencedMainThreadExecutor =
                new MainThreadExecutor(
                        getRpcService().fenceRpcServer(rpcServer, fencingToken),
                        this::validateRunsInMainThread);
    }

    protected FencedRpcEndpoint(RpcService rpcService, @Nullable F fencingToken) {
        this(rpcService, UUID.randomUUID().toString(), fencingToken);
    }

    public F getFencingToken() {
        return fencingToken;
    }

    protected void setFencingToken(@Nullable F newFencingToken) {
        // this method should only be called from within the main thread
        validateRunsInMainThread();

        this.fencingToken = newFencingToken;

        // setting a new fencing token entails that we need a new MainThreadExecutor
        // which is bound to the new fencing token
        MainThreadExecutable mainThreadExecutable =
                getRpcService().fenceRpcServer(rpcServer, newFencingToken);

        this.fencedMainThreadExecutor =
                new MainThreadExecutor(mainThreadExecutable, this::validateRunsInMainThread);
    }

    /**
     * Returns a main thread executor which is bound to the currently valid fencing token. This
     * means that runnables which are executed with this executor fail after the fencing token has
     * changed. This allows to scope operations by the fencing token.
     * 返回绑定到当前有效的防护令牌的主线程执行程序。
     * 这意味着使用此执行程序执行的可运行对象在防护令牌更改后会失败。 这允许通过防护令牌来限定操作范围。
     *
     * @return MainThreadExecutor bound to the current fencing token
     */
    @Override
    protected MainThreadExecutor getMainThreadExecutor() {
        return fencedMainThreadExecutor;
    }

    /**
     * Returns a main thread executor which is not bound to the fencing token. This means that
     * {@link Runnable} which are executed with this executor will always be executed.
     * 返回未绑定到围栏令牌的主线程执行程序。 这意味着使用此执行器执行的 {@link Runnable} 将始终被执行。
     *
     * @return MainThreadExecutor which is not bound to the fencing token
     */
    protected Executor getUnfencedMainThreadExecutor() {
        return unfencedMainThreadExecutor;
    }

    /**
     * Run the given runnable in the main thread of the RpcEndpoint without checking the fencing
     * token. This allows to run operations outside of the fencing token scope.
     * 在 RpcEndpoint 的主线程中运行给定的 runnable，而不检查 fencing 令牌。
     * 这允许在防护令牌范围之外运行操作。
     *
     * @param runnable to execute in the main thread of the rpc endpoint without checking the
     *     fencing token.
     */
    protected void runAsyncWithoutFencing(Runnable runnable) {
        if (rpcServer instanceof FencedMainThreadExecutable) {
            ((FencedMainThreadExecutable) rpcServer).runAsyncWithoutFencing(runnable);
        } else {
            throw new RuntimeException(
                    "FencedRpcEndpoint has not been started with a FencedMainThreadExecutable RpcServer.");
        }
    }

    /**
     * Run the given callable in the main thread of the RpcEndpoint without checking the fencing
     * token. This allows to run operations outside of the fencing token scope.
     * 在 RpcEndpoint 的主线程中运行给定的可调用对象，而不检查防护令牌。 这允许在防护令牌范围之外运行操作。
     *
     * @param callable to run in the main thread of the rpc endpoint without checking the fencing
     *     token.
     * @param timeout for the operation.
     * @return Future containing the callable result.
     */
    protected <V> CompletableFuture<V> callAsyncWithoutFencing(Callable<V> callable, Time timeout) {
        if (rpcServer instanceof FencedMainThreadExecutable) {
            return ((FencedMainThreadExecutable) rpcServer)
                    .callAsyncWithoutFencing(callable, timeout);
        } else {
            throw new RuntimeException(
                    "FencedRpcEndpoint has not been started with a FencedMainThreadExecutable RpcServer.");
        }
    }

    // ------------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------------

    /** Executor which executes {@link Runnable} in the main thread context without fencing.
     * 84 / 5,000
     * keyboard
     * 翻译结果
     * 在主线程上下文中执行 {@link Runnable} 的 Executor 没有围栏。
     * */
    private static class UnfencedMainThreadExecutor implements Executor {

        private final FencedMainThreadExecutable gateway;

        UnfencedMainThreadExecutor(FencedMainThreadExecutable gateway) {
            this.gateway = Preconditions.checkNotNull(gateway);
        }

        @Override
        public void execute(@Nonnull Runnable runnable) {
            gateway.runAsyncWithoutFencing(runnable);
        }
    }
}
