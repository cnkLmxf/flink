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

package org.apache.flink.streaming.api.functions.source;

import org.apache.flink.annotation.Public;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.functions.TimestampAssigner;
import org.apache.flink.streaming.api.watermark.Watermark;

import java.io.Serializable;

/**
 * Base interface for all stream data sources in Flink. The contract of a stream source is the
 * following: When the source should start emitting elements, the {@link #run} method is called with
 * a {@link SourceContext} that can be used for emitting elements. The run method can run for as
 * long as necessary. The source must, however, react to an invocation of {@link #cancel()} by
 * breaking out of its main loop.
 * Flink 中所有流数据源的基本接口。 流源的契约如下：
 * 当源应该开始发射元素时，{@link #run} 方法被调用，并带有可用于发射元素的 {@link SourceContext}。
 * run 方法可以根据需要运行。 但是，源必须通过跳出其主循环来对 {@link #cancel()} 的调用做出反应。
 *
 * <h3>CheckpointedFunction Sources</h3>
 *
 * <p>Sources that also implement the {@link
 * org.apache.flink.streaming.api.checkpoint.CheckpointedFunction} interface must ensure that state
 * checkpointing, updating of internal state and emission of elements are not done concurrently.
 * This is achieved by using the provided checkpointing lock object to protect update of state and
 * emission of elements in a synchronized block.
 * 同样实现 {@link org.apache.flink.streaming.api.checkpoint.CheckpointedFunction}
 * 接口的source必须确保状态检查点、内部状态更新和元素发射不会同时进行。
 * 这是通过使用提供的检查点锁定对象来保护同步块中的状态更新和元素发射来实现的。
 *
 * <p>This is the basic pattern one should follow when implementing a checkpointed source:
 * 这是实现检查点源时应遵循的基本模式：
 *
 * <pre>{@code
 *  public class ExampleCountSource implements SourceFunction<Long>, CheckpointedFunction {
 *      private long count = 0L;
 *      private volatile boolean isRunning = true;
 *
 *      private transient ListState<Long> checkpointedCount;
 *
 *      public void run(SourceContext<T> ctx) {
 *          while (isRunning && count < 1000) {
 *              // this synchronized block ensures that state checkpointing,
 *              // internal state updates and emission of elements are an atomic operation
 *              // 这个同步块确保状态检查点、内部状态更新和元素的发射是一个原子操作
 *              synchronized (ctx.getCheckpointLock()) {
 *                  ctx.collect(count);
 *                  count++;
 *              }
 *          }
 *      }
 *
 *      public void cancel() {
 *          isRunning = false;
 *      }
 *
 *      public void initializeState(FunctionInitializationContext context) {
 *          this.checkpointedCount = context
 *              .getOperatorStateStore()
 *              .getListState(new ListStateDescriptor<>("count", Long.class));
 *
 *          if (context.isRestored()) {
 *              for (Long count : this.checkpointedCount.get()) {
 *                  this.count = count;
 *              }
 *          }
 *      }
 *
 *      public void snapshotState(FunctionSnapshotContext context) {
 *          this.checkpointedCount.clear();
 *          this.checkpointedCount.add(count);
 *      }
 * }
 * }</pre>
 *
 * <h3>Timestamps and watermarks:</h3>
 *
 * <p>Sources may assign timestamps to elements and may manually emit watermarks. However, these are
 * only interpreted if the streaming program runs on {@link TimeCharacteristic#EventTime}. On other
 * time characteristics ({@link TimeCharacteristic#IngestionTime} and {@link
 * TimeCharacteristic#ProcessingTime}), the watermarks from the source function are ignored.
 * sources 可以为元素分配时间戳，并可以手动发出水印。
 * 但是，只有在流程序在 {@link TimeCharacteristic#EventTime} 上运行时才会解释这些。
 * 在其他时间特征（{@link TimeCharacteristic#IngestionTime} 和 {@link TimeCharacteristic#ProcessingTime}）上，来自sourceFunction的水印被忽略。
 *
 * @param <T> The type of the elements produced by this source.
 * @see org.apache.flink.streaming.api.TimeCharacteristic
 */
@Public
public interface SourceFunction<T> extends Function, Serializable {

