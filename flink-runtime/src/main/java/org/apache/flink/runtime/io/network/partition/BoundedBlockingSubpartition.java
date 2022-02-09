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
import org.apache.flink.runtime.io.network.api.EndOfPartitionEvent;
import org.apache.flink.runtime.io.network.api.serialization.EventSerializer;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.buffer.BufferConsumer;
import org.apache.flink.util.FlinkRuntimeException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * An implementation of the ResultSubpartition for a bounded result transferred in a blocking
 * manner: The result is first produced, then consumed. The result can be consumed possibly multiple
 * times.
 * 以阻塞方式传输的有界结果的 ResultSubpartition 实现：首先生成结果，然后使用结果。 结果可能会被多次使用。
 *
 * <p>Depending on the supplied implementation of {@link BoundedData}, the actual data is stored for
 * example in a file, or in a temporary memory mapped file.
 * 根据提供的 {@link BoundedData} 实现，实际数据存储在例如文件中或临时内存映射文件中。
 *
 * <h2>Important Notes on Thread Safety</h2>
 * <h2>关于线程安全的重要说明</h2>
 *
 * <p>This class does not synchronize every buffer access. It assumes the threading model of the
 * Flink network stack and is not thread-safe beyond that.
 * 此类不会同步每个缓冲区访问。 它假定 Flink 网络堆栈的线程模型，除此之外不是线程安全的。
 *
 * <p>This class assumes a single writer thread that adds buffers, flushes, and finishes the write
 * phase. That same thread is also assumed to perform the partition release, if the release happens
 * during the write phase.
 * 此类假定有一个写入器线程，它添加缓冲区、刷新并完成写入阶段。 如果释放发生在写入阶段，则还假定同一线程执行分区释放。
 *
 * <p>The implementation supports multiple concurrent readers, but assumes a single thread per
 * reader. That same thread must also release the reader. In particular, after the reader was
 * released, no buffers obtained from this reader may be accessed any more, or segmentation faults
 * might occur in some implementations.
 * 该实现支持多个并发阅读器，但假设每个阅读器有一个线程。 同一个线程也必须释放阅读器。
 * 特别是，在读取器被释放后，从该读取器获得的缓冲区可能不再被访问，或者在某些实现中可能会出现分段错误。
 *
 * <p>The method calls to create readers, dispose readers, and dispose the partition are thread-safe
 * vis-a-vis each other.
 * 创建读取器、处理读取器和处理分区的方法调用彼此之间是线程安全的。
 */
final class BoundedBlockingSubpartition extends ResultSubpartition {

    /** This lock guards the creation of readers and disposal of the memory mapped file.
     * 此锁保护读取器的创建和内存映射文件的处理。
     * */
    private final Object lock = new Object();

    /** The current buffer, may be filled further over time.
     * 当前缓冲区可能会随着时间的推移而被进一步填充。
     * */
    @Nullable private BufferConsumer currentBuffer;

    /** The bounded data store that we store the data in.
     * 我们存储数据的有界数据存储。
     * */
    private final BoundedData data;

    /** All created and not yet released readers.
     * 所有已创建但尚未发布的读者。
     * */
    @GuardedBy("lock")
    private final Set<ResultSubpartitionView> readers;

    /**
     * Flag to transfer file via FileRegion way in network stack if partition type is file without
     * SSL enabled.
     * 如果分区类型是未启用 SSL 的文件，则标记以通过网络堆栈中的 FileRegion 方式传输文件。
     */
    private final boolean useDirectFileTransfer;

    /** Counter for the number of data buffers (not events!) written.
     * 写入的数据缓冲区（不是事件！）数量的计数器。
     * */
    private int numDataBuffersWritten;

    /** The counter for the number of data buffers and events.
     * 数据缓冲区和事件数的计数器。
     * */
    private int numBuffersAndEventsWritten;

