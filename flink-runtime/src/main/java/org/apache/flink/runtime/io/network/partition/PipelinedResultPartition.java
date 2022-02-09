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

import org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter;
import org.apache.flink.runtime.io.network.buffer.BufferCompressor;
import org.apache.flink.runtime.io.network.buffer.BufferPool;
import org.apache.flink.util.function.SupplierWithException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * A result output of a task, pipelined (streamed) to the receivers.
 * 任务的结果输出，流水线（流）到接收器。
 *
 * <p>This result partition implementation is used both in batch and streaming. For streaming it
 * supports low latency transfers (ensure data is sent within x milliseconds) or unconstrained while
 * for batch it transfers only once a buffer is full. Additionally, for streaming use this typically
 * limits the length of the buffer backlog to not have too much data in flight, while for batch we
 * do not constrain this.
 * 此结果分区实现用于批处理和流式处理。 对于流式传输，它支持低延迟传输（确保数据在 x 毫秒内发送）或不受限制，
 * 而对于批处理，它仅在缓冲区已满时传输。
 * 此外，对于流式使用，这通常会限制缓冲区积压的长度，以防止传输中的数据过多，而对于批处理，我们不限制这一点。
 *
 * <h2>Specifics of the PipelinedResultPartition</h2>
 * PipelinedResultPartition 的细节
 *
 * <p>The PipelinedResultPartition cannot reconnect once a consumer disconnects (finished or
 * errored). Once all consumers have disconnected (released the subpartition, notified via the call
 * {@link #onConsumedSubpartition(int)}) then the partition as a whole is disposed and all buffers
 * are freed.
 * 一旦消费者断开连接（完成或出错），PipelinedResultPartition 就无法重新连接。
 * 一旦所有消费者都断开连接（释放子分区，通过调用 {@link #onConsumedSubpartition(int)} 通知），
 * 则整个分区被释放并释放所有缓冲区。
 */
public class PipelinedResultPartition extends BufferWritingResultPartition
        implements CheckpointedResultPartition, ChannelStateHolder {
    private static final int PIPELINED_RESULT_PARTITION_ITSELF = -42;

    /**
     * The lock that guard release operations (which can be asynchronously propagated from the
     * networks threads.
     * 保护释放操作的锁（可以从网络线程异步传播。
     */
    private final Object releaseLock = new Object();

    /**
     * A flag for each subpartition indicating whether it was already consumed or not, to make
     * releases idempotent.
     * 每个子分区的标志，指示它是否已被使用，以使发布具有幂等性。
     */
    @GuardedBy("releaseLock")
    private final boolean[] consumedSubpartitions;

    /**
     * The total number of references to subpartitions of this result. The result partition can be
     * safely released, iff the reference count is zero. Every subpartition is an user of the result
     * as well the {@link PipelinedResultPartition} is a user itself, as it's writing to those
     * results. Even if all consumers are released, partition can not be released until writer
     * releases the partition as well.
     * 对此结果的子分区的引用总数。 如果引用计数为零，则可以安全地释放结果分区。
     * 每个子分区都是结果的用户，并且 {@link PipelinedResultPartition} 本身就是用户，因为它正在写入这些结果。
     * 即使所有消费者都被释放，分区也不能被释放，直到写入者也释放分区。
     */
    @GuardedBy("releaseLock")
    private int numberOfUsers;

    public PipelinedResultPartition(
            String owningTaskName,
            int partitionIndex,
            ResultPartitionID partitionId,
            ResultPartitionType partitionType,
            ResultSubpartition[] subpartitions,
            int numTargetKeyGroups,
            ResultPartitionManager partitionManager,
            @Nullable BufferCompressor bufferCompressor,
            SupplierWithException<BufferPool, IOException> bufferPoolFactory) {

        super(
                owningTaskName,
                partitionIndex,
                partitionId,
                checkResultPartitionType(partitionType),
                subpartitions,
                numTargetKeyGroups,
                partitionManager,
                bufferCompressor,
                bufferPoolFactory);

        this.consumedSubpartitions = new boolean[subpartitions.length];
        this.numberOfUsers = subpartitions.length + 1;
    }

    @Override
    public void setChannelStateWriter(ChannelStateWriter channelStateWriter) {
        for (final ResultSubpartition subpartition : subpartitions) {
            if (subpartition instanceof ChannelStateHolder) {
                ((PipelinedSubpartition) subpartition).setChannelStateWriter(channelStateWriter);
            }
        }
    }

    /**
     * The pipelined partition releases automatically once all subpartition readers are released.
     * That is because pipelined partitions cannot be consumed multiple times, or reconnect.
     * 释放所有子分区读取器后，流水线分区会自动释放。 那是因为管道分区不能被多次使用或重新连接。
     */
    @Override
    void onConsumedSubpartition(int subpartitionIndex) {
        decrementNumberOfUsers(subpartitionIndex);
    }

    private void decrementNumberOfUsers(int subpartitionIndex) {
        if (isReleased()) {
            return;
        }

        final int remainingUnconsumed;

        // we synchronize only the bookkeeping section, to avoid holding the lock during any
        // calls into other components
        synchronized (releaseLock) {
            if (subpartitionIndex != PIPELINED_RESULT_PARTITION_ITSELF) {
                if (consumedSubpartitions[subpartitionIndex]) {
                    // repeated call - ignore
                    return;
                }

                consumedSubpartitions[subpartitionIndex] = true;
            }
            remainingUnconsumed = (--numberOfUsers);
        }

        LOG.debug(
                "{}: Received consumed notification for subpartition {}.", this, subpartitionIndex);

        if (remainingUnconsumed == 0) {
            partitionManager.onConsumedPartition(this);
        } else if (remainingUnconsumed < 0) {
            throw new IllegalStateException(
                    "Received consume notification even though all subpartitions are already consumed.");
        }
    }

    @Override
    public CheckpointedResultSubpartition getCheckpointedSubpartition(int subpartitionIndex) {
        return (CheckpointedResultSubpartition) subpartitions[subpartitionIndex];
    }

    @Override
    public void flushAll() {
        flushAllSubpartitions(false);
    }

    @Override
    public void flush(int targetSubpartition) {
        flushSubpartition(targetSubpartition, false);
    }

    @Override
    @SuppressWarnings("FieldAccessNotGuarded")
    public String toString() {
        return "PipelinedResultPartition "
                + partitionId.toString()
                + " ["
                + partitionType
                + ", "
                + subpartitions.length
                + " subpartitions, "
                + numberOfUsers
                + " pending consumptions]";
    }

    // ------------------------------------------------------------------------
    //   miscellaneous utils
    // ------------------------------------------------------------------------

    private static ResultPartitionType checkResultPartitionType(ResultPartitionType type) {
        checkArgument(
                type == ResultPartitionType.PIPELINED
                        || type == ResultPartitionType.PIPELINED_BOUNDED
                        || type == ResultPartitionType.PIPELINED_APPROXIMATE);
        return type;
    }

    @Override
    public void finishReadRecoveredState(boolean notifyAndBlockOnCompletion) throws IOException {
        for (ResultSubpartition subpartition : subpartitions) {
            ((CheckpointedResultSubpartition) subpartition)
                    .finishReadRecoveredState(notifyAndBlockOnCompletion);
        }
    }

    @Override
    public void close() {
        decrementNumberOfUsers(PIPELINED_RESULT_PARTITION_ITSELF);
        super.close();
    }
}
