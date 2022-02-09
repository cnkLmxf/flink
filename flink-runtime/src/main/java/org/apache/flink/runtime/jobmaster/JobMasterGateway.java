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

package org.apache.flink.runtime.jobmaster;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.checkpoint.CheckpointCoordinatorGateway;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.executiongraph.ExecutionJobVertex;
import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.io.network.partition.ResultPartition;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.messages.webmonitor.JobDetails;
import org.apache.flink.runtime.operators.coordination.CoordinationRequest;
import org.apache.flink.runtime.operators.coordination.CoordinationResponse;
import org.apache.flink.runtime.registration.RegistrationResponse;
import org.apache.flink.runtime.resourcemanager.ResourceManagerId;
import org.apache.flink.runtime.rpc.FencedRpcGateway;
import org.apache.flink.runtime.rpc.RpcTimeout;
import org.apache.flink.runtime.scheduler.ExecutionGraphInfo;
import org.apache.flink.runtime.slots.ResourceRequirement;
import org.apache.flink.runtime.taskexecutor.TaskExecutorToJobManagerHeartbeatPayload;
import org.apache.flink.runtime.taskexecutor.slot.SlotOffer;
import org.apache.flink.runtime.taskmanager.TaskExecutionState;
import org.apache.flink.runtime.taskmanager.UnresolvedTaskManagerLocation;
import org.apache.flink.util.SerializedValue;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** {@link JobMaster} rpc gateway interface.
 * {@link JobMaster} rpc 网关接口。
 * */
