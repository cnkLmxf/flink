/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.state;

import org.apache.flink.annotation.Internal;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;

import javax.annotation.Nonnull;

import java.util.concurrent.RunnableFuture;

/**
 * Interface for objects that can snapshot its state (state backends currently). Implementing
 * classes should ideally be stateless or at least threadsafe, i.e. this is a functional interface
 * and is can be called in parallel by multiple checkpoints.
 * 可以快照其状态（当前状态后端）的对象的接口。 理想情况下，实现类应该是无状态的或至少是线程安全的，
 * 即这是一个功能接口，可以由多个检查点并行调用。
 *
 * @param <S> type of the returned state object that represents the result of the snapshot
 *     operation.
 * @see SnapshotStrategy
 * @see SnapshotStrategyRunner
 */
@Internal
public interface Snapshotable<S extends StateObject> {

    /**
     * Operation that writes a snapshot into a stream that is provided by the given {@link
     * CheckpointStreamFactory} and returns a @{@link RunnableFuture} that gives a state handle to
     * the snapshot. It is up to the implementation if the operation is performed synchronous or
     * asynchronous. In the later case, the returned Runnable must be executed first before
     * obtaining the handle.
     * 将快照写入由给定的 {@link CheckpointStreamFactory} 提供的流并返回 @{@link RunnableFuture} 的操作，
     * 它为快照提供状态句柄。 操作是同步执行还是异步执行取决于实现。
     * 在后一种情况下，必须先执行返回的 Runnable 才能获得句柄。
     *
     * @param checkpointId The ID of the checkpoint.
     * @param timestamp The timestamp of the checkpoint.
     * @param streamFactory The factory that we can use for writing our state to streams.
     * @param checkpointOptions Options for how to perform this checkpoint.
     * @return A runnable future that will yield a {@link StateObject}.
     */
    @Nonnull
    RunnableFuture<S> snapshot(
            long checkpointId,
            long timestamp,
            @Nonnull CheckpointStreamFactory streamFactory,
            @Nonnull CheckpointOptions checkpointOptions)
            throws Exception;
}
