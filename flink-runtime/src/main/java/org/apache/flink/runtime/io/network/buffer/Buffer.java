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
import org.apache.flink.runtime.event.AbstractEvent;
import org.apache.flink.runtime.io.network.api.CheckpointBarrier;
import org.apache.flink.runtime.io.network.partition.consumer.EndOfChannelStateEvent;

import org.apache.flink.shaded.netty4.io.netty.buffer.ByteBuf;
import org.apache.flink.shaded.netty4.io.netty.buffer.ByteBufAllocator;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.apache.flink.util.Preconditions.checkState;

/**
 * Wrapper for pooled {@link MemorySegment} instances with reference counting.
 * 具有引用计数的池化 {@link MemorySegment} 实例的包装器。
 *
 * <p>This is similar to Netty's <tt>ByteBuf</tt> with some extensions and restricted to the methods
 * our use cases outside Netty handling use. In particular, we use two different indexes for read
 * and write operations, i.e. the <tt>reader</tt> and <tt>writer</tt> index (size of written data),
 * which specify three regions inside the memory segment:
 * 这类似于 Netty 的 <tt>ByteBuf</tt> 有一些扩展，并且仅限于我们在 Netty 处理之外的用例使用的方法。
 * 特别是，我们使用两个不同的索引进行读取和写入操作，即 <tt>reader</tt> 和 <tt>writer</tt> 索引（写入数据的大小），它们指定了内存段内的三个区域：
 *
 * <pre>
 *     +-------------------+----------------+----------------+
 *     | discardable bytes | readable bytes | writable bytes |
 *     +-------------------+----------------+----------------+
 *     |                   |                |                |
 *     0      <=      readerIndex  <=  writerIndex   <=  max capacity
 * </pre>
 *
 * <p>Our non-Netty usages of this <tt>Buffer</tt> class either rely on the underlying {@link
 * #getMemorySegment()} directly, or on {@link ByteBuffer} wrappers of this buffer which do not
 * modify either index, so the indices need to be updated manually via {@link #setReaderIndex(int)}
 * and {@link #setSize(int)}.
 * 我们对这个 <tt>Buffer</tt> 类的非 Netty 用法要么直接依赖于底层的 {@link #getMemorySegment()}，
 * 要么依赖于不修改任何索引的这个缓冲区的 {@link ByteBuffer} 包装器，
 * 所以 索引需要通过 {@link #setReaderIndex(int)} 和 {@link #setSize(int)} 手动更新。
 */
public interface Buffer {
    /**
     * Returns whether this buffer represents a buffer or an event.
     * 返回此缓冲区表示缓冲区还是事件。
     *
     * @return <tt>true</tt> if this is a real buffer, <tt>false</tt> if this is an event
     */
    boolean isBuffer();

    /**
     * Returns the underlying memory segment. This method is dangerous since it ignores read only
     * protections and omits slices. Use it only along the {@link #getMemorySegmentOffset()}.
     * 返回底层内存段。 这种方法很危险，因为它忽略了只读保护并省略了切片。 仅在 {@link #getMemorySegmentOffset()} 中使用它。
     *
     * <p>This method will be removed in the future. For writing use {@link BufferBuilder}.
     * 将来会删除此方法。 对于写作使用 {@link BufferBuilder}。
     *
     * @return the memory segment backing this buffer
     */
    @Deprecated
    MemorySegment getMemorySegment();

    /**
     * This method will be removed in the future. For writing use {@link BufferBuilder}.
     * 将来会删除此方法。 对于写作使用 {@link BufferBuilder}。
     *
     * @return the offset where this (potential slice) {@link Buffer}'s data start in the underlying
     *     memory segment.
     */
    @Deprecated
    int getMemorySegmentOffset();

    /**
     * Gets the buffer's recycler.
     * 获取缓冲区的回收器。
     *
     * @return buffer recycler
     */
    BufferRecycler getRecycler();

    /**
     * Releases this buffer once, i.e. reduces the reference count and recycles the buffer if the
     * reference count reaches <tt>0</tt>.
     * 释放此缓冲区一次，即减少引用计数并在引用计数达到 <tt>0</tt> 时回收缓冲区。
     *
     * @see #retainBuffer()
     */
    void recycleBuffer();

