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

import java.io.IOException;

/** A factory for buffer pools. */
public interface BufferPoolFactory {

    /**
     * Tries to create a buffer pool, which is guaranteed to provide at least the number of required
     * buffers.
     * 尝试创建一个缓冲池，保证至少提供所需的缓冲区数量。
     *
     * <p>The buffer pool is of dynamic size with at least <tt>numRequiredBuffers</tt> buffers.
     * 缓冲池是动态大小的，至少有 <tt>numRequiredBuffers</tt> 个缓冲区。
     *
     * @param numRequiredBuffers minimum number of network buffers in this pool
     * @param maxUsedBuffers maximum number of network buffers this pool offers
     */
    BufferPool createBufferPool(int numRequiredBuffers, int maxUsedBuffers) throws IOException;

    /**
     * Tries to create a buffer pool with an owner, which is guaranteed to provide at least the
     * number of required buffers.
     * 尝试创建具有所有者的缓冲池，该所有者保证至少提供所需的缓冲区数量。
     *
     * <p>The buffer pool is of dynamic size with at least <tt>numRequiredBuffers</tt> buffers.
     * 缓冲池是动态大小的，至少有 <tt>numRequiredBuffers</tt> 个缓冲区。
     *
     * @param numRequiredBuffers minimum number of network buffers in this pool
     * @param maxUsedBuffers maximum number of network buffers this pool offers
     * @param numSubpartitions number of subpartitions in this pool
     * @param maxBuffersPerChannel maximum number of buffers to use for each channel
     */
    BufferPool createBufferPool(
            int numRequiredBuffers,
            int maxUsedBuffers,
            int numSubpartitions,
            int maxBuffersPerChannel)
            throws IOException;

    /** Destroy callback for updating factory book keeping.
     * 销毁更新工厂簿记的回调。
     * */
    void destroyBufferPool(BufferPool bufferPool) throws IOException;
}
