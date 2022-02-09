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
import org.apache.flink.util.IOUtils;

import org.apache.flink.shaded.netty4.io.netty.util.internal.PlatformDependent;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * An implementation of {@link BoundedData} simply through ByteBuffers backed by memory, typically
 * from a memory mapped file, so the data gets automatically evicted from memory if it grows large.
 * {@link BoundedData} 的实现只需通过内存支持的 ByteBuffers，通常来自内存映射文件，因此如果数据变大，数据会自动从内存中逐出。
 *
 * <p>Most of the code in this class is a workaround for the fact that a memory mapped region in
 * Java cannot be larger than 2GB (== signed 32 bit int max value). The class takes {@link Buffer
 * Buffers} and writes them to several memory mapped region, using the {@link
 * BufferReaderWriterUtil} class.
 * 此类中的大多数代码都是针对 Java 中的内存映射区域不能大于 2GB（== 带符号的 32 位 int 最大值）这一事实的解决方法。
 * 该类使用 {@link BufferReaderWriterUtil} 类将 {@link Buffer Buffers} 写入多个内存映射区域。
 *
 * <h2>Important!</h2>
 *
 * <p>This class performs absolutely no synchronization and relies on single threaded access or
 * externally synchronized access. Concurrent access around disposal may cause segmentation faults!
 * 此类完全不执行同步，并且依赖于单线程访问或外部同步访问。 围绕处置的并发访问可能会导致分段错误！
 *
 * <p>This class does limited sanity checks and assumes correct use from {@link
 * BoundedBlockingSubpartition} and {@link BoundedBlockingSubpartitionReader}, such as writing first
 * and reading after. Not obeying these contracts throws NullPointerExceptions.
 * 此类进行有限的健全性检查，并假定从 {@link BoundedBlockingSubpartition}
 * 和 {@link BoundedBlockingSubpartitionReader} 正确使用，例如先写后读。 不遵守这些约定会引发 NullPointerExceptions。
 */
final class MemoryMappedBoundedData implements BoundedData {

    /** Memory mappings should be at the granularity of page sizes, for efficiency.
     * 为了提高效率，内存映射应该以页面大小为粒度。
     * */
    private static final int PAGE_SIZE = PageSizeUtil.getSystemPageSizeOrConservativeMultiple();

    /**
     * The the current memory mapped region we are writing to. This value is null once writing has
     * finished or the buffers are disposed.
     * 我们正在写入的当前内存映射区域。 写入完成或释放缓冲区后，此值为 null。
     */
    @Nullable private ByteBuffer currentBuffer;

    /** All memory mapped regions that are already full (completed).
     * 所有已满（已完成）的内存映射区域。
     * */
    private final ArrayList<ByteBuffer> fullBuffers;

    /** The file channel backing the memory mapped file.
     * 支持内存映射文件的文件通道。
     * */
    private final FileChannel file;

    /** The path of the memory mapped file.
     * 内存映射文件的路径。
     * */
    private final Path filePath;

    /** The offset where the next mapped region should start.
     * 下一个映射区域应该开始的偏移量。
     * */
    private long nextMappingOffset;

    /** The size of each mapped region.
     * 每个映射区域的大小。
     * */
    private final long mappingSize;

    MemoryMappedBoundedData(Path filePath, FileChannel fileChannel, int maxSizePerByteBuffer)
            throws IOException {

        this.filePath = filePath;
        this.file = fileChannel;
        this.mappingSize = alignSize(maxSizePerByteBuffer);
        this.fullBuffers = new ArrayList<>(4);

        rollOverToNextBuffer();
    }

    @Override
    public void writeBuffer(Buffer buffer) throws IOException {
        assert currentBuffer != null;

        if (BufferReaderWriterUtil.writeBuffer(buffer, currentBuffer)) {
            return;
        }

        rollOverToNextBuffer();

        if (!BufferReaderWriterUtil.writeBuffer(buffer, currentBuffer)) {
            throwTooLargeBuffer(buffer);
        }
    }

    @Override
    public BufferSlicer createReader(ResultSubpartitionView ignored) {
        assert currentBuffer == null;

        final List<ByteBuffer> buffers =
                fullBuffers.stream()
                        .map((bb) -> bb.slice().order(ByteOrder.nativeOrder()))
                        .collect(Collectors.toList());

        return new BufferSlicer(buffers);
    }

    /**
     * Finishes the current region and prevents further writes. After calling this method, further
     * calls to {@link #writeBuffer(Buffer)} will fail.
     * 完成当前区域并防止进一步写入。 调用此方法后，对 {@link #writeBuffer(Buffer)} 的进一步调用将失败。
     */
    @Override
    public void finishWrite() throws IOException {
        assert currentBuffer != null;

        currentBuffer.flip();
        fullBuffers.add(currentBuffer);
        currentBuffer = null; // fail further writes fast
        file.close(); // won't map further regions from now on
    }

    /**
     * Unmaps the file from memory and deletes the file. After calling this method, access to any
     * ByteBuffer obtained from this instance will cause a segmentation fault.
     * 从内存中取消映射文件并删除文件。 调用此方法后，访问任何从该实例获取的 ByteBuffer 都会导致分段错误。
     */
    public void close() throws IOException {
        IOUtils.closeQuietly(file); // in case we dispose before finishing writes

        for (ByteBuffer bb : fullBuffers) {
            PlatformDependent.freeDirectBuffer(bb);
        }
        fullBuffers.clear();

        if (currentBuffer != null) {
            PlatformDependent.freeDirectBuffer(currentBuffer);
            currentBuffer = null;
        }

        // To make this compatible with all versions of Windows, we must wait with
        // deleting the file until it is unmapped.
        // See also
        // https://stackoverflow.com/questions/11099295/file-flag-delete-on-close-and-memory-mapped-files/51649618#51649618

        Files.delete(filePath);
    }

