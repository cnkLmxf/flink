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

import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;

import java.util.Optional;

/**
 * Unknown {@link ShuffleDescriptor} for which the producer has not been deployed yet.
 * 尚未为其部署生产者的未知 {@link ShuffleDescriptor}。
 *
 * <p>When a partition consumer is being scheduled, it can happen that the producer of the partition
 * (consumer input channel) has not been scheduled and its location and other relevant data is yet
 * to be defined. To proceed with the consumer deployment, currently unknown input channels have to
 * be marked with placeholders which are this special implementation of {@link ShuffleDescriptor}.
 * 当分区消费者被调度时，可能会发生分区的生产者（消费者输入通道）尚未被调度并且其位置和其他相关数据尚未定义的情况。
 * 要继续消费者部署，当前未知的输入通道必须用占位符标记，这是 {@link ShuffleDescriptor} 的特殊实现。
 */
public final class UnknownShuffleDescriptor implements ShuffleDescriptor {

    private static final long serialVersionUID = -4001330825983412431L;

    private final ResultPartitionID resultPartitionID;

    public UnknownShuffleDescriptor(ResultPartitionID resultPartitionID) {
        this.resultPartitionID = resultPartitionID;
    }

    @Override
    public ResultPartitionID getResultPartitionID() {
        return resultPartitionID;
    }

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public Optional<ResourceID> storesLocalResourcesOn() {
        return Optional.empty();
    }
}
