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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Interface to execute {@link Runnable} and {@link Callable} in the main thread of the underlying
 * RPC endpoint.
 * 在底层 RPC 端点的主线程中执行 {@link Runnable} 和 {@link Callable} 的接口。
 *
 * <p>This interface is intended to be implemented by the self gateway in a {@link RpcEndpoint}
 * implementation which allows to dispatch local procedures to the main thread of the underlying RPC
 * endpoint.
 * 此接口旨在由自网关在 {@link RpcEndpoint} 实现中实现，该实现允许将本地过程分派到底层 RPC 端点的主线程。
 */
public interface MainThreadExecutable {

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint.
     *
     * @param runnable Runnable to be executed
     */
    void runAsync(Runnable runnable);

    /**
     * Execute the callable in the main thread of the underlying RPC endpoint and return a future
     * for the callable result. If the future is not completed within the given timeout, the
     * returned future will throw a {@link TimeoutException}.
     *
     * @param callable Callable to be executed
     * @param callTimeout Timeout for the future to complete
     * @param <V> Return value of the callable
     * @return Future of the callable result
     */
    <V> CompletableFuture<V> callAsync(Callable<V> callable, Time callTimeout);

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint, with a delay of the
     * given number of milliseconds.
     *
     * @param runnable Runnable to be executed
     * @param delay The delay, in milliseconds, after which the runnable will be executed
     */
    void scheduleRunAsync(Runnable runnable, long delay);
}
