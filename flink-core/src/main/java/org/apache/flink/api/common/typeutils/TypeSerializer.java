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
import java.io.Serializable;

/**
 * This interface describes the methods that are required for a data type to be handled by the Flink
 * runtime. Specifically, this interface contains the serialization and copying methods.
 * 该接口描述了 Flink 运行时处理数据类型所需的方法。 具体来说，该接口包含序列化和复制方法。
 *
 * <p>The methods in this class are not necessarily thread safe. To avoid unpredictable side
 * effects, it is recommended to call {@code duplicate()} method and use one serializer instance per
 * thread.
 * 此类中的方法不一定是线程安全的。 为避免不可预知的副作用，建议调用 {@code duplicate()} 方法并为每个线程使用一个序列化器实例。
 *
 * <p><b>Upgrading TypeSerializers to the new TypeSerializerSnapshot model</b>
 * 将 TypeSerializers 升级到新的 TypeSerializerSnapshot 模型
 *
 * <p>This section is relevant if you implemented a TypeSerializer in Flink versions up to 1.6 and
 * want to adapt that implementation to the new interfaces that support proper state schema
 * evolution, while maintaining backwards compatibility. Please follow these steps:
 * 如果您在 Flink 1.6 之前的版本中实现了 TypeSerializer 并希望将该实现调整到支持正确状态模式演变的新接口，
 * 同时保持向后兼容性，则本节是相关的。 请按照以下步骤操作：
 *
 * <ul>
 *   <li>Change the type serializer's config snapshot to implement {@link TypeSerializerSnapshot},
 *       rather than extending {@code TypeSerializerConfigSnapshot} (as previously).
 *       更改类型序列化器的配置快照以实现 {@link TypeSerializerSnapshot}，
 *       而不是扩展 {@code TypeSerializerConfigSnapshot}（如前所述）。
 *   <li>If the above step was completed, then the upgrade is done. Otherwise, if changing to
 *       implement {@link TypeSerializerSnapshot} directly in-place as the same class isn't possible
 *       (perhaps because the new snapshot is intended to have completely different written contents
 *       or intended to have a different class name), retain the old serializer snapshot class
 *       (extending {@code TypeSerializerConfigSnapshot}) under the same name and give the updated
 *       serializer snapshot class (the one extending {@code TypeSerializerSnapshot}) a new name.
 *       如果上述步骤完成，则升级完成。 否则，如果更改为直接就地实现 {@link TypeSerializerSnapshot} 作为同一个类是不可能的
 *       （可能是因为新快照打算具有完全不同的书面内容或打算具有不同的类名），
 *       请保留旧的 序列化器快照类（扩展 {@code TypeSerializerConfigSnapshot}）
 *       并赋予更新的序列化器快照类（扩展 {@code TypeSerializerSnapshot}）一个新名称。
 *   <li>Override the {@code
 *       TypeSerializerConfigSnapshot#resolveSchemaCompatibility(TypeSerializer)} method to perform
 *       the compatibility check based on configuration written by the old serializer snapshot
 *       class.
 *       覆盖 {@code TypeSerializerConfigSnapshot#resolveSchemaCompatibility(TypeSerializer)} 方法以根据旧序列化器快照类编写的配置执行兼容性检查。
 * </ul>
 *
 * @param <T> The data type that the serializer serializes.
 */
