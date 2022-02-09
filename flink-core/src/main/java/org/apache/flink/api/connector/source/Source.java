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

package org.apache.flink.api.connector.source;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.Serializable;

/**
 * The interface for Source. It acts like a factory class that helps construct the {@link
 * SplitEnumerator} and {@link SourceReader} and corresponding serializers.
 * Source 的接口。 它就像一个工厂类，帮助构建 {@link SplitEnumerator} 和 {@link SourceReader} 以及相应的序列化程序。
 *
 * @param <T> The type of records produced by the source.
 * @param <SplitT> The type of splits handled by the source.
 * @param <EnumChkT> The type of the enumerator checkpoints.
 */
@PublicEvolving
public interface Source<T, SplitT extends SourceSplit, EnumChkT> extends Serializable {

    /**
     * Get the boundedness of this source.
     * 获取此源的有界性。
     *
     * @return the boundedness of this source.
     */
    Boundedness getBoundedness();

    /**
     * Creates a new reader to read data from the splits it gets assigned. The reader starts fresh
     * and does not have any state to resume.
     * 创建一个新的读取器以从分配的拆分中读取数据。 读者重新开始，没有任何状态可以恢复。
     *
     * @param readerContext The {@link SourceReaderContext context} for the source reader.
     * @return A new SourceReader.
     * @throws Exception The implementor is free to forward all exceptions directly. Exceptions
     *     thrown from this method cause task failure/recovery.
     */
    SourceReader<T, SplitT> createReader(SourceReaderContext readerContext) throws Exception;

    /**
     * Creates a new SplitEnumerator for this source, starting a new input.
     * 为此源创建一个新的 SplitEnumerator，开始一个新的输入。
     *
     * @param enumContext The {@link SplitEnumeratorContext context} for the split enumerator.
     * @return A new SplitEnumerator.
     * @throws Exception The implementor is free to forward all exceptions directly. * Exceptions
     *     thrown from this method cause JobManager failure/recovery.
     */
    SplitEnumerator<SplitT, EnumChkT> createEnumerator(SplitEnumeratorContext<SplitT> enumContext)
            throws Exception;

    /**
     * Restores an enumerator from a checkpoint.
     * 从检查点恢复枚举器。
     *
     * @param enumContext The {@link SplitEnumeratorContext context} for the restored split
     *     enumerator.
     * @param checkpoint The checkpoint to restore the SplitEnumerator from.
     * @return A SplitEnumerator restored from the given checkpoint.
     * @throws Exception The implementor is free to forward all exceptions directly. * Exceptions
     *     thrown from this method cause JobManager failure/recovery.
     */
    SplitEnumerator<SplitT, EnumChkT> restoreEnumerator(
            SplitEnumeratorContext<SplitT> enumContext, EnumChkT checkpoint) throws Exception;

    // ------------------------------------------------------------------------
    //  serializers for the metadata
    // ------------------------------------------------------------------------

    /**
     * Creates a serializer for the source splits. Splits are serialized when sending them from
     * enumerator to reader, and when checkpointing the reader's current state.
     * 为源拆分创建一个序列化程序。 在将拆分从枚举器发送到阅读器以及检查阅读器的当前状态时，拆分会被序列化。
     *
     * @return The serializer for the split type.
     */
    SimpleVersionedSerializer<SplitT> getSplitSerializer();

    /**
     * Creates the serializer for the {@link SplitEnumerator} checkpoint. The serializer is used for
     * the result of the {@link SplitEnumerator#snapshotState()} method.
     * 为 {@link SplitEnumerator} 检查点创建序列化程序。
     * 序列化程序用于 {@link SplitEnumerator#snapshotState()} 方法的结果。
     *
     * @return The serializer for the SplitEnumerator checkpoint.
     */
    SimpleVersionedSerializer<EnumChkT> getEnumeratorCheckpointSerializer();
}
