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

import java.util.Iterator;
import java.util.Map;

/**
 * {@link State} interface for partitioned key-value state. The key-value pair can be added, updated
 * and retrieved.
 * {@link State} 用于分区键值状态的接口。 可以添加、更新和检索键值对。
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
 * @param <UK> Type of the keys in the state.
 * @param <UV> Type of the values in the state.
 */
@PublicEvolving
public interface MapState<UK, UV> extends State {

    /**
     * Returns the current value associated with the given key.
     * 返回与给定键关联的当前值。
     *
     * @param key The key of the mapping
     * @return The value of the mapping with the given key
     * @throws Exception Thrown if the system cannot access the state.
     */
    UV get(UK key) throws Exception;

    /**
     * Associates a new value with the given key.
     * 将新值与给定键关联。
     *
     * @param key The key of the mapping
     * @param value The new value of the mapping
     * @throws Exception Thrown if the system cannot access the state.
     */
    void put(UK key, UV value) throws Exception;

    /**
     * Copies all of the mappings from the given map into the state.
     * 将给定map中的所有映射复制到状态中。
     *
     * @param map The mappings to be stored in this state
     * @throws Exception Thrown if the system cannot access the state.
     */
    void putAll(Map<UK, UV> map) throws Exception;

    /**
     * Deletes the mapping of the given key.
     * 删除给定键的映射。
     *
     * @param key The key of the mapping
     * @throws Exception Thrown if the system cannot access the state.
     */
    void remove(UK key) throws Exception;

    /**
     * Returns whether there exists the given mapping.
     * 返回是否存在给定的映射。
     *
     * @param key The key of the mapping
     * @return True if there exists a mapping whose key equals to the given key
     * @throws Exception Thrown if the system cannot access the state.
     */
    boolean contains(UK key) throws Exception;

    /**
     * Returns all the mappings in the state.
     * 返回状态中的所有映射。
     *
     * @return An iterable view of all the key-value pairs in the state.
     * @throws Exception Thrown if the system cannot access the state.
     */
    Iterable<Map.Entry<UK, UV>> entries() throws Exception;

    /**
     * Returns all the keys in the state.
     * 返回状态中的所有键。
     *
     * @return An iterable view of all the keys in the state.
     * @throws Exception Thrown if the system cannot access the state.
     */
    Iterable<UK> keys() throws Exception;

    /**
     * Returns all the values in the state.
     * 返回状态中的所有值。
     *
     * @return An iterable view of all the values in the state.
     * @throws Exception Thrown if the system cannot access the state.
     */
    Iterable<UV> values() throws Exception;

    /**
     * Iterates over all the mappings in the state.
     * 遍历状态中的所有映射。
     *
     * @return An iterator over all the mappings in the state
     * @throws Exception Thrown if the system cannot access the state.
     */
    Iterator<Map.Entry<UK, UV>> iterator() throws Exception;

    /**
     * Returns true if this state contains no key-value mappings, otherwise false.
     * 如果此状态不包含键值映射，则返回 true，否则返回 false。
     *
     * @return True if this state contains no key-value mappings, otherwise false.
     * @throws Exception Thrown if the system cannot access the state.
     */
    boolean isEmpty() throws Exception;
}