    /** Flag indicating whether the writing has finished and this is now available for read.
     * 指示写入是否已完成并且现在可供读取的标志。
     * */
    private boolean isFinished;

    /** Flag indicating whether the subpartition has been released.
     * 指示子分区是否已释放的标志。
     * */
    private boolean isReleased;

    public BoundedBlockingSubpartition(
            int index, ResultPartition parent, BoundedData data, boolean useDirectFileTransfer) {

        super(index, parent);

        this.data = checkNotNull(data);
        this.useDirectFileTransfer = useDirectFileTransfer;
        this.readers = new HashSet<>();
    }

    // ------------------------------------------------------------------------

    /**
     * Checks if writing is finished. Readers cannot be created until writing is finished, and no
     * further writes can happen after that.
     * 检查写入是否完成。 在写入完成之前无法创建读取器，之后不会发生进一步的写入。
     */
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    @Override
    public boolean add(BufferConsumer bufferConsumer, int partialRecordLength) throws IOException {
        if (isFinished()) {
            bufferConsumer.close();
            return false;
        }

        flushCurrentBuffer();
        currentBuffer = bufferConsumer;
        return true;
    }

    @Override
    public void flush() {
        // unfortunately, the signature of flush does not allow for any exceptions, so we
        // need to do this discouraged pattern of runtime exception wrapping
        try {
            flushCurrentBuffer();
        } catch (IOException e) {
            throw new FlinkRuntimeException(e.getMessage(), e);
        }
    }

    private void flushCurrentBuffer() throws IOException {
        if (currentBuffer != null) {
            writeAndCloseBufferConsumer(currentBuffer);
            currentBuffer = null;
        }
    }

    private void writeAndCloseBufferConsumer(BufferConsumer bufferConsumer) throws IOException {
        try {
            final Buffer buffer = bufferConsumer.build();
            try {
                if (parent.canBeCompressed(buffer)) {
                    final Buffer compressedBuffer =
                            parent.bufferCompressor.compressToIntermediateBuffer(buffer);
                    data.writeBuffer(compressedBuffer);
                    if (compressedBuffer != buffer) {
                        compressedBuffer.recycleBuffer();
                    }
                } else {
                    data.writeBuffer(buffer);
                }

                numBuffersAndEventsWritten++;
                if (buffer.isBuffer()) {
                    numDataBuffersWritten++;
                }
            } finally {
                buffer.recycleBuffer();
            }
        } finally {
            bufferConsumer.close();
        }
    }

    @Override
    public void finish() throws IOException {
        checkState(!isReleased, "data partition already released");
        checkState(!isFinished, "data partition already finished");

        isFinished = true;
        flushCurrentBuffer();
        writeAndCloseBufferConsumer(
                EventSerializer.toBufferConsumer(EndOfPartitionEvent.INSTANCE, false));
        data.finishWrite();
    }

    @Override
    public void release() throws IOException {
        synchronized (lock) {
            if (isReleased) {
                return;
            }

            isReleased = true;
            isFinished = true; // for fail fast writes

            if (currentBuffer != null) {
                currentBuffer.close();
                currentBuffer = null;
            }
            checkReaderReferencesAndDispose();
        }
    }

    @Override
    public ResultSubpartitionView createReadView(BufferAvailabilityListener availability)
            throws IOException {
        synchronized (lock) {
            checkState(!isReleased, "data partition already released");
            checkState(isFinished, "writing of blocking partition not yet finished");

            final ResultSubpartitionView reader;
            if (useDirectFileTransfer) {
                reader =
                        new BoundedBlockingSubpartitionDirectTransferReader(
                                this,
                                data.getFilePath(),
                                numDataBuffersWritten,
                                numBuffersAndEventsWritten);
            } else {
                reader =
                        new BoundedBlockingSubpartitionReader(
                                this, data, numDataBuffersWritten, availability);
            }
            readers.add(reader);
            return reader;
        }
    }

