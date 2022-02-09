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

package org.apache.flink.api.common.typeutils;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

import static org.apache.flink.api.common.typeutils.TypeSerializerConfigSnapshot.ADAPTER_VERSION;

/**
 * A {@code TypeSerializerSnapshot} is a point-in-time view of a {@link TypeSerializer}'s
 * configuration. The configuration snapshot of a serializer is persisted within checkpoints as a
 * single source of meta information about the schema of serialized data in the checkpoint. This
 * serves three purposes:
 * {@code TypeSerializerSnapshot} 是 {@link TypeSerializer} 配置的时间点视图。
 * 序列化程序的配置快照作为有关检查点中序列化数据模式的元信息的单一来源保存在检查点中。 这有三个目的：
 *<ul>
 *    <li><strong>捕获序列化程序参数和架构：</strong>序列化程序的配置快照表示有关序列化程序的参数、状态和架构的信息。
 *    这将在下面更详细地解释。
 *   <li><strong>新序列化器的兼容性检查：</strong>当新的序列化器可用时，需要检查它们是否兼容读取之前序列化器写入的数据。
 *   这是通过将新的序列化程序提供给检查点中的相应序列化程序配置快照来执行的。
 *    <li><strong>需要模式转换时读取序列化程序的工厂：</strong>在新序列化程序不兼容读取先前数据的情况下，
 *    需要在新序列化程序之前对所有数据执行模式转换过程可以继续使用。
 *    此转换过程需要兼容的读取序列化程序将序列化字节恢复为对象，然后使用新的序列化程序再次写回。
 *    在这种情况下，检查点中的序列化程序配置快照兼作转换过程的读取序列化程序的工厂。
 * </ul>
 * <ul>
 *   <li><strong>Capturing serializer parameters and schema:</strong> a serializer's configuration
 *       snapshot represents information about the parameters, state, and schema of a serializer.
 *       This is explained in more detail below.
 *   <li><strong>Compatibility checks for new serializers:</strong> when new serializers are
 *       available, they need to be checked whether or not they are compatible to read the data
 *       written by the previous serializer. This is performed by providing the new serializer to
 *       the corresponding serializer configuration snapshots in checkpoints.
 *   <li><strong>Factory for a read serializer when schema conversion is required:</strong> in the
 *       case that new serializers are not compatible to read previous data, a schema conversion
 *       process executed across all data is required before the new serializer can be continued to
 *       be used. This conversion process requires a compatible read serializer to restore
 *       serialized bytes as objects, and then written back again using the new serializer. In this
 *       scenario, the serializer configuration snapshots in checkpoints doubles as a factory for
 *       the read serializer of the conversion process.
 * </ul>
 *
 * <h2>Serializer Configuration and Schema</h2>
 * 序列化器配置和架构
 *
 * <p>Since serializer configuration snapshots needs to be used to ensure serialization
 * compatibility for the same managed state as well as serving as a factory for compatible read
 * serializers, the configuration snapshot should encode sufficient information about:
 * 由于需要使用序列化器配置快照来确保相同托管状态的序列化兼容性以及充当兼容读取序列化器的工厂，
 * 因此配置快照应编码以下方面的足够信息：
 *
 * <ul>
 *   <li><strong>Parameter settings of the serializer:</strong> parameters of the serializer include
 *       settings required to setup the serializer, or the state of the serializer if it is
 *       stateful. If the serializer has nested serializers, then the configuration snapshot should
 *       also contain the parameters of the nested serializers.
 *   <li><strong>Serialization schema of the serializer:</strong> the binary format used by the
 *       serializer, or in other words, the schema of data written by the serializer.
 * </ul>
 * <ul>
 *   <li><strong>序列化器的参数设置：</strong>序列化器的参数包括设置序列化器所需的设置，
 *   或序列化器的状态（如果它是有状态的）。 如果序列化器有嵌套序列化器，那么配置快照也应该包含嵌套序列化器的参数。
 *   <li><strong>序列化器的序列化模式：</strong>序列化器使用的二进制格式，或者换句话说，序列化器写入的数据的模式。
 * </ul>
 *
 * <p>NOTE: Implementations must contain the default empty nullary constructor. This is required to
 * be able to deserialize the configuration snapshot from its binary form.
 * 注意：实现必须包含默认的空 nullary 构造函数。 这是能够从其二进制形式反序列化配置快照所必需的。
 *
 * @param <T> The data type that the originating serializer of this configuration serializes.
 */
@PublicEvolving
public interface TypeSerializerSnapshot<T> {

    /**
     * Returns the version of the current snapshot's written binary format.
     *
     * @return the version of the current snapshot's written binary format.
     */
    int getCurrentVersion();

    /**
     * Writes the serializer snapshot to the provided {@link DataOutputView}. The current version of
     * the written serializer snapshot's binary format is specified by the {@link
     * #getCurrentVersion()} method.
     * 将序列化程序快照写入提供的 {@link DataOutputView}。
     * 写入的序列化程序快照的二进制格式的当前版本由 {@link #getCurrentVersion()} 方法指定。
     *
     * @param out the {@link DataOutputView} to write the snapshot to.
     * @throws IOException Thrown if the snapshot data could not be written.
     * @see #writeVersionedSnapshot(DataOutputView, TypeSerializerSnapshot)
     */
    void writeSnapshot(DataOutputView out) throws IOException;

