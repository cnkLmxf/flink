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

package org.apache.flink.runtime.execution;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.accumulators.AccumulatorRegistry;
import org.apache.flink.runtime.broadcast.BroadcastVariableManager;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.checkpoint.CheckpointMetrics;
import org.apache.flink.runtime.checkpoint.TaskStateSnapshot;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.externalresource.ExternalResourceInfoProvider;
import org.apache.flink.runtime.io.disk.iomanager.IOManager;
import org.apache.flink.runtime.io.network.TaskEventDispatcher;
import org.apache.flink.runtime.io.network.api.writer.ResultPartitionWriter;
import org.apache.flink.runtime.io.network.partition.consumer.IndexedInputGate;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.tasks.InputSplitProvider;
import org.apache.flink.runtime.jobgraph.tasks.TaskOperatorEventGateway;
import org.apache.flink.runtime.memory.MemoryManager;
import org.apache.flink.runtime.metrics.groups.TaskMetricGroup;
import org.apache.flink.runtime.query.TaskKvStateRegistry;
import org.apache.flink.runtime.state.TaskStateManager;
import org.apache.flink.runtime.state.internal.InternalKvState;
import org.apache.flink.runtime.taskexecutor.GlobalAggregateManager;
import org.apache.flink.runtime.taskmanager.TaskManagerRuntimeInfo;
import org.apache.flink.util.UserCodeClassLoader;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * The Environment gives the code executed in a task access to the task's properties (such as name,
 * parallelism), the configurations, the data stream readers and writers, as well as the various
 * components that are provided by the TaskManager, such as memory manager, I/O manager, ...
 * Environment 使在任务中执行的代码可以访问任务的属性（例如名称、并行度）、配置、数据流读取器和写入器，
 * 以及 TaskManager 提供的各种组件，例如内存管理器、 输入输出管理器，...
 */
public interface Environment {

    /**
     * Returns the job specific {@link ExecutionConfig}.
     *
     * @return The execution configuration associated with the current job.
     */
    ExecutionConfig getExecutionConfig();

    /**
     * Returns the ID of the job that the task belongs to.
     *
     * @return the ID of the job from the original job graph
     */
    JobID getJobID();

    /**
     * Gets the ID of the JobVertex for which this task executes a parallel subtask.
     * 获取此任务为其执行并行子任务的 JobVertex 的 ID。
     *
     * @return The JobVertexID of this task.
     */
    JobVertexID getJobVertexId();

    /**
     * Gets the ID of the task execution attempt.
     *
     * @return The ID of the task execution attempt.
     */
    ExecutionAttemptID getExecutionId();

    /**
     * Returns the task-wide configuration object, originally attached to the job vertex.
     * 返回最初附加到作业顶点的任务范围的配置对象。
     *
     * @return The task-wide configuration
     */
    Configuration getTaskConfiguration();

    /**
     * Gets the task manager info, with configuration and hostname.
     * 获取任务管理器信息，包括配置和主机名。
     *
     * @return The task manager info, with configuration and hostname.
     */
    TaskManagerRuntimeInfo getTaskManagerInfo();

    /**
     * Returns the task specific metric group.
     *
     * @return The MetricGroup of this task.
     */
    TaskMetricGroup getMetricGroup();

    /**
     * Returns the job-wide configuration object that was attached to the JobGraph.
     * 返回附加到 JobGraph 的作业范围的配置对象。
     *
     * @return The job-wide configuration
     */
    Configuration getJobConfiguration();

    /**
     * Returns the {@link TaskInfo} object associated with this subtask
     *
     * @return TaskInfo for this subtask
     */
    TaskInfo getTaskInfo();

    /**
     * Returns the input split provider assigned to this environment.
     * 返回分配给此环境的输入拆分提供程序。
     *
     * @return The input split provider or {@code null} if no such provider has been assigned to
     *     this environment.
     */
    InputSplitProvider getInputSplitProvider();

    /** Gets the gateway through which operators can send events to the operator coordinators.
     * 获取操作员可以通过它向操作员协调器发送事件的网关。
     * */
    TaskOperatorEventGateway getOperatorCoordinatorEventGateway();

    /**
     * Returns the current {@link IOManager}.
     *
     * @return the current {@link IOManager}.
     */
    IOManager getIOManager();

