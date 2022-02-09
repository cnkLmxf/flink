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
import org.apache.flink.runtime.checkpoint.TaskStateSnapshot;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.function.LongPredicate;

/**
 * Classes that implement this interface serve as a task-manager-level local storage for local
 * checkpointed state. The purpose is to provide access to a state that is stored locally for a
 * faster recovery compared to the state that is stored remotely in a stable store DFS. For now,
 * this storage is only complementary to the stable storage and local state is typically lost in
 * case of machine failures. In such cases (and others), client code of this class must fall back to
 * using the slower but highly available store.
 * 实现此接口的类用作本地检查点状态的任务管理器级本地存储。
 * 目的是提供对本地存储状态的访问，以便与远程存储在稳定存储 DFS 中的状态相比更快地恢复。
 * 目前，这种存储只是对稳定存储的补充，并且在机器故障的情况下通常会丢失本地状态。
 * 在这种情况下（和其他情况），此类的客户端代码必须回退到使用速度较慢但高度可用的存储。
 */
@Internal
public interface TaskLocalStateStore {
    /**
     * Stores the local state for the given checkpoint id.
     * 存储给定检查点 ID 的本地状态。
     *
     * @param checkpointId id for the checkpoint that created the local state that will be stored.
     * @param localState the local state to store.
     */
    void storeLocalState(@Nonnegative long checkpointId, @Nullable TaskStateSnapshot localState);

    /**
     * Returns the local state that is stored under the given checkpoint id or null if nothing was
     * stored under the id.
     * 返回存储在给定检查点 id 下的本地状态，如果该 id 下没有存储任何内容，则返回 null。
     *
     * @param checkpointID the checkpoint id by which we search for local state.
     * @return the local state found for the given checkpoint id. Can be null
     */
    @Nullable
    TaskStateSnapshot retrieveLocalState(long checkpointID);

    /** Returns the {@link LocalRecoveryConfig} for this task local state store.
     * 返回此任务本地状态存储的 {@link LocalRecoveryConfig}。
     * */
    @Nonnull
    LocalRecoveryConfig getLocalRecoveryConfig();

    /**
     * Notifies that the checkpoint with the given id was confirmed as complete. This prunes the
     * checkpoint history and removes all local states with a checkpoint id that is smaller than the
     * newly confirmed checkpoint id.
     * 通知具有给定 id 的检查点已确认完成。 这会修剪检查点历史记录并删除检查点 ID 小于新确认的检查点 ID 的所有本地状态。
     */
    void confirmCheckpoint(long confirmedCheckpointId);

    /**
     * Notifies that the checkpoint with the given id was confirmed as aborted. This prunes the
     * checkpoint history and removes states with a checkpoint id that is equal to the newly aborted
     * checkpoint id.
     * 通知具有给定 id 的检查点已确认已中止。 这会修剪检查点历史记录并删除检查点 ID 等于新中止的检查点 ID 的状态。
     */
    void abortCheckpoint(long abortedCheckpointId);

    /**
     * Remove all checkpoints from the store that match the given predicate.
     * 从store中删除与给定谓词匹配的所有检查点。
     *
     * @param matcher the predicate that selects the checkpoints for pruning.
     */
    void pruneMatchingCheckpoints(LongPredicate matcher);
}