    void releaseReaderReference(ResultSubpartitionView reader) throws IOException {
        onConsumedSubpartition();

        synchronized (lock) {
            if (readers.remove(reader) && isReleased) {
                checkReaderReferencesAndDispose();
            }
        }
    }

    @GuardedBy("lock")
    private void checkReaderReferencesAndDispose() throws IOException {
        assert Thread.holdsLock(lock);

        // To avoid lingering memory mapped files (large resource footprint), we don't
        // wait for GC to unmap the files, but use a Netty utility to directly unmap the file.
        // To avoid segmentation faults, we need to wait until all readers have been released.

        if (readers.isEmpty()) {
            data.close();
        }
    }

    @VisibleForTesting
    public BufferConsumer getCurrentBuffer() {
        return currentBuffer;
    }

    // ---------------------------- statistics --------------------------------

    @Override
    public int unsynchronizedGetNumberOfQueuedBuffers() {
        return 0;
    }

    @Override
    protected long getTotalNumberOfBuffers() {
        return numBuffersAndEventsWritten;
    }

    @Override
    protected long getTotalNumberOfBytes() {
        return data.getSize();
    }

    int getBuffersInBacklog() {
        return numDataBuffersWritten;
    }

    // ---------------------------- factories --------------------------------

    /**
     * Creates a BoundedBlockingSubpartition that simply stores the partition data in a file. Data
     * is eagerly spilled (written to disk) and readers directly read from the file.
     * 创建一个 BoundedBlockingSubpartition，它只是将分区数据存储在一个文件中。
     * 数据被急切地溢出（写入磁盘），读取器直接从文件中读取。
     */
    public static BoundedBlockingSubpartition createWithFileChannel(
            int index,
            ResultPartition parent,
            File tempFile,
            int readBufferSize,
            boolean sslEnabled)
            throws IOException {

        final FileChannelBoundedData bd =
                FileChannelBoundedData.create(tempFile.toPath(), readBufferSize);
        return new BoundedBlockingSubpartition(index, parent, bd, !sslEnabled);
    }

    /**
     * Creates a BoundedBlockingSubpartition that stores the partition data in memory mapped file.
     * Data is written to and read from the mapped memory region. Disk spilling happens lazily, when
     * the OS swaps out the pages from the memory mapped file.
     * 创建一个 BoundedBlockingSubpartition，将分区数据存储在内存映射文件中。
     * 数据被写入和读取映射的内存区域。 当操作系统从内存映射文件中换出页面时，磁盘溢出会延迟发生。
     */
    public static BoundedBlockingSubpartition createWithMemoryMappedFile(
            int index, ResultPartition parent, File tempFile) throws IOException {

        final MemoryMappedBoundedData bd = MemoryMappedBoundedData.create(tempFile.toPath());
        return new BoundedBlockingSubpartition(index, parent, bd, false);
    }

    /**
     * Creates a BoundedBlockingSubpartition that stores the partition data in a file and memory
     * maps that file for reading. Data is eagerly spilled (written to disk) and then mapped into
     * memory. The main difference to the {@link #createWithMemoryMappedFile(int, ResultPartition,
     * File)} variant is that no I/O is necessary when pages from the memory mapped file are
     * evicted.
     * 创建一个 BoundedBlockingSubpartition，它将分区数据存储在一个文件中，并且内存映射该文件以供读取。
     * 数据被急切地溢出（写入磁盘），然后映射到内存中。
     * {@link #createWithMemoryMappedFile(int, ResultPartition, File)} 变体的主要区别在于，当内存映射文件中的页面被驱逐时，不需要 I/O。
     */
    public static BoundedBlockingSubpartition createWithFileAndMemoryMappedReader(
            int index, ResultPartition parent, File tempFile) throws IOException {

        final FileChannelMemoryMappedBoundedData bd =
                FileChannelMemoryMappedBoundedData.create(tempFile.toPath());
        return new BoundedBlockingSubpartition(index, parent, bd, false);
    }
}
