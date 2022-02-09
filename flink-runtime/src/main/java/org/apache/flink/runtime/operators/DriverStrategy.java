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

package org.apache.flink.runtime.operators;

import org.apache.flink.runtime.operators.chaining.ChainedAllReduceDriver;
import org.apache.flink.runtime.operators.chaining.ChainedDriver;
import org.apache.flink.runtime.operators.chaining.ChainedFlatMapDriver;
import org.apache.flink.runtime.operators.chaining.ChainedMapDriver;
import org.apache.flink.runtime.operators.chaining.ChainedReduceCombineDriver;
import org.apache.flink.runtime.operators.chaining.SynchronousChainedCombineDriver;

import static org.apache.flink.runtime.operators.DamBehavior.FULL_DAM;
import static org.apache.flink.runtime.operators.DamBehavior.MATERIALIZING;
import static org.apache.flink.runtime.operators.DamBehavior.PIPELINED;

/** Enumeration of all available operator strategies.
 * 枚举所有可用的运算符策略。
 * */
public enum DriverStrategy {
    // no local strategy, as for sources and sinks
    NONE(null, null, PIPELINED, 0),
    // a unary no-op operator
    // 一元空操作符
    UNARY_NO_OP(NoOpDriver.class, NoOpChainedDriver.class, PIPELINED, PIPELINED, 0),
    // a binary no-op operator. non implementation available
    // 二元无操作运算符。 不可用
    BINARY_NO_OP(null, null, PIPELINED, PIPELINED, 0),

    // the proper mapper
    // 正确的映射器
    MAP(MapDriver.class, ChainedMapDriver.class, PIPELINED, 0),

    // the proper map partition
    // 正确的地图分区
    MAP_PARTITION(MapPartitionDriver.class, null, PIPELINED, 0),

    // the flat mapper
    //平面映射器
    FLAT_MAP(FlatMapDriver.class, ChainedFlatMapDriver.class, PIPELINED, 0),

    // group everything together into one group and apply the Reduce function
    // 将所有内容归为一组并应用 Reduce 函数
    ALL_REDUCE(AllReduceDriver.class, ChainedAllReduceDriver.class, PIPELINED, 0),
    // group everything together into one group and apply the GroupReduce function
    // 将所有内容归为一组并应用 GroupReduce 函数
    ALL_GROUP_REDUCE(AllGroupReduceDriver.class, null, PIPELINED, 0),
    // group everything together into one group and apply the GroupReduce's combine function
    // 将所有内容归为一组并应用 GroupReduce 的组合功能
    ALL_GROUP_REDUCE_COMBINE(AllGroupReduceDriver.class, null, PIPELINED, 0),

    // grouping the inputs and apply the Reduce Function
    // 对输入进行分组并应用归约函数
    SORTED_REDUCE(ReduceDriver.class, null, PIPELINED, 1),
    // sorted partial reduce is a combiner for the Reduce. same function, but potentially not fully
    // sorted
    // sorted partial reduce 是 Reduce 的组合器。 相同的功能，但可能没有完全排序
    SORTED_PARTIAL_REDUCE(
            ReduceCombineDriver.class, ChainedReduceCombineDriver.class, MATERIALIZING, 1),

    // hashed partial reduce is a combiner for the Reduce
    // hashed partial reduce 是 Reduce 的组合器
    HASHED_PARTIAL_REDUCE(
            ReduceCombineDriver.class, ChainedReduceCombineDriver.class, MATERIALIZING, 1),

    // grouping the inputs and apply the GroupReduce function
    // 对输入进行分组并应用 GroupReduce 函数
    SORTED_GROUP_REDUCE(GroupReduceDriver.class, null, PIPELINED, 1),
    // partially grouping inputs (best effort resulting possibly in duplicates --> combiner)
    // 部分分组输入（尽最大努力可能导致重复 --> 组合器）
    SORTED_GROUP_COMBINE(
            GroupReduceCombineDriver.class,
            SynchronousChainedCombineDriver.class,
            MATERIALIZING,
            2),

    // group combine on all inputs within a partition (without grouping)
    // 对分区内的所有输入进行分组（不分组）
    ALL_GROUP_COMBINE(AllGroupCombineDriver.class, null, PIPELINED, 0),

