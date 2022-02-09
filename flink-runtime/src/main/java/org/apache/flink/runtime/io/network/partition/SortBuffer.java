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
import org.apache.flink.runtime.io.network.buffer.Buffer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Data of different channels can be appended to a {@link SortBuffer} and after the {@link
 * SortBuffer} is finished, the appended data can be copied from it in channel index order.
 * 不同通道的数据可以附加到 {@link SortBuffer} 中，在 {@link SortBuffer} 完成后，可以按照通道索引顺序从中复制附加的数据。
 */
public interface SortBuffer {

    /**
     * Appends data of the specified channel to this {@link SortBuffer} and returns true if all
     * bytes of the source buffer is copied to this {@link SortBuffer} successfully, otherwise if
     * returns false, nothing will be copied.
     * 将指定通道的数据附加到此 {@link SortBuffer} 中，
     * 如果源缓冲区的所有字节都成功复制到此 {@link SortBuffer} 中，则返回 true，否则返回 false，则不会复制任何内容。
     */
    boolean append(ByteBuffer source, int targetChannel, Buffer.DataType dataType)
            throws IOException;

    /**
     * Copies data in this {@link SortBuffer} to the target {@link MemorySegment} in channel index
     * order and returns {@link BufferWithChannel} which contains the copied data and the
     * corresponding channel index.
     * 将此 {@link SortBuffer} 中的数据按通道索引顺序复制到目标
     * {@link MemorySegment} 并返回包含复制数据和相应通道索引的 {@link BufferWithChannel}。
     */
    BufferWithChannel copyIntoSegment(MemorySegment target);

    /** Returns the number of records written to this {@link SortBuffer}.
     * 返回写入此 {@link SortBuffer} 的记录数。
     * */
    long numRecords();

    /** Returns the number of bytes written to this {@link SortBuffer}.
     * 返回写入此 {@link SortBuffer} 的字节数。
     * */
    long numBytes();

    /** Returns true if there is still data can be consumed in this {@link SortBuffer}.
     * 如果此 {@link SortBuffer} 中仍有数据可以使用，则返回 true。
     * */
    boolean hasRemaining();

    /** Finishes this {@link SortBuffer} which means no record can be appended any more.
     * 完成此 {@link SortBuffer}，这意味着不能再附加任何记录。
     * */
    void finish();

    /** Whether this {@link SortBuffer} is finished or not.
     * 这个 {@link SortBuffer} 是否完成。
     * */
    boolean isFinished();

    /** Releases this {@link SortBuffer} which releases all resources.
     * 释放这个 {@link SortBuffer} 释放所有资源。
     * */
    void release();

    /** Whether this {@link SortBuffer} is released or not.
     * 这个 {@link SortBuffer} 是否被释放。
     * */
    boolean isReleased();
}
