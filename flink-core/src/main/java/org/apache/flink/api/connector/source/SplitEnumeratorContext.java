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

package org.apache.flink.api.connector.source;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.metrics.MetricGroup;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * A context class for the {@link SplitEnumerator}. This class serves the following purposes: 1.
 * Host information necessary for the SplitEnumerator to make split assignment decisions. 2. Accept
 * and track the split assignment from the enumerator. 3. Provide a managed threading model so the
 * split enumerators do not need to create their own internal threads.
 * {@link SplitEnumerator} 的上下文类。 此类用于以下目的： 1. SplitEnumerator 做出拆分分配决策所需的主机信息。
 * 2. 接受并跟踪来自枚举器的拆分分配。 3. 提供托管线程模型，因此拆分枚举器不需要创建自己的内部线程。
 *
 * @param <SplitT> the type of the splits.
 */
@PublicEvolving
public interface SplitEnumeratorContext<SplitT extends SourceSplit> {

    MetricGroup metricGroup();

    /**
     * Send a source event to a source reader. The source reader is identified by its subtask id.
     * 将源事件发送到源阅读器。 源阅读器由其子任务 ID 标识。
     *
     * @param subtaskId the subtask id of the source reader to send this event to.
     * @param event the source event to send.
     */
    void sendEventToSourceReader(int subtaskId, SourceEvent event);

    /**
     * Get the current parallelism of this Source. Note that due to auto-scaling, the parallelism
     * may change over time. Therefore the SplitEnumerator should not cache the return value of this
     * method, but always invoke this method to get the latest parallelism.
     * 获取此 Source 的当前并行度。 请注意，由于自动缩放，并行度可能会随着时间而改变。
     * 因此SplitEnumerator不应该缓存这个方法的返回值，而是总是调用这个方法来获取最新的并行度。
     *
     * @return the parallelism of the Source.
     */
    int currentParallelism();

    /**
     * Get the currently registered readers. The mapping is from subtask id to the reader info.
     * 获取当前注册的读者。 映射是从子任务 ID 到阅读器信息。
     *
     * @return the currently registered readers.
     */
    Map<Integer, ReaderInfo> registeredReaders();

    /**
     * Assign the splits.
     *
     * @param newSplitAssignments the new split assignments to add.
     */
    void assignSplits(SplitsAssignment<SplitT> newSplitAssignments);

    /**
     * Assigns a single split.
     *
     * <p>When assigning multiple splits, it is more efficient to assign all of them in a single
     * call to the {@link #assignSplits(SplitsAssignment)} method.
     * 分配多个拆分时，在一次调用 {@link #assignSplits(SplitsAssignment)} 方法时将所有拆分分配更为有效。
     *
     * @param split The new split
     * @param subtask The index of the operator's parallel subtask that shall receive the split.
     */
    default void assignSplit(SplitT split, int subtask) {
        assignSplits(new SplitsAssignment<>(split, subtask));
    }

    /**
     * Signals a subtask that it will not receive any further split.
     * 向子任务发出信号，表示它不会再收到任何拆分。
     *
     * @param subtask The index of the operator's parallel subtask that shall be signaled it will
     *     not receive any further split.
     */
    void signalNoMoreSplits(int subtask);

    /**
     * Invoke the callable and handover the return value to the handler which will be executed by
     * the source coordinator. When this method is invoked multiple times, The <code>Callable</code>
     * s may be executed in a thread pool concurrently.
     * 调用可调用对象并将返回值移交给将由源协调器执行的处理程序。
     * 当多次调用该方法时，<code>Callable</code> 可能会在线程池中并发执行。
     *
     * <p>It is important to make sure that the callable does not modify any shared state,
     * especially the states that will be a part of the {@link SplitEnumerator#snapshotState()}.
     * Otherwise the there might be unexpected behavior.
     * 确保可调用对象不会修改任何共享状态非常重要，
     * 尤其是将成为 {@link SplitEnumerator#snapshotState()} 一部分的状态。 否则可能会出现意外行为。
     *
     * <p>Note that an exception thrown from the handler would result in failing the job.
     * 请注意，处理程序抛出的异常将导致作业失败。
     *
     * @param callable a callable to call.
     * @param handler a handler that handles the return value of or the exception thrown from the
     *     callable.
     */
    <T> void callAsync(Callable<T> callable, BiConsumer<T, Throwable> handler);

    /**
     * Invoke the given callable periodically and handover the return value to the handler which
     * will be executed by the source coordinator. When this method is invoked multiple times, The
     * <code>Callable</code>s may be executed in a thread pool concurrently.
     * 定期调用给定的可调用对象并将返回值移交给将由源协调器执行的处理程序。
     * 当多次调用该方法时，<code>Callable</code>可能会在线程池中并发执行。
     *
     * <p>It is important to make sure that the callable does not modify any shared state,
     * especially the states that will be a part of the {@link SplitEnumerator#snapshotState()}.
     * Otherwise the there might be unexpected behavior.
     * 确保可调用对象不会修改任何共享状态非常重要，
     * 尤其是将成为 {@link SplitEnumerator#snapshotState()} 一部分的状态。 否则可能会出现意外行为。
     *
     * <p>Note that an exception thrown from the handler would result in failing the job.
     * 请注意，处理程序抛出的异常将导致作业失败。
     *
     * @param callable the callable to call.
     * @param handler a handler that handles the return value of or the exception thrown from the
     *     callable.
     * @param initialDelay the initial delay of calling the callable.
     * @param period the period between two invocations of the callable.
     */
    <T> void callAsync(
            Callable<T> callable, BiConsumer<T, Throwable> handler, long initialDelay, long period);

    /**
     * Invoke the given runnable in the source coordinator thread.
     * 在源协调器线程中调用给定的可运行对象。
     *
     * <p>This can be useful when the enumerator needs to execute some action (like assignSplits)
     * triggered by some external events. E.g., Watermark from another source advanced and this
     * source now be able to assign splits to awaiting readers. The trigger can be initiated from
     * the coordinator thread of the other source. Instead of using lock for thread safety, this API
     * allows to run such externally triggered action in the coordinator thread. Hence, we can
     * ensure all enumerator actions are serialized in the single coordinator thread.
     * 当枚举器需要执行由某些外部事件触发的某些操作（如 assignSplits）时，这可能很有用。
     * 例如，来自另一个来源的 Watermark 先进，这个来源现在能够将拆分分配给等待的读者。
     * 触发器可以从其他源的协调线程启动。 这个 API 允许在协调线程中运行这种外部触发的操作，而不是使用锁来保证线程安全。
     * 因此，我们可以确保所有枚举器操作都在单个协调器线程中序列化。
     *
     * <p>It is important that the runnable does not block.
     *
     * @param runnable a runnable to execute
     */
    void runInCoordinatorThread(Runnable runnable);
}
