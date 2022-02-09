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

import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.core.memory.MemorySegmentFactory;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.buffer.BufferPool;
import org.apache.flink.runtime.io.network.buffer.NetworkBuffer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static org.apache.flink.runtime.io.network.buffer.Buffer.DataType;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * A {@link SortBuffer} implementation which sorts all appended records only by subpartition index.
 * Records of the same subpartition keep the appended order.
 * {@link SortBuffer} 实现仅按子分区索引对所有附加记录进行排序。 同一子分区的记录保持附加顺序。
 *
 * <p>It maintains a list of {@link MemorySegment}s as a joint buffer. Data will be appended to the
 * joint buffer sequentially. When writing a record, an index entry will be appended first. An index
 * entry consists of 4 fields: 4 bytes for record length, 4 bytes for {@link DataType} and 8 bytes
 * for address pointing to the next index entry of the same channel which will be used to index the
 * next record to read when coping data from this {@link SortBuffer}. For simplicity, no index entry
 * can span multiple segments. The corresponding record data is seated right after its index entry
 * and different from the index entry, records have variable length thus may span multiple segments.
 * 它维护一个 {@link MemorySegment} 列表作为联合缓冲区。 数据将按顺序附加到关节缓冲区。
 * 写入记录时，将首先附加一个索引条目。 一个索引条目由 4 个字段组成：4 个字节用于记录长度，
 * 4 个字节用于 {@link DataType} 和 8 个字节用于指向同一通道的下一个索引条目的地址，
 * 该索引条目将用于索引下一条记录以在应对时读取 来自此 {@link SortBuffer} 的数据。
 * 为简单起见，没有索引条目可以跨越多个段。 相应的记录数据位于其索引条目之后，
 * 并且与索引条目不同，记录具有可变长度，因此可能跨越多个段。
 */
@NotThreadSafe
public class PartitionSortedBuffer implements SortBuffer {

    private final Object lock;

    /**
     * Size of an index entry: 4 bytes for record length, 4 bytes for data type and 8 bytes for
     * pointer to next entry.
     * 索引条目的大小：记录长度为 4 个字节，数据类型为 4 个字节，指向下一个条目的指针为 8 个字节。
     */
    private static final int INDEX_ENTRY_SIZE = 4 + 4 + 8;

    /** A buffer pool to request memory segments from.
     * 用于请求内存段的缓冲池。
     * */
    private final BufferPool bufferPool;

    /** A segment list as a joint buffer which stores all records and index entries.
     * 作为存储所有记录和索引条目的联合缓冲区的段列表。
     * */
    @GuardedBy("lock")
    private final ArrayList<MemorySegment> segments = new ArrayList<>();

    /** Addresses of the first record's index entry for each subpartition.
     * 每个子分区的第一条记录的索引条目的地址。
     * */
    private final long[] firstIndexEntryAddresses;

    /** Addresses of the last record's index entry for each subpartition.
     * 每个子分区的最后一条记录的索引条目的地址。
     * */
    private final long[] lastIndexEntryAddresses;

    /** Size of buffers requested from buffer pool. All buffers must be of the same size.
     * 从缓冲池请求的缓冲区大小。 所有缓冲区的大小必须相同。
     * */
    private final int bufferSize;

    /** Number of guaranteed buffers can be allocated from the buffer pool for data sort.
     * 可以从缓冲池中分配保证缓冲区的数量用于数据排序。
     * */
    private final int numGuaranteedBuffers;

    // ---------------------------------------------------------------------------------------------
    // Statistics and states
    // ---------------------------------------------------------------------------------------------

    /** Total number of bytes already appended to this sort buffer.
     * 已附加到此排序缓冲区的字节总数。
     * */
    private long numTotalBytes;

    /** Total number of records already appended to this sort buffer.
     * 已附加到此排序缓冲区的记录总数。
     * */
    private long numTotalRecords;

    /** Total number of bytes already read from this sort buffer.
     * 已从此排序缓冲区读取的字节总数。
     * */
    private long numTotalBytesRead;

    /** Whether this sort buffer is finished. One can only read a finished sort buffer.
     * 此排序缓冲区是否已完成。 只能读取完成的排序缓冲区。
     * */
    private boolean isFinished;

