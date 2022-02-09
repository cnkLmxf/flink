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

import javax.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * BoundedData is the data store in a single bounded blocking subpartition.
 * BoundedData 是单个有界阻塞子分区中的数据存储。
 *
 * <h2>Life cycle</h2>
 *
 * <p>The BoundedData is first created during the "write phase" by writing a sequence of buffers
 * through the {@link #writeBuffer(Buffer)} method. The write phase is ended by calling {@link
 * #finishWrite()}. After the write phase is finished, the data can be read multiple times through
 * readers created via {@link #createReader(ResultSubpartitionView)}. Finally, the BoundedData is
 * dropped / deleted by calling {@link #close()}.
 * BoundedData 首先在“写入阶段”通过 {@link #writeBuffer(Buffer)} 方法写入一系列缓冲区来创建。
 * 写入阶段通过调用 {@link #finishWrite()} 结束。
 * 写入阶段完成后，可以通过 {@link #createReader(ResultSubpartitionView)} 创建的 reader 多次读取数据。
 * 最后，通过调用 {@link #close()} 删除/删除 BoundedData。
 *
 * <h2>Thread Safety and Concurrency</h2>
 *
 * <p>The implementations generally make no assumptions about thread safety. The only contract is
 * that multiple created readers must be able to work independently concurrently.
 * 这些实现通常不对线程安全做出任何假设。 唯一的约定是多个创建的阅读器必须能够同时独立工作。
 */
interface BoundedData extends Closeable {

    /**
     * Writes this buffer to the bounded data. This call fails if the writing phase was already
     * finished via {@link #finishWrite()}.
     * 将此缓冲区写入有界数据。 如果写入阶段已经通过 {@link #finishWrite()} 完成，则此调用失败。
     */
    void writeBuffer(Buffer buffer) throws IOException;

    /**
     * Finishes the current region and prevents further writes. After calling this method, further
     * calls to {@link #writeBuffer(Buffer)} will fail.
     * 完成当前区域并防止进一步写入。 调用此方法后，对 {@link #writeBuffer(Buffer)} 的进一步调用将失败。
     */
    void finishWrite() throws IOException;

    /**
     * Gets a reader for the bounded data. Multiple readers may be created. This call only succeeds
     * once the write phase was finished via {@link #finishWrite()}.
     * 获取有界数据的读取器。 可以创建多个阅读器。 只有通过 {@link #finishWrite()} 完成写入阶段后，此调用才会成功。
     */
    BoundedData.Reader createReader(ResultSubpartitionView subpartitionView) throws IOException;

    /**
     * Gets a reader for the bounded data. Multiple readers may be created. This call only succeeds
     * once the write phase was finished via {@link #finishWrite()}.
     * 获取有界数据的读取器。 可以创建多个阅读器。 只有通过 {@link #finishWrite()} 完成写入阶段后，此调用才会成功。
     */
    default BoundedData.Reader createReader() throws IOException {
        return createReader(new NoOpResultSubpartitionView());
    }

    /**
     * Gets the number of bytes of all written data (including the metadata in the buffer headers).
     * 获取所有写入数据的字节数（包括缓冲区标头中的元数据）。
     */
    long getSize();

    /** The file path for the persisted {@link BoundedBlockingSubpartition}.
     * 持久化 {@link BoundedBlockingSubpartition} 的文件路径。
     * */
    Path getFilePath();

    // ------------------------------------------------------------------------

    /** A reader to the bounded data.
     * 有界数据的读者。
     * */
    interface Reader extends Closeable {

        @Nullable
        Buffer nextBuffer() throws IOException;
    }
}
