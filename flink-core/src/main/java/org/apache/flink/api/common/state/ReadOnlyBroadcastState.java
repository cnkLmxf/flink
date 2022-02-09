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

import java.util.Map;

/**
 * A read-only view of the {@link BroadcastState}.
 * {@link BroadcastState} 的只读视图。
 *
 * <p>Although read-only, the user code should not modify the value returned by the {@link
 * #get(Object)} or the entries of the immutable iterator returned by the {@link
 * #immutableEntries()}, as this can lead to inconsistent states. The reason for this is that we do
 * not create extra copies of the elements for performance reasons.
 * 虽然是只读的，但用户代码不应修改 {@link #get(Object)} 返回的值或
 * {@link #immutableEntries()} 返回的不可变迭代器的条目，因为这可能导致不一致 状态。
 * 这样做的原因是出于性能原因我们不会创建元素的额外副本。
 *
 * @param <K> The key type of the elements in the {@link ReadOnlyBroadcastState}.
 * @param <V> The value type of the elements in the {@link ReadOnlyBroadcastState}.
 */
@PublicEvolving
public interface ReadOnlyBroadcastState<K, V> extends State {

    /**
     * Returns the current value associated with the given key.
     * 返回与给定键关联的当前值。
     *
     * <p>The user code must not modify the value returned, as this can lead to inconsistent states.
     * 用户代码不得修改返回的值，因为这可能导致状态不一致。
     *
     * @param key The key of the mapping
     * @return The value of the mapping with the given key
     * @throws Exception Thrown if the system cannot access the state.
     */
    V get(K key) throws Exception;

    /**
     * Returns whether there exists the given mapping.
     * 返回是否存在给定的映射。
     *
     * @param key The key of the mapping
     * @return True if there exists a mapping whose key equals to the given key
     * @throws Exception Thrown if the system cannot access the state.
     */
    boolean contains(K key) throws Exception;

    /**
     * Returns an immutable {@link Iterable} over the entries in the state.
     * 在状态中的条目上返回一个不可变的 {@link Iterable}。
     *
     * <p>The user code must not modify the entries of the returned immutable iterator, as this can
     * lead to inconsistent states.
     * 用户代码不得修改返回的不可变迭代器的条目，因为这可能导致状态不一致。
     */
    Iterable<Map.Entry<K, V>> immutableEntries() throws Exception;
}
