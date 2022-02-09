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

package org.apache.flink.runtime.executiongraph;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.accumulators.Accumulator;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.JobException;
import org.apache.flink.runtime.accumulators.AccumulatorSnapshot;
import org.apache.flink.runtime.checkpoint.CheckpointCoordinator;
import org.apache.flink.runtime.checkpoint.CheckpointIDCounter;
import org.apache.flink.runtime.checkpoint.CheckpointStatsTracker;
import org.apache.flink.runtime.checkpoint.CheckpointsCleaner;
import org.apache.flink.runtime.checkpoint.CompletedCheckpointStore;
import org.apache.flink.runtime.checkpoint.MasterTriggerRestoreHook;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.executiongraph.failover.flip1.ResultPartitionAvailabilityChecker;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.tasks.CheckpointCoordinatorConfiguration;
import org.apache.flink.runtime.query.KvStateLocationRegistry;
import org.apache.flink.runtime.scheduler.InternalFailuresListener;
import org.apache.flink.runtime.scheduler.strategy.SchedulingTopology;
import org.apache.flink.runtime.state.CheckpointStorage;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.util.OptionalFailure;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The execution graph is the central data structure that coordinates the distributed execution of a
 * data flow. It keeps representations of each parallel task, each intermediate stream, and the
 * communication between them.
 * 执行图是协调数据流的分布式执行的中央数据结构。 它保留了每个并行任务、每个中间流以及它们之间的通信的表示。
 *
 * <p>The execution graph consists of the following constructs:
 * 执行图由以下结构组成：
 *<ul>
 *    <li>{@link ExecutionJobVertex} 表示执行期间来自 JobGraph 的一个顶点（通常是一个操作，如“map”或“join”）。
 *    它保存所有并行子任务的聚合状态。 ExecutionJobVertex 在图中由 {@link JobVertexID} 标识，
 *    它从 JobGraph 的相应 JobVertex 中获取。
 *    <li>{@link ExecutionVertex} 代表一个并行子任务。对于每个 ExecutionJobVertex，
 *    ExecutionVertices 的数量与并行度一样多。 ExecutionVertex 由 ExecutionJobVertex 和并行子任务的索引标识
 *    <li>{@link Execution} 是执行 ExecutionVertex 的一种尝试。 ExecutionVertex 可能有多个执行，
 *    以防发生故障，或者在某些数据需要重新计算的情况下，因为在以后的操作请求时不再可用。
 *    执行始终由 {@link ExecutionAttemptID} 标识。 JobManager 和 TaskManager 之间
 *    关于任务部署和任务状态更新的所有消息总是使用 ExecutionAttemptID 来寻址消息接收者。
 *</ul>
 *
 * <ul>
 *   <li>The {@link ExecutionJobVertex} represents one vertex from the JobGraph (usually one
 *       operation like "map" or "join") during execution. It holds the aggregated state of all
 *       parallel subtasks. The ExecutionJobVertex is identified inside the graph by the {@link
 *       JobVertexID}, which it takes from the JobGraph's corresponding JobVertex.
 *   <li>The {@link ExecutionVertex} represents one parallel subtask. For each ExecutionJobVertex,
 *       there are as many ExecutionVertices as the parallelism. The ExecutionVertex is identified
 *       by the ExecutionJobVertex and the index of the parallel subtask
 *   <li>The {@link Execution} is one attempt to execute a ExecutionVertex. There may be multiple
 *       Executions for the ExecutionVertex, in case of a failure, or in the case where some data
 *       needs to be recomputed because it is no longer available when requested by later
 *       operations. An Execution is always identified by an {@link ExecutionAttemptID}. All
 *       messages between the JobManager and the TaskManager about deployment of tasks and updates
 *       in the task status always use the ExecutionAttemptID to address the message receiver.
 * </ul>
 */
public interface ExecutionGraph extends AccessExecutionGraph {

    void start(@Nonnull ComponentMainThreadExecutor jobMasterMainThreadExecutor);

    SchedulingTopology getSchedulingTopology();

    void enableCheckpointing(
            CheckpointCoordinatorConfiguration chkConfig,
            List<MasterTriggerRestoreHook<?>> masterHooks,
            CheckpointIDCounter checkpointIDCounter,
            CompletedCheckpointStore checkpointStore,
            StateBackend checkpointStateBackend,
            CheckpointStorage checkpointStorage,
            CheckpointStatsTracker statsTracker,
            CheckpointsCleaner checkpointsCleaner);

    @Nullable
    CheckpointCoordinator getCheckpointCoordinator();

    KvStateLocationRegistry getKvStateLocationRegistry();

    void setJsonPlan(String jsonPlan);

    Configuration getJobConfiguration();

    Throwable getFailureCause();

    @Override
    Iterable<ExecutionJobVertex> getVerticesTopologically();

    @Override
    Iterable<ExecutionVertex> getAllExecutionVertices();

