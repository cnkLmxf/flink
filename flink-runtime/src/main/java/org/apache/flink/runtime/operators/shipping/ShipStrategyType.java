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

package org.apache.flink.runtime.operators.shipping;

/**
 * Enumeration defining the different shipping types of the output, such as local forward,
 * re-partitioning by hash, or re-partitioning by range.
 * 枚举定义输出的不同运输类型，例如本地转发、按哈希重新分区或按范围重新分区。
 */
public enum ShipStrategyType {

    /** Constant used as an indicator for an unassigned ship strategy.
     * 常量用作未分配船舶策略的指标。
     * */
    NONE(false, false),

    /** Forwarding the data locally in memory.
     * 在内存中本地转发数据。
     * */
    FORWARD(false, false),

    /**
     * Repartitioning the data randomly, typically when the parallelism between two nodes changes.
     * 随机重新分区数据，通常是在两个节点之间的并行度发生变化时。
     */
    PARTITION_RANDOM(true, false),

    /** Repartitioning the data deterministically through a hash function.
     * 通过散列函数确定性地重新分区数据。
     * */
    PARTITION_HASH(true, true),

    /** Partitioning the data in ranges according to a total order.
     * 根据总顺序对范围内的数据进行分区。
     * */
    PARTITION_RANGE(true, true),

    /**
     * Partitioning the data evenly, forced at a specific location (cannot be pushed down by
     * optimizer).
     * 对数据进行均匀分区，强制在特定位置（不能被优化器下推）。
     */
    PARTITION_FORCED_REBALANCE(true, false),

    /** Replicating the data set to all instances.
     * 将数据集复制到所有实例。
     * */
    BROADCAST(true, false),

    /** Partitioning using a custom partitioner.
     * 使用自定义分区器进行分区。
     * */
    PARTITION_CUSTOM(true, true);

    // --------------------------------------------------------------------------------------------

    private final boolean isNetwork;

    private final boolean requiresComparator;

    private ShipStrategyType(boolean network, boolean requiresComparator) {
        this.isNetwork = network;
        this.requiresComparator = requiresComparator;
    }

    public boolean isNetworkStrategy() {
        return this.isNetwork;
    }

    public boolean requiresComparator() {
        return this.requiresComparator;
    }
}
