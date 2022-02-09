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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.runtime.executiongraph.IntermediateResultPartition;
import org.apache.flink.runtime.io.network.api.writer.ResultPartitionWriter;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.buffer.BufferCompressor;
import org.apache.flink.runtime.io.network.buffer.BufferPool;
import org.apache.flink.runtime.io.network.partition.consumer.LocalInputChannel;
import org.apache.flink.runtime.io.network.partition.consumer.RemoteInputChannel;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;
import org.apache.flink.runtime.taskexecutor.TaskExecutor;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.function.SupplierWithException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * A result partition for data produced by a single task.
 * 单个任务生成的数据的结果分区。
 *
 * <p>This class is the runtime part of a logical {@link IntermediateResultPartition}. Essentially,
 * a result partition is a collection of {@link Buffer} instances. The buffers are organized in one
 * or more {@link ResultSubpartition} instances or in a joint structure which further partition the
 * data depending on the number of consuming tasks and the data {@link DistributionPattern}.
 * 此类是逻辑 {@link IntermediateResultPartition} 的运行时部分。
 * 本质上，结果分区是 {@link Buffer} 实例的集合。 缓冲区被组织在一个或多个 {@link ResultSubpartition} 实例或联合结构中，
 * 根据消费任务的数量和数据 {@link DistributionPattern} 进一步划分数据。
 *
 * <p>Tasks, which consume a result partition have to request one of its subpartitions. The request
 * happens either remotely (see {@link RemoteInputChannel}) or locally (see {@link
 * LocalInputChannel})
 * 消耗结果分区的任务必须请求其子分区之一。
 * 请求发生在远程（参见 {@link RemoteInputChannel}）或本地（参见 {@link LocalInputChannel}）
 *
 * <h2>Life-cycle</h2>
 *
 * <p>The life-cycle of each result partition has three (possibly overlapping) phases:
 * 每个结果分区的生命周期具有三个（可能重叠）阶段：
 *
 * <ol>
 *   <li><strong>Produce</strong>:
 *   <li><strong>Consume</strong>:
 *   <li><strong>Release</strong>:
 * </ol>
 *
 * <h2>Buffer management</h2>
 *
 * <h2>State management</h2>
 */
public abstract class ResultPartition implements ResultPartitionWriter {

    protected static final Logger LOG = LoggerFactory.getLogger(ResultPartition.class);

    private final String owningTaskName;

    private final int partitionIndex;

    protected final ResultPartitionID partitionId;

    /** Type of this partition. Defines the concrete subpartition implementation to use. */
    protected final ResultPartitionType partitionType;

    protected final ResultPartitionManager partitionManager;

    protected final int numSubpartitions;

    private final int numTargetKeyGroups;

    // - Runtime state --------------------------------------------------------

    private final AtomicBoolean isReleased = new AtomicBoolean();

    protected BufferPool bufferPool;

    private boolean isFinished;

    private volatile Throwable cause;

    private final SupplierWithException<BufferPool, IOException> bufferPoolFactory;

    /** Used to compress buffer to reduce IO.
     * 用于压缩缓冲区以减少 IO。
     * */
    @Nullable protected final BufferCompressor bufferCompressor;

    protected Counter numBytesOut = new SimpleCounter();

    protected Counter numBuffersOut = new SimpleCounter();

    public ResultPartition(
            String owningTaskName,
            int partitionIndex,
            ResultPartitionID partitionId,
            ResultPartitionType partitionType,
            int numSubpartitions,
            int numTargetKeyGroups,
            ResultPartitionManager partitionManager,
            @Nullable BufferCompressor bufferCompressor,
            SupplierWithException<BufferPool, IOException> bufferPoolFactory) {

        this.owningTaskName = checkNotNull(owningTaskName);
        Preconditions.checkArgument(0 <= partitionIndex, "The partition index must be positive.");
        this.partitionIndex = partitionIndex;
        this.partitionId = checkNotNull(partitionId);
        this.partitionType = checkNotNull(partitionType);
        this.numSubpartitions = numSubpartitions;
        this.numTargetKeyGroups = numTargetKeyGroups;
        this.partitionManager = checkNotNull(partitionManager);
        this.bufferCompressor = bufferCompressor;
        this.bufferPoolFactory = bufferPoolFactory;
    }

    /**
     * Registers a buffer pool with this result partition.
     * 使用此结果分区注册一个缓冲池。
     *
     * <p>There is one pool for each result partition, which is shared by all its sub partitions.
     * 每个结果分区都有一个池，由其所有子分区共享。
     *
     * <p>The pool is registered with the partition *after* it as been constructed in order to
     * conform to the life-cycle of task registrations in the {@link TaskExecutor}.
     * 池在*构造后*在分区中注册，以符合 {@link TaskExecutor} 中任务注册的生命周期。
     */
    @Override
    public void setup() throws IOException {
        checkState(
                this.bufferPool == null,
                "Bug in result partition setup logic: Already registered buffer pool.");

        this.bufferPool = checkNotNull(bufferPoolFactory.get());
        partitionManager.registerResultPartition(this);
    }

