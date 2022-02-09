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

/**
 * Base of all snapshots that are taken by {@link StateBackend}s and some other components in tasks.
 * {@link StateBackend} 和任务中的其他一些组件拍摄的所有快照的基础。
 *
 * <p>Each snapshot is composed of a collection of {@link StateObject}s some of which may be
 * referenced by other checkpoints. The shared states will be registered at the given {@link
 * SharedStateRegistry} when the handle is received by the {@link
 * org.apache.flink.runtime.checkpoint.CheckpointCoordinator} and will be discarded when the
 * checkpoint is discarded.
 * 每个快照都由一组 {@link StateObject} 组成，其中一些可能被其他检查点引用。
 * 当 {@link org.apache.flink.runtime.checkpoint.CheckpointCoordinator} 收到句柄时，
 * 共享状态将在给定的 {@link SharedStateRegistry} 处注册，并在检查点被丢弃时被丢弃。
 *
 * <p>The {@link SharedStateRegistry} is responsible for the discarding of registered shared states.
 * Before their first registration through {@link #registerSharedStates(SharedStateRegistry)}, newly
 * created shared state is still owned by this handle and considered as private state until it is
 * registered for the first time. Registration transfers ownership to the {@link
 * SharedStateRegistry}. The composite state handle should only delete all private states in the
 * {@link StateObject#discardState()} method, the {@link SharedStateRegistry} is responsible for
 * deleting shared states after they were registered.
 * {@link SharedStateRegistry} 负责丢弃已注册的共享状态。
 * 在他们第一次通过 {@link #registerSharedStates(SharedStateRegistry)} 注册之前，
 * 新创建的共享状态仍然由这个句柄拥有并被视为私有状态，直到它第一次被注册。
 * 注册将所有权转让给 {@link SharedStateRegistry}。
 * 复合状态句柄应该只删除 {@link StateObject#discardState()} 方法中的所有私有状态，
 * {@link SharedStateRegistry} 负责删除注册后的共享状态。
 */
public interface CompositeStateHandle extends StateObject {

    /**
     * Register both newly created and already referenced shared states in the given {@link
     * SharedStateRegistry}. This method is called when the checkpoint successfully completes or is
     * recovered from failures.
     * 在给定的 {@link SharedStateRegistry} 中注册新创建的和已经引用的共享状态。 当检查点成功完成或从故障中恢复时调用此方法。
     *
     * <p>After this is completed, newly created shared state is considered as published is no
     * longer owned by this handle. This means that it should no longer be deleted as part of calls
     * to {@link #discardState()}. Instead, {@link #discardState()} will trigger an unregistration
     * from the registry.
     * 完成后，新创建的共享状态被视为已发布不再归该句柄所有。 这意味着不应再将其作为调用 {@link #discardState()} 的一部分删除。
     * 相反，{@link #discardState()} 将触发从注册表中取消注册。
     *
     * @param stateRegistry The registry where shared states are registered.
     */
    void registerSharedStates(SharedStateRegistry stateRegistry);
}
