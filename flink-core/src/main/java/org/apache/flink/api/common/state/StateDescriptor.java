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

package org.apache.flink.api.common.state;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;
import org.apache.flink.util.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * Base class for state descriptors. A {@code StateDescriptor} is used for creating partitioned
 * {@link State} in stateful operations.
 * 状态描述符的基类。 {@code StateDescriptor} 用于在有状态操作中创建分区 {@link State}。
 *
 * <p>Subclasses must correctly implement {@link #equals(Object)} and {@link #hashCode()}.
 * 子类必须正确实现 {@link #equals(Object)} 和 {@link #hashCode()}。
 *
 * @param <S> The type of the State objects created from this {@code StateDescriptor}.
 * @param <T> The type of the value of the state object described by this state descriptor.
 */
@PublicEvolving
public abstract class StateDescriptor<S extends State, T> implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(StateDescriptor.class);

    /**
     * An enumeration of the types of supported states. Used to identify the state type when writing
     * and restoring checkpoints and savepoints.
     * 支持的状态类型的枚举。 用于在写入和恢复检查点和保存点时识别状态类型。
     */
    // IMPORTANT: Do not change the order of the elements in this enum, ordinal is used in
    // serialization
    //重要：不要改变这个枚举中元素的顺序，序号用于序列化
    public enum Type {
        /** @deprecated Enum for migrating from old checkpoints/savepoint versions. */
        @Deprecated
        UNKNOWN,
        VALUE,
        LIST,
        REDUCING,
        FOLDING,
        AGGREGATING,
        MAP
    }

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------------

    /** Name that uniquely identifies state created from this StateDescriptor.
     * 唯一标识从此 StateDescriptor 创建的状态的名称。
     * */
    protected final String name;

    /**
     * The serializer for the type. May be eagerly initialized in the constructor, or lazily once
     * the {@link #initializeSerializerUnlessSet(ExecutionConfig)} method is called.
     * 类型的序列化程序。 可以在构造函数中急切地初始化，
     * 或者在调用 {@link #initializeSerializerUnlessSet(ExecutionConfig)} 方法后延迟初始化。
     */
    private final AtomicReference<TypeSerializer<T>> serializerAtomicReference =
            new AtomicReference<>();

    /**
     * The type information describing the value type. Only used to if the serializer is created
     * lazily.
     * 描述值类型的类型信息。 仅用于延迟创建序列化程序的情况。
     */
    @Nullable private TypeInformation<T> typeInfo;

    /** Name for queries against state created from this StateDescriptor.
     * 针对从此 StateDescriptor 创建的状态的查询的名称。
     * */
    @Nullable private String queryableStateName;

    /** Name for queries against state created from this StateDescriptor.
     * 针对从此 StateDescriptor 创建的状态的查询的名称。
     * */
    @Nonnull private StateTtlConfig ttlConfig = StateTtlConfig.DISABLED;

    /** The default value returned by the state when no other value is bound to a key.
     * 当没有其他值绑定到某个键时，状态返回的默认值。
     * */
    @Nullable protected transient T defaultValue;

    // ------------------------------------------------------------------------

    /**
     * Create a new {@code StateDescriptor} with the given name and the given type serializer.
     * 使用给定名称和给定类型序列化程序创建一个新的 {@code StateDescriptor}。
     *
     * @param name The name of the {@code StateDescriptor}.
     * @param serializer The type serializer for the values in the state.
     * @param defaultValue The default value that will be set when requesting state without setting
     *     a value before.
     */
    protected StateDescriptor(String name, TypeSerializer<T> serializer, @Nullable T defaultValue) {
        this.name = checkNotNull(name, "name must not be null");
        this.serializerAtomicReference.set(checkNotNull(serializer, "serializer must not be null"));
        this.defaultValue = defaultValue;
    }

    /**
     * Create a new {@code StateDescriptor} with the given name and the given type information.
     * 使用给定的名称和给定的类型信息创建一个新的 {@code StateDescriptor}。
     *
     * @param name The name of the {@code StateDescriptor}.
     * @param typeInfo The type information for the values in the state.
     * @param defaultValue The default value that will be set when requesting state without setting
     *     a value before.
     */
    protected StateDescriptor(String name, TypeInformation<T> typeInfo, @Nullable T defaultValue) {
        this.name = checkNotNull(name, "name must not be null");
        this.typeInfo = checkNotNull(typeInfo, "type information must not be null");
        this.defaultValue = defaultValue;
    }

    /**
     * Create a new {@code StateDescriptor} with the given name and the given type information.
     * 使用给定的名称和给定的类型信息创建一个新的 {@code StateDescriptor}。
     *
     * <p>If this constructor fails (because it is not possible to describe the type via a class),
     * consider using the {@link #StateDescriptor(String, TypeInformation, Object)} constructor.
     * 如果此构造函数失败（因为无法通过类来描述类型），
     * 请考虑使用 {@link #StateDescriptor(String, TypeInformation, Object)} 构造函数。
     *
     * @param name The name of the {@code StateDescriptor}.
     * @param type The class of the type of values in the state.
     * @param defaultValue The default value that will be set when requesting state without setting
     *     a value before.
     */
    protected StateDescriptor(String name, Class<T> type, @Nullable T defaultValue) {
        this.name = checkNotNull(name, "name must not be null");
        checkNotNull(type, "type class must not be null");

        try {
            this.typeInfo = TypeExtractor.createTypeInfo(type);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not create the type information for '"
                            + type.getName()
                            + "'. "
                            + "The most common reason is failure to infer the generic type information, due to Java's type erasure. "
                            + "In that case, please pass a 'TypeHint' instead of a class to describe the type. "
                            + "For example, to describe 'Tuple2<String, String>' as a generic type, use "
                            + "'new PravegaDeserializationSchema<>(new TypeHint<Tuple2<String, String>>(){}, serializer);'",
                    e);
        }

        this.defaultValue = defaultValue;
    }

    // ------------------------------------------------------------------------

    /** Returns the name of this {@code StateDescriptor}. */
    public String getName() {
        return name;
    }

    /** Returns the default value. */
    public T getDefaultValue() {
        if (defaultValue != null) {
            TypeSerializer<T> serializer = serializerAtomicReference.get();
            if (serializer != null) {
                return serializer.copy(defaultValue);
            } else {
                throw new IllegalStateException("Serializer not yet initialized.");
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the {@link TypeSerializer} that can be used to serialize the value in the state. Note
     * that the serializer may initialized lazily and is only guaranteed to exist after calling
     * {@link #initializeSerializerUnlessSet(ExecutionConfig)}.
     * 返回可用于序列化状态值的 {@link TypeSerializer}。 请注意，序列化程序可能会延迟初始化，
     * 并且仅在调用 {@link #initializeSerializerUnlessSet(ExecutionConfig)} 后才保证存在。
     */
    public TypeSerializer<T> getSerializer() {
        TypeSerializer<T> serializer = serializerAtomicReference.get();
        if (serializer != null) {
            return serializer.duplicate();
        } else {
            throw new IllegalStateException("Serializer not yet initialized.");
        }
    }

    @VisibleForTesting
    final TypeSerializer<T> getOriginalSerializer() {
        TypeSerializer<T> serializer = serializerAtomicReference.get();
        if (serializer != null) {
            return serializer;
        } else {
            throw new IllegalStateException("Serializer not yet initialized.");
        }
    }

    /**
     * Sets the name for queries of state created from this descriptor.
     * 设置从此描述符创建的状态查询的名称。
     *
     * <p>If a name is set, the created state will be published for queries during runtime. The name
     * needs to be unique per job. If there is another state instance published under the same name,
     * the job will fail during runtime.
     * 如果设置了名称，则创建的状态将在运行时发布以供查询。 每个作业的名称必须是唯一的。
     * 如果有另一个以相同名称发布的状态实例，则作业将在运行时失败。
     *
     * @param queryableStateName State name for queries (unique name per job)
     * @throws IllegalStateException If queryable state name already set
     */
    public void setQueryable(String queryableStateName) {
        Preconditions.checkArgument(
                ttlConfig.getUpdateType() == StateTtlConfig.UpdateType.Disabled,
                "Queryable state is currently not supported with TTL");
        if (this.queryableStateName == null) {
            this.queryableStateName =
                    Preconditions.checkNotNull(queryableStateName, "Registration name");
        } else {
            throw new IllegalStateException("Queryable state name already set");
        }
    }

    /**
     * Returns the queryable state name.
     * 返回可查询的状态名称。
     *
     * @return Queryable state name or <code>null</code> if not set.
     */
    @Nullable
    public String getQueryableStateName() {
        return queryableStateName;
    }

    /**
     * Returns whether the state created from this descriptor is queryable.
     * 返回从此描述符创建的状态是否可查询。
     *
     * @return <code>true</code> if state is queryable, <code>false</code> otherwise.
     */
    public boolean isQueryable() {
        return queryableStateName != null;
    }

    /**
     * Configures optional activation of state time-to-live (TTL).
     * 配置状态生存时间 (TTL) 的可选激活。
     *
     * <p>State user value will expire, become unavailable and be cleaned up in storage depending on
     * configured {@link StateTtlConfig}.
     * 根据配置的 {@link StateTtlConfig}，状态用户值将过期、变为不可用并在存储中清理。
     *
     * @param ttlConfig configuration of state TTL
     */
    public void enableTimeToLive(StateTtlConfig ttlConfig) {
        Preconditions.checkNotNull(ttlConfig);
        Preconditions.checkArgument(
                ttlConfig.getUpdateType() != StateTtlConfig.UpdateType.Disabled
                        && queryableStateName == null,
                "Queryable state is currently not supported with TTL");
        this.ttlConfig = ttlConfig;
    }

    @Nonnull
    @Internal
    public StateTtlConfig getTtlConfig() {
        return ttlConfig;
    }

    // ------------------------------------------------------------------------

    /**
     * Checks whether the serializer has been initialized. Serializer initialization is lazy, to
     * allow parametrization of serializers with an {@link ExecutionConfig} via {@link
     * #initializeSerializerUnlessSet(ExecutionConfig)}.
     * 检查序列化程序是否已初始化。 序列化器初始化是惰性的，
     * 以允许通过 {@link #initializeSerializerUnlessSet(ExecutionConfig)}
     * 使用 {@link ExecutionConfig} 对序列化器进行参数化。
     *
     * @return True if the serializers have been initialized, false otherwise.
     */
    public boolean isSerializerInitialized() {
        return serializerAtomicReference.get() != null;
    }

    /**
     * Initializes the serializer, unless it has been initialized before.
     * 初始化序列化程序，除非它之前已初始化。
     *
     * @param executionConfig The execution config to use when creating the serializer.
     */
    public void initializeSerializerUnlessSet(ExecutionConfig executionConfig) {
        if (serializerAtomicReference.get() == null) {
            checkState(typeInfo != null, "no serializer and no type info");
            // try to instantiate and set the serializer
            TypeSerializer<T> serializer = typeInfo.createSerializer(executionConfig);
            // use cas to assure the singleton
            if (!serializerAtomicReference.compareAndSet(null, serializer)) {
                LOG.debug("Someone else beat us at initializing the serializer.");
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Standard Utils
    // ------------------------------------------------------------------------

    @Override
    public final int hashCode() {
        return name.hashCode() + 31 * getClass().hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o != null && o.getClass() == this.getClass()) {
            final StateDescriptor<?, ?> that = (StateDescriptor<?, ?>) o;
            return this.name.equals(that.name);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{name="
                + name
                + ", defaultValue="
                + defaultValue
                + ", serializer="
                + serializerAtomicReference.get()
                + (isQueryable() ? ", queryableStateName=" + queryableStateName + "" : "")
                + '}';
    }

    public abstract Type getType();

    // ------------------------------------------------------------------------
    //  Serialization
    // ------------------------------------------------------------------------

    private void writeObject(final ObjectOutputStream out) throws IOException {
        // write all the non-transient fields
        out.defaultWriteObject();

        // write the non-serializable default value field
        if (defaultValue == null) {
            // we don't have a default value
            out.writeBoolean(false);
        } else {
            TypeSerializer<T> serializer = serializerAtomicReference.get();
            checkNotNull(serializer, "Serializer not initialized.");

            // we have a default value
            out.writeBoolean(true);

            byte[] serializedDefaultValue;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputViewStreamWrapper outView = new DataOutputViewStreamWrapper(baos)) {

                TypeSerializer<T> duplicateSerializer = serializer.duplicate();
                duplicateSerializer.serialize(defaultValue, outView);

                outView.flush();
                serializedDefaultValue = baos.toByteArray();
            } catch (Exception e) {
                throw new IOException(
                        "Unable to serialize default value of type "
                                + defaultValue.getClass().getSimpleName()
                                + ".",
                        e);
            }

            out.writeInt(serializedDefaultValue.length);
            out.write(serializedDefaultValue);
        }
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        // read the non-transient fields
        in.defaultReadObject();

        // read the default value field
        boolean hasDefaultValue = in.readBoolean();
        if (hasDefaultValue) {
            TypeSerializer<T> serializer = serializerAtomicReference.get();
            checkNotNull(serializer, "Serializer not initialized.");

            int size = in.readInt();

            byte[] buffer = new byte[size];

            in.readFully(buffer);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                    DataInputViewStreamWrapper inView = new DataInputViewStreamWrapper(bais)) {

                defaultValue = serializer.deserialize(inView);
            } catch (Exception e) {
                throw new IOException("Unable to deserialize default value.", e);
            }
        } else {
            defaultValue = null;
        }
    }
}