    public String getOwningTaskName() {
        return owningTaskName;
    }

    @Override
    public ResultPartitionID getPartitionId() {
        return partitionId;
    }

    public int getPartitionIndex() {
        return partitionIndex;
    }

    @Override
    public int getNumberOfSubpartitions() {
        return numSubpartitions;
    }

    public BufferPool getBufferPool() {
        return bufferPool;
    }

    /** Returns the total number of queued buffers of all subpartitions.
     * 返回所有子分区的队列缓冲区总数。
     * */
    public abstract int getNumberOfQueuedBuffers();

    /** Returns the number of queued buffers of the given target subpartition.
     * 返回给定目标子分区的排队缓冲区数。
     * */
    public abstract int getNumberOfQueuedBuffers(int targetSubpartition);

    /**
     * Returns the type of this result partition.
     * 返回此结果分区的类型。
     *
     * @return result partition type
     */
    public ResultPartitionType getPartitionType() {
        return partitionType;
    }

    // ------------------------------------------------------------------------

    /**
     * Finishes the result partition.
     * 完成结果分区。
     *
     * <p>After this operation, it is not possible to add further data to the result partition.
     * 此操作后，无法将更多数据添加到结果分区。
     *
     * <p>For BLOCKING results, this will trigger the deployment of consuming tasks.
     * 对于 BLOCKING 结果，这将触发消费任务的部署。
     */
    @Override
    public void finish() throws IOException {
        checkInProduceState();

        isFinished = true;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    public void release() {
        release(null);
    }

    @Override
    public void release(Throwable cause) {
        if (isReleased.compareAndSet(false, true)) {
            LOG.debug("{}: Releasing {}.", owningTaskName, this);

            // Set the error cause
            if (cause != null) {
                this.cause = cause;
            }

            releaseInternal();
        }
    }

    /** Releases all produced data including both those stored in memory and persisted on disk.
     * 释放所有生成的数据，包括存储在内存和磁盘上的数据。
     * */
    protected abstract void releaseInternal();

    @Override
    public void close() {
        if (bufferPool != null) {
            bufferPool.lazyDestroy();
        }
    }

    @Override
    public void fail(@Nullable Throwable throwable) {
        partitionManager.releasePartition(partitionId, throwable);
    }

    public Throwable getFailureCause() {
        return cause;
    }

    @Override
    public int getNumTargetKeyGroups() {
        return numTargetKeyGroups;
    }

    @Override
    public void setMetricGroup(TaskIOMetricGroup metrics) {
        numBytesOut = metrics.getNumBytesOutCounter();
        numBuffersOut = metrics.getNumBuffersOutCounter();
    }

    /**
     * Whether this partition is released.
     * 该分区是否被释放。
     *
     * <p>A partition is released when each subpartition is either consumed and communication is
     * closed by consumer or failed. A partition is also released if task is cancelled.
     * 当每个子分区被消费并且通信被消费者关闭或失败时，一个分区被释放。 如果任务被取消，分区也会被释放。
     */
    @Override
    public boolean isReleased() {
        return isReleased.get();
    }

    @Override
    public CompletableFuture<?> getAvailableFuture() {
        return bufferPool.getAvailableFuture();
    }

    @Override
    public String toString() {
        return "ResultPartition "
                + partitionId.toString()
                + " ["
                + partitionType
                + ", "
                + numSubpartitions
                + " subpartitions]";
    }

    // ------------------------------------------------------------------------

    /** Notification when a subpartition is released.
     * 释放子分区时的通知。
     * */
    void onConsumedSubpartition(int subpartitionIndex) {

        if (isReleased.get()) {
            return;
        }

        LOG.debug(
                "{}: Received release notification for subpartition {}.", this, subpartitionIndex);
    }

    // ------------------------------------------------------------------------

    protected void checkInProduceState() throws IllegalStateException {
        checkState(!isFinished, "Partition already finished.");
    }

    @VisibleForTesting
    public ResultPartitionManager getPartitionManager() {
        return partitionManager;
    }

    /**
     * Whether the buffer can be compressed or not. Note that event is not compressed because it is
     * usually small and the size can become even larger after compression.
     * 缓冲区是否可以压缩。 请注意，事件没有被压缩，因为它通常很小，压缩后大小可能会变得更大。
     */
    protected boolean canBeCompressed(Buffer buffer) {
        return bufferCompressor != null && buffer.isBuffer() && buffer.readableBytes() > 0;
    }
}
