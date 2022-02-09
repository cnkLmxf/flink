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

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.api.common.state.State;
import org.apache.flink.api.common.state.StateDescriptor;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.fs.CloseableRegistry;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.checkpoint.CheckpointType;
import org.apache.flink.runtime.query.TaskKvStateRegistry;
import org.apache.flink.runtime.state.heap.InternalKeyContext;
import org.apache.flink.runtime.state.internal.InternalKvState;
import org.apache.flink.runtime.state.metrics.LatencyTrackingStateConfig;
import org.apache.flink.runtime.state.metrics.LatencyTrackingStateFactory;
import org.apache.flink.runtime.state.ttl.TtlStateFactory;
import org.apache.flink.runtime.state.ttl.TtlTimeProvider;
import org.apache.flink.util.IOUtils;
import org.apache.flink.util.Preconditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Base implementation of KeyedStateBackend. The state can be checkpointed to streams using {@link
 * #snapshot(long, long, CheckpointStreamFactory, CheckpointOptions)}.
 * KeyedStateBackend 的基本实现。
 * 可以使用 {@link #snapshot(long, long, CheckpointStreamFactory, CheckpointOptions)} 将状态检查点到流。
 *
 * @param <K> Type of the key by which state is keyed.
 */
public abstract class AbstractKeyedStateBackend<K>
        implements CheckpointableKeyedStateBackend<K>,
                CheckpointListener,
                TestableKeyedStateBackend {

    /** The key serializer. */
    protected final TypeSerializer<K> keySerializer;

    /** Listeners to changes of ({@link #keyContext}). */
    private final ArrayList<KeySelectionListener<K>> keySelectionListeners;

    /** So that we can give out state when the user uses the same key.
     * 这样我们就可以在用户使用相同的密钥时给出状态。
     * */
    private final HashMap<String, InternalKvState<K, ?, ?>> keyValueStatesByName;

    /** For caching the last accessed partitioned state.
     * 用于缓存上次访问的分区状态。
     * */
    private String lastName;

    @SuppressWarnings("rawtypes")
    private InternalKvState lastState;

    /** The number of key-groups aka max parallelism.
     * 密钥组的数量也称为最大并行度。
     * */
    protected final int numberOfKeyGroups;

    /** Range of key-groups for which this backend is responsible.
     * 此后端负责的密钥组范围。
     * */
    protected final KeyGroupRange keyGroupRange;

    /** KvStateRegistry helper for this task.
     * 此任务的 KvStateRegistry 助手。
     * */
    protected final TaskKvStateRegistry kvStateRegistry;

    /**
     * Registry for all opened streams, so they can be closed if the task using this backend is
     * closed.
     * 所有打开的流的注册表，因此如果使用此后端的任务关闭，它们可以关闭。
     */
    protected CloseableRegistry cancelStreamRegistry;

    protected final ClassLoader userCodeClassLoader;

    private final ExecutionConfig executionConfig;

    protected final TtlTimeProvider ttlTimeProvider;

    protected final LatencyTrackingStateConfig latencyTrackingStateConfig;

    /** Decorates the input and output streams to write key-groups compressed.
     * 修饰输入和输出流以写入压缩的密钥组。
     * */
    protected final StreamCompressionDecorator keyGroupCompressionDecorator;

    /** The key context for this backend. */
    protected final InternalKeyContext<K> keyContext;

    public AbstractKeyedStateBackend(
            TaskKvStateRegistry kvStateRegistry,
            TypeSerializer<K> keySerializer,
            ClassLoader userCodeClassLoader,
            ExecutionConfig executionConfig,
            TtlTimeProvider ttlTimeProvider,
            LatencyTrackingStateConfig latencyTrackingStateConfig,
            CloseableRegistry cancelStreamRegistry,
            InternalKeyContext<K> keyContext) {
        this(
                kvStateRegistry,
                keySerializer,
                userCodeClassLoader,
                executionConfig,
                ttlTimeProvider,
                latencyTrackingStateConfig,
                cancelStreamRegistry,
                determineStreamCompression(executionConfig),
                keyContext);
    }

    public AbstractKeyedStateBackend(
            TaskKvStateRegistry kvStateRegistry,
            TypeSerializer<K> keySerializer,
            ClassLoader userCodeClassLoader,
            ExecutionConfig executionConfig,
            TtlTimeProvider ttlTimeProvider,
            LatencyTrackingStateConfig latencyTrackingStateConfig,
            CloseableRegistry cancelStreamRegistry,
            StreamCompressionDecorator keyGroupCompressionDecorator,
            InternalKeyContext<K> keyContext) {
        this.keyContext = Preconditions.checkNotNull(keyContext);
        this.numberOfKeyGroups = keyContext.getNumberOfKeyGroups();
        this.keyGroupRange = Preconditions.checkNotNull(keyContext.getKeyGroupRange());
        Preconditions.checkArgument(
                numberOfKeyGroups >= 1, "NumberOfKeyGroups must be a positive number");
        Preconditions.checkArgument(
                numberOfKeyGroups >= keyGroupRange.getNumberOfKeyGroups(),
                "The total number of key groups must be at least the number in the key group range assigned to this backend. "
                        + "The total number of key groups: %s, the number in key groups in range: %s",
                numberOfKeyGroups,
                keyGroupRange.getNumberOfKeyGroups());

        this.kvStateRegistry = kvStateRegistry;
        this.keySerializer = keySerializer;
        this.userCodeClassLoader = Preconditions.checkNotNull(userCodeClassLoader);
        this.cancelStreamRegistry = cancelStreamRegistry;
        this.keyValueStatesByName = new HashMap<>();
        this.executionConfig = executionConfig;
        this.keyGroupCompressionDecorator = keyGroupCompressionDecorator;
        this.ttlTimeProvider = Preconditions.checkNotNull(ttlTimeProvider);
        this.latencyTrackingStateConfig = Preconditions.checkNotNull(latencyTrackingStateConfig);
        this.keySelectionListeners = new ArrayList<>(1);
    }

    private static StreamCompressionDecorator determineStreamCompression(
            ExecutionConfig executionConfig) {
        if (executionConfig != null && executionConfig.isUseSnapshotCompression()) {
            return SnappyStreamCompressionDecorator.INSTANCE;
        } else {
            return UncompressedStreamCompressionDecorator.INSTANCE;
        }
    }

    /**
     * Closes the state backend, releasing all internal resources, but does not delete any
     * persistent checkpoint data.
     * 关闭状态后端，释放所有内部资源，但不删除任何持久检查点数据。
     */
    @Override
    public void dispose() {

        IOUtils.closeQuietly(cancelStreamRegistry);

        if (kvStateRegistry != null) {
            kvStateRegistry.unregisterAll();
        }

        lastName = null;
        lastState = null;
        keyValueStatesByName.clear();
    }

    /** @see KeyedStateBackend */
    @Override
    public void setCurrentKey(K newKey) {
        notifyKeySelected(newKey);
        this.keyContext.setCurrentKey(newKey);
        this.keyContext.setCurrentKeyGroupIndex(
                KeyGroupRangeAssignment.assignToKeyGroup(newKey, numberOfKeyGroups));
    }

    private void notifyKeySelected(K newKey) {
        // we prefer a for-loop over other iteration schemes for performance reasons here.
        // 出于性能原因，我们更喜欢 for 循环而不是其他迭代方案。
        for (int i = 0; i < keySelectionListeners.size(); ++i) {
            keySelectionListeners.get(i).keySelected(newKey);
        }
    }

    @Override
    public void registerKeySelectionListener(KeySelectionListener<K> listener) {
        keySelectionListeners.add(listener);
    }

    @Override
    public boolean deregisterKeySelectionListener(KeySelectionListener<K> listener) {
        return keySelectionListeners.remove(listener);
    }

    /** @see KeyedStateBackend */
    @Override
    public TypeSerializer<K> getKeySerializer() {
        return keySerializer;
    }

    /** @see KeyedStateBackend */
    @Override
    public K getCurrentKey() {
        return this.keyContext.getCurrentKey();
    }

    /** @see KeyedStateBackend */
    public int getCurrentKeyGroupIndex() {
        return this.keyContext.getCurrentKeyGroupIndex();
    }

    /** @see KeyedStateBackend */
    public int getNumberOfKeyGroups() {
        return numberOfKeyGroups;
    }

    /** @see KeyedStateBackend */
    @Override
    public KeyGroupRange getKeyGroupRange() {
        return keyGroupRange;
    }

    /** @see KeyedStateBackend */
    @Override
    public <N, S extends State, T> void applyToAllKeys(
            final N namespace,
            final TypeSerializer<N> namespaceSerializer,
            final StateDescriptor<S, T> stateDescriptor,
            final KeyedStateFunction<K, S> function)
            throws Exception {

        applyToAllKeys(
                namespace,
                namespaceSerializer,
                stateDescriptor,
                function,
                this::getPartitionedState);
    }

    public <N, S extends State, T> void applyToAllKeys(
            final N namespace,
            final TypeSerializer<N> namespaceSerializer,
            final StateDescriptor<S, T> stateDescriptor,
            final KeyedStateFunction<K, S> function,
            final PartitionStateFactory partitionStateFactory)
            throws Exception {

        try (Stream<K> keyStream = getKeys(stateDescriptor.getName(), namespace)) {

            final S state =
                    partitionStateFactory.get(namespace, namespaceSerializer, stateDescriptor);

            keyStream.forEach(
                    (K key) -> {
                        setCurrentKey(key);
                        try {
                            function.process(key, state);
                        } catch (Throwable e) {
                            // we wrap the checked exception in an unchecked
                            // one and catch it (and re-throw it) later.
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    /** @see KeyedStateBackend */
    @Override
    @SuppressWarnings("unchecked")
    public <N, S extends State, V> S getOrCreateKeyedState(
            final TypeSerializer<N> namespaceSerializer, StateDescriptor<S, V> stateDescriptor)
            throws Exception {
        checkNotNull(namespaceSerializer, "Namespace serializer");
        checkNotNull(
                keySerializer,
                "State key serializer has not been configured in the config. "
                        + "This operation cannot use partitioned state.");

        InternalKvState<K, ?, ?> kvState = keyValueStatesByName.get(stateDescriptor.getName());
        if (kvState == null) {
            if (!stateDescriptor.isSerializerInitialized()) {
                stateDescriptor.initializeSerializerUnlessSet(executionConfig);
            }
            kvState =
                    LatencyTrackingStateFactory.createStateAndWrapWithLatencyTrackingIfEnabled(
                            TtlStateFactory.createStateAndWrapWithTtlIfEnabled(
                                    namespaceSerializer, stateDescriptor, this, ttlTimeProvider),
                            stateDescriptor,
                            latencyTrackingStateConfig);
            keyValueStatesByName.put(stateDescriptor.getName(), kvState);
            publishQueryableStateIfEnabled(stateDescriptor, kvState);
        }
        return (S) kvState;
    }

    public void publishQueryableStateIfEnabled(
            StateDescriptor<?, ?> stateDescriptor, InternalKvState<?, ?, ?> kvState) {
        if (stateDescriptor.isQueryable()) {
            if (kvStateRegistry == null) {
                throw new IllegalStateException("State backend has not been initialized for job.");
            }
            String name = stateDescriptor.getQueryableStateName();
            kvStateRegistry.registerKvState(keyGroupRange, name, kvState, userCodeClassLoader);
        }
    }

    /**
     * TODO: NOTE: This method does a lot of work caching / retrieving states just to update the
     * namespace. This method should be removed for the sake of namespaces being lazily fetched from
     * the keyed state backend, or being set on the state directly.
     *
     * @see KeyedStateBackend
     */
    @SuppressWarnings("unchecked")
    @Override
    public <N, S extends State> S getPartitionedState(
            final N namespace,
            final TypeSerializer<N> namespaceSerializer,
            final StateDescriptor<S, ?> stateDescriptor)
            throws Exception {

        checkNotNull(namespace, "Namespace");

        if (lastName != null && lastName.equals(stateDescriptor.getName())) {
            lastState.setCurrentNamespace(namespace);
            return (S) lastState;
        }

        InternalKvState<K, ?, ?> previous = keyValueStatesByName.get(stateDescriptor.getName());
        if (previous != null) {
            lastState = previous;
            lastState.setCurrentNamespace(namespace);
            lastName = stateDescriptor.getName();
            return (S) previous;
        }

        final S state = getOrCreateKeyedState(namespaceSerializer, stateDescriptor);
        final InternalKvState<K, N, ?> kvState = (InternalKvState<K, N, ?>) state;

        lastName = stateDescriptor.getName();
        lastState = kvState;
        kvState.setCurrentNamespace(namespace);

        return state;
    }

    @Override
    public void close() throws IOException {
        cancelStreamRegistry.close();
    }

    public LatencyTrackingStateConfig getLatencyTrackingStateConfig() {
        return latencyTrackingStateConfig;
    }

    @VisibleForTesting
    public StreamCompressionDecorator getKeyGroupCompressionDecorator() {
        return keyGroupCompressionDecorator;
    }

    @VisibleForTesting
    public int numKeyValueStatesByName() {
        return keyValueStatesByName.size();
    }

    // TODO remove this once heap-based timers are working with RocksDB incremental snapshots!
    public boolean requiresLegacySynchronousTimerSnapshots(CheckpointType checkpointType) {
        return false;
    }

    public interface PartitionStateFactory {
        <N, S extends State> S get(
                final N namespace,
                final TypeSerializer<N> namespaceSerializer,
                final StateDescriptor<S, ?> stateDescriptor)
                throws Exception;
    }
}
