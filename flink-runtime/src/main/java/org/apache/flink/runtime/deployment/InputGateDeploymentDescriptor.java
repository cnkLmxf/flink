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

package org.apache.flink.runtime.deployment;

import org.apache.flink.runtime.io.network.partition.ResultPartitionType;
import org.apache.flink.runtime.io.network.partition.consumer.SingleInputGate;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.runtime.shuffle.ShuffleDescriptor;

import javax.annotation.Nonnegative;

import java.io.Serializable;
import java.util.Arrays;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Deployment descriptor for a single input gate instance.
 * 单个输入门实例的部署描述符。
 *
 * <p>Each input gate consumes partitions of a single intermediate result. The consumed subpartition
 * index is the same for each consumed partition.
 * 每个input gate消耗单个中间结果的分区。 每个消费分区的消费子分区索引都是相同的。
 *
 * @see SingleInputGate
 */
public class InputGateDeploymentDescriptor implements Serializable {

    private static final long serialVersionUID = -7143441863165366704L;
    /**
     * The ID of the consumed intermediate result. Each input gate consumes partitions of the
     * intermediate result specified by this ID. This ID also identifies the input gate at the
     * consuming task.
     * 消费的中间结果的 ID。 每个输入门使用此 ID 指定的中间结果的分区。 此 ID 还标识消费任务的输入门。
     */
    private final IntermediateDataSetID consumedResultId;

    /** The type of the partition the input gate is going to consume.
     * 输入门将要使用的分区类型。
     * */
    private final ResultPartitionType consumedPartitionType;

    /**
     * The index of the consumed subpartition of each consumed partition. This index depends on the
     * {@link DistributionPattern} and the subtask indices of the producing and consuming task.
     * 每个消费分区的消费子分区的索引。 该索引取决于 {@link DistributionPattern} 以及生产和消费任务的子任务索引。
     */
    @Nonnegative private final int consumedSubpartitionIndex;

    /** An input channel for each consumed subpartition.
     * 每个使用的子分区的输入通道。
     * */
    private final ShuffleDescriptor[] inputChannels;

    public InputGateDeploymentDescriptor(
            IntermediateDataSetID consumedResultId,
            ResultPartitionType consumedPartitionType,
            @Nonnegative int consumedSubpartitionIndex,
            ShuffleDescriptor[] inputChannels) {
        this.consumedResultId = checkNotNull(consumedResultId);
        this.consumedPartitionType = checkNotNull(consumedPartitionType);
        this.consumedSubpartitionIndex = consumedSubpartitionIndex;
        this.inputChannels = checkNotNull(inputChannels);
    }

    public IntermediateDataSetID getConsumedResultId() {
        return consumedResultId;
    }

    /**
     * Returns the type of this input channel's consumed result partition.
     * 返回此输入通道的消费结果分区的类型。
     *
     * @return consumed result partition type
     */
    public ResultPartitionType getConsumedPartitionType() {
        return consumedPartitionType;
    }

    @Nonnegative
    public int getConsumedSubpartitionIndex() {
        return consumedSubpartitionIndex;
    }

    public ShuffleDescriptor[] getShuffleDescriptors() {
        return inputChannels;
    }

    @Override
    public String toString() {
        return String.format(
                "InputGateDeploymentDescriptor [result id: %s, "
                        + "consumed subpartition index: %d, input channels: %s]",
                consumedResultId.toString(),
                consumedSubpartitionIndex,
                Arrays.toString(inputChannels));
    }
}
