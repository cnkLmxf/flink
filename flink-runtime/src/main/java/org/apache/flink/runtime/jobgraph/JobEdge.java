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

package org.apache.flink.runtime.jobgraph;

import org.apache.flink.runtime.io.network.api.writer.SubtaskStateMapper;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * This class represent edges (communication channels) in a job graph. The edges always go from an
 * intermediate result partition to a job vertex. An edge is parametrized with its {@link
 * DistributionPattern}.
 * 此类表示作业图中的边（通信通道）。 边总是从中间结果分区到作业顶点。 一条边使用其 {@link DistributionPattern} 参数化。
 */
public class JobEdge implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** The vertex connected to this edge.
     * 连接到该边的顶点。
     * */
    private final JobVertex target;

    /** The distribution pattern that should be used for this job edge.
     * 应用于此作业边缘的分布模式。
     * */
    private final DistributionPattern distributionPattern;

    /** The channel rescaler that should be used for this job edge on downstream side.
     * 应该用于下游侧的此作业边缘的通道缩放器。
     * */
    private SubtaskStateMapper downstreamSubtaskStateMapper = SubtaskStateMapper.ROUND_ROBIN;

    /** The channel rescaler that should be used for this job edge on upstream side.
     * 应该用于上游侧的此作业边缘的通道缩放器。
     * */
    private SubtaskStateMapper upstreamSubtaskStateMapper = SubtaskStateMapper.ROUND_ROBIN;

    /** The data set at the source of the edge, may be null if the edge is not yet connected
     * 边缘源的数据集，如果边缘尚未连接，则可能为空
     * */
    private IntermediateDataSet source;

    /** The id of the source intermediate data set
     * 源中间数据集的id
     * */
    private IntermediateDataSetID sourceId;

    /**
     * Optional name for the data shipping strategy (forward, partition hash, rebalance, ...), to be
     * displayed in the JSON plan
     * 要在 JSON 计划中显示的数据传输策略的可选名称（转发、分区哈希、重新平衡等）
     */
    private String shipStrategyName;

    /**
     * Optional name for the pre-processing operation (sort, combining sort, ...), to be displayed
     * in the JSON plan要在 JSON 计划中显示的预处理操作（排序、组合排序等）的可选名称
     *
     */
    private String preProcessingOperationName;

    /** Optional description of the caching inside an operator, to be displayed in the JSON plan
     * 运算符内部缓存的可选描述，将显示在 JSON 计划中
     * */
    private String operatorLevelCachingDescription;

    /**
     * Constructs a new job edge, that connects an intermediate result to a consumer task.
     * 构造一个新的工作边缘，将中间结果连接到消费者任务。
     *
     * @param source The data set that is at the source of this edge.
     * @param target The operation that is at the target of this edge.
     * @param distributionPattern The pattern that defines how the connection behaves in parallel.
     */
    public JobEdge(
            IntermediateDataSet source, JobVertex target, DistributionPattern distributionPattern) {
        if (source == null || target == null || distributionPattern == null) {
            throw new NullPointerException();
        }
        this.target = target;
        this.distributionPattern = distributionPattern;
        this.source = source;
        this.sourceId = source.getId();
    }

    /**
     * Constructs a new job edge that refers to an intermediate result via the Id, rather than
     * directly through the intermediate data set structure.
     * 构造一个新的作业边，该边通过 Id 引用中间结果，而不是直接通过中间数据集结构。
     *
     * @param sourceId The id of the data set that is at the source of this edge.
     * @param target The operation that is at the target of this edge.
     * @param distributionPattern The pattern that defines how the connection behaves in parallel.
     */
    public JobEdge(
            IntermediateDataSetID sourceId,
            JobVertex target,
            DistributionPattern distributionPattern) {
        if (sourceId == null || target == null || distributionPattern == null) {
            throw new NullPointerException();
        }
        this.target = target;
        this.distributionPattern = distributionPattern;
        this.sourceId = sourceId;
    }

    /**
     * Returns the data set at the source of the edge. May be null, if the edge refers to the source
     * via an ID and has not been connected.
     * 返回边缘源处的数据集。 如果边缘通过 ID 引用源并且尚未连接，则可能为 null。
     *
     * @return The data set at the source of the edge
     */
    public IntermediateDataSet getSource() {
        return source;
    }

    /**
     * Returns the vertex connected to this edge.
     * 返回连接到该边的顶点。
     *
     * @return The vertex connected to this edge.
     */
    public JobVertex getTarget() {
        return target;
    }

    /**
     * Returns the distribution pattern used for this edge.
     * 返回用于此边的分布模式。
     *
     * @return The distribution pattern used for this edge.
     */
    public DistributionPattern getDistributionPattern() {
        return this.distributionPattern;
    }

    /**
     * Gets the ID of the consumed data set.
     * 获取消费数据集的ID。
     *
     * @return The ID of the consumed data set.
     */
    public IntermediateDataSetID getSourceId() {
        return sourceId;
    }

    public boolean isIdReference() {
        return this.source == null;
    }

    // --------------------------------------------------------------------------------------------

    public void connecDataSet(IntermediateDataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }
        if (this.source != null) {
            throw new IllegalStateException("The edge is already connected.");
        }
        if (!dataSet.getId().equals(sourceId)) {
            throw new IllegalArgumentException(
                    "The data set to connect does not match the sourceId.");
        }

        this.source = dataSet;
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Gets the name of the ship strategy for the represented input, like "forward", "partition
     * hash", "rebalance", "broadcast", ...
     * 获取表示输入的船舶策略的名称，如“转发”、“分区哈希”、“重新平衡”、“广播”等
     * return 表示的输入的运输策略的名称，如果没有设置，则返回 null。
     * @return The name of the ship strategy for the represented input, or null, if none was set.
     */
    public String getShipStrategyName() {
        return shipStrategyName;
    }

    /**
     * Sets the name of the ship strategy for the represented input.
     * 为表示的输入设置运输策略的名称。
     *
     * @param shipStrategyName The name of the ship strategy.
     */
    public void setShipStrategyName(String shipStrategyName) {
        this.shipStrategyName = shipStrategyName;
    }

    /**
     * Gets the channel state rescaler used for rescaling persisted data on downstream side of this
     * JobEdge.
     * 获取用于重新缩放此 JobEdge 下游侧的持久数据的通道状态重新缩放器。
     *
     * @return The channel state rescaler to use, or null, if none was set.
     */
    public SubtaskStateMapper getDownstreamSubtaskStateMapper() {
        return downstreamSubtaskStateMapper;
    }

    /**
     * Sets the channel state rescaler used for rescaling persisted data on downstream side of this
     * JobEdge.
     * 设置用于重新缩放此 JobEdge 下游侧的持久数据的通道状态重新缩放器。
     *
     * @param downstreamSubtaskStateMapper The channel state rescaler selector to use.
     */
    public void setDownstreamSubtaskStateMapper(SubtaskStateMapper downstreamSubtaskStateMapper) {
        this.downstreamSubtaskStateMapper = checkNotNull(downstreamSubtaskStateMapper);
    }

    /**
     * Gets the channel state rescaler used for rescaling persisted data on upstream side of this
     * JobEdge.
     * 获取用于重新缩放此 JobEdge 上游侧的持久数据的通道状态重新缩放器。
     *
     * @return The channel state rescaler to use, or null, if none was set.
     */
    public SubtaskStateMapper getUpstreamSubtaskStateMapper() {
        return upstreamSubtaskStateMapper;
    }

    /**
     * Sets the channel state rescaler used for rescaling persisted data on upstream side of this
     * JobEdge.
     * 设置用于重新缩放此 JobEdge 上游侧的持久数据的通道状态重新缩放器。
     *
     * @param upstreamSubtaskStateMapper The channel state rescaler selector to use.
     */
    public void setUpstreamSubtaskStateMapper(SubtaskStateMapper upstreamSubtaskStateMapper) {
        this.upstreamSubtaskStateMapper = checkNotNull(upstreamSubtaskStateMapper);
    }

    /**
     * Gets the name of the pro-processing operation for this input.
     * 获取此输入的前处理操作的名称。
     *
     * @return The name of the pro-processing operation, or null, if none was set.
     */
    public String getPreProcessingOperationName() {
        return preProcessingOperationName;
    }

    /**
     * Sets the name of the pre-processing operation for this input.
     * 设置此输入的预处理操作的名称。
     *
     * @param preProcessingOperationName The name of the pre-processing operation.
     */
    public void setPreProcessingOperationName(String preProcessingOperationName) {
        this.preProcessingOperationName = preProcessingOperationName;
    }

    /**
     * Gets the operator-level caching description for this input.
     * 获取此输入的操作员级缓存描述。
     *
     * @return The description of operator-level caching, or null, is none was set.
     */
    public String getOperatorLevelCachingDescription() {
        return operatorLevelCachingDescription;
    }

    /**
     * Sets the operator-level caching description for this input.
     * 设置此输入的操作员级缓存描述。
     *
     * @param operatorLevelCachingDescription The description of operator-level caching.
     */
    public void setOperatorLevelCachingDescription(String operatorLevelCachingDescription) {
        this.operatorLevelCachingDescription = operatorLevelCachingDescription;
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("%s --> %s [%s]", sourceId, target, distributionPattern.name());
    }
}
