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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.types.Either;

import java.util.function.Consumer;

/** Request execution state of partition producer, the response accepts state check callbacks.
 * 请求分区生产者的执行状态，响应接受状态检查回调。
 * */
public interface PartitionProducerStateProvider {
    /**
     * Trigger the producer execution state request.
     * 触发生产者执行状态请求。
     *
     * @param intermediateDataSetId ID of the parent intermediate data set.
     * @param resultPartitionId ID of the result partition to check. This identifies the producing
     *     execution and partition.
     * @param responseConsumer consumer for the response handle.
     */
    void requestPartitionProducerState(
            IntermediateDataSetID intermediateDataSetId,
            ResultPartitionID resultPartitionId,
            Consumer<? super ResponseHandle> responseConsumer);

    /** Result of state query, accepts state check callbacks.
     * 状态查询结果，接受状态检查回调。
     * */
    interface ResponseHandle {
        ExecutionState getConsumerExecutionState();

        Either<ExecutionState, Throwable> getProducerExecutionState();

        /** Cancel the partition consumptions as a result of state check.
         * 作为状态检查的结果取消分区消耗。
         * */
        void cancelConsumption();

        /**
         * Fail the partition consumptions as a result of state check.
         * 由于状态检查，分区消耗失败。
         *
         * @param cause failure cause
         */
        void failConsumption(Throwable cause);
    }
}