    /**
     * Returns whether this buffer has been recycled or not.
     * 返回此缓冲区是否已被回收。
     *
     * @return <tt>true</tt> if already recycled, <tt>false</tt> otherwise
     */
    boolean isRecycled();

    /**
     * Retains this buffer for further use, increasing the reference counter by <tt>1</tt>.
     * 保留此缓冲区以供进一步使用，将引用计数器增加 <tt>1</tt>。
     *
     * @return <tt>this</tt> instance (for chained calls)
     * @see #recycleBuffer()
     */
    Buffer retainBuffer();

    /**
     * Returns a read-only slice of this buffer's readable bytes, i.e. between {@link
     * #getReaderIndex()} and {@link #getSize()}.
     * 返回此缓冲区可读字节的只读片段，即在 {@link #getReaderIndex()} 和 {@link #getSize()} 之间。
     *
     * <p>Reader and writer indices as well as markers are not shared. Reference counters are shared
     * but the slice is not {@link #retainBuffer() retained} automatically.
     * 读取器和写入器索引以及标记不共享。 引用计数器是共享的，但切片不是 {@link #retainBuffer() 自动保留}。
     *
     * @return a read-only sliced buffer
     */
    Buffer readOnlySlice();

    /**
     * Returns a read-only slice of this buffer.
     * 返回此缓冲区的只读切片。
     *
     * <p>Reader and writer indices as well as markers are not shared. Reference counters are shared
     * but the slice is not {@link #retainBuffer() retained} automatically.
     * 读取器和写入器索引以及标记不共享。 引用计数器是共享的，但切片不是 {@link #retainBuffer() 自动保留}。
     *
     * @param index the index to start from
     * @param length the length of the slice
     * @return a read-only sliced buffer
     */
    Buffer readOnlySlice(int index, int length);

    /**
     * Returns the maximum size of the buffer, i.e. the capacity of the underlying {@link
     * MemorySegment}.
     * 返回缓冲区的最大大小，即底层 {@link MemorySegment} 的容量。
     *
     * @return size of the buffer
     */
    int getMaxCapacity();

    /**
     * Returns the <tt>reader index</tt> of this buffer.
     * 返回此缓冲区的 <tt>阅读器索引</tt>。
     *
     * <p>This is where readable (unconsumed) bytes start in the backing memory segment.
     * 这是后备内存段中可读（未使用）字节的开始位置。
     *
     * @return reader index (from 0 (inclusive) to the size of the backing {@link MemorySegment}
     *     (inclusive))
     */
    int getReaderIndex();

    /**
     * Sets the <tt>reader index</tt> of this buffer.
     * 设置此缓冲区的<tt>阅读器索引</tt>。
     *
     * @throws IndexOutOfBoundsException if the index is less than <tt>0</tt> or greater than {@link
     *     #getSize()}
     */
    void setReaderIndex(int readerIndex) throws IndexOutOfBoundsException;

    /**
     * Returns the size of the written data, i.e. the <tt>writer index</tt>, of this buffer.
     * 返回此缓冲区的写入数据的大小，即 <tt>writer index</tt>。
     *
     * <p>This is where writable bytes start in the backing memory segment.
     * 这是后备内存段中可写字节的开始位置。
     *
     * @return writer index (from 0 (inclusive) to the size of the backing {@link MemorySegment}
     *     (inclusive))
     */
    int getSize();

    /**
     * Sets the size of the written data, i.e. the <tt>writer index</tt>, of this buffer.
     * 设置此缓冲区的写入数据的大小，即 <tt>writer index</tt>。
     *
     * @throws IndexOutOfBoundsException if the index is less than {@link #getReaderIndex()} or
     *     greater than {@link #getMaxCapacity()}
     */
    void setSize(int writerIndex);

    /**
     * Returns the number of readable bytes (same as <tt>{@link #getSize()} - {@link
     * #getReaderIndex()}</tt>).
     * 返回可读字节数（与 <tt>{@link #getSize()} - {@link #getReaderIndex()}</tt> 相同）。
     */
    int readableBytes();

