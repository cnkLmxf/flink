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

package org.apache.flink.runtime.state.storage;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.IllegalConfigurationException;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.state.CheckpointStorage;
import org.apache.flink.runtime.state.CheckpointStorageAccess;
import org.apache.flink.runtime.state.CompletedCheckpointStorageLocation;
import org.apache.flink.runtime.state.ConfigurableCheckpointStorage;
import org.apache.flink.runtime.state.filesystem.AbstractFsCheckpointStorageAccess;
import org.apache.flink.runtime.state.filesystem.FsCheckpointStorageAccess;
import org.apache.flink.util.MathUtils;

import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.net.URI;

import static org.apache.flink.configuration.CheckpointingOptions.FS_SMALL_FILE_THRESHOLD;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * {@link FileSystemCheckpointStorage} checkpoints state as files to a file system.
 * {@link FileSystemCheckpointStorage} 检查点状态为文件系统的文件。
 *
 * <p>Each checkpoint individually will store all its files in a subdirectory that includes the
 * checkpoint number, such as {@code hdfs://namenode:port/flink-checkpoints/chk-17/}.
 * 每个检查点都会将其所有文件单独存储在包含检查点编号的子目录中，例如 {@code hdfs://namenode:port/flink-checkpoints/chk-17/}。
 *
 * <h1>State Size Considerations</h1>
 * 状态大小注意事项
 *
 * <p>This checkpoint storage stores small state chunks directly with the metadata, to avoid
 * creating many small files. The threshold for that is configurable. When increasing this
 * threshold, the size of the checkpoint metadata increases. The checkpoint metadata of all retained
 * completed checkpoints needs to fit into the JobManager's heap memory. This is typically not a
 * problem, unless the threshold {@link #getMinFileSizeThreshold()} is increased significantly.
 * 此检查点存储直接与元数据一起存储小状态块，以避免创建许多小文件。 阈值是可配置的。 增加此阈值时，检查点元数据的大小会增加。
 * 所有保留的已完成检查点的检查点元数据需要适合 JobManager 的堆内存。
 * 这通常不是问题，除非阈值 {@link #getMinFileSizeThreshold()} 显着增加。
 *
 * <h1>Persistence Guarantees</h1>
 * 持久性保证
 *
 * <p>Checkpoints from this checkpoint storage are as persistent and available as filesystem that is
 * written to. If the file system is a persistent distributed file system, this checkpoint storage
 * supports highly available setups. The backend additionally supports savepoints and externalized
 * checkpoints.
 * 此检查点存储中的检查点与写入的文件系统一样持久且可用。 如果文件系统是持久分布式文件系统，则此检查点存储支持高可用性设置。
 * 后端还支持保存点和外部检查点。
 *
 * <h1>Configuration</h1>
 *
 * <p>As for all checkpoint storage policies, this backend can either be configured within the
 * application (by creating the backend with the respective constructor parameters and setting it on
 * the execution environment) or by specifying it in the Flink configuration.
 * 对于所有的检查点存储策略，这个后端既可以在应用程序中配置（通过使用相应的构造函数参数创建后端并在执行环境中设置），
 * 也可以在 Flink 配置中指定。
 *
 * <p>If the checkpoint storage was specified in the application, it may pick up additional
 * configuration parameters from the Flink configuration. For example, if the backend if configured
 * in the application without a default savepoint directory, it will pick up a default savepoint
 * directory specified in the Flink configuration of the running job/cluster. That behavior is
 * implemented via the {@link #configure(ReadableConfig, ClassLoader)} method.
 * 如果在应用程序中指定了检查点存储，它可能会从 Flink 配置中获取额外的配置参数。
 * 例如，如果后端在应用程序中配置时没有默认保存点目录，它将选择正在运行的作业/集群的 Flink 配置中指定的默认保存点目录。
 * 该行为是通过 {@link #configure(ReadableConfig, ClassLoader)} 方法实现的。
 */
@PublicEvolving
public class FileSystemCheckpointStorage
        implements CheckpointStorage, ConfigurableCheckpointStorage {

    private static final long serialVersionUID = -8191916350224044011L;

    /** Maximum size of state that is stored with the metadata, rather than in files (1 MiByte). */
    private static final int MAX_FILE_STATE_THRESHOLD = 1024 * 1024;

    // ------------------------------------------------------------------------

    /** The location where snapshots will be externalized. */
    private final ExternalizedSnapshotLocation location;

    /**
     * State below this size will be stored as part of the metadata, rather than in files. A value
     * of '-1' means not yet configured, in which case the default will be used.
     */
    private final int fileStateThreshold;

    /**
     * The write buffer size for created checkpoint stream, this should not be less than file state
     * threshold when we want state below that threshold stored as part of metadata not files. A
     * value of '-1' means not yet configured, in which case the default will be used.
     */
    private final int writeBufferSize;

    /**
     * Creates a new checkpoint storage that stores its checkpoint data in the file system and
     * location defined by the given URI.
     *
     * <p>A file system for the file system scheme in the URI (e.g., 'file://', 'hdfs://', or
     * 'S3://') must be accessible via {@link FileSystem#get(URI)}.
     *
     * <p>For a Job targeting HDFS, this means that the URI must either specify the authority (host
     * and port), or that the Hadoop configuration that describes that information must be in the
     * classpath.
     *
     * @param checkpointDirectory The path to write checkpoint metadata to.
     */
    public FileSystemCheckpointStorage(String checkpointDirectory) {
        this(new Path(checkpointDirectory));
    }

    /**
     * Creates a new checkpoint storage that stores its checkpoint data in the file system and
     * location defined by the given URI.
     *
     * <p>A file system for the file system scheme in the URI (e.g., 'file://', 'hdfs://', or
     * 'S3://') must be accessible via {@link FileSystem#get(URI)}.
     *
     * <p>For a Job targeting HDFS, this means that the URI must either specify the authority (host
     * and port), or that the Hadoop configuration that describes that information must be in the
     * classpath.
     *
     * @param checkpointDirectory The path to write checkpoint metadata to.
     */
    public FileSystemCheckpointStorage(Path checkpointDirectory) {
        this(checkpointDirectory, -1, -1);
    }

    /**
     * Creates a new checkpoint storage that stores its checkpoint data in the file system and
     * location defined by the given URI.
     *
     * <p>A file system for the file system scheme in the URI (e.g., 'file://', 'hdfs://', or
     * 'S3://') must be accessible via {@link FileSystem#get(URI)}.
     *
     * <p>For a Job targeting HDFS, this means that the URI must either specify the authority (host
     * and port), or that the Hadoop configuration that describes that information must be in the
     * classpath.
     *
     * @param checkpointDirectory The path to write checkpoint metadata to.
     */
    public FileSystemCheckpointStorage(URI checkpointDirectory) {
        this(new Path(checkpointDirectory));
    }

    /**
     * Creates a new checkpoint storage that stores its checkpoint data in the file system and
     * location defined by the given URI.
     *
     * <p>A file system for the file system scheme in the URI (e.g., 'file://', 'hdfs://', or
     * 'S3://') must be accessible via {@link FileSystem#get(URI)}.
     *
     * <p>For a Job targeting HDFS, this means that the URI must either specify the authority (host
     * and port), or that the Hadoop configuration that describes that information must be in the
     * classpath.
     *
     * @param checkpointDirectory The path to write checkpoint metadata to.
     * @param fileStateSizeThreshold State below this size will be stored as part of the metadata,
     *     rather than in files. If -1, the value configured in the runtime configuration will be
     *     used, or the default value (1KB) if nothing is configured.
     */
    public FileSystemCheckpointStorage(URI checkpointDirectory, int fileStateSizeThreshold) {
        this(new Path(checkpointDirectory), fileStateSizeThreshold, -1);
    }

    /**
     * Creates a new checkpoint storage that stores its checkpoint data in the file system and
     * location defined by the given URI.
     *
     * <p>A file system for the file system scheme in the URI (e.g., 'file://', 'hdfs://', or
     * 'S3://') must be accessible via {@link FileSystem#get(URI)}.
     *
     * <p>For a Job targeting HDFS, this means that the URI must either specify the authority (host
     * and port), or that the Hadoop configuration that describes that information must be in the
     * classpath.
     *
     * @param checkpointDirectory The path to write checkpoint metadata to.
     * @param fileStateSizeThreshold State below this size will be stored as part of the metadata,
     *     rather than in files. If -1, the value configured in the runtime configuration will be
     *     used, or the default value (1KB) if nothing is configured.
     * @param writeBufferSize Write buffer size used to serialize state. If -1, the value configured
     *     in the runtime configuration will be used, or the default value (4KB) if nothing is
     *     configured.
     */
    public FileSystemCheckpointStorage(
            Path checkpointDirectory, int fileStateSizeThreshold, int writeBufferSize) {

        checkNotNull(checkpointDirectory, "checkpoint directory is null");
        checkArgument(
                fileStateSizeThreshold >= -1 && fileStateSizeThreshold <= MAX_FILE_STATE_THRESHOLD,
                "The threshold for file state size must be in [-1, %s], where '-1' means to use "
                        + "the value from the deployment's configuration.",
                MAX_FILE_STATE_THRESHOLD);
        checkArgument(
                writeBufferSize >= -1,
                "The write buffer size must be not less than '-1', where '-1' means to use "
                        + "the value from the deployment's configuration.");

        this.fileStateThreshold = fileStateSizeThreshold;
        this.writeBufferSize = writeBufferSize;
        this.location =
                ExternalizedSnapshotLocation.newBuilder()
                        .withCheckpointPath(checkpointDirectory)
                        .build();
    }

    /**
     * Private constructor that creates a re-configured copy of the checkpoint storage.
     *
     * @param original The checkpoint storage to re-configure
     * @param configuration The configuration
     */
    private FileSystemCheckpointStorage(
            FileSystemCheckpointStorage original, ReadableConfig configuration) {
        if (getValidFileStateThreshold(original.fileStateThreshold) >= 0) {
            this.fileStateThreshold = original.fileStateThreshold;
        } else {
            final int configuredStateThreshold =
                    getValidFileStateThreshold(
                            configuration.get(FS_SMALL_FILE_THRESHOLD).getBytes());

            if (configuredStateThreshold >= 0) {
                this.fileStateThreshold = configuredStateThreshold;
            } else {
                this.fileStateThreshold =
                        MathUtils.checkedDownCast(
                                FS_SMALL_FILE_THRESHOLD.defaultValue().getBytes());

                // because this is the only place we (unlikely) ever log, we lazily
                // create the logger here
                LoggerFactory.getLogger(FileSystemCheckpointStorage.class)
                        .warn(
                                "Ignoring invalid file size threshold value ({}): {} - using default value {} instead.",
                                FS_SMALL_FILE_THRESHOLD.key(),
                                configuration.get(FS_SMALL_FILE_THRESHOLD).getBytes(),
                                FS_SMALL_FILE_THRESHOLD.defaultValue());
            }
        }

        final int bufferSize =
                original.writeBufferSize >= 0
                        ? original.writeBufferSize
                        : configuration.get(CheckpointingOptions.FS_WRITE_BUFFER_SIZE);

        this.writeBufferSize = Math.max(bufferSize, this.fileStateThreshold);
        this.location =
                ExternalizedSnapshotLocation.newBuilder()
                        .withCheckpointPath(original.location.getBaseCheckpointPath())
                        .withSavepointPath(original.location.getBaseSavepointPath())
                        .withConfiguration(configuration)
                        .build();
    }

    private int getValidFileStateThreshold(long fileStateThreshold) {
        if (fileStateThreshold >= 0 && fileStateThreshold <= MAX_FILE_STATE_THRESHOLD) {
            return (int) fileStateThreshold;
        }
        return -1;
    }

    @Override
    public FileSystemCheckpointStorage configure(ReadableConfig config, ClassLoader classLoader)
            throws IllegalConfigurationException {
        return new FileSystemCheckpointStorage(this, config);
    }

    /**
     * Creates a new {@link FileSystemCheckpointStorage} using the given configuration.
     *
     * @param config The Flink configuration (loaded by the TaskManager).
     * @param classLoader The class loader that should be used to load the checkpoint storage.
     * @return The created checkpoint storage.
     * @throws IllegalConfigurationException If the configuration misses critical values, or
     *     specifies invalid values
     */
    public static FileSystemCheckpointStorage createFromConfig(
            ReadableConfig config, ClassLoader classLoader) throws IllegalConfigurationException {
        // we need to explicitly read the checkpoint directory here, because that
        // is a required constructor parameter
        final String checkpointDir = config.get(CheckpointingOptions.CHECKPOINTS_DIRECTORY);
        if (checkpointDir == null) {
            throw new IllegalConfigurationException(
                    "Cannot create the file system state backend: The configuration does not specify the "
                            + "checkpoint directory '"
                            + CheckpointingOptions.CHECKPOINTS_DIRECTORY.key()
                            + '\'');
        }

        try {
            return new FileSystemCheckpointStorage(checkpointDir).configure(config, classLoader);
        } catch (IllegalArgumentException e) {
            throw new IllegalConfigurationException(
                    "Invalid configuration for the state backend", e);
        }
    }

    @Override
    public CompletedCheckpointStorageLocation resolveCheckpoint(String pointer) throws IOException {
        return AbstractFsCheckpointStorageAccess.resolveCheckpointPointer(pointer);
    }

    @Override
    public CheckpointStorageAccess createCheckpointStorage(JobID jobId) throws IOException {
        checkNotNull(jobId, "jobId");
        return new FsCheckpointStorageAccess(
                location.getBaseCheckpointPath(),
                location.getBaseSavepointPath(),
                jobId,
                getMinFileSizeThreshold(),
                getWriteBufferSize());
    }

    /**
     * Gets the base directory where all the checkpoints are stored. The job-specific checkpoint
     * directory is created inside this directory.
     *
     * @return The base directory for checkpoints.
     */
    @Nonnull
    public Path getCheckpointPath() {
        // we know that this can never be null by the way of constructor checks
        //noinspection ConstantConditions
        return location.getBaseCheckpointPath();
    }

    /** @return The default location where savepoints will be externalized if set. */
    @Nullable
    public Path getSavepointPath() {
        return location.getBaseSavepointPath();
    }

    /**
     * Gets the threshold below which state is stored as part of the metadata, rather than in files.
     * This threshold ensures that the backend does not create a large amount of very small files,
     * where potentially the file pointers are larger than the state itself.
     *
     * <p>If not explicitly configured, this is the default value of {@link
     * CheckpointingOptions#FS_SMALL_FILE_THRESHOLD}.
     *
     * @return The file size threshold, in bytes.
     */
    public int getMinFileSizeThreshold() {
        return fileStateThreshold >= 0
                ? fileStateThreshold
                : MathUtils.checkedDownCast(FS_SMALL_FILE_THRESHOLD.defaultValue().getBytes());
    }

    /**
     * Gets the write buffer size for created checkpoint stream.
     *
     * <p>If not explicitly configured, this is the default value of {@link
     * CheckpointingOptions#FS_WRITE_BUFFER_SIZE}.
     *
     * @return The write buffer size, in bytes.
     */
    public int getWriteBufferSize() {
        return writeBufferSize >= 0
                ? writeBufferSize
                : CheckpointingOptions.FS_WRITE_BUFFER_SIZE.defaultValue();
    }
}
