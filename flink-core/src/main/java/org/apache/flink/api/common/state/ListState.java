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

import java.util.List;

/**
 * {@link State} interface for partitioned list state in Operations. The state is accessed and
 * modified by user functions, and checkpointed consistently by the system as part of the
 * distributed snapshots.
 * 操作中分区列表状态的 {@link State} 接口。 状态由用户函数访问和修改，并作为分布式快照的一部分由系统一致地检查点。
 *
 * <p>The state can be a keyed list state or an operator list state.
 * 状态可以是键控列表状态或操作员列表状态。
 *
 * <p>When it is a keyed list state, it is accessed by functions applied on a {@code KeyedStream}.
 * The key is automatically supplied by the system, so the function always sees the value mapped to
 * the key of the current element. That way, the system can handle stream and state partitioning
 * consistently together.
 * 当它是一个键控列表状态时，它由应用在 {@code KeyedStream} 上的函数访问。
 * 键是由系统自动提供的，所以函数总是看到映射到当前元素键的值。 这样，系统可以一致地同时处理流和状态分区。
 *
 * <p>When it is an operator list state, the list is a collection of state items that are
 * independent from each other and eligible for redistribution across operator instances in case of
 * changed operator parallelism.
 * 当它是一个算子列表状态时，该列表是一个状态项的集合，这些状态项彼此独立并且有资格在算子并行度发生变化的情况下跨算子实例重新分配。
 *
 * @param <T> Type of values that this list state keeps.
 */
@PublicEvolving
public interface ListState<T> extends MergingState<T, Iterable<T>> {

    /**
     * Updates the operator state accessible by {@link #get()} by updating existing values to to the
     * given list of values. The next time {@link #get()} is called (for the same state partition)
     * the returned state will represent the updated list.
     * 通过将现有值更新到给定的值列表来更新 {@link #get()} 可访问的运算符状态。
     * 下次调用 {@link #get()} 时（对于相同的状态分区），返回的状态将代表更新后的列表。
     *
     * <p>If null or an empty list is passed in, the state value will be null.
     * 如果传入 null 或空列表，则状态值将为 null。
     *
     * @param values The new values for the state.
     * @throws Exception The method may forward exception thrown internally (by I/O or functions).
     */
    void update(List<T> values) throws Exception;

    /**
     * Updates the operator state accessible by {@link #get()} by adding the given values to
     * existing list of values. The next time {@link #get()} is called (for the same state
     * partition) the returned state will represent the updated list.
     * 通过将给定值添加到现有值列表来更新 {@link #get()} 可访问的运算符状态。
     * 下次调用 {@link #get()} 时（对于相同的状态分区），返回的状态将代表更新后的列表。
     *
     * <p>If null or an empty list is passed in, the state value remains unchanged.
     * 如果传入 null 或空列表，则状态值保持不变。
     *
     * @param values The new values to be added to the state.
     * @throws Exception The method may forward exception thrown internally (by I/O or functions).
     */
    void addAll(List<T> values) throws Exception;
}