    /**
     * Gets the number of bytes of all written data (including the metadata in the buffer headers).
     * 获取所有写入数据的字节数（包括缓冲区标头中的元数据）。
     */
    @Override
    public long getSize() {
        long size = 0L;
        for (ByteBuffer bb : fullBuffers) {
            size += bb.remaining();
        }
        if (currentBuffer != null) {
            size += currentBuffer.position();
        }
        return size;
    }

    @Override
    public Path getFilePath() {
        return filePath;
    }

    private void rollOverToNextBuffer() throws IOException {
        if (currentBuffer != null) {
            // we need to remember the original buffers, not any slices.
            // slices have no cleaner, which we need to trigger explicit unmapping
            currentBuffer.flip();
            fullBuffers.add(currentBuffer);
        }

        currentBuffer = file.map(MapMode.READ_WRITE, nextMappingOffset, mappingSize);
        currentBuffer.order(ByteOrder.nativeOrder());
        nextMappingOffset += mappingSize;
    }

    private void throwTooLargeBuffer(Buffer buffer) throws IOException {
        throw new IOException(
                String.format(
                        "The buffer (%d bytes) is larger than the maximum size of a memory buffer (%d bytes)",
                        buffer.getSize(), mappingSize));
    }

    /**
     * Rounds the size down to the next multiple of the {@link #PAGE_SIZE}. We need to round down
     * here to not exceed the original maximum size value. Otherwise, values like INT_MAX would
     * round up to overflow the valid maximum size of a memory mapping region in Java.
     * 将大小向下舍入到 {@link #PAGE_SIZE} 的下一个倍数。 我们需要在这里四舍五入到不超过原来的最大尺寸值。
     * 否则，像 INT_MAX 这样的值将四舍五入以溢出 Java 中内存映射区域的有效最大大小。
     */
    private static int alignSize(int maxRegionSize) {
        checkArgument(maxRegionSize >= PAGE_SIZE);
        return maxRegionSize - (maxRegionSize % PAGE_SIZE);
    }

    // ------------------------------------------------------------------------
    //  Reader
    // ------------------------------------------------------------------------

    /**
     * The "reader" for the memory region. It slices a sequence of buffers from the sequence of
     * mapped ByteBuffers.
     * 内存区域的“阅读器”。 它从映射的 ByteBuffers 序列中切出一系列缓冲区。
     */
    static final class BufferSlicer implements BoundedData.Reader {

        /**
         * The memory mapped region we currently read from. Max 2GB large. Further regions may be in
         * the {@link #furtherData} field.
         * 我们当前读取的内存映射区域。 最大 2GB 大。 其他区域可能位于 {@link #furtherData} 字段中。
         */
        private ByteBuffer currentData;

        /**
         * Further byte buffers, to handle cases where there is more data than fits into one mapped
         * byte buffer (2GB = Integer.MAX_VALUE).
         * 更多字节缓冲区，用于处理数据多于一个映射字节缓冲区 (2GB = Integer.MAX_VALUE) 的情况。
         */
        private final Iterator<ByteBuffer> furtherData;

        BufferSlicer(Iterable<ByteBuffer> data) {
            this.furtherData = data.iterator();
            this.currentData = furtherData.next();
        }

        @Override
        @Nullable
        public Buffer nextBuffer() {
            // should only be null once empty or disposed, in which case this method
            // should not be called any more
            // 只有在为空或释放后才为 null，在这种情况下，不应再调用此方法
            assert currentData != null;

            final Buffer next = BufferReaderWriterUtil.sliceNextBuffer(currentData);
            if (next != null) {
                return next;
            }

            if (!furtherData.hasNext()) {
                return null;
            }

            currentData = furtherData.next();
            return nextBuffer();
        }

        @Override
        public void close() throws IOException {
            // nothing to do, this class holds no actual resources of its own,
            // only references to the mapped byte buffers
        }
    }

    // ------------------------------------------------------------------------
    //  Factories
    // ------------------------------------------------------------------------

    /** Creates new MemoryMappedBoundedData, creating a memory mapped file at the given path.
     * 创建新的 MemoryMappedBoundedData，在给定路径创建内存映射文件。
     * */
    public static MemoryMappedBoundedData create(Path memMappedFilePath) throws IOException {
        return createWithRegionSize(memMappedFilePath, Integer.MAX_VALUE);
    }

    /**
     * Creates new MemoryMappedBoundedData, creating a memory mapped file at the given path. Each
     * mapped region (= ByteBuffer) will be of the given size.
     * 创建新的 MemoryMappedBoundedData，在给定路径创建内存映射文件。 每个映射区域 (= ByteBuffer) 将具有给定的大小。
     */
    public static MemoryMappedBoundedData createWithRegionSize(
            Path memMappedFilePath, int regionSize) throws IOException {
        final FileChannel fileChannel =
                FileChannel.open(
                        memMappedFilePath,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW);

        return new MemoryMappedBoundedData(memMappedFilePath, fileChannel, regionSize);
    }
}