@PublicEvolving
public abstract class TypeSerializer<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // --------------------------------------------------------------------------------------------
    // General information about the type and the serializer
    // --------------------------------------------------------------------------------------------

    /**
     * Gets whether the type is an immutable type.
     * 获取类型是否为不可变类型。
     *
     * @return True, if the type is immutable.
     */
    public abstract boolean isImmutableType();

    /**
     * Creates a deep copy of this serializer if it is necessary, i.e. if it is stateful. This can
     * return itself if the serializer is not stateful.
     * 如果有必要，即如果它是有状态的，则创建此序列化程序的深层副本。 如果序列化程序没有状态，这可以返回自身。
     *
     * <p>We need this because Serializers might be used in several threads. Stateless serializers
     * are inherently thread-safe while stateful serializers might not be thread-safe.
     * 我们需要它，因为序列化程序可能会在多个线程中使用。 无状态序列化器本质上是线程安全的，而有状态序列化器可能不是线程安全的。
     */
    public abstract TypeSerializer<T> duplicate();

    // --------------------------------------------------------------------------------------------
    // Instantiation & Cloning
    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new instance of the data type.
     * 创建数据类型的新实例。
     *
     * @return A new instance of the data type.
     */
    public abstract T createInstance();

    /**
     * Creates a deep copy of the given element in a new element.
     * 在新元素中创建给定元素的深层副本。
     *
     * @param from The element reuse be copied.
     * @return A deep copy of the element.
     */
    public abstract T copy(T from);

    /**
     * Creates a copy from the given element. The method makes an attempt to store the copy in the
     * given reuse element, if the type is mutable. This is, however, not guaranteed.
     * 从给定元素创建副本。 如果类型是可变的，该方法会尝试将副本存储在给定的重用元素中。 然而，这并不能保证。
     *
     * @param from The element to be copied.
     * @param reuse The element to be reused. May or may not be used.
     * @return A deep copy of the element.
     */
    public abstract T copy(T from, T reuse);

    // --------------------------------------------------------------------------------------------

    /**
     * Gets the length of the data type, if it is a fix length data type.
     * 获取数据类型的长度，如果它是定长数据类型。
     *
     * @return The length of the data type, or <code>-1</code> for variable length data types.
     * 数据类型的长度，或 <code>-1</code> 用于可变长度数据类型。
     */
    public abstract int getLength();

    // --------------------------------------------------------------------------------------------

    /**
     * Serializes the given record to the given target output view.
     * 将给定的记录序列化到给定的目标输出视图。
     *
     * @param record The record to serialize.
     * @param target The output view to write the serialized data to.
     * @throws IOException Thrown, if the serialization encountered an I/O related error. Typically
     *     raised by the output view, which may have an underlying I/O channel to which it
     *     delegates.
     *     如果序列化遇到 I/O 相关错误。 通常由输出视图引发，输出视图可能具有它委托的底层 I/O 通道。
     */
    public abstract void serialize(T record, DataOutputView target) throws IOException;

    /**
     * De-serializes a record from the given source input view.
     * 从给定的源输入视图反序列化记录。
     *
     * @param source The input view from which to read the data.
     *              从中读取数据的输入视图。
     * @return The deserialized element.
     * @throws IOException Thrown, if the de-serialization encountered an I/O related error.
     *     Typically raised by the input view, which may have an underlying I/O channel from which
     *     it reads.
     */
    public abstract T deserialize(DataInputView source) throws IOException;

    /**
     * De-serializes a record from the given source input view into the given reuse record instance
     * if mutable.
     * 如果可变，则将给定源输入视图中的记录反序列化为给定的重用记录实例。
     *
     * @param reuse The record instance into which to de-serialize the data.
     * @param source The input view from which to read the data.
     * @return The deserialized element.
     * @throws IOException Thrown, if the de-serialization encountered an I/O related error.
     *     Typically raised by the input view, which may have an underlying I/O channel from which
     *     it reads.
     */
    public abstract T deserialize(T reuse, DataInputView source) throws IOException;

    /**
     * Copies exactly one record from the source input view to the target output view. Whether this
     * operation works on binary data or partially de-serializes the record to determine its length
     * (such as for records of variable length) is up to the implementer. Binary copies are
     * typically faster. A copy of a record containing two integer numbers (8 bytes total) is most
     * efficiently implemented as {@code target.write(source, 8);}.
     * 将一条记录从源输入视图复制到目标输出视图。
     * 此操作是否适用于二进制数据或部分反序列化记录以确定其长度（例如对于可变长度的记录）取决于实现者。
     * 二进制副本通常更快。 包含两个整数（总共 8 个字节）的记录副本最有效地实现为 {@code target.write(source, 8);}。
     *
     * @param source The input view from which to read the record.
     * @param target The target output view to which to write the record.
     * @throws IOException Thrown if any of the two views raises an exception.
     */
    public abstract void copy(DataInputView source, DataOutputView target) throws IOException;

    public abstract boolean equals(Object obj);

    public abstract int hashCode();

    // --------------------------------------------------------------------------------------------
    // Serializer configuration snapshot for checkpoints/savepoints
    // --------------------------------------------------------------------------------------------

    /**
     * Snapshots the configuration of this TypeSerializer. This method is only relevant if the
     * serializer is used to state stored in checkpoints/savepoints.
     * 快照此 TypeSerializer 的配置。 仅当序列化程序用于状态存储在检查点/保存点中时，此方法才相关。
     *
     * <p>The snapshot of the TypeSerializer is supposed to contain all information that affects the
     * serialization format of the serializer. The snapshot serves two purposes: First, to reproduce
     * the serializer when the checkpoint/savepoint is restored, and second, to check whether the
     * serialization format is compatible with the serializer used in the restored program.
     * TypeSerializer 的快照应该包含影响序列化器序列化格式的所有信息。
     * 快照有两个目的：第一，在检查点/保存点恢复时重现序列化程序，第二，检查序列化格式是否与恢复程序中使用的序列化程序兼容。
     *
     * <p><b>IMPORTANT:</b> TypeSerializerSnapshots changed after Flink 1.6. Serializers implemented
     * against Flink versions up to 1.6 should still work, but adjust to new model to enable state
     * evolution and be future-proof. See the class-level comments, section "Upgrading
     * TypeSerializers to the new TypeSerializerSnapshot model" for details.
     * <b>重要提示：</b> TypeSerializerSnapshots 在 Flink 1.6 之后发生了变化。
     * 针对高达 1.6 的 Flink 版本实现的序列化程序应该仍然有效，但会调整到新模型以实现状态演化并面向未来。
     * 有关详细信息，请参阅类级注释，“将 TypeSerializers 升级到新的 TypeSerializerSnapshot 模型”部分。
     *
     * @see TypeSerializerSnapshot#resolveSchemaCompatibility(TypeSerializer)
     * @return snapshot of the serializer's current configuration (cannot be {@code null}).
     */
    public abstract TypeSerializerSnapshot<T> snapshotConfiguration();
}
