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
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.concurrent.ScheduledFutureAdapter;
import org.apache.flink.util.AutoCloseableAsync;
import org.apache.flink.util.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Base class for RPC endpoints. Distributed components which offer remote procedure calls have to
 * extend the RPC endpoint base class. An RPC endpoint is backed by an {@link RpcService}.
 * RPC 端点的基类。 提供远程过程调用的分布式组件必须扩展 RPC 端点基类。 RPC 端点由 {@link RpcService} 支持。
 *
 * <h1>Endpoint and Gateway</h1>
 * 端点和网关
 *
 * To be done...
 *
 * <h1>Single Threaded Endpoint Execution </h1>
 * 单线程端点执行
 *
 * <p>All RPC calls on the same endpoint are called by the same thread (referred to as the
 * endpoint's <i>main thread</i>). Thus, by executing all state changing operations within the main
 * thread, we don't have to reason about concurrent accesses, in the same way in the Actor Model of
 * Erlang or Akka.
 * 同一端点上的所有 RPC 调用都由同一线程（称为端点的<i>主线程</i>）调用。
 * 因此，通过在主线程中执行所有状态更改操作，我们不必考虑并发访问，就像在 Erlang 或 Akka 的 Actor 模型中一样。
 *
 * <p>The RPC endpoint provides {@link #runAsync(Runnable)}, {@link #callAsync(Callable, Time)} and
 * the {@link #getMainThreadExecutor()} to execute code in the RPC endpoint's main thread.
 * RPC 端点提供 {@link #runAsync(Runnable)}、{@link #callAsync(Callable, Time)}
 * 和 {@link #getMainThreadExecutor()} 来在 RPC 端点的主线程中执行代码。
 *
 * <h1>Lifecycle</h1>
 *
 * <p>The RPC endpoint has the following stages:
 * RPC 端点具有以下阶段：
 *
 * <ul>
 *   <li>The RPC endpoint is created in a non-running state and does not serve any RPC requests.
 *   <li>Calling the {@link #start()} method triggers the start of the RPC endpoint and schedules
 *       overridable {@link #onStart()} method call to the main thread.
 *   <li>When the start operation ends the RPC endpoint is moved to the running state and starts to
 *       serve and complete RPC requests.
 *   <li>Calling the {@link #closeAsync()} method triggers the termination of the RPC endpoint and
 *       schedules overridable {@link #onStop()} method call to the main thread.
 *   <li>When {@link #onStop()} method is called, it triggers an asynchronous stop operation. The
 *       RPC endpoint is not in the running state anymore but it continues to serve RPC requests.
 *   <li>When the asynchronous stop operation ends, the RPC endpoint terminates completely and does
 *       not serve RPC requests anymore.
 * </ul>
 * <ul>
 *     <li>RPC 端点在非运行状态下创建，不服务任何 RPC 请求。
 *     <li>调用 {@link #start()} 方法会触发 RPC 端点的启动，并将可覆盖的 {@link #onStart()} 方法调用调度到主线程。
 *     <li>当启动操作结束时，RPC 端点被移动到运行状态并开始服务并完成 RPC 请求。
 *     <li>调用 {@link #closeAsync()} 方法会触发 RPC 端点的终止，并将可覆盖的 {@link #onStop()} 方法调用调度到主线程。
 *     <li>当调用{@link #onStop()} 方法时，会触发异步停止操作。 RPC 端点不再处于运行状态，但它继续为 RPC 请求提供服务。
 *     <li>当异步停止操作结束时，RPC 端点完全终止，不再服务 RPC 请求。
 *</ul>
 *
 * The running state can be queried in a RPC method handler or in the main thread by calling {@link
 * #isRunning()} method.
 * 运行状态可以在 RPC 方法处理程序中查询，也可以通过调用 {@link #isRunning()} 方法在主线程中查询。
 */
public abstract class RpcEndpoint implements RpcGateway, AutoCloseableAsync {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // ------------------------------------------------------------------------

    /** RPC service to be used to start the RPC server and to obtain rpc gateways.
     * 用于启动 RPC 服务器和获取 rpc 网关的 RPC 服务。
     * */
    private final RpcService rpcService;

    /** Unique identifier for this rpc endpoint. */
    private final String endpointId;

    /** Interface to access the underlying rpc server.
     * 访问底层 rpc 服务器的接口。
     * */
    protected final RpcServer rpcServer;

    /**
     * A reference to the endpoint's main thread, if the current method is called by the main
     * thread.
     * 如果当前方法由主线程调用，则对端点的主线程的引用。
     */
    final AtomicReference<Thread> currentMainThread = new AtomicReference<>(null);

    /**
     * The main thread executor to be used to execute future callbacks in the main thread of the
     * executing rpc server.
     * 主线程执行器，用于在正在执行的 rpc 服务器的主线程中执行未来的回调。
     */
    private final MainThreadExecutor mainThreadExecutor;

    /**
     * Indicates whether the RPC endpoint is started and not stopped or being stopped.
     * 指示 RPC 端点是已启动、未停止还是正在停止。
     *
     * <p>IMPORTANT: the running state is not thread safe and can be used only in the main thread of
     * the rpc endpoint.
     * 重要提示：运行状态不是线程安全的，只能在 rpc 端点的主线程中使用。
     */
    private boolean isRunning;

    /**
     * Initializes the RPC endpoint.
     * 初始化 RPC 端点。
     *
     * @param rpcService The RPC server that dispatches calls to this RPC endpoint.
     * @param endpointId Unique identifier for this endpoint
     */
    protected RpcEndpoint(final RpcService rpcService, final String endpointId) {
        this.rpcService = checkNotNull(rpcService, "rpcService");
        this.endpointId = checkNotNull(endpointId, "endpointId");

        this.rpcServer = rpcService.startServer(this);

        this.mainThreadExecutor = new MainThreadExecutor(rpcServer, this::validateRunsInMainThread);
    }

    /**
     * Initializes the RPC endpoint with a random endpoint id.
     * 使用随机端点 id 初始化 RPC 端点。
     *
     * @param rpcService The RPC server that dispatches calls to this RPC endpoint.
     */
    protected RpcEndpoint(final RpcService rpcService) {
        this(rpcService, UUID.randomUUID().toString());
    }

    /**
     * Returns the rpc endpoint's identifier.
     *
     * @return Rpc endpoint's identifier.
     */
    public String getEndpointId() {
        return endpointId;
    }

    /**
     * Returns whether the RPC endpoint is started and not stopped or being stopped.
     *
     * @return whether the RPC endpoint is started and not stopped or being stopped.
     */
    protected boolean isRunning() {
        validateRunsInMainThread();
        return isRunning;
    }

    // ------------------------------------------------------------------------
    //  Start & shutdown & lifecycle callbacks
    // ------------------------------------------------------------------------

    /**
     * Triggers start of the rpc endpoint. This tells the underlying rpc server that the rpc
     * endpoint is ready to process remote procedure calls.
     * 触发 rpc 端点的开始。 这告诉底层 rpc 服务器 rpc 端点已准备好处理远程过程调用。
     */
    public final void start() {
        rpcServer.start();
    }

    /**
     * Internal method which is called by the RpcService implementation to start the RpcEndpoint.
     * 由 RpcService 实现调用以启动 RpcEndpoint 的内部方法。
     *
     * @throws Exception indicating that the rpc endpoint could not be started. If an exception
     *     occurs, then the rpc endpoint will automatically terminate.
     */
    public final void internalCallOnStart() throws Exception {
        validateRunsInMainThread();
        isRunning = true;
        onStart();
    }

    /**
     * User overridable callback which is called from {@link #internalCallOnStart()}.
     * 从 {@link #internalCallOnStart()} 调用的用户可覆盖回调。
     *
     * <p>This method is called when the RpcEndpoint is being started. The method is guaranteed to
     * be executed in the main thread context and can be used to start the rpc endpoint in the
     * context of the rpc endpoint's main thread.
     * 在启动 RpcEndpoint 时调用此方法。
     * 该方法保证在主线程上下文中执行，并可用于在 rpc 端点的主线程的上下文中启动 rpc 端点。
     *
     * <p>IMPORTANT: This method should never be called directly by the user.
     * 重要提示：用户永远不应直接调用此方法。
     *
     * @throws Exception indicating that the rpc endpoint could not be started. If an exception
     *     occurs, then the rpc endpoint will automatically terminate.
     */
    protected void onStart() throws Exception {}

    /**
     * Triggers stop of the rpc endpoint. This tells the underlying rpc server that the rpc endpoint
     * is no longer ready to process remote procedure calls.
     * 触发 rpc 端点的停止。 这告诉底层 rpc 服务器 rpc 端点不再准备好处理远程过程调用。
     */
    protected final void stop() {
        rpcServer.stop();
    }

    /**
     * Internal method which is called by the RpcService implementation to stop the RpcEndpoint.
     * 由 RpcService 实现调用以停止 RpcEndpoint 的内部方法。
     *
     * @return Future which is completed once all post stop actions are completed. If an error
     *     occurs this future is completed exceptionally
     */
    public final CompletableFuture<Void> internalCallOnStop() {
        validateRunsInMainThread();
        CompletableFuture<Void> stopFuture = onStop();
        isRunning = false;
        return stopFuture;
    }

    /**
     * User overridable callback which is called from {@link #internalCallOnStop()}.
     * 从 {@link #internalCallOnStop()} 调用的用户可覆盖回调。
     *
     * <p>This method is called when the RpcEndpoint is being shut down. The method is guaranteed to
     * be executed in the main thread context and can be used to clean up internal state.
     * 在关闭 RpcEndpoint 时调用此方法。 该方法保证在主线程上下文中执行，可用于清理内部状态。
     *
     * <p>IMPORTANT: This method should never be called directly by the user.
     * 重要提示：用户永远不应直接调用此方法。
     *
     * @return Future which is completed once all post stop actions are completed. If an error
     *     occurs this future is completed exceptionally
     *     完成所有后停止操作后完成的未来。 如果发生错误，这个未来会异常完成
     */
    protected CompletableFuture<Void> onStop() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Triggers the shut down of the rpc endpoint. The shut down is executed asynchronously.
     *
     * <p>In order to wait on the completion of the shut down, obtain the termination future via
     * {@link #getTerminationFuture()}} and wait on its completion.
     */
    @Override
    public final CompletableFuture<Void> closeAsync() {
        rpcService.stopServer(rpcServer);
        return getTerminationFuture();
    }

    // ------------------------------------------------------------------------
    //  Basic RPC endpoint properties
    // ------------------------------------------------------------------------

    /**
     * Returns a self gateway of the specified type which can be used to issue asynchronous calls
     * against the RpcEndpoint.
     * 返回指定类型的自网关，可用于针对 RpcEndpoint 发出异步调用。
     *
     * <p>IMPORTANT: The self gateway type must be implemented by the RpcEndpoint. Otherwise the
     * method will fail.
     *
     * @param selfGatewayType class of the self gateway type
     * @param <C> type of the self gateway to create
     * @return Self gateway of the specified type which can be used to issue asynchronous rpcs
     */
    public <C extends RpcGateway> C getSelfGateway(Class<C> selfGatewayType) {
        if (selfGatewayType.isInstance(rpcServer)) {
            @SuppressWarnings("unchecked")
            C selfGateway = ((C) rpcServer);

            return selfGateway;
        } else {
            throw new RuntimeException(
                    "RpcEndpoint does not implement the RpcGateway interface of type "
                            + selfGatewayType
                            + '.');
        }
    }

    /**
     * Gets the address of the underlying RPC endpoint. The address should be fully qualified so
     * that a remote system can connect to this RPC endpoint via this address.
     * 获取底层 RPC 终结点的地址。 该地址应该是完全限定的，以便远程系统可以通过该地址连接到该 RPC 端点。
     *
     * @return Fully qualified address of the underlying RPC endpoint
     */
    @Override
    public String getAddress() {
        return rpcServer.getAddress();
    }

    /**
     * Gets the hostname of the underlying RPC endpoint.
     *
     * @return Hostname on which the RPC endpoint is running
     */
    @Override
    public String getHostname() {
        return rpcServer.getHostname();
    }

    /**
     * Gets the main thread execution context. The main thread execution context can be used to
     * execute tasks in the main thread of the underlying RPC endpoint.
     * 获取主线程执行上下文。 主线程执行上下文可用于在底层 RPC 端点的主线程中执行任务。
     *
     * @return Main thread execution context
     */
    protected MainThreadExecutor getMainThreadExecutor() {
        return mainThreadExecutor;
    }

    /**
     * Gets the endpoint's RPC service.
     *
     * @return The endpoint's RPC service
     */
    public RpcService getRpcService() {
        return rpcService;
    }

    /**
     * Return a future which is completed with true when the rpc endpoint has been terminated. In
     * case of a failure, this future is completed with the occurring exception.
     * 当 rpc 端点终止时，返回一个以 true 完成的未来。 在失败的情况下，这个未来会在发生异常的情况下完成。
     *
     * @return Future which is completed when the rpc endpoint has been terminated.
     */
    public CompletableFuture<Void> getTerminationFuture() {
        return rpcServer.getTerminationFuture();
    }

    // ------------------------------------------------------------------------
    //  Asynchronous executions
    // ------------------------------------------------------------------------

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint.
     *
     * @param runnable Runnable to be executed in the main thread of the underlying RPC endpoint
     */
    protected void runAsync(Runnable runnable) {
        rpcServer.runAsync(runnable);
    }

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint, with a delay of the
     * given number of milliseconds.
     *
     * @param runnable Runnable to be executed
     * @param delay The delay after which the runnable will be executed
     */
    protected void scheduleRunAsync(Runnable runnable, Time delay) {
        scheduleRunAsync(runnable, delay.getSize(), delay.getUnit());
    }

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint, with a delay of the
     * given number of milliseconds.
     *
     * @param runnable Runnable to be executed
     * @param delay The delay after which the runnable will be executed
     */
    protected void scheduleRunAsync(Runnable runnable, long delay, TimeUnit unit) {
        rpcServer.scheduleRunAsync(runnable, unit.toMillis(delay));
    }

    /**
     * Execute the callable in the main thread of the underlying RPC service, returning a future for
     * the result of the callable. If the callable is not completed within the given timeout, then
     * the future will be failed with a {@link TimeoutException}.
     * 在底层 RPC 服务的主线程中执行 callable，为 callable 的结果返回一个 future。
     * 如果在给定的超时时间内未完成可调用，则未来将失败并出现 {@link TimeoutException}。
     *
     * @param callable Callable to be executed in the main thread of the underlying rpc server
     * @param timeout Timeout for the callable to be completed
     * @param <V> Return type of the callable
     * @return Future for the result of the callable.
     */
    protected <V> CompletableFuture<V> callAsync(Callable<V> callable, Time timeout) {
        return rpcServer.callAsync(callable, timeout);
    }

    // ------------------------------------------------------------------------
    //  Main Thread Validation
    // ------------------------------------------------------------------------

    /**
     * Validates that the method call happens in the RPC endpoint's main thread.
     * 验证方法调用发生在 RPC 端点的主线程中。
     *
     * <p><b>IMPORTANT:</b> This check only happens when assertions are enabled, such as when
     * running tests.
     * <b>重要提示：</b>此检查仅在启用断言时发生，例如在运行测试时。
     *
     * <p>This can be used for additional checks, like
     * 这可用于其他检查，例如
     *
     * <pre>{@code
     * protected void concurrencyCriticalMethod() {
     *     validateRunsInMainThread();
     *
     *     // some critical stuff
     * }
     * }</pre>
     */
    public void validateRunsInMainThread() {
        assert MainThreadValidatorUtil.isRunningInExpectedThread(currentMainThread.get());
    }

    // ------------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------------

    /** Executor which executes runnables in the main thread context.
     * 在主线程上下文中执行 runnables 的 Executor。
     * */
    protected static class MainThreadExecutor implements ComponentMainThreadExecutor {

        private final MainThreadExecutable gateway;
        private final Runnable mainThreadCheck;

        MainThreadExecutor(MainThreadExecutable gateway, Runnable mainThreadCheck) {
            this.gateway = Preconditions.checkNotNull(gateway);
            this.mainThreadCheck = Preconditions.checkNotNull(mainThreadCheck);
        }

        private void scheduleRunAsync(Runnable runnable, long delayMillis) {
            gateway.scheduleRunAsync(runnable, delayMillis);
        }

        @Override
        public void execute(@Nonnull Runnable command) {
            gateway.runAsync(command);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            final long delayMillis = TimeUnit.MILLISECONDS.convert(delay, unit);
            FutureTask<Void> ft = new FutureTask<>(command, null);
            scheduleRunAsync(ft, delayMillis);
            return new ScheduledFutureAdapter<>(ft, delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            final long delayMillis = TimeUnit.MILLISECONDS.convert(delay, unit);
            FutureTask<V> ft = new FutureTask<>(callable);
            scheduleRunAsync(ft, delayMillis);
            return new ScheduledFutureAdapter<>(ft, delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException(
                    "Not implemented because the method is currently not required.");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException(
                    "Not implemented because the method is currently not required.");
        }

        @Override
        public void assertRunningInMainThread() {
            mainThreadCheck.run();
        }
    }
}
