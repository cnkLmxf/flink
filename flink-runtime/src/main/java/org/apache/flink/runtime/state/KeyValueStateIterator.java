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

package org.apache.flink.runtime.state;

import java.io.IOException;

/**
 * Iterator that over all key-value state entries in a {@link KeyedStateBackend}. For use during
 * snapshotting.
 * {@link KeyedStateBackend} 中所有键值状态条目的迭代器。 用于快照期间。
 *
 * <p>This is required to partition all states into contiguous key-groups. The resulting iteration
 * sequence is ordered by (key-group, kv-state).
 * 这是将所有状态划分为连续的key group所必需的。 生成的迭代序列按 (key-group, kv-state) 排序。
 */
public interface KeyValueStateIterator extends AutoCloseable {

    /**
     * Advances the iterator. Should only be called if {@link #isValid()} returned true. Valid flag
     * can only change after calling {@link #next()}.
     * 推进迭代器。 仅当 {@link #isValid()} 返回 true 时才应调用。 有效标志只能在调用 {@link #next()} 后更改。
     */
    void next() throws IOException;

    /** Returns the key-group for the current key. */
    int keyGroup();

    byte[] key();

    byte[] value();

    /** Returns the Id of the K/V state to which the current key belongs.
     * 返回当前key所属的K/V状态的Id。
     * */
    int kvStateId();

    /**
     * Indicates if current key starts a new k/v-state, i.e. belong to a different k/v-state than
     * it's predecessor.
     * 指示当前密钥是否开始一个新的 k/v-state，即属于与其前身不同的 k/v-state。
     *
     * @return true iff the current key belong to a different k/v-state than it's predecessor.
     */
    boolean isNewKeyValueState();

    /**
     * Indicates if current key starts a new key-group, i.e. belong to a different key-group than
     * it's predecessor.
     * 指示当前密钥是否启动一个新的key-group，即属于与其前身不同的密钥组。
     *
     * @return true iff the current key belong to a different key-group than it's predecessor.
     */
    boolean isNewKeyGroup();

    /**
     * Check if the iterator is still valid. Getters like {@link #key()}, {@link #value()}, etc. as
     * well as {@link #next()} should only be called if valid returned true. Should be checked after
     * each call to {@link #next()} before accessing iterator state.
     * 检查迭代器是否仍然有效。 像 {@link #key()}、{@link #value()} 等以及 {@link #next()}
     * 这样的 getter 应该只在有效返回 true 时才被调用。
     * 应在每次调用 {@link #next()} 后检查，然后再访问迭代器状态。
     *
     * @return True iff this iterator is valid.
     */
    boolean isValid();

    @Override
    void close();
}