    /** Whether this sort buffer is released. A released sort buffer can not be used.
     * 是否释放此排序缓冲区。 无法使用已释放的排序缓冲区。
     * */
    @GuardedBy("lock")
    private boolean isReleased;

    // ---------------------------------------------------------------------------------------------
    // For writing
    // ---------------------------------------------------------------------------------------------

    /** Array index in the segment list of the current available buffer for writing.
     * 当前可写入缓冲区的段列表中的数组索引。
     * */
    private int writeSegmentIndex;

    /** Next position in the current available buffer for writing.
     * 当前可用缓冲区中用于写入的下一个位置。
     * */
    private int writeSegmentOffset;

    // ---------------------------------------------------------------------------------------------
    // For reading
    // ---------------------------------------------------------------------------------------------

    /** Data of different subpartitions in this sort buffer will be read in this order.
     * 此排序缓冲区中不同子分区的数据将按此顺序读取。
     * */
    private final int[] subpartitionReadOrder;

    /** Index entry address of the current record or event to be read.
     * 当前要读取的记录或事件的索引入口地址。
     * */
    private long readIndexEntryAddress;

    /** Record bytes remaining after last copy, which must be read first in next copy.
     * 记录上次复制后剩余的字节数，必须在下一次复制中首先读取。
     * */
    private int recordRemainingBytes;

    /** Used to index the current available channel to read data from.
     * 用于索引当前可用通道以从中读取数据。
     * */
    private int readOrderIndex = -1;

    public PartitionSortedBuffer(
            Object lock,
            BufferPool bufferPool,
            int numSubpartitions,
            int bufferSize,
            int numGuaranteedBuffers,
            @Nullable int[] customReadOrder) {
        checkArgument(bufferSize > INDEX_ENTRY_SIZE, "Buffer size is too small.");
        checkArgument(numGuaranteedBuffers > 0, "No guaranteed buffers for sort.");

        this.lock = checkNotNull(lock);
        this.bufferPool = checkNotNull(bufferPool);
        this.bufferSize = bufferSize;
        this.numGuaranteedBuffers = numGuaranteedBuffers;
        this.firstIndexEntryAddresses = new long[numSubpartitions];
        this.lastIndexEntryAddresses = new long[numSubpartitions];

        // initialized with -1 means the corresponding channel has no data
        Arrays.fill(firstIndexEntryAddresses, -1L);
        Arrays.fill(lastIndexEntryAddresses, -1L);

        this.subpartitionReadOrder = new int[numSubpartitions];
        if (customReadOrder != null) {
            checkArgument(customReadOrder.length == numSubpartitions, "Illegal data read order.");
            System.arraycopy(customReadOrder, 0, this.subpartitionReadOrder, 0, numSubpartitions);
        } else {
            for (int channel = 0; channel < numSubpartitions; ++channel) {
                this.subpartitionReadOrder[channel] = channel;
            }
        }
    }

    @Override
    public boolean append(ByteBuffer source, int targetChannel, DataType dataType)
            throws IOException {
        checkArgument(source.hasRemaining(), "Cannot append empty data.");
        checkState(!isFinished, "Sort buffer is already finished.");
        checkState(!isReleased, "Sort buffer is already released.");

        int totalBytes = source.remaining();

        // return false directly if it can not allocate enough buffers for the given record
        if (!allocateBuffersForRecord(totalBytes)) {
            return false;
        }

        // write the index entry and record or event data
        writeIndex(targetChannel, totalBytes, dataType);
        writeRecord(source);

        ++numTotalRecords;
        numTotalBytes += totalBytes;

        return true;
    }

    private void writeIndex(int channelIndex, int numRecordBytes, Buffer.DataType dataType) {
        MemorySegment segment = segments.get(writeSegmentIndex);

        // record length takes the high 32 bits and data type takes the low 32 bits
        segment.putLong(writeSegmentOffset, ((long) numRecordBytes << 32) | dataType.ordinal());

        // segment index takes the high 32 bits and segment offset takes the low 32 bits
        long indexEntryAddress = ((long) writeSegmentIndex << 32) | writeSegmentOffset;

        long lastIndexEntryAddress = lastIndexEntryAddresses[channelIndex];
        lastIndexEntryAddresses[channelIndex] = indexEntryAddress;

        if (lastIndexEntryAddress >= 0) {
            // link the previous index entry of the given channel to the new index entry
            segment = segments.get(getSegmentIndexFromPointer(lastIndexEntryAddress));
            segment.putLong(
                    getSegmentOffsetFromPointer(lastIndexEntryAddress) + 8, indexEntryAddress);
        } else {
            firstIndexEntryAddresses[channelIndex] = indexEntryAddress;
        }

        // move the write position forward so as to write the corresponding record
        updateWriteSegmentIndexAndOffset(INDEX_ENTRY_SIZE);
    }