    // both inputs are merged, but materialized to the side for block-nested-loop-join among values
    // with equal key
    // 两个输入都被合并，但在具有相同键的值之间实现块嵌套循环连接
    INNER_MERGE(JoinDriver.class, null, MATERIALIZING, MATERIALIZING, 2),
    LEFT_OUTER_MERGE(LeftOuterJoinDriver.class, null, MATERIALIZING, MATERIALIZING, 2),
    RIGHT_OUTER_MERGE(RightOuterJoinDriver.class, null, MATERIALIZING, MATERIALIZING, 2),
    FULL_OUTER_MERGE(FullOuterJoinDriver.class, null, MATERIALIZING, MATERIALIZING, 2),

    // co-grouping inputs
    CO_GROUP(CoGroupDriver.class, null, PIPELINED, PIPELINED, 2),
    // python-cogroup
    CO_GROUP_RAW(CoGroupRawDriver.class, null, PIPELINED, PIPELINED, 0),

    // the first input is build side, the second side is probe side of a hybrid hash table
    // 第一个输入是构建端，第二个是混合哈希表的探测端
    HYBRIDHASH_BUILD_FIRST(JoinDriver.class, null, FULL_DAM, MATERIALIZING, 2),
    // the second input is build side, the first side is probe side of a hybrid hash table
    // 第二个输入是构建端，第一个是混合哈希表的探测端
    HYBRIDHASH_BUILD_SECOND(JoinDriver.class, null, MATERIALIZING, FULL_DAM, 2),
    // a cached variant of HYBRIDHASH_BUILD_FIRST, that can only be used inside of iterations
    // HYBRIDHASH_BUILD_FIRST 的缓存变体，只能在迭代内部使用
    HYBRIDHASH_BUILD_FIRST_CACHED(
            BuildFirstCachedJoinDriver.class, null, FULL_DAM, MATERIALIZING, 2),
    //  cached variant of HYBRIDHASH_BUILD_SECOND, that can only be used inside of iterations
    // HYBRIDHASH_BUILD_SECOND 的缓存变体，只能在迭代内部使用
    HYBRIDHASH_BUILD_SECOND_CACHED(
            BuildSecondCachedJoinDriver.class, null, MATERIALIZING, FULL_DAM, 2),

    // right outer join, the first input is build side, the second input is probe side of a hybrid
    // hash table.
    // 右外连接，第一个输入是构建端，第二个输入是混合哈希表的探测端。
    RIGHT_HYBRIDHASH_BUILD_FIRST(RightOuterJoinDriver.class, null, FULL_DAM, MATERIALIZING, 2),
    // right outer join, the first input is probe side, the second input is build side of a hybrid
    // hash table.
    // 右外连接，第一个输入是探测端，第二个输入是混合哈希表的构建端。
    RIGHT_HYBRIDHASH_BUILD_SECOND(RightOuterJoinDriver.class, null, FULL_DAM, MATERIALIZING, 2),
    // left outer join, the first input is build side, the second input is probe side of a hybrid
    // hash table.
    //左外连接，第一个输入是构建端，第二个输入是混合哈希表的探测端。
    LEFT_HYBRIDHASH_BUILD_FIRST(LeftOuterJoinDriver.class, null, MATERIALIZING, FULL_DAM, 2),
    // left outer join, the first input is probe side, the second input is build side of a hybrid
    // hash table.
    //左外连接，第一个输入是探测端，第二个输入是混合哈希表的构建端。
    LEFT_HYBRIDHASH_BUILD_SECOND(LeftOuterJoinDriver.class, null, MATERIALIZING, FULL_DAM, 2),
    // full outer join, the first input is build side, the second input is the probe side of a
    // hybrid hash table.
    // 全外连接，第一个输入是构建端，第二个输入是混合哈希表的探测端。
    FULL_OUTER_HYBRIDHASH_BUILD_FIRST(FullOuterJoinDriver.class, null, FULL_DAM, MATERIALIZING, 2),
    // full outer join, the first input is probe side, the second input is the build side of a
    // hybrid hash table.
    // 全外连接，第一个输入是探测端，第二个输入是混合哈希表的构建端。
    FULL_OUTER_HYBRIDHASH_BUILD_SECOND(FullOuterJoinDriver.class, null, MATERIALIZING, FULL_DAM, 2),