    /**
     * Gets a new {@link ByteBuffer} instance wrapping this buffer's readable bytes, i.e. between
     * {@link #getReaderIndex()} and {@link #getSize()}.
     * 获取包装此缓冲区的可读字节的新 {@link ByteBuffer} 实例，
     * 即在 {@link #getReaderIndex()} 和 {@link #getSize()} 之间。
     *
     * <p>Please note that neither index is updated by the returned buffer.
     * 请注意，返回的缓冲区不会更新任何索引。
     *
     * @return byte buffer sharing the contents of the underlying memory segment
     */
    ByteBuffer getNioBufferReadable();

    /**
     * Gets a new {@link ByteBuffer} instance wrapping this buffer's bytes.
     * 获取包装此缓冲区字节的新 {@link ByteBuffer} 实例。
     *
     * <p>Please note that neither <tt>read</tt> nor <tt>write</tt> index are updated by the
     * returned buffer.
     * 请注意，返回的缓冲区不会更新 <tt>read</tt> 和 <tt>write</tt> 索引。
     *
     * @return byte buffer sharing the contents of the underlying memory segment
     * @throws IndexOutOfBoundsException if the indexes are not without the buffer's bounds
     * @see #getNioBufferReadable()
     */
    ByteBuffer getNioBuffer(int index, int length) throws IndexOutOfBoundsException;

    /**
     * Sets the buffer allocator for use in netty.
     * 设置用于 netty 的缓冲区分配器。
     *
     * @param allocator netty buffer allocator
     */
    void setAllocator(ByteBufAllocator allocator);

    /** @return self as ByteBuf implementation. */
    ByteBuf asByteBuf();

    /** @return whether the buffer is compressed or not. */
    boolean isCompressed();

    /** Tags the buffer as compressed or uncompressed. */
    void setCompressed(boolean isCompressed);

    /** Gets the type of data this buffer represents. */
    DataType getDataType();

    /** Sets the type of data this buffer represents. */
    void setDataType(DataType dataType);

    default String toDebugString(boolean includeHash) {
        StringBuilder prettyString = new StringBuilder("Buffer{size=").append(getSize());
        if (includeHash) {
            byte[] bytes = new byte[getSize()];
            readOnlySlice().asByteBuf().readBytes(bytes);
            prettyString.append(", hash=").append(Arrays.hashCode(bytes));
        }
        return prettyString.append("}").toString();
    }

    /**
     * Used to identify the type of data contained in the {@link Buffer} so that we can get the
     * information without deserializing the serialized data.
     * 用于标识 {@link Buffer} 中包含的数据类型，以便我们无需反序列化序列化数据即可获取信息。
     *
     * <p>Notes: Currently, one byte is used to serialize the ordinal of {@link DataType} in {@link
     * org.apache.flink.runtime.io.network.netty.NettyMessage.BufferResponse}, so the maximum number
     * of supported data types is 128.
     * 注意：目前，{@link org.apache.flink.runtime.io.network.netty.NettyMessage.BufferResponse}
     * 中的 {@link DataType} 序号使用一个字节进行序列化，因此支持的最大数据类型数为 128.
     */
    enum DataType {
        /** {@link #NONE} indicates that there is no buffer.
         * {@link #NONE} 表示没有缓冲区。
         * */
        NONE(false, false, false, false, false),

        /** {@link #DATA_BUFFER} indicates that this buffer represents a non-event data buffer.
         * {@link #DATA_BUFFER} 表示这个缓冲区代表一个非事件数据缓冲区。
         * */
        DATA_BUFFER(true, false, false, false, false),

        /**
         * {@link #EVENT_BUFFER} indicates that this buffer represents serialized data of an event.
         * Note that this type can be further divided into more fine-grained event types like {@link
         * #ALIGNED_CHECKPOINT_BARRIER} and etc.
         * {@link #EVENT_BUFFER} 表示此缓冲区表示事件的序列化数据。
         * 请注意，此类型可以进一步分为更细粒度的事件类型，例如 {@link #ALIGNED_CHECKPOINT_BARRIER} 等。
         */
        EVENT_BUFFER(false, true, false, false, false),

        /** Same as EVENT_BUFFER, but the event has been prioritized (e.g. it skipped buffers).
         * 与 EVENT_BUFFER 相同，但事件已被优先处理（例如，它跳过了缓冲区）。
         * */
        PRIORITIZED_EVENT_BUFFER(false, true, false, true, false),

