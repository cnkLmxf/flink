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

package org.apache.flink.runtime.io.network.buffer;

import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.core.memory.MemorySegmentFactory;
import org.apache.flink.runtime.io.compression.BlockCompressionFactory;
import org.apache.flink.runtime.io.compression.BlockCompressor;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/** Compressor for {@link Buffer}. */
public class BufferCompressor {

    /** The backing block compressor for data compression.
     * 用于数据压缩的后备块压缩器。
     * */
    private final BlockCompressor blockCompressor;

    /** The intermediate buffer for the compressed data.
     * 压缩数据的中间缓冲区。
     * */
    private final NetworkBuffer internalBuffer;

    public BufferCompressor(int bufferSize, String factoryName) {
        checkArgument(bufferSize > 0);
        checkNotNull(factoryName);
        // the size of this intermediate heap buffer will be gotten from the
        // plugin configuration in the future, and currently, double size of
        // the input buffer is enough for lz4-java compression library.
        final byte[] heapBuffer = new byte[2 * bufferSize];
        this.internalBuffer =
                new NetworkBuffer(
                        MemorySegmentFactory.wrap(heapBuffer), FreeingBufferRecycler.INSTANCE);
        this.blockCompressor =
                BlockCompressionFactory.createBlockCompressionFactory(factoryName).getCompressor();
    }

    /**
     * Compresses the given {@link Buffer} using {@link BlockCompressor}. The compressed data will
     * be stored in the intermediate buffer of this {@link BufferCompressor} and returned to the
     * caller. The caller must guarantee that the returned {@link Buffer} has been freed when
     * calling the method next time.
     * 使用 {@link BlockCompressor} 压缩给定的 {@link Buffer}。
     * 压缩后的数据将存储在此 {@link BufferCompressor} 的中间缓冲区中并返回给调用者。
     * 调用者必须保证下次调用该方法时返回的 {@link Buffer} 已被释放。
     *
     * <p>Notes that the compression will always start from offset 0 to the size of the input {@link
     * Buffer}.
     * 请注意，压缩总是从偏移量 0 开始到输入 {@link Buffer} 的大小。
     */
    public Buffer compressToIntermediateBuffer(Buffer buffer) {
        int compressedLen;
        if ((compressedLen = compress(buffer)) == 0) {
            return buffer;
        }

        internalBuffer.setCompressed(true);
        internalBuffer.setSize(compressedLen);
        return internalBuffer.retainBuffer();
    }

    /**
     * The difference between this method and {@link #compressToIntermediateBuffer(Buffer)} is that
     * this method will copy the compressed data back to the input {@link Buffer} starting from
     * offset 0.
     * 此方法与 {@link #compressToIntermediateBuffer(Buffer)} 的区别在于，
     * 此方法将从偏移量 0 开始将压缩数据复制回输入 {@link Buffer}。
     *
     * <p>The caller must guarantee that the input {@link Buffer} is writable.
     * 调用者必须保证输入 {@link Buffer} 是可写的。
     */
    public Buffer compressToOriginalBuffer(Buffer buffer) {
        int compressedLen;
        if ((compressedLen = compress(buffer)) == 0) {
            return buffer;
        }

        // copy the compressed data back
        int memorySegmentOffset = buffer.getMemorySegmentOffset();
        MemorySegment segment = buffer.getMemorySegment();
        segment.put(memorySegmentOffset, internalBuffer.array(), 0, compressedLen);

        return new ReadOnlySlicedNetworkBuffer(
                buffer.asByteBuf(), 0, compressedLen, memorySegmentOffset, true);
    }

    /**
     * Compresses the given {@link Buffer} into the intermediate buffer and returns the compressed
     * data size.
     * 将给定的 {@link Buffer} 压缩到中间缓冲区并返回压缩后的数据大小。
     */
    private int compress(Buffer buffer) {
        checkArgument(buffer != null, "The input buffer must not be null.");
        checkArgument(buffer.isBuffer(), "Event can not be compressed.");
        checkArgument(!buffer.isCompressed(), "Buffer already compressed.");
        checkArgument(buffer.getReaderIndex() == 0, "Reader index of the input buffer must be 0.");
        checkArgument(buffer.readableBytes() > 0, "No data to be compressed.");
        checkState(
                internalBuffer.refCnt() == 1,
                "Illegal reference count, buffer need to be released.");

        try {
            int length = buffer.getSize();
            // compress the given buffer into the internal heap buffer
            int compressedLen =
                    blockCompressor.compress(
                            buffer.getNioBuffer(0, length),
                            0,
                            length,
                            internalBuffer.getNioBuffer(0, internalBuffer.capacity()),
                            0);
            return compressedLen < length ? compressedLen : 0;
        } catch (Throwable throwable) {
            // return the original buffer if failed to compress
            return 0;
        }
    }
}
