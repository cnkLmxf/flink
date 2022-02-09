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

package org.apache.flink.core.memory;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.TaskManagerExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static org.apache.flink.util.Preconditions.checkArgument;

/** A factory for memory segments ({@link MemorySegment}). */
@Internal
public final class MemorySegmentFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentFactory.class);
    private static final Runnable NO_OP = () -> {};

    /**
     * Creates a new memory segment that targets the given heap memory region.
     * 创建一个以给定堆内存区域为目标的新内存段。
     *
     * <p>This method should be used to turn short lived byte arrays into memory segments.
     * 应该使用此方法将寿命短的字节数组转换为内存段。
     *
     * @param buffer The heap memory region.
     * @return A new memory segment that targets the given heap memory region.
     */
    public static MemorySegment wrap(byte[] buffer) {
        return new MemorySegment(buffer, null);
    }

    /**
     * Copies the given heap memory region and creates a new memory segment wrapping it.
     * 复制给定的堆内存区域并创建一个包装它的新内存段。
     *
     * @param bytes The heap memory region.
     * @param start starting position, inclusive
     * @param end end position, exclusive
     * @return A new memory segment that targets a copy of the given heap memory region.
     * @throws IllegalArgumentException if start > end or end > bytes.length
     */
    public static MemorySegment wrapCopy(byte[] bytes, int start, int end)
            throws IllegalArgumentException {
        checkArgument(end >= start);
        checkArgument(end <= bytes.length);
        MemorySegment copy = allocateUnpooledSegment(end - start);
        copy.put(0, bytes, start, copy.size());
        return copy;
    }

    /**
     * Wraps the four bytes representing the given number with a {@link MemorySegment}.
     * 用 {@link MemorySegment} 包装表示给定数字的四个字节。
     *
     * @see ByteBuffer#putInt(int)
     */
    public static MemorySegment wrapInt(int value) {
        return wrap(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    /**
     * Allocates some unpooled memory and creates a new memory segment that represents that memory.
     * 分配一些未池化的内存并创建一个表示该内存的新内存段。
     *
     * <p>This method is similar to {@link #allocateUnpooledSegment(int, Object)}, but the memory
     * segment will have null as the owner.
     * 此方法类似于{@link #allocateUnpooledSegment(int, Object)}，但内存段将以null作为所有者。
     *
     * @param size The size of the memory segment to allocate.
     * @return A new memory segment, backed by unpooled heap memory.
     */
    public static MemorySegment allocateUnpooledSegment(int size) {
        return allocateUnpooledSegment(size, null);
    }

    /**
     * Allocates some unpooled memory and creates a new memory segment that represents that memory.
     * 分配一些未池化的内存并创建一个表示该内存的新内存段。
     *
     * <p>This method is similar to {@link #allocateUnpooledSegment(int)}, but additionally sets the
     * owner of the memory segment.
     * 此方法类似于 {@link #allocateUnpooledSegment(int)}，但额外设置了内存段的所有者。
     *
     * @param size The size of the memory segment to allocate.
     * @param owner The owner to associate with the memory segment.
     * @return A new memory segment, backed by unpooled heap memory.
     */
    public static MemorySegment allocateUnpooledSegment(int size, Object owner) {
        return new MemorySegment(new byte[size], owner);
    }

    /**
     * Allocates some unpooled off-heap memory and creates a new memory segment that represents that
     * memory.
     * 分配一些未池化的堆外内存并创建一个表示该内存的新内存段。
     *
     * @param size The size of the off-heap memory segment to allocate.
     * @return A new memory segment, backed by unpooled off-heap memory.
     */
    public static MemorySegment allocateUnpooledOffHeapMemory(int size) {
        return allocateUnpooledOffHeapMemory(size, null);
    }

    /**
     * Allocates some unpooled off-heap memory and creates a new memory segment that represents that
     * memory.
     * 分配一些未池化的堆外内存并创建一个表示该内存的新内存段。
     *
     * @param size The size of the off-heap memory segment to allocate.
     * @param owner The owner to associate with the off-heap memory segment.
     * @return A new memory segment, backed by unpooled off-heap memory.
     */
    public static MemorySegment allocateUnpooledOffHeapMemory(int size, Object owner) {
        ByteBuffer memory = allocateDirectMemory(size);
        return new MemorySegment(memory, owner);
    }

    @VisibleForTesting
    public static MemorySegment allocateOffHeapUnsafeMemory(int size) {
        return allocateOffHeapUnsafeMemory(size, null, NO_OP);
    }

    private static ByteBuffer allocateDirectMemory(int size) {
        //noinspection ErrorNotRethrown
        try {
            return ByteBuffer.allocateDirect(size);
        } catch (OutOfMemoryError outOfMemoryError) {
            // TODO: this error handling can be removed in future,
            // once we find a common way to handle OOM errors in netty threads.
            // Here we enrich it to propagate better OOM message to the receiver
            // if it happens in a netty thread.
            TaskManagerExceptionUtils.tryEnrichTaskManagerError(outOfMemoryError);
            if (ExceptionUtils.isDirectOutOfMemoryError(outOfMemoryError)) {
                LOG.error("Cannot allocate direct memory segment", outOfMemoryError);
            }

            ExceptionUtils.rethrow(outOfMemoryError);
            return null;
        }
    }

    /**
     * Allocates an off-heap unsafe memory and creates a new memory segment to represent that
     * memory.
     * 分配一个堆外不安全内存并创建一个新的内存段来表示该内存。
     *
     * <p>Creation of this segment schedules its memory freeing operation when its java wrapping
     * object is about to be garbage collected, similar to {@link
     * java.nio.DirectByteBuffer#DirectByteBuffer(int)}. The difference is that this memory
     * allocation is out of option -XX:MaxDirectMemorySize limitation.
     * 当它的 java 包装对象即将被垃圾回收时，这个段的创建安排了它的内存释放操作，
     * 类似于 {@link java.nio.DirectByteBuffer#DirectByteBuffer(int)}。
     * 不同之处在于此内存分配超出了选项 -XX:MaxDirectMemorySize 的限制。
     *
     * @param size The size of the off-heap unsafe memory segment to allocate.
     * @param owner The owner to associate with the off-heap unsafe memory segment.
     * @param customCleanupAction A custom action to run upon calling GC cleaner.
     * @return A new memory segment, backed by off-heap unsafe memory.
     */
    public static MemorySegment allocateOffHeapUnsafeMemory(
            int size, Object owner, Runnable customCleanupAction) {
        long address = MemoryUtils.allocateUnsafe(size);
        ByteBuffer offHeapBuffer = MemoryUtils.wrapUnsafeMemoryWithByteBuffer(address, size);
        Runnable cleaner = MemoryUtils.createMemoryCleaner(address, customCleanupAction);
        return new MemorySegment(offHeapBuffer, owner, false, cleaner);
    }

    /**
     * Creates a memory segment that wraps the off-heap memory backing the given ByteBuffer. Note
     * that the ByteBuffer needs to be a <i>direct ByteBuffer</i>.
     * 创建一个内存段，它包装支持给定 ByteBuffer 的堆外内存。 请注意，ByteBuffer 必须是 <i>直接 ByteBuffer</i>。
     *
     * <p>This method is intended to be used for components which pool memory and create memory
     * segments around long-lived memory regions.
     * 此方法旨在用于池内存并在长寿命内存区域周围创建内存段的组件。
     *
     * @param memory The byte buffer with the off-heap memory to be represented by the memory
     *     segment.
     * @return A new memory segment representing the given off-heap memory.
     */
    public static MemorySegment wrapOffHeapMemory(ByteBuffer memory) {
        return new MemorySegment(memory, null);
    }
}
