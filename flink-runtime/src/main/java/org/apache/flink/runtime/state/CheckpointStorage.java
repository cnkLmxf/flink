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

package org.apache.flink.runtime.state;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.JobID;

import java.io.IOException;

/**
 * CheckpointStorage defines how {@link StateBackend}'s store their state for fault tolerance in
 * streaming applications. Various implementations store their checkpoints in different fashions and
 * have different requirements and availability guarantees.
 * CheckpointStorage 定义了 {@link StateBackend} 如何存储其状态以在流应用程序中进行容错。
 * 各种实现以不同的方式存储它们的检查点，并具有不同的要求和可用性保证。
 *
 * <p>For example, {@link org.apache.flink.runtime.state.storage.JobManagerCheckpointStorage
 * JobManagerCheckpointStorage} stores checkpoints in the memory of the JobManager. It is
 * lightweight and without additional dependencies but is not scalable and only supports small state
 * sizes. This checkpoint storage policy is convenient for local testing and development.
 * 例如{@link org.apache.flink.runtime.state.storage.JobManagerCheckpointStorage}
 * 在JobManager的内存中存储checkpoint。 它是轻量级的，没有额外的依赖项，但不可扩展，只支持小状态大小。
 * 这种检查点存储策略便于本地测试和开发。
 *
 * <p>{@link org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage
 * FileSystemCheckpointStorage} stores checkpoints in a filesystem. For systems like HDFS, NFS
 * Drives, S3, and GCS, this storage policy supports large state size, in the magnitude of many
 * terabytes while providing a highly available foundation for stateful applications. This
 * checkpoint storage policy is recommended for most production deployments.
 * {@link org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage FileSystemCheckpointStorage}
 * 在文件系统中存储检查点。 对于 HDFS、NFS 驱动器、S3 和 GCS 等系统，此存储策略支持大状态大小，达到数 TB，同时为有状态应用程序提供高度可用的基础。
 * 对于大多数生产部署，建议使用此检查点存储策略。
 *
 * <h2>Raw Bytes Storage</h2>
 *
 * <p>The {@code CheckpointStorage} creates services for <i>raw bytes storage</i>.
 *
 * <p>The <i>raw bytes storage</i> (through the {@link CheckpointStreamFactory}) is the fundamental
 * service that simply stores bytes in a fault tolerant fashion. This service is used by the
 * JobManager to store checkpoint and recovery metadata and is typically also used by the keyed- and
 * operator state backends to store checkpointed state.
 * <i>原始字节存储</i>（通过 {@link CheckpointStreamFactory}）是基本服务，它以容错方式简单地存储字节。
 * JobManager 使用此服务来存储检查点和恢复元数据，并且通常也被键控和操作员状态后端用于存储检查点状态。
 *
 * <h2>Serializability</h2>
 *
 * <p>Implementations need to be {@link java.io.Serializable serializable}, because they distributed
 * across parallel processes (for distributed execution) together with the streaming application
 * code.
 * 实现需要是 {@link java.io.Serializable serializable}，因为它们与流应用程序代码一起分布在并行进程中（用于分布式执行）。
 *
 * <p>Because of that, {@code CheckpointStorage} implementations are meant to be like
 * <i>factories</i> that create the proper states stores that provide access to the persistent. That
 * way, the Checkpoint Storage can be very lightweight (contain only configurations) which makes it
 * easier to be serializable.
 * 因此，{@code CheckpointStorage} 实现就像 <i>factories</i> 一样，创建适当的状态存储，提供对持久化的访问。
 * 这样，检查点存储可以非常轻量级（仅包含配置），从而更易于序列化。
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Checkpoint storage implementations have to be thread-safe. Multiple threads may be creating
 * streams concurrently.
 * 检查点存储实现必须是线程安全的。 多个线程可能同时创建流。
 */
@PublicEvolving
public interface CheckpointStorage extends java.io.Serializable {

    /**
     * Resolves the given pointer to a checkpoint/savepoint into a checkpoint location. The location
     * supports reading the checkpoint metadata, or disposing the checkpoint storage location.
     * 将给定的指向检查点/保存点的指针解析为检查点位置。 该位置支持读取检查点元数据，或处置检查点存储位置。
     *
     * @param externalPointer The external checkpoint pointer to resolve.
     * @return The checkpoint location handle.
     * @throws IOException Thrown, if the state backend does not understand the pointer, or if the
     *     pointer could not be resolved due to an I/O error.
     */
    CompletedCheckpointStorageLocation resolveCheckpoint(String externalPointer) throws IOException;

    /**
     * Creates a storage for checkpoints for the given job. The checkpoint storage is used to write
     * checkpoint data and metadata.
     * 为给定作业的检查点创建存储。 检查点存储用于写入检查点数据和元数据。
     *
     * @param jobId The job to store checkpoint data for.
     * @return A checkpoint storage for the given job.
     * @throws IOException Thrown if the checkpoint storage cannot be initialized.
     */
    CheckpointStorageAccess createCheckpointStorage(JobID jobId) throws IOException;
}