        /**
         * {@link #ALIGNED_CHECKPOINT_BARRIER} indicates that this buffer represents a serialized
         * checkpoint barrier of aligned exactly-once checkpoint mode.
         * {@link #ALIGNED_CHECKPOINT_BARRIER} 表示这个缓冲区代表一个对齐的精确一次检查点模式的序列化检查点屏障。
         */
        ALIGNED_CHECKPOINT_BARRIER(false, true, true, false, false),

        /**
         * {@link #TIMEOUTABLE_ALIGNED_CHECKPOINT_BARRIER} indicates that this buffer represents a
         * serialized checkpoint barrier of aligned exactly-once checkpoint mode, that can be
         * time-out'ed to an unaligned checkpoint barrier.
         * {@link #TIMEOUTABLE_ALIGNED_CHECKPOINT_BARRIER}
         * 表示此缓冲区表示对齐的仅一次检查点模式的序列化检查点屏障，可以超时到未对齐的检查点屏障。
         */
        TIMEOUTABLE_ALIGNED_CHECKPOINT_BARRIER(false, true, true, false, true),

        /**
         * Indicates that this subpartition state is fully recovered (emitted). Further data can be
         * consumed after unblocking.
         * 表示此子分区状态已完全恢复（已发出）。 解除阻塞后可以消耗更多的数据。
         */
        RECOVERY_COMPLETION(false, true, true, false, false);

        private final boolean isBuffer;
        private final boolean isEvent;
        private final boolean isBlockingUpstream;
        private final boolean hasPriority;
        /**
         * If buffer (currently only Events are supported in that case) requires announcement, it's
         * arrival in the {@link
         * org.apache.flink.runtime.io.network.partition.consumer.RemoteInputChannel} will be
         * announced, by a special announcement message. Announcement messages are {@link
         * #PRIORITIZED_EVENT_BUFFER} processed out of order. It allows readers of the input to
         * react sooner on arrival of such Events, before it will be able to be processed normally.
         * 如果缓冲区（在这种情况下目前仅支持事件）需要公告，则将通过特殊公告消息宣布它到达
         * {@link org.apache.flink.runtime.io.network.partition.consumer.RemoteInputChannel}。
         * 通知消息被无序处理 {@link #PRIORITIZED_EVENT_BUFFER}。
         * 它允许输入的读者在此类事件到达时更快地做出反应，然后才能正常处理。
         */
        private final boolean requiresAnnouncement;

        DataType(
                boolean isBuffer,
                boolean isEvent,
                boolean isBlockingUpstream,
                boolean hasPriority,
                boolean requiresAnnouncement) {
            checkState(
                    !(requiresAnnouncement && hasPriority),
                    "DataType [%s] has both priority and requires announcement, which is not supported "
                            + "and doesn't make sense. There should be no need for announcing priority events, which are always "
                            + "overtaking in-flight data.",
                    this);
            this.isBuffer = isBuffer;
            this.isEvent = isEvent;
            this.isBlockingUpstream = isBlockingUpstream;
            this.hasPriority = hasPriority;
            this.requiresAnnouncement = requiresAnnouncement;
        }

        public boolean isBuffer() {
            return isBuffer;
        }

        public boolean isEvent() {
            return isEvent;
        }

        public boolean hasPriority() {
            return hasPriority;
        }

        public boolean isBlockingUpstream() {
            return isBlockingUpstream;
        }

        public boolean requiresAnnouncement() {
            return requiresAnnouncement;
        }

        public static DataType getDataType(AbstractEvent event, boolean hasPriority) {
            if (hasPriority) {
                return PRIORITIZED_EVENT_BUFFER;
            } else if (event instanceof EndOfChannelStateEvent) {
                return RECOVERY_COMPLETION;
            } else if (!(event instanceof CheckpointBarrier)) {
                return EVENT_BUFFER;
            }
            CheckpointBarrier barrier = (CheckpointBarrier) event;
            if (barrier.getCheckpointOptions().needsAlignment()) {
                if (barrier.getCheckpointOptions().isTimeoutable()) {
                    return TIMEOUTABLE_ALIGNED_CHECKPOINT_BARRIER;
                } else {
                    return ALIGNED_CHECKPOINT_BARRIER;
                }
            } else {
                return EVENT_BUFFER;
            }
        }
    }
}
