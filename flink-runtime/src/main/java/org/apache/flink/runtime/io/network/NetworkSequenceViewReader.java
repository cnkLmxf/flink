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

package org.apache.flink.runtime.io.network;

import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.io.network.partition.ResultPartitionProvider;
import org.apache.flink.runtime.io.network.partition.consumer.InputChannel.BufferAndAvailability;
import org.apache.flink.runtime.io.network.partition.consumer.InputChannelID;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Simple wrapper for the partition readerQueue iterator, which increments a sequence number for
 * each returned buffer and remembers the receiver ID.
 * 分区 readerQueue 迭代器的简单包装器，它为每个返回的缓冲区增加一个序列号并记住接收者 ID。
 */
public interface NetworkSequenceViewReader {

    void requestSubpartitionView(
            ResultPartitionProvider partitionProvider,
            ResultPartitionID resultPartitionId,
            int subPartitionIndex)
            throws IOException;

    @Nullable
    BufferAndAvailability getNextBuffer() throws IOException;

    /**
     * The credits from consumer are added in incremental way.
     * 来自消费者的信用以增量方式添加。
     *
     * @param creditDeltas The credit deltas
     */
    void addCredit(int creditDeltas);

    /** Resumes data consumption after an exactly once checkpoint.
     * 在恰好一次检查点后恢复数据消耗。
     * */
    void resumeConsumption();

    /**
     * Checks whether this reader is available or not.
     * 检查此阅读器是否可用。
     *
     * @return True if the reader is available.
     */
    boolean isAvailable();

    boolean isRegisteredAsAvailable();

    /**
     * Updates the value to indicate whether the reader is enqueued in the pipeline or not.
     * 更新值以指示读取器是否在管道中排队。
     *
     * @param isRegisteredAvailable True if this reader is already enqueued in the pipeline.
     */
    void setRegisteredAsAvailable(boolean isRegisteredAvailable);

    boolean isReleased();

    void releaseAllResources() throws IOException;

    Throwable getFailureCause();

    InputChannelID getReceiverId();
}
