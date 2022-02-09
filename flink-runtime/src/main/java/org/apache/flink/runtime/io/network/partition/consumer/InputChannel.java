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

package org.apache.flink.runtime.io.network.partition.consumer;

import org.apache.flink.metrics.Counter;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.checkpoint.channel.InputChannelInfo;
import org.apache.flink.runtime.event.TaskEvent;
import org.apache.flink.runtime.execution.CancelTaskException;
import org.apache.flink.runtime.io.network.api.CheckpointBarrier;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.PartitionException;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.io.network.partition.ResultSubpartitionView;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * An input channel consumes a single {@link ResultSubpartitionView}.
 * 输入通道使用单个 {@link ResultSubpartitionView}。
 *
 * <p>For each channel, the consumption life cycle is as follows:
 * 对于每个渠道，消费生命周期如下：
 *
 * <ol>
 *   <li>{@link #requestSubpartition(int)}
 *   <li>{@link #getNextBuffer()}
 *   <li>{@link #releaseAllResources()}
 * </ol>
 */
public abstract class InputChannel {
    /** The info of the input channel to identify it globally within a task.
     * 输入通道的信息，用于在任务中全局识别它。
     * */
    protected final InputChannelInfo channelInfo;

    protected final ResultPartitionID partitionId;

    protected final SingleInputGate inputGate;

    // - Asynchronous error notification --------------------------------------

    private final AtomicReference<Throwable> cause = new AtomicReference<Throwable>();

    // - Partition request backoff --------------------------------------------

    /** The initial backoff (in ms). */
    protected final int initialBackoff;

    /** The maximum backoff (in ms). */
    protected final int maxBackoff;

    protected final Counter numBytesIn;

    protected final Counter numBuffersIn;

    /** The current backoff (in ms). */
    private int currentBackoff;

    protected InputChannel(
            SingleInputGate inputGate,
            int channelIndex,
            ResultPartitionID partitionId,
            int initialBackoff,
            int maxBackoff,
            Counter numBytesIn,
            Counter numBuffersIn) {

        checkArgument(channelIndex >= 0);

        int initial = initialBackoff;
        int max = maxBackoff;

        checkArgument(initial >= 0 && initial <= max);

        this.inputGate = checkNotNull(inputGate);
        this.channelInfo = new InputChannelInfo(inputGate.getGateIndex(), channelIndex);
        this.partitionId = checkNotNull(partitionId);

        this.initialBackoff = initial;
        this.maxBackoff = max;
        this.currentBackoff = initial == 0 ? -1 : 0;

        this.numBytesIn = numBytesIn;
        this.numBuffersIn = numBuffersIn;
    }

    // ------------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------------

    /** Returns the index of this channel within its {@link SingleInputGate}.
     * 返回此通道在其 {@link SingleInputGate} 内的索引。
     * */
    public int getChannelIndex() {
        return channelInfo.getInputChannelIdx();
    }

    /**
     * Returns the info of this channel, which uniquely identifies the channel in respect to its
     * operator instance.
     * 返回此通道的信息，该信息相对于其操作员实例唯一标识通道。
     */
    public InputChannelInfo getChannelInfo() {
        return channelInfo;
    }

    public ResultPartitionID getPartitionId() {
        return partitionId;
    }

    /**
     * After sending a {@link org.apache.flink.runtime.io.network.api.CheckpointBarrier} of
     * exactly-once mode, the upstream will be blocked and become unavailable. This method tries to
     * unblock the corresponding upstream and resume data consumption.
     * 发送一个exactly-once模式的{@link org.apache.flink.runtime.io.network.api.CheckpointBarrier}后，
     * upstream会被阻塞，变得不可用。 该方法尝试解除对相应上游的阻塞，恢复数据消费。
     */
    public abstract void resumeConsumption() throws IOException;

    /**
     * Notifies the owning {@link SingleInputGate} that this channel became non-empty.
     * 通知拥有的 {@link SingleInputGate} 该通道变为非空。
     *
     * <p>This is guaranteed to be called only when a Buffer was added to a previously empty input
     * channel. The notion of empty is atomically consistent with the flag {@link
     * BufferAndAvailability#moreAvailable()} when polling the next buffer from this channel.
     * 保证仅在将 Buffer 添加到先前为空的输入通道时才调用它。
     * 当从该通道轮询下一个缓冲区时，空的概念在原子上与标志 {@link BufferAndAvailability#moreAvailable()} 一致。
     *
     * <p><b>Note:</b> When the input channel observes an exception, this method is called
     * regardless of whether the channel was empty before. That ensures that the parent InputGate
     * will always be notified about the exception.
     * <b>注意：</b>当输入通道观察到异常时，无论之前通道是否为空，都会调用该方法。
     * 这确保了父 InputGate 将始终收到有关异常的通知。
     */
    protected void notifyChannelNonEmpty() {
        inputGate.notifyChannelNonEmpty(this);
    }

    public void notifyPriorityEvent(int priorityBufferNumber) {
        inputGate.notifyPriorityEvent(this, priorityBufferNumber);
    }

    protected void notifyBufferAvailable(int numAvailableBuffers) throws IOException {}

    // ------------------------------------------------------------------------
    // Consume
    // ------------------------------------------------------------------------

    /**
     * Requests the queue with the specified index of the source intermediate result partition.
     * 请求具有源中间结果分区的指定索引的队列。
     *
     * <p>The queue index to request depends on which sub task the channel belongs to and is
     * specified by the consumer of this channel.
     * 请求的队列索引取决于通道属于哪个子任务，由该通道的消费者指定。
     */
    abstract void requestSubpartition(int subpartitionIndex)
            throws IOException, InterruptedException;

    /**
     * Returns the next buffer from the consumed subpartition or {@code Optional.empty()} if there
     * is no data to return.
     * 如果没有要返回的数据，则从使用的子分区或 {@code Optional.empty()} 返回下一个缓冲区。
     */
    abstract Optional<BufferAndAvailability> getNextBuffer()
            throws IOException, InterruptedException;

    /**
     * Called by task thread when checkpointing is started (e.g., any input channel received
     * barrier).
     * 启动检查点时由任务线程调用（例如，任何输入通道收到屏障）。
     */
    public void checkpointStarted(CheckpointBarrier barrier) throws CheckpointException {}

    /** Called by task thread on cancel/complete to clean-up temporary data.
     * 由任务线程在取消/完成时调用以清理临时数据。
     * */
    public void checkpointStopped(long checkpointId) {}

    public void convertToPriorityEvent(int sequenceNumber) throws IOException {}

    // ------------------------------------------------------------------------
    // Task events
    // ------------------------------------------------------------------------

    /**
     * Sends a {@link TaskEvent} back to the task producing the consumed result partition.
     * 将 {@link TaskEvent} 发送回产生消费结果分区的任务。
     *
     * <p><strong>Important</strong>: The producing task has to be running to receive backwards
     * events. This means that the result type needs to be pipelined and the task logic has to
     * ensure that the producer will wait for all backwards events. Otherwise, this will lead to an
     * Exception at runtime.
     * <strong>重要提示</strong>：生产任务必须正在运行才能接收向后事件。
     * 这意味着结果类型需要流水线化，并且任务逻辑必须确保生产者将等待所有向后事件。 否则，这将导致运行时异常。
     */
    abstract void sendTaskEvent(TaskEvent event) throws IOException;

    // ------------------------------------------------------------------------
    // Life cycle
    // ------------------------------------------------------------------------

    abstract boolean isReleased();

    /** Releases all resources of the channel. */
    abstract void releaseAllResources() throws IOException;

    // ------------------------------------------------------------------------
    // Error notification
    // ------------------------------------------------------------------------

    /**
     * Checks for an error and rethrows it if one was reported.
     * 检查错误并在报告错误时重新抛出它。
     *
     * <p>Note: Any {@link PartitionException} instances should not be transformed and make sure
     * they are always visible in task failure cause.
     * 注意：不应转换任何 {@link PartitionException} 实例，并确保它们在任务失败原因中始终可见。
     */
    protected void checkError() throws IOException {
        final Throwable t = cause.get();

        if (t != null) {
            if (t instanceof CancelTaskException) {
                throw (CancelTaskException) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException(t);
            }
        }
    }

    /**
     * Atomically sets an error for this channel and notifies the input gate about available data to
     * trigger querying this channel by the task thread.
     * 原子地为此通道设置错误并通知输入门有关可用数据以触发任务线程查询此通道。
     */
    protected void setError(Throwable cause) {
        if (this.cause.compareAndSet(null, checkNotNull(cause))) {
            // Notify the input gate.
            notifyChannelNonEmpty();
        }
    }

    // ------------------------------------------------------------------------
    // Partition request exponential backoff
    // ------------------------------------------------------------------------

    /** Returns the current backoff in ms. */
    protected int getCurrentBackoff() {
        return currentBackoff <= 0 ? 0 : currentBackoff;
    }

    /**
     * Increases the current backoff and returns whether the operation was successful.
     * 增加当前退避并返回操作是否成功。
     *
     * @return <code>true</code>, iff the operation was successful. Otherwise, <code>false</code>.
     */
    protected boolean increaseBackoff() {
        // Backoff is disabled
        if (currentBackoff < 0) {
            return false;
        }

        // This is the first time backing off
        if (currentBackoff == 0) {
            currentBackoff = initialBackoff;

            return true;
        }

        // Continue backing off
        else if (currentBackoff < maxBackoff) {
            currentBackoff = Math.min(currentBackoff * 2, maxBackoff);

            return true;
        }

        // Reached maximum backoff
        return false;
    }

    // ------------------------------------------------------------------------
    // Metric related method
    // ------------------------------------------------------------------------

    public int unsynchronizedGetNumberOfQueuedBuffers() {
        return 0;
    }

    // ------------------------------------------------------------------------

    /**
     * A combination of a {@link Buffer} and a flag indicating availability of further buffers, and
     * the backlog length indicating how many non-event buffers are available in the subpartition.
     * {@link Buffer} 和指示更多缓冲区可用性的标志的组合，以及指示子分区中有多少非事件缓冲区可用的积压长度。
     */
    public static final class BufferAndAvailability {

        private final Buffer buffer;
        private final Buffer.DataType nextDataType;
        private final int buffersInBacklog;
        private final int sequenceNumber;

        public BufferAndAvailability(
                Buffer buffer,
                Buffer.DataType nextDataType,
                int buffersInBacklog,
                int sequenceNumber) {
            this.buffer = checkNotNull(buffer);
            this.nextDataType = checkNotNull(nextDataType);
            this.buffersInBacklog = buffersInBacklog;
            this.sequenceNumber = sequenceNumber;
        }

        public Buffer buffer() {
            return buffer;
        }

        public boolean moreAvailable() {
            return nextDataType != Buffer.DataType.NONE;
        }

        public boolean morePriorityEvents() {
            return nextDataType.hasPriority();
        }

        public int buffersInBacklog() {
            return buffersInBacklog;
        }

        public boolean hasPriority() {
            return buffer.getDataType().hasPriority();
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }

        @Override
        public String toString() {
            return "BufferAndAvailability{"
                    + "buffer="
                    + buffer
                    + ", nextDataType="
                    + nextDataType
                    + ", buffersInBacklog="
                    + buffersInBacklog
                    + ", sequenceNumber="
                    + sequenceNumber
                    + '}';
        }
    }

    void setup() throws IOException {}
}
