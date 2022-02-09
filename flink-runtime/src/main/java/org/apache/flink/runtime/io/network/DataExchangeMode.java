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

package org.apache.flink.runtime.io.network;

import org.apache.flink.api.common.ExecutionMode;
import org.apache.flink.runtime.operators.shipping.ShipStrategyType;

/** Defines how the data exchange between two specific operators happens.
 * 定义两个特定运算符之间的数据交换如何发生。
 * */
public enum DataExchangeMode {

    /**
     * The data exchange is streamed, sender and receiver are online at the same time, and the
     * receiver back-pressures the sender.
     * 数据交换是流式传输的，发送方和接收方同时在线，接收方反压发送方。
     */
    PIPELINED,

    /**
     * The data exchange is decoupled. The sender first produces its entire result and finishes.
     * After that, the receiver is started and may consume the data.
     * 数据交换是解耦的。 发送者首先产生其整个结果并完成。 之后，接收器启动并可以使用数据。
     */
    BATCH,

    /**
     * The data exchange starts like in {@link #PIPELINED} and falls back to {@link #BATCH} for
     * recovery runs.
     * 数据交换像在 {@link #PIPELINED} 中一样开始，然后回退到 {@link #BATCH} 以进行恢复运行。
     */
    PIPELINE_WITH_BATCH_FALLBACK;

    // ------------------------------------------------------------------------

    public static DataExchangeMode getForForwardExchange(ExecutionMode mode) {
        return FORWARD[mode.ordinal()];
    }

    public static DataExchangeMode getForShuffleOrBroadcast(ExecutionMode mode) {
        return SHUFFLE[mode.ordinal()];
    }

    public static DataExchangeMode getPipelineBreakingExchange(ExecutionMode mode) {
        return BREAKING[mode.ordinal()];
    }

    /**
     * Computes the mode of data exchange to be used for a given execution mode and ship strategy.
     * The type of the data exchange depends also on whether this connection has been identified to
     * require pipeline breaking for deadlock avoidance.
     * 计算用于给定执行模式和运输策略的数据交换模式。
     * 数据交换的类型还取决于此连接是否已被识别为需要中断管道以避免死锁。
     *<ul>
     *     <li>如果连接设置为管道中断，则返回执行模式 {@link org.apache.flink.runtime.io.network.DataExchangeMode#getPipelineBreakingExchange(org.apache.flink.api . common.ExecutionMode)}。
     *     <li>如果数据交换是简单的 FORWARD（一对一通信），则返回 {@link org.apache.flink.runtime.io.network.DataExchangeMode#getForForwardExchange(org.apache.flink.api.common .ExecutionMode)}。
     *     <li>否则，返回 {@link org.apache.flink.runtime.io.network.DataExchangeMode#getForShuffleOrBroadcast(org.apache.flink.api.common.ExecutionMode)}。
     * </ul>
     * <ul>
     *   <li>If the connection is set to be pipeline breaking, this returns the pipeline breaking
     *       variant of the execution mode {@link
     *       org.apache.flink.runtime.io.network.DataExchangeMode#getPipelineBreakingExchange(org.apache.flink.api.common.ExecutionMode)}.
     *   <li>If the data exchange is a simple FORWARD (one-to-one communication), this returns
     *       {@link
     *       org.apache.flink.runtime.io.network.DataExchangeMode#getForForwardExchange(org.apache.flink.api.common.ExecutionMode)}.
     *   <li>If otherwise, this returns {@link
     *       org.apache.flink.runtime.io.network.DataExchangeMode#getForShuffleOrBroadcast(org.apache.flink.api.common.ExecutionMode)}.
     * </ul>
     *
     * @param shipStrategy The ship strategy (FORWARD, PARTITION, BROADCAST, ...) of the runtime
     *     data exchange.
     * @return The data exchange mode for the connection, given the concrete ship strategy.
     */
    public static DataExchangeMode select(
            ExecutionMode executionMode, ShipStrategyType shipStrategy, boolean breakPipeline) {

        if (shipStrategy == null || shipStrategy == ShipStrategyType.NONE) {
            throw new IllegalArgumentException("shipStrategy may not be null or NONE");
        }
        if (executionMode == null) {
            throw new IllegalArgumentException("executionMode may not mbe null");
        }

        if (breakPipeline) {
            return getPipelineBreakingExchange(executionMode);
        } else if (shipStrategy == ShipStrategyType.FORWARD) {
            return getForForwardExchange(executionMode);
        } else {
            return getForShuffleOrBroadcast(executionMode);
        }
    }

    // ------------------------------------------------------------------------

    private static final DataExchangeMode[] FORWARD =
            new DataExchangeMode[ExecutionMode.values().length];

    private static final DataExchangeMode[] SHUFFLE =
            new DataExchangeMode[ExecutionMode.values().length];

    private static final DataExchangeMode[] BREAKING =
            new DataExchangeMode[ExecutionMode.values().length];

    // initialize the map between execution modes and exchange modes in
    // 初始化执行模式和交换模式之间的映射
    static {
        FORWARD[ExecutionMode.PIPELINED_FORCED.ordinal()] = PIPELINED;
        SHUFFLE[ExecutionMode.PIPELINED_FORCED.ordinal()] = PIPELINED;
        BREAKING[ExecutionMode.PIPELINED_FORCED.ordinal()] = PIPELINED;

        FORWARD[ExecutionMode.PIPELINED.ordinal()] = PIPELINED;
        SHUFFLE[ExecutionMode.PIPELINED.ordinal()] = PIPELINED;
        BREAKING[ExecutionMode.PIPELINED.ordinal()] = BATCH;

        FORWARD[ExecutionMode.BATCH.ordinal()] = PIPELINED;
        SHUFFLE[ExecutionMode.BATCH.ordinal()] = BATCH;
        BREAKING[ExecutionMode.BATCH.ordinal()] = BATCH;

        FORWARD[ExecutionMode.BATCH_FORCED.ordinal()] = BATCH;
        SHUFFLE[ExecutionMode.BATCH_FORCED.ordinal()] = BATCH;
        BREAKING[ExecutionMode.BATCH_FORCED.ordinal()] = BATCH;
    }
}
