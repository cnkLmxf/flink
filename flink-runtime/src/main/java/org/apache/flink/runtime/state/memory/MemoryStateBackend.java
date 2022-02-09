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

package org.apache.flink.runtime.state.memory;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.fs.CloseableRegistry;
import org.apache.flink.core.fs.Path;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.execution.Environment;
import org.apache.flink.runtime.query.TaskKvStateRegistry;
import org.apache.flink.runtime.state.AbstractKeyedStateBackend;
import org.apache.flink.runtime.state.AbstractStateBackend;
import org.apache.flink.runtime.state.BackendBuildingException;
import org.apache.flink.runtime.state.CheckpointStorageAccess;
import org.apache.flink.runtime.state.ConfigurableStateBackend;
import org.apache.flink.runtime.state.DefaultOperatorStateBackendBuilder;
import org.apache.flink.runtime.state.KeyGroupRange;
import org.apache.flink.runtime.state.KeyedStateHandle;
import org.apache.flink.runtime.state.OperatorStateBackend;
import org.apache.flink.runtime.state.OperatorStateHandle;
import org.apache.flink.runtime.state.TaskStateManager;
import org.apache.flink.runtime.state.filesystem.AbstractFileStateBackend;
import org.apache.flink.runtime.state.heap.HeapKeyedStateBackendBuilder;
import org.apache.flink.runtime.state.heap.HeapPriorityQueueSetFactory;
import org.apache.flink.runtime.state.metrics.LatencyTrackingStateConfig;
import org.apache.flink.runtime.state.ttl.TtlTimeProvider;
import org.apache.flink.util.TernaryBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.util.Collection;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * <b>IMPORTANT</b> {@link MemoryStateBackend} is deprecated in favor of {@link
 * org.apache.flink.runtime.state.hashmap.HashMapStateBackend} and {@link
 * org.apache.flink.runtime.state.storage.JobManagerCheckpointStorage}. This change does not affect
 * the runtime characteristics of your Jobs and is simply an API change to help better communicate
 * the ways Flink separates local state storage from fault tolerance. Jobs can be upgraded without
 * loss of state. If configuring your state backend via the {@code StreamExecutionEnvironment}
 * please make the following changes.
 * <b>重要</b> {@link MemoryStateBackend} 已弃用，
 * 取而代之的是 {@link org.apache.flink.runtime.state.hashmap.HashMapStateBackend} 和
 * {@link org.apache.flink.runtime.state.storage .JobManagerCheckpointStorage}。
 * 此更改不会影响 Job 的运行时特性，只是一个 API 更改，以帮助更好地传达 Flink 将本地状态存储与容错分离的方式。
 * 可以在不丢失状态的情况下升级作业。 如果通过 {@code StreamExecutionEnvironment} 配置您的状态后端，请进行以下更改。
 *
 * <pre>{@code
 * 		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
 * 		env.setStateBackend(new HashMapStateBackend());
 * 		env.getCheckpointConfig().setCheckpointStorage(new JobManagerCheckpointStorage());
 * }</pre>
 *
 * <p>If you are configuring your state backend via the {@code flink-conf.yaml} please make the
 * following changes:
 *
 * <pre>{@code
 * state.backend: hashmap
 * state.checkpoint-storage: jobmanager
 * }</pre>
 *
 * <p>This state backend holds the working state in the memory (JVM heap) of the TaskManagers. The
 * state backend checkpoints state directly to the JobManager's memory (hence the backend's name),
 * but the checkpoints will be persisted to a file system for high-availability setups and
 * savepoints. The MemoryStateBackend is consequently a FileSystem-based backend that can work
 * without a file system dependency in simple setups.
 * 此状态后端将工作状态保存在 TaskManager 的内存（JVM 堆）中。 状态后端检查点状态直接到 JobManager 的内存（因此是后端的名称），
 * 但检查点将持久保存到文件系统以用于高可用性设置和保存点。
 * 因此，MemoryStateBackend 是一个基于文件系统的后端，它可以在简单的设置中不依赖于文件系统而工作。
 *
 * <p>This state backend should be used only for experimentation, quick local setups, or for
 * streaming applications that have very small state: Because it requires checkpoints to go through
 * the JobManager's memory, larger state will occupy larger portions of the JobManager's main
 * memory, reducing operational stability. For any other setup, the {@link
 * org.apache.flink.runtime.state.filesystem.FsStateBackend FsStateBackend} should be used. The
 * {@code FsStateBackend} holds the working state on the TaskManagers in the same way, but
 * checkpoints state directly to files rather than to the JobManager's memory, thus supporting large
 * state sizes.
 * 此状态后端仅应用于实验、快速本地设置或状态非常小的流式应用程序：因为它需要检查点通过 JobManager 的内存，
 * 较大的状态将占用 JobManager 的主内存的较大部分，降低运行稳定性 .
 * 对于任何其他设置，应使用 {@link org.apache.flink.runtime.state.filesystem.FsStateBackend FsStateBackend}。 {@code FsStateBackend} 以相同的方式保存 TaskManager 上的工作状态，但检查点状态直接发送到文件而不是 JobManager 的内存，因此支持较大的状态大小。
 *
 * <h1>State Size Considerations</h1>
 *
 * <p>State checkpointing with this state backend is subject to the following conditions:
 * 使用此状态后端的状态检查点受以下条件的约束：
 *<ul>
 *    <li>每个单独的状态不得超过配置的最大状态大小（请参阅 {@link #getMaxStateSize()}。
 *     <li>一个任务的所有状态（即任务的所有链式操作符的所有操作符状态和键控状态的总和）不得超过 RPC 系统支持的值，
 *     默认值 < 10 MB。 可以配置该限制，但通常不建议这样做。
 *   <li>应用程序中所有状态的总和乘以所有保留的检查点必须能够轻松适应 JobManager 的 JVM 堆空间。
 *   </ul>
 * <ul>
 *   <li>Each individual state must not exceed the configured maximum state size (see {@link
 *       #getMaxStateSize()}.
 *   <li>All state from one task (i.e., the sum of all operator states and keyed states from all
 *       chained operators of the task) must not exceed what the RPC system supports, which is be
 *       default < 10 MB. That limit can be configured up, but that is typically not advised.
 *   <li>The sum of all states in the application times all retained checkpoints must comfortably
 *       fit into the JobManager's JVM heap space.
 * </ul>
 *
 * <h1>Persistence Guarantees</h1>
 *
 * <p>For the use cases where the state sizes can be handled by this backend, the backend does
 * guarantee persistence for savepoints, externalized checkpoints (of configured), and checkpoints
 * (when high-availability is configured).
 * 对于此后端可以处理状态大小的用例，后端确实保证保存点、外部化检查点（配置的）和检查点（配置高可用性时）的持久性。
 *
 * <h1>Configuration</h1>
 *
 * <p>As for all state backends, this backend can either be configured within the application (by
 * creating the backend with the respective constructor parameters and setting it on the execution
 * environment) or by specifying it in the Flink configuration.
 * 对于所有状态后端，该后端既可以在应用程序中配置（通过使用相应的构造函数参数创建后端并在执行环境中设置），也可以在 Flink 配置中指定。
 *
 * <p>If the state backend was specified in the application, it may pick up additional configuration
 * parameters from the Flink configuration. For example, if the backend if configured in the
 * application without a default savepoint directory, it will pick up a default savepoint directory
 * specified in the Flink configuration of the running job/cluster. That behavior is implemented via
 * the {@link #configure(ReadableConfig, ClassLoader)} method.
 * 如果在应用程序中指定了状态后端，它可能会从 Flink 配置中获取额外的配置参数。
 * 例如，如果后端在应用程序中配置时没有默认保存点目录，它将选择正在运行的作业/集群的 Flink 配置中指定的默认保存点目录。
 * 该行为是通过 {@link #configure(ReadableConfig, ClassLoader)} 方法实现的。
 */
@Deprecated
@PublicEvolving
public class MemoryStateBackend extends AbstractFileStateBackend
        implements ConfigurableStateBackend {

    private static final long serialVersionUID = 4109305377809414635L;

    /** The default maximal size that the snapshotted memory state may have (5 MiBytes). */
    public static final int DEFAULT_MAX_STATE_SIZE = 5 * 1024 * 1024;

    /** The maximal size that the snapshotted memory state may have. */
    private final int maxStateSize;

    // ------------------------------------------------------------------------

    /**
     * Creates a new memory state backend that accepts states whose serialized forms are up to the
     * default state size (5 MB).
     *
     * <p>Checkpoint and default savepoint locations are used as specified in the runtime
     * configuration.
     */
    public MemoryStateBackend() {
        this(null, null, DEFAULT_MAX_STATE_SIZE, TernaryBoolean.UNDEFINED);
    }

    /**
     * Creates a new memory state backend that accepts states whose serialized forms are up to the
     * default state size (5 MB). The state backend uses asynchronous snapshots or synchronous
     * snapshots as configured.
     *
     * <p>Checkpoint and default savepoint locations are used as specified in the runtime
     * configuration.
     *
     * @param asynchronousSnapshots This parameter is only there for API compatibility. Checkpoints
     *     are always asynchronous now.
     */
    public MemoryStateBackend(boolean asynchronousSnapshots) {
        this(null, null, DEFAULT_MAX_STATE_SIZE, TernaryBoolean.fromBoolean(asynchronousSnapshots));
    }

    /**
     * Creates a new memory state backend that accepts states whose serialized forms are up to the
     * given number of bytes.
     *
     * <p>Checkpoint and default savepoint locations are used as specified in the runtime
     * configuration.
     *
     * <p><b>WARNING:</b> Increasing the size of this value beyond the default value ({@value
     * #DEFAULT_MAX_STATE_SIZE}) should be done with care. The checkpointed state needs to be send
     * to the JobManager via limited size RPC messages, and there and the JobManager needs to be
     * able to hold all aggregated state in its memory.
     *
     * @param maxStateSize The maximal size of the serialized state
     */
    public MemoryStateBackend(int maxStateSize) {
        this(null, null, maxStateSize, TernaryBoolean.UNDEFINED);
    }

    /**
     * Creates a new memory state backend that accepts states whose serialized forms are up to the
     * given number of bytes and that uses asynchronous snashots as configured.
     *
     * <p>Checkpoint and default savepoint locations are used as specified in the runtime
     * configuration.
     *
     * <p><b>WARNING:</b> Increasing the size of this value beyond the default value ({@value
     * #DEFAULT_MAX_STATE_SIZE}) should be done with care. The checkpointed state needs to be send
     * to the JobManager via limited size RPC messages, and there and the JobManager needs to be
     * able to hold all aggregated state in its memory.
     *
     * @param maxStateSize The maximal size of the serialized state
     * @param asynchronousSnapshots This parameter is only there for API compatibility. Checkpoints
     *     are always asynchronous now.
     */
    public MemoryStateBackend(int maxStateSize, boolean asynchronousSnapshots) {
        this(null, null, maxStateSize, TernaryBoolean.fromBoolean(asynchronousSnapshots));
    }

    /**
     * Creates a new MemoryStateBackend, setting optionally the path to persist checkpoint metadata
     * to, and to persist savepoints to.
     *
     * @param checkpointPath The path to write checkpoint metadata to. If null, the value from the
     *     runtime configuration will be used.
     * @param savepointPath The path to write savepoints to. If null, the value from the runtime
     *     configuration will be used.
     */
    public MemoryStateBackend(@Nullable String checkpointPath, @Nullable String savepointPath) {
        this(checkpointPath, savepointPath, DEFAULT_MAX_STATE_SIZE, TernaryBoolean.UNDEFINED);
    }

    /**
     * Creates a new MemoryStateBackend, setting optionally the paths to persist checkpoint metadata
     * and savepoints to, as well as configuring state thresholds and asynchronous operations.
     *
     * <p><b>WARNING:</b> Increasing the size of this value beyond the default value ({@value
     * #DEFAULT_MAX_STATE_SIZE}) should be done with care. The checkpointed state needs to be send
     * to the JobManager via limited size RPC messages, and there and the JobManager needs to be
     * able to hold all aggregated state in its memory.
     *
     * @param checkpointPath The path to write checkpoint metadata to. If null, the value from the
     *     runtime configuration will be used.
     * @param savepointPath The path to write savepoints to. If null, the value from the runtime
     *     configuration will be used.
     * @param maxStateSize The maximal size of the serialized state.
     * @param asynchronousSnapshots This parameter is only there for API compatibility. Checkpoints
     *     are always asynchronous now.
     */
    public MemoryStateBackend(
            @Nullable String checkpointPath,
            @Nullable String savepointPath,
            int maxStateSize,
            TernaryBoolean asynchronousSnapshots) {

        super(
                checkpointPath == null ? null : new Path(checkpointPath),
                savepointPath == null ? null : new Path(savepointPath));

        checkArgument(maxStateSize > 0, "maxStateSize must be > 0");
        this.maxStateSize = maxStateSize;
    }

    /**
     * Private constructor that creates a re-configured copy of the state backend.
     *
     * @param original The state backend to re-configure
     * @param configuration The configuration
     * @param classLoader The class loader
     */
    private MemoryStateBackend(
            MemoryStateBackend original, ReadableConfig configuration, ClassLoader classLoader) {
        super(original.getCheckpointPath(), original.getSavepointPath(), configuration);

        this.maxStateSize = original.maxStateSize;

        // configure latency tracking
        latencyTrackingConfigBuilder =
                original.latencyTrackingConfigBuilder.configure(configuration);
    }

    // ------------------------------------------------------------------------
    //  Properties
    // ------------------------------------------------------------------------

    /**
     * Gets the maximum size that an individual state can have, as configured in the constructor (by
     * default {@value #DEFAULT_MAX_STATE_SIZE}).
     *
     * @return The maximum size that an individual state can have
     */
    public int getMaxStateSize() {
        return maxStateSize;
    }

    /**
     * Gets whether the key/value data structures are asynchronously snapshotted, which is always
     * true for this state backend.
     */
    public boolean isUsingAsynchronousSnapshots() {
        return true;
    }

    // ------------------------------------------------------------------------
    //  Reconfiguration
    // ------------------------------------------------------------------------

    /**
     * Creates a copy of this state backend that uses the values defined in the configuration for
     * fields where that were not specified in this state backend.
     *
     * @param config The configuration
     * @param classLoader The class loader
     * @return The re-configured variant of the state backend
     */
    @Override
    public MemoryStateBackend configure(ReadableConfig config, ClassLoader classLoader) {
        return new MemoryStateBackend(this, config, classLoader);
    }

    // ------------------------------------------------------------------------
    //  checkpoint state persistence
    // ------------------------------------------------------------------------

    @Override
    public CheckpointStorageAccess createCheckpointStorage(JobID jobId) throws IOException {
        return new MemoryBackendCheckpointStorageAccess(
                jobId, getCheckpointPath(), getSavepointPath(), maxStateSize);
    }

    // ------------------------------------------------------------------------
    //  state holding structures
    // ------------------------------------------------------------------------

    @Override
    public OperatorStateBackend createOperatorStateBackend(
            Environment env,
            String operatorIdentifier,
            @Nonnull Collection<OperatorStateHandle> stateHandles,
            CloseableRegistry cancelStreamRegistry)
            throws Exception {

        return new DefaultOperatorStateBackendBuilder(
                        env.getUserCodeClassLoader().asClassLoader(),
                        env.getExecutionConfig(),
                        isUsingAsynchronousSnapshots(),
                        stateHandles,
                        cancelStreamRegistry)
                .build();
    }

    @Override
    public <K> AbstractKeyedStateBackend<K> createKeyedStateBackend(
            Environment env,
            JobID jobID,
            String operatorIdentifier,
            TypeSerializer<K> keySerializer,
            int numberOfKeyGroups,
            KeyGroupRange keyGroupRange,
            TaskKvStateRegistry kvStateRegistry,
            TtlTimeProvider ttlTimeProvider,
            MetricGroup metricGroup,
            @Nonnull Collection<KeyedStateHandle> stateHandles,
            CloseableRegistry cancelStreamRegistry)
            throws BackendBuildingException {

        TaskStateManager taskStateManager = env.getTaskStateManager();
        HeapPriorityQueueSetFactory priorityQueueSetFactory =
                new HeapPriorityQueueSetFactory(keyGroupRange, numberOfKeyGroups, 128);
        LatencyTrackingStateConfig latencyTrackingStateConfig =
                latencyTrackingConfigBuilder.setMetricGroup(metricGroup).build();
        return new HeapKeyedStateBackendBuilder<>(
                        kvStateRegistry,
                        keySerializer,
                        env.getUserCodeClassLoader().asClassLoader(),
                        numberOfKeyGroups,
                        keyGroupRange,
                        env.getExecutionConfig(),
                        ttlTimeProvider,
                        latencyTrackingStateConfig,
                        stateHandles,
                        AbstractStateBackend.getCompressionDecorator(env.getExecutionConfig()),
                        taskStateManager.createLocalRecoveryConfig(),
                        priorityQueueSetFactory,
                        isUsingAsynchronousSnapshots(),
                        cancelStreamRegistry)
                .build();
    }

    // ------------------------------------------------------------------------
    //  utilities
    // ------------------------------------------------------------------------

    @Override
    public String toString() {
        return "MemoryStateBackend (data in heap memory / checkpoints to JobManager) "
                + "(checkpoints: '"
                + getCheckpointPath()
                + "', savepoints: '"
                + getSavepointPath()
                + ", maxStateSize: "
                + maxStateSize
                + ")";
    }
}
