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
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.fs.CloseableRegistry;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.execution.Environment;
import org.apache.flink.runtime.query.TaskKvStateRegistry;
import org.apache.flink.runtime.state.ttl.TtlTimeProvider;

import javax.annotation.Nonnull;

import java.util.Collection;

/**
 * A <b>State Backend</b> defines how the state of a streaming application is stored locally within
 * the cluster. Different State Backends store their state in different fashions, and use different
 * data structures to hold the state of a running application.
 * <b>状态后端</b>定义了流应用程序的状态如何在集群中本地存储。
 * 不同的状态后端以不同的方式存储它们的状态，并使用不同的数据结构来保存正在运行的应用程序的状态。
 *
 * <p>For example, the {@link org.apache.flink.runtime.state.hashmap.HashMapStateBackend hashmap
 * state backend} keeps working state in the memory of the TaskManager. The backend is lightweight
 * and without additional dependencies.
 * 例如{@link org.apache.flink.runtime.state.hashmap.HashMapStateBackend hashmap state backend}
 * 在TaskManager的内存中保持工作状态。 后端是轻量级的，没有额外的依赖。
 *
 * <p>The {@code EmbeddedRocksDBStateBackend} stores working state in an embedded <a
 * href="http://rocksdb.org/">RocksDB</a> and is able to scale working state to many terabytes in
 * size, only limited by available disk space across all task managers.
 * {@code EmbeddedRocksDBStateBackend} 将工作状态存储在嵌入式 <a href="http://rocksdb.org/">RocksDB</a> 中，
 * 并且能够将工作状态扩展到许多 TB 的大小，仅受可用磁盘的限制 所有任务管理器的空间。
 *
 * <h2>Raw Bytes Storage and Backends</h2>
 *
 * <p>The {@code StateBackend} creates services for for <i>keyed state</i> and <i>operator
 * state</i>.
 * {@code StateBackend} 为 <i>keyed state</i> 和 <i>operator state</i> 创建服务。
 *
 * <p>The {@link CheckpointableKeyedStateBackend} and {@link OperatorStateBackend} created by this
 * state backend define how to hold the working state for keys and operators. They also define how
 * to checkpoint that state, frequently using the raw bytes storage (via the {@code
 * CheckpointStreamFactory}). However, it is also possible that for example a keyed state backend
 * simply implements the bridge to a key/value store, and that it does not need to store anything in
 * the raw byte storage upon a checkpoint.
 * 此状态后端创建的 {@link CheckpointableKeyedStateBackend} 和 {@link OperatorStateBackend} 定义了如何保持键和运算符的工作状态。
 * 他们还定义了如何检查该状态，经常使用原始字节存储（通过 {@code CheckpointStreamFactory}）。
 * 然而，也有可能例如一个键控状态后端简单地实现到键/值存储的桥接，并且它不需要在检查点时在原始字节存储中存储任何内容。
 *
 * <h2>Serializability</h2>
 *
 * <p>State Backends need to be {@link java.io.Serializable serializable}, because they distributed
 * across parallel processes (for distributed execution) together with the streaming application
 * code.
 * 状态后端需要 {@link java.io.Serializable 可序列化}，因为它们与流应用程序代码一起分布在并行进程中（用于分布式执行）。
 *
 * <p>Because of that, {@code StateBackend} implementations (typically subclasses of {@link
 * AbstractStateBackend}) are meant to be like <i>factories</i> that create the proper states stores
 * that provide access to the persistent storage and hold the keyed- and operator state data
 * structures. That way, the State Backend can be very lightweight (contain only configurations)
 * which makes it easier to be serializable.
 * 因此，{@code StateBackend} 实现（通常是 {@link AbstractStateBackend} 的子类）就像 <i>factories</i> 一样，
 * 创建适当的状态存储，提供对持久存储的访问并保存密钥 - 和运营商状态数据结构。
 * 这样，状态后端可以非常轻量级（仅包含配置），这使得可序列化变得更容易。
 *
 * <h2>Thread Safety</h2>
 *
 * <p>State backend implementations have to be thread-safe. Multiple threads may be creating
 * keyed-/operator state backends concurrently.
 * 状态后端实现必须是线程安全的。 多个线程可能同时创建键控/操作符状态后端。
 */