    /**
     * Returns the current {@link MemoryManager}.
     *
     * @return the current {@link MemoryManager}.
     */
    MemoryManager getMemoryManager();

    /** Returns the user code class loader */
    UserCodeClassLoader getUserCodeClassLoader();

    Map<String, Future<Path>> getDistributedCacheEntries();

    BroadcastVariableManager getBroadcastVariableManager();

    TaskStateManager getTaskStateManager();

    GlobalAggregateManager getGlobalAggregateManager();

    /**
     * Get the {@link ExternalResourceInfoProvider} which contains infos of available external
     * resources.
     * 获取包含可用外部资源信息的 {@link ExternalResourceInfoProvider}。
     *
     * @return {@link ExternalResourceInfoProvider} which contains infos of available external
     *     resources
     */
    ExternalResourceInfoProvider getExternalResourceInfoProvider();

    /**
     * Return the registry for accumulators which are periodically sent to the job manager.
     * 返回定期发送到作业管理器的累加器的注册表。
     *
     * @return the registry
     */
    AccumulatorRegistry getAccumulatorRegistry();

    /**
     * Returns the registry for {@link InternalKvState} instances.
     * 返回 {@link InternalKvState} 实例的注册表。
     *
     * @return KvState registry
     */
    TaskKvStateRegistry getTaskKvStateRegistry();

    /**
     * Confirms that the invokable has successfully completed all steps it needed to to for the
     * checkpoint with the give checkpoint-ID. This method does not include any state in the
     * checkpoint.
     * 确认 invokable 已成功完成它需要为具有给定检查点 ID 的检查点执行的所有步骤。
     * 此方法不包括检查点中的任何状态。
     *
     * @param checkpointId ID of this checkpoint
     * @param checkpointMetrics metrics for this checkpoint
     */
    void acknowledgeCheckpoint(long checkpointId, CheckpointMetrics checkpointMetrics);

    /**
     * Confirms that the invokable has successfully completed all required steps for the checkpoint
     * with the give checkpoint-ID. This method does include the given state in the checkpoint.
     * 确认可调用对象已成功完成具有给定检查点 ID 的检查点所需的所有步骤。 此方法确实包括检查点中的给定状态。
     *
     * @param checkpointId ID of this checkpoint
     * @param checkpointMetrics metrics for this checkpoint
     * @param subtaskState All state handles for the checkpointed state
     */
    void acknowledgeCheckpoint(
            long checkpointId, CheckpointMetrics checkpointMetrics, TaskStateSnapshot subtaskState);

    /**
     * Declines a checkpoint. This tells the checkpoint coordinator that this task will not be able
     * to successfully complete a certain checkpoint.
     * 拒绝检查点。 这告诉检查点协调器这个任务将无法成功完成某个检查点。
     *
     * @param checkpointId The ID of the declined checkpoint.
     * @param checkpointException The exception why the checkpoint was declined.
     */
    void declineCheckpoint(long checkpointId, CheckpointException checkpointException);

    /**
     * Marks task execution failed for an external reason (a reason other than the task code itself
     * throwing an exception). If the task is already in a terminal state (such as FINISHED,
     * CANCELED, FAILED), or if the task is already canceling this does nothing. Otherwise it sets
     * the state to FAILED, and, if the invokable code is running, starts an asynchronous thread
     * that aborts that code.
     * 标记任务执行因外部原因失败（除了任务代码本身引发异常的原因）。
     * 如果任务已经处于终止状态（例如 FINISHED、CANCELED、FAILED），或者如果任务已经取消，则不执行任何操作。
     * 否则，它将状态设置为 FAILED，并且，如果可调用代码正在运行，则启动一个异步线程来中止该代码。
     *
     * <p>This method never blocks.
     */
    void failExternally(Throwable cause);

    // --------------------------------------------------------------------------------------------
    //  Fields relevant to the I/O system. Should go into Task
    // --------------------------------------------------------------------------------------------

    ResultPartitionWriter getWriter(int index);

    ResultPartitionWriter[] getAllWriters();

    IndexedInputGate getInputGate(int index);

    IndexedInputGate[] getAllInputGates();

    TaskEventDispatcher getTaskEventDispatcher();
}
