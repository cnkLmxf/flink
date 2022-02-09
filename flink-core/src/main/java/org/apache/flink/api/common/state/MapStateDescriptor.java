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

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.base.MapSerializer;
import org.apache.flink.api.java.typeutils.MapTypeInfo;

import java.util.Map;

/**
 * A {@link StateDescriptor} for {@link MapState}. This can be used to create state where the type
 * is a map that can be updated and iterated over.
 * {@link MapState} 的 {@link StateDescriptor}。 这可用于创建类型是可以更新和迭代的映射的状态。
 *
 * <p>Using {@code MapState} is typically more efficient than manually maintaining a map in a {@link
 * ValueState}, because the backing implementation can support efficient updates, rather then
 * replacing the full map on write.
 * 使用 {@code MapState} 通常比在 {@link ValueState} 中手动维护映射更有效，
 * 因为支持实现可以支持有效的更新，而不是在写入时替换完整的映射。
 *
 * <p>To create keyed map state (on a KeyedStream), use {@link
 * org.apache.flink.api.common.functions.RuntimeContext#getMapState(MapStateDescriptor)}.
 * 要创建键控映射状态（在 KeyedStream 上），
 * 请使用 {@link org.apache.flink.api.common.functions.RuntimeContext#getMapState(MapStateDescriptor)}。
 *
 * <p>Note: The map state with TTL currently supports {@code null} user values only if the user
 * value serializer can handle {@code null} values. If the serializer does not support {@code null}
 * values, it can be wrapped with {@link
 * org.apache.flink.api.java.typeutils.runtime.NullableSerializer} at the cost of an extra byte in
 * the serialized form.
 * 注意：只有当用户值序列化程序可以处理 {@code null} 值时，具有 TTL 的映射状态当前才支持 {@code null} 用户值。
 * 如果序列化程序不支持 {@code null} 值，则可以用 {@link org.apache.flink.api.java.typeutils.runtime.NullableSerializer} 包装它，
 * 但需要在序列化形式中增加一个字节。
 *
 * @param <UK> The type of the keys that can be added to the map state.
 */
@PublicEvolving
public class MapStateDescriptor<UK, UV> extends StateDescriptor<MapState<UK, UV>, Map<UK, UV>> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new {@code MapStateDescriptor} with the given name and the given type serializers.
     * 使用给定的名称和给定的类型序列化程序创建一个新的 {@code MapStateDescriptor}。
     *
     * @param name The name of the {@code MapStateDescriptor}.
     * @param keySerializer The type serializer for the keys in the state.
     * @param valueSerializer The type serializer for the values in the state.
     */
    public MapStateDescriptor(
            String name, TypeSerializer<UK> keySerializer, TypeSerializer<UV> valueSerializer) {
        super(name, new MapSerializer<>(keySerializer, valueSerializer), null);
    }

    /**
     * Create a new {@code MapStateDescriptor} with the given name and the given type information.
     * 使用给定的名称和给定的类型信息创建一个新的 {@code MapStateDescriptor}。
     *
     * @param name The name of the {@code MapStateDescriptor}.
     * @param keyTypeInfo The type information for the keys in the state.
     * @param valueTypeInfo The type information for the values in the state.
     */
    public MapStateDescriptor(
            String name, TypeInformation<UK> keyTypeInfo, TypeInformation<UV> valueTypeInfo) {
        super(name, new MapTypeInfo<>(keyTypeInfo, valueTypeInfo), null);
    }

    /**
     * Create a new {@code MapStateDescriptor} with the given name and the given type information.
     * 使用给定的名称和给定的类型信息创建一个新的 {@code MapStateDescriptor}。
     *
     * <p>If this constructor fails (because it is not possible to describe the type via a class),
     * consider using the {@link #MapStateDescriptor(String, TypeInformation, TypeInformation)}
     * constructor.
     * 如果此构造函数失败（因为无法通过类描述类型），
     * 请考虑使用 {@link #MapStateDescriptor(String, TypeInformation, TypeInformation)} 构造函数。
     *
     * @param name The name of the {@code MapStateDescriptor}.
     * @param keyClass The class of the type of keys in the state.
     * @param valueClass The class of the type of values in the state.
     */
    public MapStateDescriptor(String name, Class<UK> keyClass, Class<UV> valueClass) {
        super(name, new MapTypeInfo<>(keyClass, valueClass), null);
    }

    @Override
    public Type getType() {
        return Type.MAP;
    }

    /**
     * Gets the serializer for the keys in the state.
     * 获取状态中键的序列化程序。
     *
     * @return The serializer for the keys in the state.
     */
    public TypeSerializer<UK> getKeySerializer() {
        final TypeSerializer<Map<UK, UV>> rawSerializer = getSerializer();
        if (!(rawSerializer instanceof MapSerializer)) {
            throw new IllegalStateException("Unexpected serializer type.");
        }

        return ((MapSerializer<UK, UV>) rawSerializer).getKeySerializer();
    }

    /**
     * Gets the serializer for the values in the state.
     * 获取状态中值的序列化程序。
     *
     * @return The serializer for the values in the state.
     */
    public TypeSerializer<UV> getValueSerializer() {
        final TypeSerializer<Map<UK, UV>> rawSerializer = getSerializer();
        if (!(rawSerializer instanceof MapSerializer)) {
            throw new IllegalStateException("Unexpected serializer type.");
        }

        return ((MapSerializer<UK, UV>) rawSerializer).getValueSerializer();
    }
}