    /**
     * Starts the source. Implementations can use the {@link SourceContext} emit elements.
     * 启动source。 实现可以使用 {@link SourceContext} 发射元素。
     *
     * <p>Sources that implement {@link
     * org.apache.flink.streaming.api.checkpoint.CheckpointedFunction} must lock on the checkpoint
     * lock (using a synchronized block) before updating internal state and emitting elements, to
     * make both an atomic operation:
     * 实现 {@link org.apache.flink.streaming.api.checkpoint.CheckpointedFunction} 的源
     * 必须在更新内部状态和发射元素之前锁定检查点锁（使用同步块），以使两者成为原子操作：
     *
     * <pre>{@code
     *  public class ExampleCountSource implements SourceFunction<Long>, CheckpointedFunction {
     *      private long count = 0L;
     *      private volatile boolean isRunning = true;
     *
     *      private transient ListState<Long> checkpointedCount;
     *
     *      public void run(SourceContext<T> ctx) {
     *          while (isRunning && count < 1000) {
     *              // this synchronized block ensures that state checkpointing,
     *              // internal state updates and emission of elements are an atomic operation
     *              synchronized (ctx.getCheckpointLock()) {
     *                  ctx.collect(count);
     *                  count++;
     *              }
     *          }
     *      }
     *
     *      public void cancel() {
     *          isRunning = false;
     *      }
     *
     *      public void initializeState(FunctionInitializationContext context) {
     *          this.checkpointedCount = context
     *              .getOperatorStateStore()
     *              .getListState(new ListStateDescriptor<>("count", Long.class));
     *
     *          if (context.isRestored()) {
     *              for (Long count : this.checkpointedCount.get()) {
     *                  this.count = count;
     *              }
     *          }
     *      }
     *
     *      public void snapshotState(FunctionSnapshotContext context) {
     *          this.checkpointedCount.clear();
     *          this.checkpointedCount.add(count);
     *      }
     * }
     * }</pre>
     *
     * @param ctx The context to emit elements to and for accessing locks.
     */
    void run(SourceContext<T> ctx) throws Exception;

    /**
     * Cancels the source. Most sources will have a while loop inside the {@link
     * #run(SourceContext)} method. The implementation needs to ensure that the source will break
     * out of that loop after this method is called.
     * 取消源。 大多数源代码在 {@link #run(SourceContext)} 方法中有一个 while 循环。 实现需要确保在调用此方法后source会跳出该循环。
     *
     * <p>A typical pattern is to have an {@code "volatile boolean isRunning"} flag that is set to
     * {@code false} in this method. That flag is checked in the loop condition.
     * 一个典型的模式是在此方法中将 {@code "volatile boolean isRunning"} 标志设置为 {@code false}。 在循环条件中检查该标志。
     *
     * <p>When a source is canceled, the executing thread will also be interrupted (via {@link
     * Thread#interrupt()}). The interruption happens strictly after this method has been called, so
     * any interruption handler can rely on the fact that this method has completed. It is good
     * practice to make any flags altered by this method "volatile", in order to guarantee the
     * visibility of the effects of this method to any interruption handler.
     * 当一个源被取消时，正在执行的线程也将被中断（通过 {@link Thread#interrupt()}）。
     * 中断严格发生在调用此方法之后，因此任何中断处理程序都可以依赖于此方法已完成的事实。
     * 使此方法更改的任何标志为“volatile”是一种很好的做法，以保证此方法的效果对任何中断处理程序都是可见的。
     */
    void cancel();

    // ------------------------------------------------------------------------
    //  source context
    // ------------------------------------------------------------------------

    /**
     * Interface that source functions use to emit elements, and possibly watermarks.
     * 源函数用来发出元素和可能的水印的接口。
     *
     * @param <T> The type of the elements produced by the source.
     */
    @Public // Interface might be extended in the future with additional methods.
    interface SourceContext<T> {

        /**
         * Emits one element from the source, without attaching a timestamp. In most cases, this is
         * the default way of emitting elements.
         * 从源发出一个元素，不附加时间戳。 在大多数情况下，这是发射元素的默认方式。
         *
         * <p>The timestamp that the element will get assigned depends on the time characteristic of
         * the streaming program:
         * 元素将被分配的时间戳取决于流媒体程序的时间特征：
         *
         * <ul>
         *   <li>On {@link TimeCharacteristic#ProcessingTime}, the element has no timestamp.
         *   <li>On {@link TimeCharacteristic#IngestionTime}, the element gets the system's current
         *       time as the timestamp.
         *   <li>On {@link TimeCharacteristic#EventTime}, the element will have no timestamp
         *       initially. It needs to get a timestamp (via a {@link TimestampAssigner}) before any
         *       time-dependent operation (like time windows).
         *       该元素最初将没有时间戳。 它需要在任何依赖于时间的操作（如时间窗口）之前获取时间戳（通过 {@link TimestampAssigner}）。
         * </ul>
         *
         * @param element The element to emit
         */
        void collect(T element);

