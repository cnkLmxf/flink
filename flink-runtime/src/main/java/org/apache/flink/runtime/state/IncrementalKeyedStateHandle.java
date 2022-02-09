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

import javax.annotation.Nonnull;

import java.util.Set;
import java.util.UUID;

/** Common interface to all incremental {@link KeyedStateHandle}.
 * 所有增量 {@link KeyedStateHandle} 的通用接口。
 * */
public interface IncrementalKeyedStateHandle extends KeyedStateHandle {

    /** Returns the ID of the checkpoint for which the handle was created.
     * 返回为其创建句柄的检查点的 ID。
     * */
    long getCheckpointId();

    /** Returns the identifier of the state backend from which this handle was created.
     * 返回创建此句柄的状态后端的标识符。
     * */
    @Nonnull
    UUID getBackendIdentifier();

    /**
     * Returns a set of ids of all registered shared states in the backend at the time this was
     * created.
     * 返回创建时后端中所有已注册共享状态的一组 id。
     */
    @Nonnull
    Set<StateHandleID> getSharedStateHandleIDs();
}
