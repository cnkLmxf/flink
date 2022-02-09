/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.checkpoint.channel;

import org.apache.flink.annotation.Internal;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.state.InputChannelStateHandle;
import org.apache.flink.runtime.state.ResultSubpartitionStateHandle;
import org.apache.flink.util.CloseableIterator;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/** Writes channel state during checkpoint/savepoint.
 * 在检查点/保存点期间写入通道状态。
 * */
@Internal
public interface ChannelStateWriter extends Closeable {

    /** Channel state write result. */
    class ChannelStateWriteResult {
        final CompletableFuture<Collection<InputChannelStateHandle>> inputChannelStateHandles;
        final CompletableFuture<Collection<ResultSubpartitionStateHandle>>
                resultSubpartitionStateHandles;

        ChannelStateWriteResult() {
            this(new CompletableFuture<>(), new CompletableFuture<>());
        }

        ChannelStateWriteResult(
                CompletableFuture<Collection<InputChannelStateHandle>> inputChannelStateHandles,
                CompletableFuture<Collection<ResultSubpartitionStateHandle>>
                        resultSubpartitionStateHandles) {
            this.inputChannelStateHandles = inputChannelStateHandles;
            this.resultSubpartitionStateHandles = resultSubpartitionStateHandles;
        }

        public CompletableFuture<Collection<InputChannelStateHandle>>
                getInputChannelStateHandles() {
            return inputChannelStateHandles;
        }

        public CompletableFuture<Collection<ResultSubpartitionStateHandle>>
                getResultSubpartitionStateHandles() {
            return resultSubpartitionStateHandles;
        }

        public static final ChannelStateWriteResult EMPTY =
                new ChannelStateWriteResult(
                        CompletableFuture.completedFuture(Collections.emptyList()),
                        CompletableFuture.completedFuture(Collections.emptyList()));

        public void fail(Throwable e) {
            inputChannelStateHandles.completeExceptionally(e);
            resultSubpartitionStateHandles.completeExceptionally(e);
        }

        boolean isDone() {
            return inputChannelStateHandles.isDone() && resultSubpartitionStateHandles.isDone();
        }
    }

    /**
     * Sequence number for the buffers that were saved during the previous execution attempt; then
     * restored; and now are to be saved again (as opposed to the buffers received from the upstream
     * or from the operator).
     * 在上一次执行尝试期间保存的缓冲区的序列号； 然后恢复； 现在将再次保存（与从上游或操作员接收的缓冲区相反）。
     */
    int SEQUENCE_NUMBER_RESTORED = -1;

    /**
     * Signifies that buffer sequence number is unknown (e.g. if passing sequence numbers is not
     * implemented).
     * 表示缓冲区序列号未知（例如，如果未实现传递序列号）。
     */
    int SEQUENCE_NUMBER_UNKNOWN = -2;

    /** Initiate write of channel state for the given checkpoint id.
     * 启动给定检查点 ID 的通道状态写入。
     * */
    void start(long checkpointId, CheckpointOptions checkpointOptions);

    /**
     * Add in-flight buffers from the {@link
     * org.apache.flink.runtime.io.network.partition.consumer.InputChannel InputChannel}. Must be
     * called after {@link #start} (long)} and before {@link #finishInput(long)}. Buffers are
     * recycled after they are written or exception occurs.
     * 从 {@link org.apache.flink.runtime.io.network.partition.consumer.InputChannel InputChannel} 添加动态缓冲区。
     * 必须在 {@link #start} (long)} 和 {@link #finishInput(long)} 之前调用。
     * 缓冲区在写入或发生异常后会被回收。
     *
     * @param startSeqNum sequence number of the 1st passed buffer. It is intended to use for
     *     incremental snapshots. If no data is passed it is ignored.
     * @param data zero or more <b>data</b> buffers ordered by their sequence numbers
     * @see org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter#SEQUENCE_NUMBER_RESTORED
     * @see org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter#SEQUENCE_NUMBER_UNKNOWN
     */
    void addInputData(
            long checkpointId,
            InputChannelInfo info,
            int startSeqNum,
            CloseableIterator<Buffer> data);