        /**
         * Emits one element from the source, and attaches the given timestamp. This method is
         * relevant for programs using {@link TimeCharacteristic#EventTime}, where the sources
         * assign timestamps themselves, rather than relying on a {@link TimestampAssigner} on the
         * stream.
         * 从源发出一个元素，并附加给定的时间戳。
         * 此方法与使用 {@link TimeCharacteristic#EventTime} 的程序相关，其中source自己分配时间戳，而不是依赖流上的 {@link TimestampAssigner}。
         *
         * <p>On certain time characteristics, this timestamp may be ignored or overwritten. This
         * allows programs to switch between the different time characteristics and behaviors
         * without changing the code of the source functions.
         * 在某些时间特征上，此时间戳可能会被忽略或覆盖。 这允许程序在不同的时间特征和行为之间切换，而无需更改sourceFunction的代码。
         *
         * <ul>
         *   <li>On {@link TimeCharacteristic#ProcessingTime}, the timestamp will be ignored,
         *       because processing time never works with element timestamps.
         *       时间戳将被忽略，因为处理时间永远不会与元素时间戳一起使用。
         *   <li>On {@link TimeCharacteristic#IngestionTime}, the timestamp is overwritten with the
         *       system's current time, to realize proper ingestion time semantics.
         *       时间戳被系统的当前时间覆盖，以实现正确的摄取时间语义。
         *   <li>On {@link TimeCharacteristic#EventTime}, the timestamp will be used.
         * </ul>
         *
         * @param element The element to emit
         * @param timestamp The timestamp in milliseconds since the Epoch
         *                  自 Epoch 以来的时间戳（以毫秒为单位）
         */
        @PublicEvolving
        void collectWithTimestamp(T element, long timestamp);

        /**
         * Emits the given {@link Watermark}. A Watermark of value {@code t} declares that no
         * elements with a timestamp {@code t' <= t} will occur any more. If further such elements
         * will be emitted, those elements are considered <i>late</i>.
         * 发出给定的 {@link Watermark}。 值为 {@code t} 的 Watermark 声明不会再出现带有时间戳 {@code t' <= t} 的元素。
         * 如果将发出更多此类元素，则将这些元素视为 <i>late</i>。
         *
         * <p>This method is only relevant when running on {@link TimeCharacteristic#EventTime}. On
         * {@link TimeCharacteristic#ProcessingTime},Watermarks will be ignored. On {@link
         * TimeCharacteristic#IngestionTime}, the Watermarks will be replaced by the automatic
         * ingestion time watermarks.
         * 此方法仅在 {@link TimeCharacteristic#EventTime} 上运行时相关。
         * 在 {@link TimeCharacteristic#ProcessingTime} 上，水印将被忽略。
         * 在 {@link TimeCharacteristic#IngestionTime} 上，水印将被自动摄取时间水印替换。
         *
         * @param mark The Watermark to emit
         */
        @PublicEvolving
        void emitWatermark(Watermark mark);

        /**
         * Marks the source to be temporarily idle. This tells the system that this source will
         * temporarily stop emitting records and watermarks for an indefinite amount of time. This
         * is only relevant when running on {@link TimeCharacteristic#IngestionTime} and {@link
         * TimeCharacteristic#EventTime}, allowing downstream tasks to advance their watermarks
         * without the need to wait for watermarks from this source while it is idle.
         * 将源标记为暂时空闲。 这告诉系统该源将无限期地暂时停止发出记录和水印。
         * 这仅在 {@link TimeCharacteristic#IngestionTime} 和 {@link TimeCharacteristic#EventTime} 上运行时相关，
         * 允许下游任务推进其水印，而无需在空闲时等待来自该source的水印。
         *
         * <p>Source functions should make a best effort to call this method as soon as they
         * acknowledge themselves to be idle. The system will consider the source to resume activity
         * again once {@link SourceContext#collect(T)}, {@link SourceContext#collectWithTimestamp(T,
         * long)}, or {@link SourceContext#emitWatermark(Watermark)} is called to emit elements or
         * watermarks from the source.
         * 源函数应尽最大努力在确认自己处于空闲状态后立即调用此方法。
         * 一旦 {@link SourceContext#collect(T)}、{@link SourceContext#collectWithTimestamp(T, long)}
         * 或 {@link SourceContext#emitWatermark(Watermark)} 被调用以发出来自source的元素或水印。
         */
        @PublicEvolving
        void markAsTemporarilyIdle();

        /**
         * Returns the checkpoint lock. Please refer to the class-level comment in {@link
         * SourceFunction} for details about how to write a consistent checkpointed source.
         * 返回检查点锁。 有关如何编写一致的检查点源的详细信息，请参阅 {@link SourceFunction} 中的类级注释。
         *
         * @return The object to use as the lock
         */
        Object getCheckpointLock();

        /** This method is called by the system to shut down the context.
         * 系统调用此方法来关闭上下文。
         * */
        void close();
    }
}
