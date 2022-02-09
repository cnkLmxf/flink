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

package org.apache.flink.runtime.persistence;

import org.apache.flink.runtime.state.RetrievableStateHandle;
import org.apache.flink.runtime.zookeeper.ZooKeeperStateHandleStore;

import java.io.Serializable;

/**
 * State storage helper which is used by {@link ZooKeeperStateHandleStore} to persist state before
 * the state handle is written to ZooKeeper.
 * {@link ZooKeeperStateHandleStore} 使用状态存储助手在将状态句柄写入 ZooKeeper 之前保持状态。
 *
 * @param <T> The type of the data that can be stored by this storage helper.
 */
public interface RetrievableStateStorageHelper<T extends Serializable> {

    /**
     * Stores the given state and returns a state handle to it.
     * 存储给定的状态并返回它的状态句柄。
     *
     * @param state State to be stored
     * @return State handle to the stored state
     * @throws Exception
     */
    RetrievableStateHandle<T> store(T state) throws Exception;
}
