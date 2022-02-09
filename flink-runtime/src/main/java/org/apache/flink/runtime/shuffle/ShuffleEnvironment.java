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

package org.apache.flink.runtime.shuffle;

import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.deployment.InputGateDeploymentDescriptor;
import org.apache.flink.runtime.deployment.ResultPartitionDeploymentDescriptor;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.executiongraph.PartitionInfo;
import org.apache.flink.runtime.io.network.api.writer.ResultPartitionWriter;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.PartitionProducerStateProvider;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.io.network.partition.consumer.IndexedInputGate;
import org.apache.flink.runtime.io.network.partition.consumer.InputGate;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Interface for the implementation of shuffle service local environment.
 * 用于实现 shuffle 服务本地环境的接口。
 *
 * <p>Input/Output interface of local shuffle service environment is based on memory {@link Buffer
 * Buffers}. A producer can write shuffle data into the buffers, obtained from the created {@link
 * ResultPartitionWriter ResultPartitionWriters} and a consumer reads the buffers from the created
 * {@link InputGate InputGates}.
 * 本地 shuffle 服务环境的 Input/Output 接口基于内存 {@link Buffer Buffers}。
 * 生产者可以将 shuffle 数据写入缓冲区，从创建的 {@link ResultPartitionWriter ResultPartitionWriters} 获得，
 * 消费者从创建的 {@link InputGate InputGates} 读取缓冲区。
 *
 * <h1>Lifecycle management.</h1>
 * 生命周期管理。
 *
 * <p>The interface contains method's to manage the lifecycle of the local shuffle service
 * environment:
 * 该接口包含管理本地 shuffle 服务环境生命周期的方法：
 *<ol>
 *     <li>{@link ShuffleEnvironment#start}必须在使用shuffle服务环境之前调用。
 *     <li>调用{@link ShuffleEnvironment#close}释放shuffle服务环境。
 *</ol>
 * <ol>
 *   <li>{@link ShuffleEnvironment#start} must be called before using the shuffle service
 *       environment.
 *   <li>{@link ShuffleEnvironment#close} is called to release the shuffle service environment.
 * </ol>
 *
 * <h1>Shuffle Input/Output management.</h1>
 * 随机输入/输出管理。
 *
 * <h2>Result partition management.</h2>
 * 结果分区管理。
 *
 * <p>The interface implements a factory of result partition writers to produce shuffle data: {@link
 * ShuffleEnvironment#createResultPartitionWriters}. The created writers are grouped per owner. The
 * owner is responsible for the writers' lifecycle from the moment of creation.
 * 该接口实现了结果分区写入器的工厂以生成随机数据：{@link ShuffleEnvironment#createResultPartitionWriters}。
 * 创建的作者按所有者分组。 所有者从创作的那一刻起对作家的生命周期负责。
 *
 * <p>Partitions are fully released in the following cases:
 * 在以下情况下完全释放分区：
 *<ol>
 *     <li>{@link ResultPartitionWriter#fail(Throwable)} 和 {@link ResultPartitionWriter#close()} 在生产失败时被调用。
 *     <li>对于 PIPELINED 分区，如果检测到消费尝试并且在有界生产完成后失败或完成
 *     （已调用 {@link ResultPartitionWriter#finish()} 和 {@link ResultPartitionWriter#close()}） .
 *     PIPELINED 分区目前只需要一次消费尝试，因此可以在之后释放它。
 *     <li>如果在生产者线程之外调用以下方法：
 *         <ol>
 *           <li>{@link ShuffleMaster#releasePartitionExternally(ShuffleDescriptor)}
 *           <li>如果它占用了任何生产者本地资源 ({@link ShuffleDescriptor#storesLocalResourcesOn()})，
 *           那么 {@link ShuffleEnvironment#releasePartitionsLocally(Collection)}
 *         </ol>
 *         例如管理 BLOCKING 结果分区的生命周期，这些分区的生命周期可能超过其生产者。 BLOCKING 分区可以被多次使用。
 * </ol>
 * <ol>
 *   <li>{@link ResultPartitionWriter#fail(Throwable)} and {@link ResultPartitionWriter#close()} are
 *       called if the production has failed.
 *   <li>for PIPELINED partitions if there was a detected consumption attempt and it either failed
 *       or finished after the bounded production has been done ({@link
 *       ResultPartitionWriter#finish()} and {@link ResultPartitionWriter#close()} have been
 *       called). Only one consumption attempt is ever expected for the PIPELINED partition at the
 *       moment so it can be released afterwards.
 *   <li>if the following methods are called outside of the producer thread:
 *       <ol>
 *         <li>{@link ShuffleMaster#releasePartitionExternally(ShuffleDescriptor)}
 *         <li>and if it occupies any producer local resources ({@link
 *             ShuffleDescriptor#storesLocalResourcesOn()}) then also {@link
 *             ShuffleEnvironment#releasePartitionsLocally(Collection)}
 *       </ol>
 *       e.g. to manage the lifecycle of BLOCKING result partitions which can outlive their
 *       producers. The BLOCKING partitions can be consumed multiple times.
 * </ol>
 *
 * <p>The partitions, which currently still occupy local resources, can be queried with {@link
 * ShuffleEnvironment#getPartitionsOccupyingLocalResources}.
 * 可以通过 {@link ShuffleEnvironment#getPartitionsOccupyingLocalResources} 查询当前仍占用本地资源的分区。
 *
 * <h2>Input gate management.</h2>
 * 输入门管理。
 *
 * <p>The interface implements a factory for the input gates: {@link
 * ShuffleEnvironment#createInputGates}. The created gates are grouped per owner. The owner is
 * responsible for the gates' lifecycle from the moment of creation.
 * 该接口为input gates实现了一个工厂：{@link ShuffleEnvironment#createInputGates}。
 * 创建的门按所有者分组。 所有者从创建之日起就负责大门的生命周期。
 *
 * <p>When the input gates are created, it can happen that not all consumed partitions are known at
 * that moment e.g. because their producers have not been started yet. Therefore, the {@link
 * ShuffleEnvironment} provides a method {@link ShuffleEnvironment#updatePartitionInfo} to update
 * them externally, when the producer becomes known. The update mechanism has to be threadsafe
 * because the updated gate can be read concurrently from a different thread.
 * 创建input gates时，可能会发生当时并非所有消耗的分区都是已知的，例如 因为他们的生产者还没有开始。
 * 因此，{@link ShuffleEnvironment} 提供了一个方法 {@link ShuffleEnvironment#updatePartitionInfo}
 * 来在生产者已知时从外部更新它们。 更新机制必须是线程安全的，因为更新的门可以从不同的线程并发读取。
 *
 * @param <P> type of provided result partition writers
 * @param <G> type of provided input gates
 */
public interface ShuffleEnvironment<P extends ResultPartitionWriter, G extends IndexedInputGate>
        extends AutoCloseable {

    /**
     * Start the internal related services before using the shuffle service environment.
     * 在使用shuffle服务环境之前启动内部相关服务。
     *
     * @return a port to connect for the shuffle data exchange, -1 if only local connection is
     *     possible.
     */
    int start() throws IOException;

    /**
     * Create a context of the shuffle input/output owner used to create partitions or gates
     * belonging to the owner.
     * 创建 shuffle 输入/输出所有者的上下文，用于创建属于所有者的分区或门。
     *
     * <p>This method has to be called only once to avoid duplicated internal metric group
     * registration.
     * 此方法必须只调用一次以避免重复的内部度量组注册。
     *
     * @param ownerName the owner name, used for logs
     * @param executionAttemptID execution attempt id of the producer or consumer
     * @param parentGroup parent of shuffle specific metric group
     * @return context of the shuffle input/output owner used to create partitions or gates
     *     belonging to the owner
     */
    ShuffleIOOwnerContext createShuffleIOOwnerContext(
            String ownerName, ExecutionAttemptID executionAttemptID, MetricGroup parentGroup);

    /**
     * Factory method for the {@link ResultPartitionWriter ResultPartitionWriters} to produce result
     * partitions.
     * {@link ResultPartitionWriter ResultPartitionWriters} 生成结果分区的工厂方法。
     *
     * <p>The order of the {@link ResultPartitionWriter ResultPartitionWriters} in the returned
     * collection should be the same as the iteration order of the passed {@code
     * resultPartitionDeploymentDescriptors}.
     * 返回集合中的 {@link ResultPartitionWriter ResultPartitionWriters}
     * 的顺序应与传递的 {@code resultPartitionDeploymentDescriptors} 的迭代顺序相同。
     *
     * @param ownerContext the owner context relevant for partition creation
     * @param resultPartitionDeploymentDescriptors descriptors of the partition, produced by the
     *     owner
     * @return list of the {@link ResultPartitionWriter ResultPartitionWriters}
     */
    List<P> createResultPartitionWriters(
            ShuffleIOOwnerContext ownerContext,
            List<ResultPartitionDeploymentDescriptor> resultPartitionDeploymentDescriptors);

    /**
     * Release local resources occupied by the given partitions.
     * 释放给定分区占用的本地资源。
     *
     * <p>This is called for partitions which occupy resources locally (can be checked by {@link
     * ShuffleDescriptor#storesLocalResourcesOn()}).
     * 这用于在本地占用资源的分区（可以通过 {@link ShuffleDescriptor#storesLocalResourcesOn()} 检查）。
     *
     * @param partitionIds identifying the partitions to be released
     */
    void releasePartitionsLocally(Collection<ResultPartitionID> partitionIds);

    /**
     * Report partitions which still occupy some resources locally.
     * 报告在本地仍占用部分资源的分区。
     *
     * @return collection of partitions which still occupy some resources locally and have not been
     *     released yet.
     */
    Collection<ResultPartitionID> getPartitionsOccupyingLocalResources();

    /**
     * Factory method for the {@link InputGate InputGates} to consume result partitions.
     * {@link InputGate InputGates} 使用结果分区的工厂方法。
     *
     * <p>The order of the {@link InputGate InputGates} in the returned collection should be the
     * same as the iteration order of the passed {@code inputGateDeploymentDescriptors}.
     * 返回集合中的 {@link InputGate InputGates} 的顺序应与传递的
     * {@code inputGateDeploymentDescriptors} 的迭代顺序相同。
     *
     * @param ownerContext the owner context relevant for gate creation
     * @param partitionProducerStateProvider producer state provider to query whether the producer
     *     is ready for consumption
     * @param inputGateDeploymentDescriptors descriptors of the input gates to consume
     * @return list of the {@link InputGate InputGates}
     */
    List<G> createInputGates(
            ShuffleIOOwnerContext ownerContext,
            PartitionProducerStateProvider partitionProducerStateProvider,
            List<InputGateDeploymentDescriptor> inputGateDeploymentDescriptors);

    /**
     * Update a gate with the newly available partition information, previously unknown.
     * 使用以前未知的新可用分区信息更新gates。
     *
     * @param consumerID execution id to distinguish gates with the same id from the different
     *     consumer executions
     * @param partitionInfo information needed to consume the updated partition, e.g. network
     *     location
     * @return {@code true} if the partition has been updated or {@code false} if the partition is
     *     not available anymore.
     * @throws IOException IO problem by the update
     * @throws InterruptedException potentially blocking operation was interrupted
     */
    boolean updatePartitionInfo(ExecutionAttemptID consumerID, PartitionInfo partitionInfo)
            throws IOException, InterruptedException;
}
