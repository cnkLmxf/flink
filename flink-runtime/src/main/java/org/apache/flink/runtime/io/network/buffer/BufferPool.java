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

/** A dynamically sized buffer pool.
 * 动态大小的缓冲池。
 * */
public interface BufferPool extends BufferProvider, BufferRecycler {

    /**
     * Destroys this buffer pool.
     * 销毁此缓冲池。
     *
     * <p>If not all buffers are available, they are recycled lazily as soon as they are recycled.
     * 如果不是所有的缓冲区都可用，它们一被回收就会被懒惰地回收。
     */
    void lazyDestroy();

    /** Checks whether this buffer pool has been destroyed.
     * 检查此缓冲池是否已被销毁。
     * */
    @Override
    boolean isDestroyed();

    /** Returns the number of guaranteed (minimum number of) memory segments of this buffer pool.
     * 返回此缓冲池的保证（最小数量）内存段数。
     * */
    int getNumberOfRequiredMemorySegments();

    /**
     * Returns the maximum number of memory segments this buffer pool should use.
     * 返回此缓冲池应使用的最大内存段数。
     *
     * @return maximum number of memory segments to use or <tt>-1</tt> if unlimited
     */
    int getMaxNumberOfMemorySegments();

    /**
     * Returns the current size of this buffer pool.
     * 返回此缓冲池的当前大小。
     *
     * <p>The size of the buffer pool can change dynamically at runtime.
     */
    int getNumBuffers();

    /**
     * Sets the current size of this buffer pool.
     * 设置此缓冲池的当前大小。
     *
     * <p>The size needs to be greater or equal to the guaranteed number of memory segments.
     * 大小需要大于或等于保证的内存段数。
     */
    void setNumBuffers(int numBuffers);

    /** Returns the number memory segments, which are currently held by this buffer pool.
     * 返回此缓冲池当前持有的内存段数。
     * */
    int getNumberOfAvailableMemorySegments();

    /** Returns the number of used buffers of this buffer pool.
     * 返回此缓冲池的已使用缓冲区数。
     * */
    int bestEffortGetNumOfUsedBuffers();
}