@PublicEvolving
public interface StateBackend extends java.io.Serializable {

    /**
     * Creates a new {@link CheckpointableKeyedStateBackend} that is responsible for holding
     * <b>keyed state</b> and checkpointing it.
     * 创建一个新的 {@link CheckpointableKeyedStateBackend}，负责保存 <b>keyed state</b> 并对其进行检查点。
     *
     * <p><i>Keyed State</i> is state where each value is bound to a key.
     * <i>Keyed State</i> 是每个值都绑定到一个键的状态。
     *
     * @param env The environment of the task.
     * @param jobID The ID of the job that the task belongs to.
     * @param operatorIdentifier The identifier text of the operator.
     * @param keySerializer The key-serializer for the operator.
     * @param numberOfKeyGroups The number of key-groups aka max parallelism.
     * @param keyGroupRange Range of key-groups for which the to-be-created backend is responsible.
     *                      要创建的后端负责的键组范围。
     * @param kvStateRegistry KvStateRegistry helper for this task.
     * @param ttlTimeProvider Provider for TTL logic to judge about state expiration.
     * @param metricGroup The parent metric group for all state backend metrics.
     * @param stateHandles The state handles for restore.用于恢复的状态句柄。
     * @param cancelStreamRegistry The registry to which created closeable objects will be
     *     registered during restore.
     * @param <K> The type of the keys by which the state is organized.
     * @return The Keyed State Backend for the given job, operator, and key group range.
     * @throws Exception This method may forward all exceptions that occur while instantiating the
     *     backend.
     */
    <K> CheckpointableKeyedStateBackend<K> createKeyedStateBackend(
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
            throws Exception;

    /**
     * Creates a new {@link CheckpointableKeyedStateBackend} with the given managed memory fraction.
     * Backends that use managed memory are required to implement this interface.
     * 使用给定的托管内存部分创建一个新的 {@link CheckpointableKeyedStateBackend}。
     * 需要使用托管内存的后端来实现此接口。
     */
    default <K> CheckpointableKeyedStateBackend<K> createKeyedStateBackend(
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
            CloseableRegistry cancelStreamRegistry,
            double managedMemoryFraction)
            throws Exception {

        // ignore managed memory fraction by default
        // 默认忽略托管内存部分
        return createKeyedStateBackend(
                env,
                jobID,
                operatorIdentifier,
                keySerializer,
                numberOfKeyGroups,
                keyGroupRange,
                kvStateRegistry,
                ttlTimeProvider,
                metricGroup,
                stateHandles,
                cancelStreamRegistry);
    }

    /**
     * Creates a new {@link OperatorStateBackend} that can be used for storing operator state.
     * 创建可用于存储操作员状态的新 {@link OperatorStateBackend}。
     *
     * <p>Operator state is state that is associated with parallel operator (or function) instances,
     * rather than with keys.
     * 运算符状态是与并行运算符（或函数）实例相关联的状态，而不是与键相关联的状态。
     *
     * @param env The runtime environment of the executing task.
     * @param operatorIdentifier The identifier of the operator whose state should be stored.
     * @param stateHandles The state handles for restore.
     * @param cancelStreamRegistry The registry to register streams to close if task canceled.
     * @return The OperatorStateBackend for operator identified by the job and operator identifier.
     * @throws Exception This method may forward all exceptions that occur while instantiating the
     *     backend.
     */
    OperatorStateBackend createOperatorStateBackend(
            Environment env,
            String operatorIdentifier,
            @Nonnull Collection<OperatorStateHandle> stateHandles,
            CloseableRegistry cancelStreamRegistry)
            throws Exception;

    /** Whether the state backend uses Flink's managed memory.
     * 状态后端是否使用 Flink 的托管内存。
     * */
    default boolean useManagedMemory() {
        return false;
    }
}
