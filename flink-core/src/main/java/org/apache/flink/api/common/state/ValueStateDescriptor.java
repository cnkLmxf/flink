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

/**
 * {@link StateDescriptor} for {@link ValueState}. This can be used to create partitioned value
 * state using {@link
 * org.apache.flink.api.common.functions.RuntimeContext#getState(ValueStateDescriptor)}.
 * {@link ValueState} 的 {@link StateDescriptor}。
 * 这可用于使用 {@link org.apache.flink.api.common.functions.RuntimeContext#getState(ValueStateDescriptor)} 创建分区值状态。
 *
 * <p>If you don't use one of the constructors that set a default value the value that you get when
 * reading a {@link ValueState} using {@link ValueState#value()} will be {@code null}.
 * 如果您不使用设置默认值的构造函数之一，
 * 则使用 {@link ValueState#value()} 读取 {@link ValueState} 时获得的值将为 {@code null}。
 *
 * @param <T> The type of the values that the value state can hold.
 */
@PublicEvolving
public class ValueStateDescriptor<T> extends StateDescriptor<ValueState<T>, T> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@code ValueStateDescriptor} with the given name, type, and default value.
     * 使用给定的名称、类型和默认值创建一个新的 {@code ValueStateDescriptor}。
     *
     * <p>If this constructor fails (because it is not possible to describe the type via a class),
     * consider using the {@link #ValueStateDescriptor(String, TypeInformation, Object)}
     * constructor.
     * 如果此构造函数失败（因为无法通过类来描述类型），
     * 请考虑使用 {@link #ValueStateDescriptor(String, TypeInformation, Object)} 构造函数。
     *
     * @deprecated Use {@link #ValueStateDescriptor(String, Class)} instead and manually manage the
     *     default value by checking whether the contents of the state is {@code null}.
     * @param name The (unique) name for the state.
     * @param typeClass The type of the values in the state.
     * @param defaultValue The default value that will be set when requesting state without setting
     *     a value before.
     */
    @Deprecated
    public ValueStateDescriptor(String name, Class<T> typeClass, T defaultValue) {
        super(name, typeClass, defaultValue);
    }

    /**
     * Creates a new {@code ValueStateDescriptor} with the given name and default value.
     * 使用给定的名称和默认值创建一个新的 {@code ValueStateDescriptor}。
     *
     * @deprecated Use {@link #ValueStateDescriptor(String, TypeInformation)} instead and manually
     *     manage the default value by checking whether the contents of the state is {@code null}.
     * @param name The (unique) name for the state.
     * @param typeInfo The type of the values in the state.
     * @param defaultValue The default value that will be set when requesting state without setting
     *     a value before.
     */
    @Deprecated
    public ValueStateDescriptor(String name, TypeInformation<T> typeInfo, T defaultValue) {
        super(name, typeInfo, defaultValue);
    }

    /**
     * Creates a new {@code ValueStateDescriptor} with the given name, default value, and the
     * specific serializer.
     * 使用给定的名称、默认值和特定的序列化程序创建一个新的 {@code ValueStateDescriptor}。
     *
     * @deprecated Use {@link #ValueStateDescriptor(String, TypeSerializer)} instead and manually
     *     manage the default value by checking whether the contents of the state is {@code null}.
     * @param name The (unique) name for the state.
     * @param typeSerializer The type serializer of the values in the state.
     * @param defaultValue The default value that will be set when requesting state without setting
     *     a value before.
     */
    @Deprecated
    public ValueStateDescriptor(String name, TypeSerializer<T> typeSerializer, T defaultValue) {
        super(name, typeSerializer, defaultValue);
    }

    /**
     * Creates a new {@code ValueStateDescriptor} with the given name and type
     * 使用给定的名称、默认值和特定的序列化程序创建一个新的 {@code ValueStateDescriptor}。
     *
     * <p>If this constructor fails (because it is not possible to describe the type via a class),
     * consider using the {@link #ValueStateDescriptor(String, TypeInformation)} constructor.
     *
     * @param name The (unique) name for the state.
     * @param typeClass The type of the values in the state.
     */
    public ValueStateDescriptor(String name, Class<T> typeClass) {
        super(name, typeClass, null);
    }

    /**
     * Creates a new {@code ValueStateDescriptor} with the given name and type.
     *
     * @param name The (unique) name for the state.
     * @param typeInfo The type of the values in the state.
     */
    public ValueStateDescriptor(String name, TypeInformation<T> typeInfo) {
        super(name, typeInfo, null);
    }

    /**
     * Creates a new {@code ValueStateDescriptor} with the given name and the specific serializer.
     *
     * @param name The (unique) name for the state.
     * @param typeSerializer The type serializer of the values in the state.
     */
    public ValueStateDescriptor(String name, TypeSerializer<T> typeSerializer) {
        super(name, typeSerializer, null);
    }

    @Override
    public Type getType() {
        return Type.VALUE;
    }
}
