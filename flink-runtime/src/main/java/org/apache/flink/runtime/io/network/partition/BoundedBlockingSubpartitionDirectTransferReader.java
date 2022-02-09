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

import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.ResultSubpartition.BufferAndBacklog;
import org.apache.flink.util.IOUtils;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The reader (read view) of a BoundedBlockingSubpartition based on {@link
 * org.apache.flink.shaded.netty4.io.netty.channel.FileRegion}.
 * 基于 {@link org.apache.flink.shaded.netty4.io.netty.channel.FileRegion}
 * 的 BoundedBlockingSubpartition 的读取器（读取视图）。
 */
public class BoundedBlockingSubpartitionDirectTransferReader implements ResultSubpartitionView {

    /** The result subpartition that we read.
     * 我们读取的结果子分区。
     * */
    private final BoundedBlockingSubpartition parent;

    /** The reader/decoder to the file region with the data we currently read from.
     * 带有我们当前读取的数据的文件区域的读取器/解码器。
     * */
    private final BoundedData.Reader dataReader;

    /** The remaining number of data buffers (not events) in the result.
     * 结果中剩余的数据缓冲区（不是事件）数。
     * */
    private int numDataBuffers;

    /** The remaining number of data buffers and events in the result.
     * 结果中剩余的数据缓冲区和事件数。
     * */
    private int numDataAndEventBuffers;

    /** Flag whether this reader is released.
     * 标记此阅读器是否已释放。
     * */
    private boolean isReleased;

    private int sequenceNumber;

    BoundedBlockingSubpartitionDirectTransferReader(
            BoundedBlockingSubpartition parent,
            Path filePath,
            int numDataBuffers,
            int numDataAndEventBuffers)
            throws IOException {

        this.parent = checkNotNull(parent);

        checkNotNull(filePath);
        this.dataReader = new FileRegionReader(filePath);

        checkArgument(numDataBuffers >= 0);
        this.numDataBuffers = numDataBuffers;

        checkArgument(numDataAndEventBuffers >= 0);
        this.numDataAndEventBuffers = numDataAndEventBuffers;
    }

    @Nullable
    @Override
    public BufferAndBacklog getNextBuffer() throws IOException {
        if (isReleased) {
            return null;
        }

        Buffer current = dataReader.nextBuffer();
        if (current == null) {
            // as per contract, we must return null when the reader is empty,
            // but also in case the reader is disposed (rather than throwing an exception)
            return null;
        }

        updateStatistics(current);

        // We simply assume all the data are non-events for batch jobs to avoid pre-fetching the
        // next header
        Buffer.DataType nextDataType =
                numDataAndEventBuffers > 0 ? Buffer.DataType.DATA_BUFFER : Buffer.DataType.NONE;
        return BufferAndBacklog.fromBufferAndLookahead(
                current, nextDataType, numDataBuffers, sequenceNumber++);
    }

    private void updateStatistics(Buffer buffer) {
        if (buffer.isBuffer()) {
            numDataBuffers--;
        }
        numDataAndEventBuffers--;
    }

    @Override
    public boolean isAvailable(int numCreditsAvailable) {
        // We simply assume there are no events except EndOfPartitionEvent for bath jobs,
        // then it has no essential effect to ignore the judgement of next event buffer.
        // 我们简单假设bath作业除了EndOfPartitionEvent之外没有事件，
        // 那么忽略对next event buffer的判断并没有本质的影响。
        return numCreditsAvailable > 0 && numDataAndEventBuffers > 0;
    }

    @Override
    public void releaseAllResources() throws IOException {
        // it is not a problem if this method executes multiple times
        isReleased = true;

        IOUtils.closeQuietly(dataReader);

        // Notify the parent that this one is released. This allows the parent to
        // eventually release all resources (when all readers are done and the
        // parent is disposed).
        parent.releaseReaderReference(this);
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    @Override
    public Throwable getFailureCause() {
        // we can never throw an error after this was created
        return null;
    }

    @Override
    public int unsynchronizedGetNumberOfQueuedBuffers() {
        return parent.unsynchronizedGetNumberOfQueuedBuffers();
    }

    @Override
    public void notifyDataAvailable() {
        throw new UnsupportedOperationException("Method should never be called.");
    }

    @Override
    public void resumeConsumption() {
        throw new UnsupportedOperationException("Method should never be called.");
    }

    @Override
    public String toString() {
        return String.format(
                "Blocking Subpartition Reader: ID=%s, index=%d",
                parent.parent.getPartitionId(), parent.getSubPartitionIndex());
    }

    /**
     * The reader to read from {@link BoundedBlockingSubpartition} and return the wrapped {@link
     * org.apache.flink.shaded.netty4.io.netty.channel.FileRegion} based buffer.
     * 读取器从 {@link BoundedBlockingSubpartition} 读取并返回基于包装的
     * {@link org.apache.flink.shaded.netty4.io.netty.channel.FileRegion} 缓冲区。
     */
    static final class FileRegionReader implements BoundedData.Reader {

        private final FileChannel fileChannel;

        private final ByteBuffer headerBuffer;

        FileRegionReader(Path filePath) throws IOException {
            this.fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
            this.headerBuffer = BufferReaderWriterUtil.allocatedHeaderBuffer();
        }

        @Nullable
        @Override
        public Buffer nextBuffer() throws IOException {
            return BufferReaderWriterUtil.readFileRegionFromByteChannel(fileChannel, headerBuffer);
        }

        @Override
        public void close() throws IOException {
            fileChannel.close();
        }
    }
}
