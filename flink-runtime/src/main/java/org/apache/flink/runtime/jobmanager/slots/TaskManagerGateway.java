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

package org.apache.flink.runtime.jobmanager.slots;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.deployment.TaskDeploymentDescriptor;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.executiongraph.PartitionInfo;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.flink.runtime.rpc.RpcTimeout;
import org.apache.flink.runtime.taskexecutor.TaskExecutorOperatorEventGateway;
import org.apache.flink.util.SerializedValue;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Task manager gateway interface to communicate with the task manager.
 * 任务管理器网关接口，用于与任务管理器进行通信。
 * */
public interface TaskManagerGateway extends TaskExecutorOperatorEventGateway {

    /**
     * Return the address of the task manager with which the gateway is associated.
     * 返回与网关关联的任务管理器的地址。
     *
     * @return Address of the task manager with which this gateway is associated.
     */
    String getAddress();

    /**
     * Submit a task to the task manager.
     * 向任务管理器提交任务。
     *
     * @param tdd describing the task to submit
     * @param timeout of the submit operation
     * @return Future acknowledge of the successful operation
     */
    CompletableFuture<Acknowledge> submitTask(TaskDeploymentDescriptor tdd, Time timeout);

    /**
     * Cancel the given task.
     *
     * @param executionAttemptID identifying the task
     * @param timeout of the submit operation
     * @return Future acknowledge if the task is successfully canceled
     */
    CompletableFuture<Acknowledge> cancelTask(ExecutionAttemptID executionAttemptID, Time timeout);

    /**
     * Update the task where the given partitions can be found.
     * 更新可以找到给定分区的任务。
     *
     * @param executionAttemptID identifying the task
     * @param partitionInfos telling where the partition can be retrieved from
     * @param timeout of the submit operation
     * @return Future acknowledge if the partitions have been successfully updated
     */
    CompletableFuture<Acknowledge> updatePartitions(
            ExecutionAttemptID executionAttemptID,
            Iterable<PartitionInfo> partitionInfos,
            Time timeout);

    /**
     * Batch release intermediate result partitions.
     * 批量发布中间结果分区。
     *
     * @param jobId id of the job that the partitions belong to
     * @param partitionIds partition ids to release
     */
    void releasePartitions(JobID jobId, Set<ResultPartitionID> partitionIds);

    /**
     * Notify the given task about a completed checkpoint.
     * 通知给定任务有关已完成的检查点。
     *
     * @param executionAttemptID identifying the task
     * @param jobId identifying the job to which the task belongs
     * @param checkpointId of the completed checkpoint
     * @param timestamp of the completed checkpoint
     */
    void notifyCheckpointComplete(
            ExecutionAttemptID executionAttemptID, JobID jobId, long checkpointId, long timestamp);

    /**
     * Notify the given task about a aborted checkpoint.
     * 通知给定任务有关已中止的检查点。
     *
     * @param executionAttemptID identifying the task
     * @param jobId identifying the job to which the task belongs
     * @param checkpointId of the subsumed checkpoint
     * @param timestamp of the subsumed checkpoint
     */
    void notifyCheckpointAborted(
            ExecutionAttemptID executionAttemptID, JobID jobId, long checkpointId, long timestamp);

    /**
     * Trigger for the given task a checkpoint.
     * 触发给定任务的检查点。
     *
     * @param executionAttemptID identifying the task
     * @param jobId identifying the job to which the task belongs
     * @param checkpointId of the checkpoint to trigger
     * @param timestamp of the checkpoint to trigger
     * @param checkpointOptions of the checkpoint to trigger
     */
    void triggerCheckpoint(
            ExecutionAttemptID executionAttemptID,
            JobID jobId,
            long checkpointId,
            long timestamp,
            CheckpointOptions checkpointOptions);

    /**
     * Frees the slot with the given allocation ID.
     * 释放具有给定分配 ID 的槽。
     *
     * @param allocationId identifying the slot to free
     * @param cause of the freeing operation
     * @param timeout for the operation
     * @return Future acknowledge which is returned once the slot has been freed
     */
    CompletableFuture<Acknowledge> freeSlot(
            final AllocationID allocationId, final Throwable cause, @RpcTimeout final Time timeout);

    @Override
    CompletableFuture<Acknowledge> sendOperatorEventToTask(
            ExecutionAttemptID task, OperatorID operator, SerializedValue<OperatorEvent> evt);
}