    // the second input is inner loop, the first input is outer loop and block-wise processed
    // 第二个输入是内循环，第一个输入是外循环并逐块处理
    NESTEDLOOP_BLOCKED_OUTER_FIRST(CrossDriver.class, null, MATERIALIZING, FULL_DAM, 0),
    // the first input is inner loop, the second input is outer loop and block-wise processed
    // 第一个输入是内循环，第二个输入是外循环并逐块处理
    NESTEDLOOP_BLOCKED_OUTER_SECOND(CrossDriver.class, null, FULL_DAM, MATERIALIZING, 0),
    // the second input is inner loop, the first input is outer loop and stream-processed
    // 第二个输入是内循环，第一个输入是外循环和流处理
    NESTEDLOOP_STREAMED_OUTER_FIRST(CrossDriver.class, null, PIPELINED, FULL_DAM, 0),
    // the first input is inner loop, the second input is outer loop and stream-processed
    // 第一个输入是内循环，第二个输入是外循环并经过流处理
    NESTEDLOOP_STREAMED_OUTER_SECOND(CrossDriver.class, null, FULL_DAM, PIPELINED, 0),

    // union utility op. unions happen implicitly on the network layer (in the readers) when
    // bundling streams
    //联合实用程序操作。 捆绑流时，联合隐式发生在网络层（在阅读器中）
    UNION(null, null, PIPELINED, PIPELINED, 0),
    // explicit binary union between a streamed and a cached input
    // 流式输入和缓存输入之间的显式二进制联合
    UNION_WITH_CACHED(UnionWithTempOperator.class, null, FULL_DAM, PIPELINED, 0),

    // some enumeration constants to mark sources and sinks
    // 一些枚举常量来标记源和汇
    SOURCE(null, null, PIPELINED, 0),
    SINK(null, null, PIPELINED, 0);

    // --------------------------------------------------------------------------------------------

    private final Class<? extends Driver<?, ?>> driverClass;

    private final Class<? extends ChainedDriver<?, ?>> pushChainDriver;

    private final DamBehavior dam1;
    private final DamBehavior dam2;

    private final int numInputs;

    private final int numRequiredComparators;

    @SuppressWarnings("unchecked")
    private DriverStrategy(
            @SuppressWarnings("rawtypes") Class<? extends Driver> driverClass,
            @SuppressWarnings("rawtypes") Class<? extends ChainedDriver> pushChainDriverClass,
            DamBehavior dam,
            int numComparator) {
        this.driverClass = (Class<? extends Driver<?, ?>>) driverClass;
        this.pushChainDriver = (Class<? extends ChainedDriver<?, ?>>) pushChainDriverClass;
        this.numInputs = 1;
        this.dam1 = dam;
        this.dam2 = null;
        this.numRequiredComparators = numComparator;
    }

    @SuppressWarnings("unchecked")
    private DriverStrategy(
            @SuppressWarnings("rawtypes") Class<? extends Driver> driverClass,
            @SuppressWarnings("rawtypes") Class<? extends ChainedDriver> pushChainDriverClass,
            DamBehavior firstDam,
            DamBehavior secondDam,
            int numComparator) {
        this.driverClass = (Class<? extends Driver<?, ?>>) driverClass;
        this.pushChainDriver = (Class<? extends ChainedDriver<?, ?>>) pushChainDriverClass;
        this.numInputs = 2;
        this.dam1 = firstDam;
        this.dam2 = secondDam;
        this.numRequiredComparators = numComparator;
    }

    // --------------------------------------------------------------------------------------------

    public Class<? extends Driver<?, ?>> getDriverClass() {
        return this.driverClass;
    }

    public Class<? extends ChainedDriver<?, ?>> getPushChainDriverClass() {
        return this.pushChainDriver;
    }

    public int getNumInputs() {
        return this.numInputs;
    }

    public DamBehavior firstDam() {
        return this.dam1;
    }

    public DamBehavior secondDam() {
        if (this.numInputs == 2) {
            return this.dam2;
        } else {
            throw new IllegalArgumentException("The given strategy does not work on two inputs.");
        }
    }

    public DamBehavior damOnInput(int num) {
        if (num < this.numInputs) {
            if (num == 0) {
                return this.dam1;
            } else if (num == 1) {
                return this.dam2;
            }
        }
        throw new IllegalArgumentException();
    }

    public boolean isMaterializing() {
        return this.dam1.isMaterializing() || (this.dam2 != null && this.dam2.isMaterializing());
    }

    public int getNumRequiredComparators() {
        return this.numRequiredComparators;
    }
}