    private void writeRecord(ByteBuffer source) {
        while (source.hasRemaining()) {
            MemorySegment segment = segments.get(writeSegmentIndex);
            int toCopy = Math.min(bufferSize - writeSegmentOffset, source.remaining());
            segment.put(writeSegmentOffset, source, toCopy);

            // move the write position forward so as to write the remaining bytes or next record
            updateWriteSegmentIndexAndOffset(toCopy);
        }
    }

    private boolean allocateBuffersForRecord(int numRecordBytes) throws IOException {
        int numBytesRequired = INDEX_ENTRY_SIZE + numRecordBytes;
        int availableBytes =
                writeSegmentIndex == segments.size() ? 0 : bufferSize - writeSegmentOffset;

        // return directly if current available bytes is adequate
        if (availableBytes >= numBytesRequired) {
            return true;
        }

        // skip the remaining free space if the available bytes is not enough for an index entry
        if (availableBytes < INDEX_ENTRY_SIZE) {
            updateWriteSegmentIndexAndOffset(availableBytes);
            availableBytes = 0;
        }

        // allocate exactly enough buffers for the appended record
        do {
            MemorySegment segment = requestBufferFromPool();
            if (segment == null) {
                // return false if we can not allocate enough buffers for the appended record
                return false;
            }

            availableBytes += bufferSize;
            addBuffer(segment);
        } while (availableBytes < numBytesRequired);

        return true;
    }

    private void addBuffer(MemorySegment segment) {
        synchronized (lock) {
            if (segment.size() != bufferSize) {
                bufferPool.recycle(segment);
                throw new IllegalStateException("Illegal memory segment size.");
            }

            if (isReleased) {
                bufferPool.recycle(segment);
                throw new IllegalStateException("Sort buffer is already released.");
            }

            segments.add(segment);
        }
    }