public interface JobMasterGateway
        extends CheckpointCoordinatorGateway,
                FencedRpcGateway<JobMasterId>,
                KvStateLocationOracle,
                KvStateRegistryGateway,
                JobMasterOperatorEventGateway {

    /**
     * Cancels the currently executed job.
     * 取消当前执行的作业。
     *
     * @param timeout of this operation
     * @return Future acknowledge of the operation
     */
    CompletableFuture<Acknowledge> cancel(@RpcTimeout Time timeout);

    /**
     * Updates the task execution state for a given task.
     * 更新给定任务的任务执行状态。
     *
     * @param taskExecutionState New task execution state for a given task
     * @return Future flag of the task execution state update result
     */
    CompletableFuture<Acknowledge> updateTaskExecutionState(
            final TaskExecutionState taskExecutionState);

    /**
     * Requests the next input split for the {@link ExecutionJobVertex}. The next input split is
     * sent back to the sender as a {@link SerializedInputSplit} message.
     * 请求 {@link ExecutionJobVertex} 的下一个输入拆分。
     * 下一个输入拆分作为 {@link SerializedInputSplit} 消息发送回发送者。
     *
     * @param vertexID The job vertex id
     * @param executionAttempt The execution attempt id
     * @return The future of the input split. If there is no further input split, will return an
     *     empty object.
     */
    CompletableFuture<SerializedInputSplit> requestNextInputSplit(
            final JobVertexID vertexID, final ExecutionAttemptID executionAttempt);

    /**
     * Requests the current state of the partition. The state of a partition is currently bound to
     * the state of the producing execution.
     * 请求分区的当前状态。 分区的状态当前绑定到生产执行的状态。
     *
     * @param intermediateResultId The execution attempt ID of the task requesting the partition
     *     state.
     * @param partitionId The partition ID of the partition to request the state of.
     * @return The future of the partition state
     */
    CompletableFuture<ExecutionState> requestPartitionState(
            final IntermediateDataSetID intermediateResultId, final ResultPartitionID partitionId);

    /**
     * Notifies the JobManager about available data for a produced partition.
     * 通知 JobManager 有关已生成分区的可用数据。
     *
     * <p>There is a call to this method for each {@link ExecutionVertex} instance once per produced
     * {@link ResultPartition} instance, either when first producing data (for pipelined executions)
     * or when all data has been produced (for staged executions).
     * 每个产生的 {@link ResultPartition} 实例都会对每个 {@link ExecutionVertex} 实例调用此方法一次，
     * 无论是在第一次产生数据时（对于流水线执行）还是在所有数据都已产生时（对于分阶段执行）。
     *
     * <p>The JobManager then can decide when to schedule the partition consumers of the given
     * session.
     * JobManager 然后可以决定何时调度给定会话的分区消费者。
     *
     * @param partitionID The partition which has already produced data
     * @param timeout before the rpc call fails
     * @return Future acknowledge of the notification
     */
    CompletableFuture<Acknowledge> notifyPartitionDataAvailable(
            final ResultPartitionID partitionID, @RpcTimeout final Time timeout);

    /**
     * Disconnects the given {@link org.apache.flink.runtime.taskexecutor.TaskExecutor} from the
     * {@link JobMaster}.
     * 断开给定的 {@link org.apache.flink.runtime.taskexecutor.TaskExecutor} 与 {@link JobMaster} 的连接。
     *
     * @param resourceID identifying the TaskManager to disconnect
     * @param cause for the disconnection of the TaskManager
     * @return Future acknowledge once the JobMaster has been disconnected from the TaskManager
     */
    CompletableFuture<Acknowledge> disconnectTaskManager(ResourceID resourceID, Exception cause);

    /**
     * Disconnects the resource manager from the job manager because of the given cause.
     * 由于给定原因，断开资源管理器与作业管理器的连接。
     *
     * @param resourceManagerId identifying the resource manager leader id
     * @param cause of the disconnect
     */
    void disconnectResourceManager(
            final ResourceManagerId resourceManagerId, final Exception cause);

    /**
     * Offers the given slots to the job manager. The response contains the set of accepted slots.
     * 将给定的插槽提供给作业管理器。 响应包含一组接受的插槽。
     *
     * @param taskManagerId identifying the task manager
     * @param slots to offer to the job manager
     * @param timeout for the rpc call
     * @return Future set of accepted slots.
     */
    CompletableFuture<Collection<SlotOffer>> offerSlots(
            final ResourceID taskManagerId,
            final Collection<SlotOffer> slots,
            @RpcTimeout final Time timeout);

    /**
     * Fails the slot with the given allocation id and cause.
     * 使用给定的分配 id 和原因使槽失败。
     *
     * @param taskManagerId identifying the task manager
     * @param allocationId identifying the slot to fail
     * @param cause of the failing
     */
    void failSlot(
            final ResourceID taskManagerId, final AllocationID allocationId, final Exception cause);

    /**
     * Registers the task manager at the job manager.
     * 在作业管理器中注册任务管理器。
     *
     * @param taskManagerRpcAddress the rpc address of the task manager
     * @param unresolvedTaskManagerLocation unresolved location of the task manager
     * @param jobId jobId specifying the job for which the JobMaster should be responsible
     * @param timeout for the rpc call
     * @return Future registration response indicating whether the registration was successful or
     *     not
     */
    CompletableFuture<RegistrationResponse> registerTaskManager(
            final String taskManagerRpcAddress,
            final UnresolvedTaskManagerLocation unresolvedTaskManagerLocation,
            final JobID jobId,
            @RpcTimeout final Time timeout);

    /**
     * Sends the heartbeat to job manager from task manager.
     * 将心跳从任务管理器发送到作业管理器。
     *
     * @param resourceID unique id of the task manager
     * @param payload report payload
     */
    void heartbeatFromTaskManager(
            final ResourceID resourceID, final TaskExecutorToJobManagerHeartbeatPayload payload);

    /**
     * Sends heartbeat request from the resource manager.
     * 从资源管理器发送心跳请求。
     *
     * @param resourceID unique id of the resource manager
     */
    void heartbeatFromResourceManager(final ResourceID resourceID);

    /**
     * Request the details of the executed job.
     * 请求执行作业的详细信息。
     *
     * @param timeout for the rpc call
     * @return Future details of the executed job
     */
    CompletableFuture<JobDetails> requestJobDetails(@RpcTimeout Time timeout);

    /**
     * Requests the current job status.
     * 请求当前作业状态。
     *
     * @param timeout for the rpc call
     * @return Future containing the current job status
     */
    CompletableFuture<JobStatus> requestJobStatus(@RpcTimeout Time timeout);

    /**
     * Requests the {@link ExecutionGraphInfo} of the executed job.
     * 请求已执行作业的 {@link ExecutionGraphInfo}。
     *
     * @param timeout for the rpc call
     * @return Future which is completed with the {@link ExecutionGraphInfo} of the executed job
     */
    CompletableFuture<ExecutionGraphInfo> requestJob(@RpcTimeout Time timeout);

    /**
     * Triggers taking a savepoint of the executed job.
     * 触发器获取已执行作业的保存点。
     *
     * @param targetDirectory to which to write the savepoint data or null if the default savepoint
     *     directory should be used
     * @param timeout for the rpc call
     * @return Future which is completed with the savepoint path once completed
     */
    CompletableFuture<String> triggerSavepoint(
            @Nullable final String targetDirectory,
            final boolean cancelJob,
            @RpcTimeout final Time timeout);

    /**
     * Stops the job with a savepoint.
     * 使用保存点停止作业。
     *
     * @param targetDirectory to which to write the savepoint data or null if the default savepoint
     *     directory should be used
     * @param terminate flag indicating if the job should terminate or just suspend
     * @param timeout for the rpc call
     * @return Future which is completed with the savepoint path once completed
     */
    CompletableFuture<String> stopWithSavepoint(
            @Nullable final String targetDirectory,
            final boolean terminate,
            @RpcTimeout final Time timeout);

    /**
     * Notifies that the allocation has failed.
     * 通知分配失败。
     *
     * @param allocationID the failed allocation id.
     * @param cause the reason that the allocation failed
     */
    void notifyAllocationFailure(AllocationID allocationID, Exception cause);

    /**
     * Notifies that not enough resources are available to fulfill the resource requirements of a
     * job.
     * 通知没有足够的资源可用于满足作业的资源需求。
     *
     * @param acquiredResources the resources that have been acquired for the job
     */
    void notifyNotEnoughResourcesAvailable(Collection<ResourceRequirement> acquiredResources);

    /**
     * Update the aggregate and return the new value.
     * 更新聚合并返回新值。
     *
     * @param aggregateName The name of the aggregate to update
     * @param aggregand The value to add to the aggregate
     * @param serializedAggregationFunction The function to apply to the current aggregate and
     *     aggregand to obtain the new aggregate value, this should be of type {@link
     *     AggregateFunction}
     * @return The updated aggregate
     */
    CompletableFuture<Object> updateGlobalAggregate(
            String aggregateName, Object aggregand, byte[] serializedAggregationFunction);

    /**
     * Deliver a coordination request to a specified coordinator and return the response.
     * 向指定的协调者发送协调请求并返回响应。
     *
     * @param operatorId identifying the coordinator to receive the request
     * @param serializedRequest serialized request to deliver
     * @return A future containing the response. The response will fail with a {@link
     *     org.apache.flink.util.FlinkException} if the task is not running, or no
     *     operator/coordinator exists for the given ID, or the coordinator cannot handle client
     *     events.
     */
    CompletableFuture<CoordinationResponse> deliverCoordinationRequestToCoordinator(
            OperatorID operatorId,
            SerializedValue<CoordinationRequest> serializedRequest,
            @RpcTimeout Time timeout);
}
