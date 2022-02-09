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
import org.apache.flink.runtime.io.AvailabilityProvider;

import javax.annotation.Nullable;

/**
 * A buffer provider to request buffers from in a synchronous or asynchronous fashion.
 * 以同步或异步方式请求缓冲区的缓冲区提供程序。
 *
 * <p>The data producing side (result partition writers) request buffers in a synchronous fashion,
 * whereas the input side requests asynchronously.
 * 数据生成端（结果分区写入器）以同步方式请求缓冲区，而输入端异步请求。
 */
public interface BufferProvider extends AvailabilityProvider {

    /**
     * Returns a {@link Buffer} instance from the buffer provider, if one is available.
     * 从缓冲区提供程序返回一个 {@link Buffer} 实例（如果可用）。
     *
     * @return {@code null} if no buffer is available or the buffer provider has been destroyed.
     * {@code null} 如果没有可用的缓冲区或缓冲区提供程序已被破坏。
     */
    @Nullable
    Buffer requestBuffer();

    /**
     * Returns a {@link BufferBuilder} instance from the buffer provider. This equals to {@link
     * #requestBufferBuilder(int)} with unknown target channel.
     * 从缓冲区提供程序返回一个 {@link BufferBuilder} 实例。
     * 这等于具有未知目标通道的 {@link #requestBufferBuilder(int)}。
     *
     * @return {@code null} if no buffer is available or the buffer provider has been destroyed.
     * {@code null} 如果没有可用的缓冲区或缓冲区提供程序已被破坏。
     */
    @Nullable
    BufferBuilder requestBufferBuilder();

    /**
     * Returns a {@link BufferBuilder} instance from the buffer provider.
     * 从缓冲区提供程序返回一个 {@link BufferBuilder} 实例。
     *
     * @param targetChannel to which the request will be accounted to.
     * @return {@code null} if no buffer is available or the buffer provider has been destroyed.
     */
    @Nullable
    BufferBuilder requestBufferBuilder(int targetChannel);

    /**
     * Returns a {@link BufferBuilder} instance from the buffer provider. This equals to {@link
     * #requestBufferBuilderBlocking(int)} with unknown target channel.
     * 从缓冲区提供程序返回一个 {@link BufferBuilder} 实例。
     * 这等于具有未知目标通道的 {@link #requestBufferBuilderBlocking(int)}。
     *
     * <p>If there is no buffer available, the call will block until one becomes available again or
     * the buffer provider has been destroyed.
     * 如果没有可用的缓冲区，则调用将阻塞，直到一个缓冲区再次可用或缓冲区提供程序已被销毁。
     */
    BufferBuilder requestBufferBuilderBlocking() throws InterruptedException;

    /**
     * Returns a {@link BufferBuilder} instance from the buffer provider.
     * 从缓冲区提供程序返回一个 {@link BufferBuilder} 实例。
     *
     * <p>If there is no buffer available, the call will block until one becomes available again or
     * the buffer provider has been destroyed.
     * 如果没有可用的缓冲区，则调用将阻塞，直到一个缓冲区再次可用或缓冲区提供程序已被销毁。
     *
     * @param targetChannel to which the request will be accounted to.
     */
    BufferBuilder requestBufferBuilderBlocking(int targetChannel) throws InterruptedException;

    /**
     * Adds a buffer availability listener to the buffer provider.
     * 向缓冲区提供程序添加缓冲区可用性侦听器。
     *
     * <p>The operation fails with return value <code>false</code>, when there is a buffer available
     * or the buffer provider has been destroyed.
     * 当有可用缓冲区或缓冲区提供程序已销毁时，操作失败并返回值 <code>false</code>。
     */
    boolean addBufferListener(BufferListener listener);

    /** Returns whether the buffer provider has been destroyed. */
    boolean isDestroyed();

    /**
     * Returns a {@link MemorySegment} instance from the buffer provider.
     * 从缓冲区提供程序返回一个 {@link MemorySegment} 实例。
     *
     * @return {@code null} if no memory segment is available or the buffer provider has been
     *     destroyed.
     *     {@code null} 如果没有可用的内存段或缓冲区提供程序已被破坏。
     */
    @Nullable
    MemorySegment requestMemorySegment();

    /**
     * Returns a {@link MemorySegment} instance from the buffer provider.
     * 从缓冲区提供程序返回一个 {@link MemorySegment} 实例。
     *
     * <p>If there is no memory segment available, the call will block until one becomes available
     * again or the buffer provider has been destroyed.
     * 如果没有可用的内存段，则调用将阻塞，直到内存段再次可用或缓冲区提供程序已被销毁。
     */
    MemorySegment requestMemorySegmentBlocking() throws InterruptedException;
}
