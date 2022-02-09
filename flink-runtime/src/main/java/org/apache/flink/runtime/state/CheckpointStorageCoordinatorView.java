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

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * This interface creates a {@link CheckpointStorageLocation} to which an individual checkpoint or
 * savepoint is stored.
 * 此接口创建一个 {@link CheckpointStorageLocation}，其中存储了单个检查点或保存点。
 *
 * <p>Methods of this interface act as an administration role in checkpoint coordinator.
 * 此接口的方法在检查点协调器中充当管理角色。
 */
public interface CheckpointStorageCoordinatorView {

    /**
     * Checks whether this backend supports highly available storage of data.
     * 检查此后端是否支持数据的高可用存储。
     *
     * <p>Some state backends may not support highly-available durable storage, with default
     * settings, which makes them suitable for zero-config prototyping, but not for actual
     * production setups.
     * 某些状态后端可能不支持具有默认设置的高可用持久存储，这使得它们适用于零配置原型设计，但不适用于实际生产设置。
     */
    boolean supportsHighlyAvailableStorage();

    /** Checks whether the storage has a default savepoint location configured.
     * 检查存储是否配置了默认保存点位置。
     * */
    boolean hasDefaultSavepointLocation();

    /**
     * Resolves the given pointer to a checkpoint/savepoint into a checkpoint location. The location
     * supports reading the checkpoint metadata, or disposing the checkpoint storage location.
     * 将给定的指向检查点/保存点的指针解析为检查点位置。 该位置支持读取检查点元数据，或处置检查点存储位置。
     *
     * <p>If the state backend cannot understand the format of the pointer (for example because it
     * was created by a different state backend) this method should throw an {@code IOException}.
     * 如果状态后端无法理解指针的格式（例如，因为它是由不同的状态后端创建的），则此方法应抛出 {@code IOException}。
     *
     * @param externalPointer The external checkpoint pointer to resolve.
     * @return The checkpoint location handle.
     * @throws IOException Thrown, if the state backend does not understand the pointer, or if the
     *     pointer could not be resolved due to an I/O error.
     */
    CompletedCheckpointStorageLocation resolveCheckpoint(String externalPointer) throws IOException;

    /**
     * Initializes the necessary prerequisites for storage locations of checkpoints/savepoints.
     * 为检查点/保存点的存储位置初始化必要的先决条件。
     *
     * <p>For file-based checkpoint storage, this method would initialize essential base checkpoint
     * directories on checkpoint coordinator side and should be executed before calling {@link
     * #initializeLocationForCheckpoint(long)} and {@link #initializeLocationForSavepoint(long,
     * String)}.
     * 对于基于文件的检查点存储，此方法将初始化检查点协调器端的基本检查点目录，
     * 并且应在调用 {@link #initializeLocationForCheckpoint(long)} 和 {@link #initializeLocationForSavepoint(long, String)} 之前执行。
     *
     * @throws IOException Thrown, if these base storage locations cannot be initialized due to an
     *     I/O exception.
     */
    void initializeBaseLocations() throws IOException;

    /**
     * Initializes a storage location for new checkpoint with the given ID.
     * 使用给定的 ID 初始化新检查点的存储位置。
     *
     * <p>The returned storage location can be used to write the checkpoint data and metadata to and
     * to obtain the pointers for the location(s) where the actual checkpoint data should be stored.
     * 返回的存储位置可用于将检查点数据和元数据写入到应存储实际检查点数据的位置，并获取指向该位置的指针。
     *
     * @param checkpointId The ID (logical timestamp) of the checkpoint that should be persisted.
     * @return A storage location for the data and metadata of the given checkpoint.
     * @throws IOException Thrown if the storage location cannot be initialized due to an I/O
     *     exception.
     */
    CheckpointStorageLocation initializeLocationForCheckpoint(long checkpointId) throws IOException;

    /**
     * Initializes a storage location for new savepoint with the given ID.
     * 使用给定的 ID 初始化新保存点的存储位置。
     *
     * <p>If an external location pointer is passed, the savepoint storage location will be
     * initialized at the location of that pointer. If the external location pointer is null, the
     * default savepoint location will be used. If no default savepoint location is configured, this
     * will throw an exception. Whether a default savepoint location is configured can be checked
     * via {@link #hasDefaultSavepointLocation()}.
     * 如果传递了外部位置指针，则保存点存储位置将在该指针的位置进行初始化。 如果外部位置指针为空，则将使用默认保存点位置。
     * 如果没有配置默认保存点位置，这将引发异常。 可以通过 {@link #hasDefaultSavepointLocation()} 检查是否配置了默认保存点位置。
     *
     * @param checkpointId The ID (logical timestamp) of the savepoint's checkpoint.
     * @param externalLocationPointer Optionally, a pointer to the location where the savepoint
     *     should be stored. May be null.
     * @return A storage location for the data and metadata of the savepoint.
     * @throws IOException Thrown if the storage location cannot be initialized due to an I/O
     *     exception.
     */
    CheckpointStorageLocation initializeLocationForSavepoint(
            long checkpointId, @Nullable String externalLocationPointer) throws IOException;
}
