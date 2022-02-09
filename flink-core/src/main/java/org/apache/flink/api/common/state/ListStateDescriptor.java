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
import org.apache.flink.api.common.typeutils.base.ListSerializer;
import org.apache.flink.api.java.typeutils.ListTypeInfo;

import java.util.List;

/**
 * A {@link StateDescriptor} for {@link ListState}. This can be used to create state where the type
 * is a list that can be appended and iterated over.
 * {@link ListState} 的 {@link StateDescriptor}。 这可用于创建类型是可以附加和迭代的列表的状态。
 *
 * <p>Using {@code ListState} is typically more efficient than manually maintaining a list in a
 * {@link ValueState}, because the backing implementation can support efficient appends, rather than
 * replacing the full list on write.
 * 使用 {@code ListState} 通常比手动维护 {@link ValueState} 中的列表更有效，
 * 因为支持实现可以支持有效的追加，而不是在写入时替换完整列表。
 *
 * <p>To create keyed list state (on a KeyedStream), use {@link
 * org.apache.flink.api.common.functions.RuntimeContext#getListState(ListStateDescriptor)}.
 * 要创建键控列表状态（在 KeyedStream 上），
 * 请使用 {@link org.apache.flink.api.common.functions.RuntimeContext#getListState(ListStateDescriptor)}。
 *
 * @param <T> The type of the values that can be added to the list state.
 */
@PublicEvolving
public class ListStateDescriptor<T> extends StateDescriptor<ListState<T>, List<T>> {
    private static final long serialVersionUID = 2L;

    /**
     * Creates a new {@code ListStateDescriptor} with the given name and list element type.
     * 使用给定的名称和列表元素类型创建一个新的 {@code ListStateDescriptor}。
     *
     * <p>If this constructor fails (because it is not possible to describe the type via a class),
     * consider using the {@link #ListStateDescriptor(String, TypeInformation)} constructor.
     * 如果此构造函数失败（因为无法通过类描述类型），请考虑使用 {@link #ListStateDescriptor(String, TypeInformation)} 构造函数。
     *
     * @param name The (unique) name for the state.
     * @param elementTypeClass The type of the elements in the state.
     */
    public ListStateDescriptor(String name, Class<T> elementTypeClass) {
        super(name, new ListTypeInfo<>(elementTypeClass), null);
    }

    /**
     * Creates a new {@code ListStateDescriptor} with the given name and list element type.
     * 使用给定的名称和列表元素类型创建一个新的 {@code ListStateDescriptor}。
     *
     * @param name The (unique) name for the state.
     * @param elementTypeInfo The type of the elements in the state.
     */
    public ListStateDescriptor(String name, TypeInformation<T> elementTypeInfo) {
        super(name, new ListTypeInfo<>(elementTypeInfo), null);
    }

    /**
     * Creates a new {@code ListStateDescriptor} with the given name and list element type.
     * 使用给定的名称和列表元素类型创建一个新的 {@code ListStateDescriptor}。
     *
     * @param name The (unique) name for the state.
     * @param typeSerializer The type serializer for the list values.
     */
    public ListStateDescriptor(String name, TypeSerializer<T> typeSerializer) {
        super(name, new ListSerializer<>(typeSerializer), null);
    }

    /**
     * Gets the serializer for the elements contained in the list.
     * 获取列表中包含的元素的序列化程序。
     *
     * @return The serializer for the elements in the list.
     */
    public TypeSerializer<T> getElementSerializer() {
        // call getSerializer() here to get the initialization check and proper error message
        final TypeSerializer<List<T>> rawSerializer = getSerializer();
        if (!(rawSerializer instanceof ListSerializer)) {
            throw new IllegalStateException();
        }

        return ((ListSerializer<T>) rawSerializer).getElementSerializer();
    }

    @Override
    public Type getType() {
        return Type.LIST;
    }
}
