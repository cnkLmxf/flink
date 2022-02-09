/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.serialization;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.functions.InvalidTypesException;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.util.FlinkRuntimeException;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The deserialization schema describes how to turn the byte messages delivered by certain data
 * sources (for example Apache Kafka) into data types (Java/Scala objects) that are processed by
 * Flink.
 * 反序列化模式描述了如何将某些数据源（例如 Apache Kafka）传递的字节消息转换为 Flink 处理的数据类型（Java/Scala 对象）。
 *
 * <p>This base variant of the deserialization schema produces the type information automatically by
 * extracting it from the generic class arguments.
 * 这个反序列化模式的基本变体通过从通用类参数中提取类型信息来自动生成类型信息。
 *
 * <h3>Common Use</h3>
 * 常用
 *
 * <p>To write a deserialization schema for a specific type, simply extend this class and declare
 * the type in the class signature. Flink will reflectively determine the type and create the proper
 * TypeInformation:
 * 要为特定类型编写反序列化模式，只需扩展此类并在类签名中声明该类型。 Flink 将反射性地确定类型并创建正确的 TypeInformation：
 *
 * <pre>{@code
 * public class MyDeserializationSchema extends AbstractDeserializationSchema<MyType> {
 *
 *     public MyType deserialize(byte[] message) throws IOException {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <h3>Generic Use</h3>
 * 一般用途
 *
 * <p>If you want to write a more generic DeserializationSchema that works for different types, you
 * need to pass the TypeInformation (or an equivalent hint) to the constructor:
 * 如果要编写适用于不同类型的更通用的 DeserializationSchema，则需要将 TypeInformation（或等效提示）传递给构造函数：
 *
 * <pre>{@code
 * public class MyGenericSchema<T> extends AbstractDeserializationSchema<T> {
 *
 *     public MyGenericSchema(Class<T> type) {
 *         super(type);
 *     }
 *
 *     public T deserialize(byte[] message) throws IOException {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * @param <T> The type created by the deserialization schema.
 */
@PublicEvolving
public abstract class AbstractDeserializationSchema<T> implements DeserializationSchema<T> {

    private static final long serialVersionUID = 2L;

    /** The type produced by this {@code DeserializationSchema}.
     * 此 {@code DeserializationSchema} 生成的类型。
     * */
    private final TypeInformation<T> type;

    // ------------------------------------------------------------------------

    /**
     * Creates a new AbstractDeserializationSchema and tries to infer the type returned by this
     * DeserializationSchema.
     * 创建一个新的 AbstractDeserializationSchema 并尝试推断此 DeserializationSchema 返回的类型。
     *
     * <p>This constructor is usable whenever the DeserializationSchema concretely defines its type,
     * without generic variables:
     * 只要 DeserializationSchema 具体定义了它的类型，这个构造函数就可以使用，没有泛型变量：
     *
     * <pre>{@code
     * public class MyDeserializationSchema extends AbstractDeserializationSchema<MyType> {
     *
     *     public MyType deserialize(byte[] message) throws IOException {
     *         ...
     *     }
     * }
     * }</pre>
     */
    protected AbstractDeserializationSchema() {
        try {
            this.type =
                    TypeExtractor.createTypeInfo(
                            AbstractDeserializationSchema.class, getClass(), 0, null, null);
        } catch (InvalidTypesException e) {
            throw new FlinkRuntimeException(
                    "The implementation of AbstractDeserializationSchema is using a generic variable. "
                            + "This is not supported, because due to Java's generic type erasure, it will not be possible to "
                            + "determine the full type at runtime. For generic implementations, please pass the TypeInformation "
                            + "or type class explicitly to the constructor.");
        }
    }

    /**
     * Creates an AbstractDeserializationSchema that returns the TypeInformation indicated by the
     * given class. This constructor is only necessary when creating a generic implementation, see
     * {@link AbstractDeserializationSchema Generic Use}.
     * 创建一个 AbstractDeserializationSchema，它返回给定类指示的 TypeInformation。
     * 只有在创建泛型实现时才需要此构造函数，请参阅 {@link AbstractDeserializationSchema Generic Use}。
     *
     * <p>This constructor may fail if the class is generic. In that case, please use the
     * constructor that accepts a {@link #AbstractDeserializationSchema(TypeHint) TypeHint}, or a
     * {@link #AbstractDeserializationSchema(TypeInformation) TypeInformation}.
     * 如果类是泛型的，则此构造函数可能会失败。
     * 在这种情况下，请使用接受 {@link #AbstractDeserializationSchema(TypeHint) TypeHint}
     * 或 {@link #AbstractDeserializationSchema(TypeInformation) TypeInformation} 的构造函数。
     *
     * @param type The class of the produced type.
     */
    protected AbstractDeserializationSchema(Class<T> type) {
        checkNotNull(type, "type");
        this.type = TypeInformation.of(type);
    }

    /**
     * Creates an AbstractDeserializationSchema that returns the TypeInformation indicated by the
     * given type hint. This constructor is only necessary when creating a generic implementation,
     * see {@link AbstractDeserializationSchema Generic Use}.
     * 创建一个 AbstractDeserializationSchema，它返回由给定类型提示指示的 TypeInformation。
     * 只有在创建泛型实现时才需要此构造函数，请参阅 {@link AbstractDeserializationSchema Generic Use}。
     *
     * @param typeHint The TypeHint for the produced type.
     */
    protected AbstractDeserializationSchema(TypeHint<T> typeHint) {
        checkNotNull(typeHint, "typeHint");
        this.type = typeHint.getTypeInfo();
    }

    /**
     * Creates an AbstractDeserializationSchema that returns the given TypeInformation for the
     * produced type. This constructor is only necessary when creating a generic implementation, see
     * {@link AbstractDeserializationSchema Generic Use}.
     * 创建一个 AbstractDeserializationSchema，它为生成的类型返回给定的 TypeInformation。
     * 只有在创建泛型实现时才需要此构造函数，请参阅 {@link AbstractDeserializationSchema Generic Use}。
     *
     * @param typeInfo The TypeInformation for the produced type.
     */
    protected AbstractDeserializationSchema(TypeInformation<T> typeInfo) {
        this.type = checkNotNull(typeInfo, "typeInfo");
    }

    // ------------------------------------------------------------------------

    /**
     * De-serializes the byte message.
     * 反序列化字节消息。
     *
     * @param message The message, as a byte array.
     * @return The de-serialized message as an object.
     */
    @Override
    public abstract T deserialize(byte[] message) throws IOException;

    /**
     * Method to decide whether the element signals the end of the stream. If true is returned the
     * element won't be emitted.
     * 确定元素是否发出流结束信号的方法。 如果返回 true，则不会发出元素。
     *
     * <p>This default implementation returns always false, meaning the stream is interpreted to be
     * unbounded.
     * 此默认实现始终返回 false，这意味着流被解释为无界。
     *
     * @param nextElement The element to test for the end-of-stream signal.
     * @return True, if the element signals end of stream, false otherwise.
     */
    @Override
    public boolean isEndOfStream(T nextElement) {
        return false;
    }

    /**
     * Gets the type produced by this deserializer. This is the type that was passed to the
     * constructor, or reflectively inferred (if the default constructor was called).
     * 获取此反序列化程序生成的类型。 这是传递给构造函数或反射推断的类型（如果调用了默认构造函数）。
     */
    @Override
    public TypeInformation<T> getProducedType() {
        return type;
    }
}
