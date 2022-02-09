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

package org.apache.flink.runtime.state.hashmap;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.IllegalConfigurationException;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.fs.CloseableRegistry;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.execution.Environment;
import org.apache.flink.runtime.query.TaskKvStateRegistry;
import org.apache.flink.runtime.state.AbstractKeyedStateBackend;
import org.apache.flink.runtime.state.AbstractStateBackend;
import org.apache.flink.runtime.state.BackendBuildingException;
import org.apache.flink.runtime.state.ConfigurableStateBackend;
import org.apache.flink.runtime.state.DefaultOperatorStateBackendBuilder;
import org.apache.flink.runtime.state.KeyGroupRange;
import org.apache.flink.runtime.state.KeyedStateHandle;
import org.apache.flink.runtime.state.LocalRecoveryConfig;
import org.apache.flink.runtime.state.OperatorStateBackend;
import org.apache.flink.runtime.state.OperatorStateHandle;
import org.apache.flink.runtime.state.TaskStateManager;
import org.apache.flink.runtime.state.heap.HeapKeyedStateBackendBuilder;
import org.apache.flink.runtime.state.heap.HeapPriorityQueueSetFactory;
import org.apache.flink.runtime.state.metrics.LatencyTrackingStateConfig;
import org.apache.flink.runtime.state.ttl.TtlTimeProvider;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.util.Collection;

/**
 * This state backend holds the working state in the memory (JVM heap) of the TaskManagers and
 * checkpoints based on the configured {@link org.apache.flink.runtime.state.CheckpointStorage}.
 * 此状态后端根据配置的 {@link org.apache.flink.runtime.state.CheckpointStorage}
 * 将工作状态保存在 TaskManager 和检查点的内存（JVM 堆）中。
 *
 * <h1>State Size Considerations</h1>
 * 状态大小注意事项
 *
 * <p>Working state is kept on the TaskManager heap. If a TaskManager executes multiple tasks
 * concurrently (if the TaskManager has multiple slots, or if slot-sharing is used) then the
 * aggregate state of all tasks needs to fit into that TaskManager's memory.
 * 工作状态保存在 TaskManager 堆上。 如果一个 TaskManager 并发执行多个任务
 * （如果 TaskManager 有多个 slot，或者如果使用 slot-sharing），那么所有任务的聚合状态需要适合该 TaskManager 的内存。
 *
 * <h1>Configuration</h1>
 *
 * <p>As for all state backends, this backend can either be configured within the application (by
 * creating the backend with the respective constructor parameters and setting it on the execution
 * environment) or by specifying it in the Flink configuration.
 * 对于所有的状态后端，这个后端既可以在应用程序中配置（通过使用相应的构造函数参数创建后端并在执行环境中设置），
 * 也可以在 Flink 配置中指定。
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
@PublicEvolving
public class HashMapStateBackend extends AbstractStateBackend implements ConfigurableStateBackend {

    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------

    /** Creates a new state backend. */
    public HashMapStateBackend() {}

    private HashMapStateBackend(HashMapStateBackend original, ReadableConfig config) {
        // configure latency tracking
        latencyTrackingConfigBuilder = original.latencyTrackingConfigBuilder.configure(config);
    }

    @Override
    public HashMapStateBackend configure(ReadableConfig config, ClassLoader classLoader)
            throws IllegalConfigurationException {
        return new HashMapStateBackend(this, config);
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
            throws IOException {

        TaskStateManager taskStateManager = env.getTaskStateManager();
        LocalRecoveryConfig localRecoveryConfig = taskStateManager.createLocalRecoveryConfig();
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
                        getCompressionDecorator(env.getExecutionConfig()),
                        localRecoveryConfig,
                        priorityQueueSetFactory,
                        true,
                        cancelStreamRegistry)
                .build();
    }

    @Override
    public OperatorStateBackend createOperatorStateBackend(
            Environment env,
            String operatorIdentifier,
            @Nonnull Collection<OperatorStateHandle> stateHandles,
            CloseableRegistry cancelStreamRegistry)
            throws BackendBuildingException {

        return new DefaultOperatorStateBackendBuilder(
                        env.getUserCodeClassLoader().asClassLoader(),
                        env.getExecutionConfig(),
                        true,
                        stateHandles,
                        cancelStreamRegistry)
                .build();
    }
}