    @Override
    ExecutionJobVertex getJobVertex(JobVertexID id);

    @Override
    Map<JobVertexID, ExecutionJobVertex> getAllVertices();

    /**
     * Gets the number of restarts, including full restarts and fine grained restarts. If a recovery
     * is currently pending, this recovery is included in the count.
     * 获取重启次数，包括完全重启和细粒度重启。 如果恢复当前处于待处理状态，则此恢复将包含在计数中。
     *
     * @return The number of restarts so far
     */
    long getNumberOfRestarts();

    int getTotalNumberOfVertices();

    Map<IntermediateDataSetID, IntermediateResult> getAllIntermediateResults();

    /**
     * Merges all accumulator results from the tasks previously executed in the Executions.
     * 合并先前在 Executions 中执行的任务的所有累加器结果。
     *
     * @return The accumulator map
     */
    Map<String, OptionalFailure<Accumulator<?, ?>>> aggregateUserAccumulators();

    /**
     * Updates the accumulators during the runtime of a job. Final accumulator results are
     * transferred through the UpdateTaskExecutionState message.
     * 在作业运行期间更新累加器。 最终累加器结果通过 UpdateTaskExecutionState 消息传输。
     *
     * @param accumulatorSnapshot The serialized flink and user-defined accumulators
     */
    void updateAccumulators(AccumulatorSnapshot accumulatorSnapshot);

    void setInternalTaskFailuresListener(InternalFailuresListener internalTaskFailuresListener);

    void attachJobGraph(List<JobVertex> topologiallySorted) throws JobException;

    void transitionToRunning();

    void cancel();

    /**
     * Suspends the current ExecutionGraph.
     * 暂停当前的 ExecutionGraph。
     *
     * <p>The JobStatus will be directly set to {@link JobStatus#SUSPENDED} iff the current state is
     * not a terminal state. All ExecutionJobVertices will be canceled and the onTerminalState() is
     * executed.
     * 如果当前状态不是终端状态，则 JobStatus 将直接设置为 {@link JobStatus#SUSPENDED}。
     * 所有的 ExecutionJobVertices 将被取消并执行 onTerminalState()。
     *
     * <p>The {@link JobStatus#SUSPENDED} state is a local terminal state which stops the execution
     * of the job but does not remove the job from the HA job store so that it can be recovered by
     * another JobManager.
     * {@link JobStatus#SUSPENDED} 状态是一种本地终端状态，它会停止作业的执行，
     * 但不会从 HA 作业存储中删除作业，以便可以由另一个 JobManager 恢复它。
     *
     * @param suspensionCause Cause of the suspension
     */
    void suspend(Throwable suspensionCause);

    void failJob(Throwable cause, long timestamp);

    /**
     * Returns the termination future of this {@link ExecutionGraph}. The termination future is
     * completed with the terminal {@link JobStatus} once the ExecutionGraph reaches this terminal
     * state and all {@link Execution} have been terminated.
     * 返回此 {@link ExecutionGraph} 的终止future。
     * 一旦 ExecutionGraph 达到此终端状态并且所有 {@link Execution} 都已终止，
     * 终止future将通过终端 {@link JobStatus} 完成。
     *
     * @return Termination future of this {@link ExecutionGraph}.
     */
    CompletableFuture<JobStatus> getTerminationFuture();

    @VisibleForTesting
    JobStatus waitUntilTerminal() throws InterruptedException;

    boolean transitionState(JobStatus current, JobStatus newState);

    void incrementRestarts();

    void initFailureCause(Throwable t, long timestamp);

    /**
     * Updates the state of one of the ExecutionVertex's Execution attempts. If the new status if
     * "FINISHED", this also updates the accumulators.
     * 更新 ExecutionVertex 的执行尝试之一的状态。 如果新状态为“已完成”，这也会更新累加器。
     *
     * @param state The state update.
     * @return True, if the task update was properly applied, false, if the execution attempt was
     *     not found.
     */
    boolean updateState(TaskExecutionStateTransition state);

    /**
     * Mark the data of a result partition to be available. Note that only PIPELINED partitions are
     * accepted because it is for the case that a TM side PIPELINED result partition has data
     * produced and notifies JM.
     * 将结果分区的数据标记为可用。 请注意，仅接受 PIPELINED 分区，
     * 因为这是针对 TM 端 PIPELINED 结果分区产生数据并通知 JM 的情况。
     *
     * @param partitionId specifying the result partition whose data have become available
     */
    void notifyPartitionDataAvailable(ResultPartitionID partitionId);

    Map<ExecutionAttemptID, Execution> getRegisteredExecutions();

    void registerJobStatusListener(JobStatusListener listener);

    ResultPartitionAvailabilityChecker getResultPartitionAvailabilityChecker();

    int getNumFinishedVertices();

    @Nonnull
    ComponentMainThreadExecutor getJobMasterMainThreadExecutor();
}