    /**
     * Reads the serializer snapshot from the provided {@link DataInputView}. The version of the
     * binary format that the serializer snapshot was written with is provided. This version can be
     * used to determine how the serializer snapshot should be read.
     * 从提供的 {@link DataInputView} 读取序列化程序快照。 提供了用于编写序列化程序快照的二进制格式的版本。
     * 此版本可用于确定应如何读取序列化程序快照。
     *
     * @param readVersion version of the serializer snapshot's written binary format
     * @param in the {@link DataInputView} to read the snapshot from.
     * @param userCodeClassLoader the user code classloader
     * @throws IOException Thrown if the snapshot data could be read or parsed.
     * @see #readVersionedSnapshot(DataInputView, ClassLoader)
     */
    void readSnapshot(int readVersion, DataInputView in, ClassLoader userCodeClassLoader)
            throws IOException;

    /**
     * Recreates a serializer instance from this snapshot. The returned serializer can be safely
     * used to read data written by the prior serializer (i.e., the serializer that created this
     * snapshot).
     * 从此快照重新创建序列化程序实例。 返回的序列化程序可以安全地用于读取先前序列化程序（即创建此快照的序列化程序）写入的数据。
     *
     * @return a serializer instance restored from this serializer snapshot.
     */
    TypeSerializer<T> restoreSerializer();

    /**
     * Checks a new serializer's compatibility to read data written by the prior serializer.
     * 检查新序列化程序对读取先前序列化程序写入的数据的兼容性。
     *
     * <p>When a checkpoint/savepoint is restored, this method checks whether the serialization
     * format of the data in the checkpoint/savepoint is compatible for the format of the serializer
     * used by the program that restores the checkpoint/savepoint. The outcome can be that the
     * serialization format is compatible, that the program's serializer needs to reconfigure itself
     * (meaning to incorporate some information from the TypeSerializerSnapshot to be compatible),
     * that the format is outright incompatible, or that a migration needed. In the latter case, the
     * TypeSerializerSnapshot produces a serializer to deserialize the data, and the restoring
     * program's serializer re-serializes the data, thus converting the format during the restore
     * operation.
     * 恢复检查点/保存点时，该方法检查检查点/保存点中数据的序列化格式是否与恢复检查点/保存点的程序使用的序列化器格式兼容。
     * 结果可能是序列化格式兼容，程序的序列化程序需要重新配置自身（意味着合并来自 TypeSerializerSnapshot 的一些信息以兼容），
     * 格式完全不兼容，或者需要迁移。 在后一种情况下，TypeSerializerSnapshot 产生一个序列化器来反序列化数据，
     * 恢复程序的序列化器重新序列化数据，从而在恢复操作期间转换格式。
     *
     * @param newSerializer the new serializer to check.
     * @return the serializer compatibility result.
     */
    TypeSerializerSchemaCompatibility<T> resolveSchemaCompatibility(
            TypeSerializer<T> newSerializer);

    // ------------------------------------------------------------------------
    //  read / write utilities
    // ------------------------------------------------------------------------

    /**
     * Writes the given snapshot to the out stream. One should always use this method to write
     * snapshots out, rather than directly calling {@link #writeSnapshot(DataOutputView)}.
     * 将给定的快照写入输出流。 应始终使用此方法写入快照，而不是直接调用 {@link #writeSnapshot(DataOutputView)}。
     *
     * <p>The snapshot written with this method can be read via {@link
     * #readVersionedSnapshot(DataInputView, ClassLoader)}.
     * 使用此方法写入的快照可以通过 {@link #readVersionedSnapshot(DataInputView, ClassLoader)} 读取。
     */
    static void writeVersionedSnapshot(DataOutputView out, TypeSerializerSnapshot<?> snapshot)
            throws IOException {
        out.writeUTF(snapshot.getClass().getName());
        out.writeInt(snapshot.getCurrentVersion());
        snapshot.writeSnapshot(out);
    }

    /**
     * Reads a snapshot from the stream, performing resolving
     * 从流中读取快照，执行解析
     *
     * <p>This method reads snapshots written by {@link #writeVersionedSnapshot(DataOutputView,
     * TypeSerializerSnapshot)}.
     */
    static <T> TypeSerializerSnapshot<T> readVersionedSnapshot(DataInputView in, ClassLoader cl)
            throws IOException {
        final TypeSerializerSnapshot<T> snapshot =
                TypeSerializerSnapshotSerializationUtil.readAndInstantiateSnapshotClass(in, cl);

        int version = in.readInt();

        if (version == ADAPTER_VERSION && !(snapshot instanceof TypeSerializerConfigSnapshot)) {
            // the snapshot was upgraded directly in-place from a TypeSerializerConfigSnapshot;
            // read and drop the previously Java-serialized serializer, and get the actual correct
            // read version.
            // NOTE: this implicitly assumes that the version was properly written before the actual
            // snapshot content.
            TypeSerializerSerializationUtil.tryReadSerializer(in, cl, true);
            version = in.readInt();
        }
        snapshot.readSnapshot(version, in, cl);

        return snapshot;
    }
}
