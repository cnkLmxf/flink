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
import org.apache.flink.api.common.functions.AggregateFunction;

/**
 * {@link State} interface for aggregating state, based on an {@link AggregateFunction}. Elements
 * that are added to this type of state will be eagerly pre-aggregated using a given {@code
 * AggregateFunction}.
 * {@link State} 接口用于聚合状态，基于 {@link AggregateFunction}。
 * 添加到此类状态的元素将使用给定的 {@code AggregateFunction} 预先聚合。
 *
 * <p>The state holds internally always the accumulator type of the {@code AggregateFunction}. When
 * accessing the result of the state, the function's {@link AggregateFunction#getResult(Object)}
 * method.
 * 该状态始终在内部保存 {@code AggregateFunction} 的累加器类型。
 * 访问状态结果时，函数的 {@link AggregateFunction#getResult(Object)} 方法。
 *
 * <p>The state is accessed and modified by user functions, and checkpointed consistently by the
 * system as part of the distributed snapshots.
 * 状态由用户函数访问和修改，并作为分布式快照的一部分由系统一致地检查点。
 *
 * <p>The state is only accessible by functions applied on a {@code KeyedStream}. The key is
 * automatically supplied by the system, so the function always sees the value mapped to the key of
 * the current element. That way, the system can handle stream and state partitioning consistently
 * together.
 * 该状态只能由应用在 {@code KeyedStream} 上的函数访问。 键是由系统自动提供的，所以函数总是看到映射到当前元素键的值。
 * 这样，系统可以一致地同时处理流和状态分区。
 *
 * @param <IN> Type of the value added to the state.
 * @param <OUT> Type of the value extracted from the state.
 */
@PublicEvolving
public interface AggregatingState<IN, OUT> extends MergingState<IN, OUT> {}
