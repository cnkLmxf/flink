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

import org.apache.flink.annotation.Internal;

import javax.annotation.Nonnull;

/** Interface to deal with state snapshot and restore of state. TODO find better name?
 * 处理状态快照和状态恢复的接口。 TODO 找到更好的名字了吗？
 * */
@Internal
public interface StateSnapshotRestore {

    /** Returns a snapshot of the state.
     * 返回状态的快照。
     * */
    @Nonnull
    StateSnapshot stateSnapshot();

    /**
     * This method returns a {@link StateSnapshotKeyGroupReader} that can be used to restore the
     * state on a per-key-group basis. This method tries to return a reader for the given version
     * hint.
     * 此方法返回一个 {@link StateSnapshotKeyGroupReader}，可用于在每个密钥组的基础上恢复状态。
     * 此方法尝试返回给定版本提示的阅读器。
     *
     * @param readVersionHint the required version of the state to read.
     * @return a reader that reads state by key-groups, for the given read version.
     */
    @Nonnull
    StateSnapshotKeyGroupReader keyGroupReader(int readVersionHint);
}
