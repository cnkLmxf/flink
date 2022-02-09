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
 * This interface implements the durable storage of checkpoint data and metadata streams. An
 * individual checkpoint or savepoint is stored to a {@link CheckpointStorageLocation} which created
 * by {@link CheckpointStorageCoordinatorView}.
 * 该接口实现了检查点数据和元数据流的持久存储。
 * 单个检查点或保存点存储到由 {@link CheckpointStorageCoordinatorView} 创建的 {@link CheckpointStorageLocation}。
 *
 * <p>Methods of this interface act as a worker role in task manager.
 * 此接口的方法在任务管理器中充当辅助角色。
 */
public interface CheckpointStorageWorkerView {

    /**
     * Resolves a storage location reference into a CheckpointStreamFactory.
     * 将存储位置引用解析为 CheckpointStreamFactory。
     *
     * <p>The reference may be the {@link CheckpointStorageLocationReference#isDefaultReference()
     * default reference}, in which case the method should return the default location, taking
     * existing configuration and checkpoint ID into account.
     * 该引用可能是 {@link CheckpointStorageLocationReference#isDefaultReference() 默认引用}，
     * 在这种情况下，该方法应返回默认位置，同时考虑现有配置和检查点 ID。
     *
     * @param checkpointId The ID of the checkpoint that the location is initialized for.
     * @param reference The checkpoint location reference.
     * @return A checkpoint storage location reflecting the reference and checkpoint ID.
     * @throws IOException Thrown, if the storage location cannot be initialized from the reference.
     */
    CheckpointStreamFactory resolveCheckpointStorageLocation(
            long checkpointId, CheckpointStorageLocationReference reference) throws IOException;

    /**
     * Opens a stream to persist checkpoint state data that is owned strictly by tasks and not
     * attached to the life cycle of a specific checkpoint.
     * 打开一个流以保留严格由任务拥有且不附加到特定检查点的生命周期的检查点状态数据。
     *
     * <p>This method should be used when the persisted data cannot be immediately dropped once the
     * checkpoint that created it is dropped. Examples are write-ahead-logs. For those, the state
     * can only be dropped once the data has been moved to the target system, which may sometimes
     * take longer than one checkpoint (if the target system is temporarily unable to keep up).
     * 当创建它的检查点被删除后无法立即删除持久化数据时，应使用此方法。 示例是预写日志。
     * 对于那些，只有在将数据移动到目标系统后才能删除状态，这有时可能需要比一个检查点更长的时间（如果目标系统暂时无法跟上）。
     *
     * <p>The fact that the job manager does not own the life cycle of this type of state means also
     * that it is strictly the responsibility of the tasks to handle the cleanup of this data.
     * 作业管理器不拥有此类状态的生命周期这一事实也意味着，处理这些数据的清理工作严格由任务负责。
     *
     * <p>Developer note: In the future, we may be able to make this a special case of "shared
     * state", where the task re-emits the shared state reference as long as it needs to hold onto
     * the persisted state data.
     * 开发者说明：在未来，我们或许可以将其作为“共享状态”的一个特例，只要任务需要保留持久化的状态数据，它就会重新发出共享状态引用。
     *
     * @return A checkpoint state stream to the location for state owned by tasks.
     * @throws IOException Thrown, if the stream cannot be opened.
     */
    CheckpointStreamFactory.CheckpointStateOutputStream createTaskOwnedStateStream()
            throws IOException;
}
