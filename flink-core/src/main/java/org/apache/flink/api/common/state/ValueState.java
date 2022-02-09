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

import java.io.IOException;

/**
 * {@link State} interface for partitioned single-value state. The value can be retrieved or
 * updated.
 * {@link State} 分区单值状态接口。 可以检索或更新该值。
 *
 * <p>The state is accessed and modified by user functions, and checkpointed consistently by the
 * system as part of the distributed snapshots.
 * 状态由用户函数访问和修改，并作为分布式快照的一部分由系统一致地检查点。
 *
 * <p>The state is only accessible by functions applied on a {@code KeyedStream}. The key is
 * automatically supplied by the system, so the function always sees the value mapped to the key of
 * the current element. That way, the system can handle stream and state partitioning consistently
 * together.
 * 该状态只能由应用在 {@code KeyedStream} 上的函数访问。
 * 键是由系统自动提供的，所以函数总是看到映射到当前元素键的值。 这样，系统可以一致地同时处理流和状态分区。
 *
 * @param <T> Type of the value in the state.
 */
@PublicEvolving
public interface ValueState<T> extends State {

    /**
     * Returns the current value for the state. When the state is not partitioned the returned value
     * is the same for all inputs in a given operator instance. If state partitioning is applied,
     * the value returned depends on the current operator input, as the operator maintains an
     * independent state for each partition.
     * 返回状态的当前值。 当状态未分区时，返回的值对于给定运算符实例中的所有输入都是相同的。
     * 如果应用了状态分区，则返回的值取决于当前的运算符输入，因为运算符为每个分区维护一个独立的状态。
     *
     * <p>If you didn't specify a default value when creating the {@link ValueStateDescriptor} this
     * will return {@code null} when to value was previously set using {@link #update(Object)}.
     * 如果您在创建 {@link ValueStateDescriptor} 时未指定默认值，
     * 则当先前使用 {@link #update(Object)} 设置值时，这将返回 {@code null}。
     *
     * @return The state value corresponding to the current input.
     * @throws IOException Thrown if the system cannot access the state.
     */
    T value() throws IOException;

    /**
     * Updates the operator state accessible by {@link #value()} to the given value. The next time
     * {@link #value()} is called (for the same state partition) the returned state will represent
     * the updated value. When a partitioned state is updated with null, the state for the current
     * key will be removed and the default value is returned on the next access.
     * 将 {@link #value()} 可访问的运算符状态更新为给定值。
     * 下次调用 {@link #value()} 时（对于相同的状态分区），返回的状态将代表更新后的值。
     * 当分区状态更新为 null 时，当前键的状态将被删除，并在下次访问时返回默认值。
     *
     * @param value The new value for the state.
     * @throws IOException Thrown if the system cannot access the state.
     */
    void update(T value) throws IOException;
}