    private MemorySegment requestBufferFromPool() throws IOException {
        try {
            // blocking request buffers if there is still guaranteed memory
            if (segments.size() < numGuaranteedBuffers) {
                return bufferPool.requestMemorySegmentBlocking();
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while requesting buffer.");
        }

        return bufferPool.requestMemorySegment();
    }

    private void updateWriteSegmentIndexAndOffset(int numBytes) {
        writeSegmentOffset += numBytes;

        // using the next available free buffer if the current is full
        if (writeSegmentOffset == bufferSize) {
            ++writeSegmentIndex;
            writeSegmentOffset = 0;
        }
    }

    @Override
    public BufferWithChannel copyIntoSegment(MemorySegment target) {
        checkState(hasRemaining(), "No data remaining.");
        checkState(isFinished, "Should finish the sort buffer first before coping any data.");
        checkState(!isReleased, "Sort buffer is already released.");

        int numBytesCopied = 0;
        DataType bufferDataType = DataType.DATA_BUFFER;
        int channelIndex = subpartitionReadOrder[readOrderIndex];

        do {
            int sourceSegmentIndex = getSegmentIndexFromPointer(readIndexEntryAddress);
            int sourceSegmentOffset = getSegmentOffsetFromPointer(readIndexEntryAddress);
            MemorySegment sourceSegment = segments.get(sourceSegmentIndex);

            long lengthAndDataType = sourceSegment.getLong(sourceSegmentOffset);
            int length = getSegmentIndexFromPointer(lengthAndDataType);
            DataType dataType = DataType.values()[getSegmentOffsetFromPointer(lengthAndDataType)];

            // return the data read directly if the next to read is an event
            if (dataType.isEvent() && numBytesCopied > 0) {
                break;
            }
            bufferDataType = dataType;

            // get the next index entry address and move the read position forward
            long nextReadIndexEntryAddress = sourceSegment.getLong(sourceSegmentOffset + 8);
            sourceSegmentOffset += INDEX_ENTRY_SIZE;

            // allocate a temp buffer for the event if the target buffer is not big enough
            if (bufferDataType.isEvent() && target.size() < length) {
                target = MemorySegmentFactory.allocateUnpooledSegment(length);
            }

            numBytesCopied +=
                    copyRecordOrEvent(
                            target,
                            numBytesCopied,
                            sourceSegmentIndex,
                            sourceSegmentOffset,
                            length);

            if (recordRemainingBytes == 0) {
                // move to next channel if the current channel has been finished
                if (readIndexEntryAddress == lastIndexEntryAddresses[channelIndex]) {
                    updateReadChannelAndIndexEntryAddress();
                    break;
                }
                readIndexEntryAddress = nextReadIndexEntryAddress;
            }
        } while (numBytesCopied < target.size() && bufferDataType.isBuffer());

        numTotalBytesRead += numBytesCopied;
        Buffer buffer = new NetworkBuffer(target, (buf) -> {}, bufferDataType, numBytesCopied);
        return new BufferWithChannel(buffer, channelIndex);
    }

    private int copyRecordOrEvent(
            MemorySegment targetSegment,
            int targetSegmentOffset,
            int sourceSegmentIndex,
            int sourceSegmentOffset,
            int recordLength) {
        if (recordRemainingBytes > 0) {
            // skip the data already read if there is remaining partial record after the previous
            // copy
            long position = (long) sourceSegmentOffset + (recordLength - recordRemainingBytes);
            sourceSegmentIndex += (position / bufferSize);
            sourceSegmentOffset = (int) (position % bufferSize);
        } else {
            recordRemainingBytes = recordLength;
        }

        int targetSegmentSize = targetSegment.size();
        int numBytesToCopy =
                Math.min(targetSegmentSize - targetSegmentOffset, recordRemainingBytes);
        do {
            // move to next data buffer if all data of the current buffer has been copied
            if (sourceSegmentOffset == bufferSize) {
                ++sourceSegmentIndex;
                sourceSegmentOffset = 0;
            }

            int sourceRemainingBytes =
                    Math.min(bufferSize - sourceSegmentOffset, recordRemainingBytes);
            int numBytes = Math.min(targetSegmentSize - targetSegmentOffset, sourceRemainingBytes);
            MemorySegment sourceSegment = segments.get(sourceSegmentIndex);
            sourceSegment.copyTo(sourceSegmentOffset, targetSegment, targetSegmentOffset, numBytes);

            recordRemainingBytes -= numBytes;
            targetSegmentOffset += numBytes;
            sourceSegmentOffset += numBytes;
        } while ((recordRemainingBytes > 0 && targetSegmentOffset < targetSegmentSize));

        return numBytesToCopy;
    }

    private void updateReadChannelAndIndexEntryAddress() {
        // skip the channels without any data
        while (++readOrderIndex < firstIndexEntryAddresses.length) {
            int channelIndex = subpartitionReadOrder[readOrderIndex];
            if ((readIndexEntryAddress = firstIndexEntryAddresses[channelIndex]) >= 0) {
                break;
            }
        }
    }

    private int getSegmentIndexFromPointer(long value) {
        return (int) (value >>> 32);
    }

    private int getSegmentOffsetFromPointer(long value) {
        return (int) (value);
    }

    @Override
    public long numRecords() {
        return numTotalRecords;
    }

    @Override
    public long numBytes() {
        return numTotalBytes;
    }

    @Override
    public boolean hasRemaining() {
        return numTotalBytesRead < numTotalBytes;
    }

    @Override
    public void finish() {
        checkState(!isFinished, "SortBuffer is already finished.");

        isFinished = true;

        // prepare for reading
        updateReadChannelAndIndexEntryAddress();
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public void release() {
        // the sort buffer can be released by other threads
        synchronized (lock) {
            if (isReleased) {
                return;
            }

            isReleased = true;

            for (MemorySegment segment : segments) {
                bufferPool.recycle(segment);
            }
            segments.clear();

            numTotalBytes = 0;
            numTotalRecords = 0;
        }
    }

    @Override
    public boolean isReleased() {
        synchronized (lock) {
            return isReleased;
        }
    }
}
