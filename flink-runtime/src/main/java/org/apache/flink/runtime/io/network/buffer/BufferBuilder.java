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

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

import java.nio.ByteBuffer;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * Not thread safe class for filling in the content of the {@link MemorySegment}. To access written
 * data please use {@link BufferConsumer} which allows to build {@link Buffer} instances from the
 * written data.
 * 不是用于填充 {@link MemorySegment} 内容的线程安全类。
 * 要访问写入的数据，请使用 {@link BufferConsumer}，它允许从写入的数据构建 {@link Buffer} 实例。
 */
@NotThreadSafe
public class BufferBuilder implements AutoCloseable {
    private final Buffer buffer;
    private final MemorySegment memorySegment;

    private final SettablePositionMarker positionMarker = new SettablePositionMarker();

    private boolean bufferConsumerCreated = false;

    public BufferBuilder(MemorySegment memorySegment, BufferRecycler recycler) {
        this.memorySegment = checkNotNull(memorySegment);
        this.buffer = new NetworkBuffer(memorySegment, recycler);
    }

    /**
     * This method always creates a {@link BufferConsumer} starting from the current writer offset.
     * Data written to {@link BufferBuilder} before creation of {@link BufferConsumer} won't be
     * visible for that {@link BufferConsumer}.
     * 此方法始终从当前写入器偏移量开始创建一个 {@link BufferConsumer}。
     * 在创建 {@link BufferConsumer} 之前写入 {@link BufferBuilder} 的数据对于该 {@link BufferConsumer} 将不可见。
     *
     * @return created matching instance of {@link BufferConsumer} to this {@link BufferBuilder}.
     */
    public BufferConsumer createBufferConsumer() {
        return createBufferConsumer(positionMarker.cachedPosition);
    }

    /**
     * This method always creates a {@link BufferConsumer} starting from position 0 of {@link
     * MemorySegment}.
     * 此方法总是从 {@link MemorySegment} 的位置 0 开始创建一个 {@link BufferConsumer}。
     *
     * @return created matching instance of {@link BufferConsumer} to this {@link BufferBuilder}.
     */
    public BufferConsumer createBufferConsumerFromBeginning() {
        return createBufferConsumer(0);
    }

    private BufferConsumer createBufferConsumer(int currentReaderPosition) {
        checkState(
                !bufferConsumerCreated, "Two BufferConsumer shouldn't exist for one BufferBuilder");
        bufferConsumerCreated = true;
        return new BufferConsumer(buffer.retainBuffer(), positionMarker, currentReaderPosition);
    }

    /** Same as {@link #append(ByteBuffer)} but additionally {@link #commit()} the appending. */
    public int appendAndCommit(ByteBuffer source) {
        int writtenBytes = append(source);
        commit();
        return writtenBytes;
    }

    /**
     * Append as many data as possible from {@code source}. Not everything might be copied if there
     * is not enough space in the underlying {@link MemorySegment}
     * 从 {@code source} 附加尽可能多的数据。 如果底层 {@link MemorySegment} 中没有足够的空间，则不会复制所有内容
     *
     * @return number of copied bytes
     */
    public int append(ByteBuffer source) {
        checkState(!isFinished());

        int needed = source.remaining();
        int available = getMaxCapacity() - positionMarker.getCached();
        int toCopy = Math.min(needed, available);

        memorySegment.put(positionMarker.getCached(), source, toCopy);
        positionMarker.move(toCopy);
        return toCopy;
    }

    /**
     * Make the change visible to the readers. This is costly operation (volatile access) thus in
     * case of bulk writes it's better to commit them all together instead one by one.
     * 使更改对读者可见。 这是昂贵的操作（易失性访问），因此在批量写入的情况下，最好将它们全部一起提交，而不是一个一个地提交。
     */
    public void commit() {
        positionMarker.commit();
    }

    /**
     * Mark this {@link BufferBuilder} and associated {@link BufferConsumer} as finished - no new
     * data writes will be allowed.
     * 将此 {@link BufferBuilder} 和关联的 {@link BufferConsumer} 标记为已完成 - 不允许新的数据写入。
     *
     * <p>This method should be idempotent to handle failures and task interruptions. Check
     * FLINK-8948 for more details.
     * 这种方法应该是幂等的来处理故障和任务中断。 查看 FLINK-8948 了解更多详情。
     *
     * @return number of written bytes.
     */
    public int finish() {
        int writtenBytes = positionMarker.markFinished();
        commit();
        return writtenBytes;
    }

    public boolean isFinished() {
        return positionMarker.isFinished();
    }

    public boolean isFull() {
        checkState(positionMarker.getCached() <= getMaxCapacity());
        return positionMarker.getCached() == getMaxCapacity();
    }

    public int getWritableBytes() {
        checkState(positionMarker.getCached() <= getMaxCapacity());
        return getMaxCapacity() - positionMarker.getCached();
    }

    public int getCommittedBytes() {
        return positionMarker.getCached();
    }

    public int getMaxCapacity() {
        return buffer.getMaxCapacity();
    }

    @Override
    public void close() {
        buffer.recycleBuffer();
    }

    /**
     * Holds a reference to the current writer position. Negative values indicate that writer
     * ({@link BufferBuilder} has finished. Value {@code Integer.MIN_VALUE} represents finished
     * empty buffer.
     * 持有对当前写入者位置的引用。
     * 负值表示 writer ({@link BufferBuilder} 已完成。值 {@code Integer.MIN_VALUE} 表示已完成的空缓冲区。
     */
    @ThreadSafe
    interface PositionMarker {
        int FINISHED_EMPTY = Integer.MIN_VALUE;

        int get();

        static boolean isFinished(int position) {
            return position < 0;
        }

        static int getAbsolute(int position) {
            if (position == FINISHED_EMPTY) {
                return 0;
            }
            return Math.abs(position);
        }
    }

    /**
     * Cached writing implementation of {@link PositionMarker}.
     * {@link PositionMarker} 的缓存写入实现。
     *
     * <p>Writer ({@link BufferBuilder}) and reader ({@link BufferConsumer}) caches must be
     * implemented independently of one another - so that the cached values can not accidentally
     * leak from one to another.
     * 写入器 ({@link BufferBuilder}) 和读取器 ({@link BufferConsumer}) 缓存必须相互独立地实现
     * - 这样缓存的值就不会意外地从一个泄漏到另一个。
     *
     * <p>Remember to commit the {@link SettablePositionMarker} to make the changes visible.
     */
    static class SettablePositionMarker implements PositionMarker {
        private volatile int position = 0;

        /**
         * Locally cached value of volatile {@code position} to avoid unnecessary volatile accesses.
         * volatile {@code position} 的本地缓存值，以避免不必要的 volatile 访问。
         */
        private int cachedPosition = 0;

        @Override
        public int get() {
            return position;
        }

        public boolean isFinished() {
            return PositionMarker.isFinished(cachedPosition);
        }

        public int getCached() {
            return PositionMarker.getAbsolute(cachedPosition);
        }

        /**
         * Marks this position as finished and returns the current position.
         * 将此位置标记为已完成并返回当前位置。
         *
         * @return current position as of {@link #getCached()}
         */
        public int markFinished() {
            int currentPosition = getCached();
            int newValue = -currentPosition;
            if (newValue == 0) {
                newValue = FINISHED_EMPTY;
            }
            set(newValue);
            return currentPosition;
        }

        public void move(int offset) {
            set(cachedPosition + offset);
        }

        public void set(int value) {
            cachedPosition = value;
        }

        public void commit() {
            position = cachedPosition;
        }
    }
}
