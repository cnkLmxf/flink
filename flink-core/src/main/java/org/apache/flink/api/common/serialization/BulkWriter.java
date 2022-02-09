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

package org.apache.flink.api.common.serialization;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.fs.FSDataOutputStream;

import java.io.IOException;
import java.io.Serializable;

/**
 * An encoder that encodes data in a bulk fashion, encoding many records together at a time.
 * 一种以批量方式对数据进行编码的编码器，一次将许多记录编码在一起。
 *
 * <p>Examples for bulk encoding are most compressed formats, including formats like Parquet and ORC
 * which encode batches of records into blocks of column vectors.
 * 批量编码的示例是大多数压缩格式，包括像 Parquet 和 ORC 这样的格式，它们将成批的记录编码为列向量块。
 *
 * <p>The bulk encoder may be stateful and is bound to a single stream during its lifetime.
 * 批量编码器可能是有状态的，并且在其生命周期内绑定到单个流。
 *
 * @param <T> The type of the elements encoded through this encoder.
 */
@PublicEvolving
public interface BulkWriter<T> {

    /**
     * Adds an element to the encoder. The encoder may temporarily buffer the element, or
     * immediately write it to the stream.
     * 向编码器添加一个元素。 编码器可以临时缓冲元素，或者立即将其写入流。
     *
     * <p>It may be that adding this element fills up an internal buffer and causes the encoding and
     * flushing of a batch of internally buffered elements.
     * 添加此元素可能会填满内部缓冲区并导致一批内部缓冲元素的编码和刷新。
     *
     * @param element The element to add.
     * @throws IOException Thrown, if the element cannot be added to the encoder, or if the output
     *     stream throws an exception.
     */
    void addElement(T element) throws IOException;

    /**
     * Flushes all intermediate buffered data to the output stream. It is expected that flushing
     * often may reduce the efficiency of the encoding.
     * 将所有中间缓冲数据刷新到输出流。 预计经常刷新可能会降低编码的效率。
     *
     * @throws IOException Thrown if the encoder cannot be flushed, or if the output stream throws
     *     an exception.
     */
    void flush() throws IOException;

    /**
     * Finishes the writing. This must flush all internal buffer, finish encoding, and write
     * footers.
     * 写完。 这必须刷新所有内部缓冲区、完成编码并写入页脚。
     *
     * <p>The writer is not expected to handle any more records via {@link #addElement(Object)}
     * after this method is called.
     * 调用此方法后，作者不应再通过 {@link #addElement(Object)} 处理任何记录。
     *
     * <p><b>Important:</b> This method MUST NOT close the stream that the writer writes to. Closing
     * the stream is expected to happen through the invoker of this method afterwards.
     * <b>重要提示：</b>此方法不得关闭 writer 写入的流。 预计随后将通过此方法的调用者关闭流。
     *
     * @throws IOException Thrown if the finalization fails.
     */
    void finish() throws IOException;

    // ------------------------------------------------------------------------

    /**
     * A factory that creates a {@link BulkWriter}.
     * 创建 {@link BulkWriter} 的工厂。
     *
     * @param <T> The type of record to write.
     */
    @FunctionalInterface
    interface Factory<T> extends Serializable {

        /**
         * Creates a writer that writes to the given stream.
         * 创建写入给定流的写入器。
         *
         * @param out The output stream to write the encoded data to.
         * @throws IOException Thrown if the writer cannot be opened, or if the output stream throws
         *     an exception.
         */
        BulkWriter<T> create(FSDataOutputStream out) throws IOException;
    }
}