    /**
     * Add in-flight buffers from the {@link
     * org.apache.flink.runtime.io.network.partition.ResultSubpartition ResultSubpartition}. Must be
     * called after {@link #start} and before {@link #finishOutput(long)}. Buffers are recycled
     * after they are written or exception occurs.
     * 从 {@link org.apache.flink.runtime.io.network.partition.ResultSubpartition ResultSubpartition} 添加动态缓冲区。
     * 必须在 {@link #start} 和 {@link #finishOutput(long)} 之前调用。 缓冲区在写入或发生异常后会被回收。
     *
     * @param startSeqNum sequence number of the 1st passed buffer. It is intended to use for
     *     incremental snapshots. If no data is passed it is ignored.
     * @param data zero or more <b>data</b> buffers ordered by their sequence numbers
     * @throws IllegalArgumentException if one or more passed buffers {@link Buffer#isBuffer() isn't
     *     a buffer}
     * @see org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter#SEQUENCE_NUMBER_RESTORED
     * @see org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter#SEQUENCE_NUMBER_UNKNOWN
     */
    void addOutputData(
            long checkpointId, ResultSubpartitionInfo info, int startSeqNum, Buffer... data)
            throws IllegalArgumentException;

    /**
     * Finalize write of channel state data for the given checkpoint id. Must be called after {@link
     * #start(long, CheckpointOptions)} and all of the input data of the given checkpoint added.
     * When both {@link #finishInput} and {@link #finishOutput} were called the results can be
     * (eventually) obtained using {@link #getAndRemoveWriteResult}
     * 为给定的检查点 ID 完成通道状态数据的写入。 必须在 {@link #start(long, CheckpointOptions)}
     * 并添加给定检查点的所有输入数据之后调用。 当同时调用 {@link #finishInput} 和 {@link #finishOutput} 时，
     * 可以（最终）使用 {@link #getAndRemoveWriteResult} 获得结果
     */
    void finishInput(long checkpointId);

    /**
     * Finalize write of channel state data for the given checkpoint id. Must be called after {@link
     * #start(long, CheckpointOptions)} and all of the output data of the given checkpoint added.
     * When both {@link #finishInput} and {@link #finishOutput} were called the results can be
     * (eventually) obtained using {@link #getAndRemoveWriteResult}
     * 为给定的检查点 ID 完成通道状态数据的写入。
     * 必须在 {@link #start(long, CheckpointOptions)} 并添加给定检查点的所有输出数据之后调用。
     * 当同时调用 {@link #finishInput} 和 {@link #finishOutput} 时，
     * 可以（最终）使用 {@link #getAndRemoveWriteResult} 获得结果
     */
    void finishOutput(long checkpointId);

    /**
     * Aborts the checkpoint and fails pending result for this checkpoint.
     * 中止检查点并失败此检查点的挂起结果。
     *
     * @param cleanup true if {@link #getAndRemoveWriteResult(long)} is not supposed to be called
     *     afterwards.
     */
    void abort(long checkpointId, Throwable cause, boolean cleanup);

    /**
     * Must be called after {@link #start(long, CheckpointOptions)} once.
     * 必须在 {@link #start(long, CheckpointOptions)} 之后调用一次。
     *
     * @throws IllegalArgumentException if the passed checkpointId is not known.
     */
    ChannelStateWriteResult getAndRemoveWriteResult(long checkpointId)
            throws IllegalArgumentException;

    ChannelStateWriter NO_OP = new NoOpChannelStateWriter();

    /** No-op implementation of {@link ChannelStateWriter}. */
    class NoOpChannelStateWriter implements ChannelStateWriter {
        @Override
        public void start(long checkpointId, CheckpointOptions checkpointOptions) {}

        @Override
        public void addInputData(
                long checkpointId,
                InputChannelInfo info,
                int startSeqNum,
                CloseableIterator<Buffer> data) {}

        @Override
        public void addOutputData(
                long checkpointId, ResultSubpartitionInfo info, int startSeqNum, Buffer... data) {}

        @Override
        public void finishInput(long checkpointId) {}

        @Override
        public void finishOutput(long checkpointId) {}

        @Override
        public void abort(long checkpointId, Throwable cause, boolean cleanup) {}

        @Override
        public ChannelStateWriteResult getAndRemoveWriteResult(long checkpointId) {
            return new ChannelStateWriteResult(
                    CompletableFuture.completedFuture(Collections.emptyList()),
                    CompletableFuture.completedFuture(Collections.emptyList()));
        }

        @Override
        public void close() {}
    }
}
