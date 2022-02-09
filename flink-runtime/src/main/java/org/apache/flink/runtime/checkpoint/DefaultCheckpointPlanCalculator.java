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

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.executiongraph.Execution;
import org.apache.flink.runtime.executiongraph.ExecutionJobVertex;
import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.executiongraph.InternalExecutionGraphAccessor;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.jobgraph.IntermediateResultPartitionID;
import org.apache.flink.runtime.jobgraph.JobEdge;
import org.apache.flink.runtime.jobgraph.JobVertexID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Default implementation for {@link CheckpointPlanCalculator}. If all tasks are running, it
 * directly marks all the sources as tasks to trigger, otherwise it would try to find the running
 * tasks without running processors as tasks to trigger.
 * {@link CheckpointPlanCalculator} 的默认实现。
 * 如果所有任务都在运行，则直接将所有源标记为要触发的任务，
 * 否则会在不运行处理器的情况下尝试查找正在运行的任务作为要触发的任务。
 */
public class DefaultCheckpointPlanCalculator implements CheckpointPlanCalculator {

    private final JobID jobId;

    private final CheckpointPlanCalculatorContext context;

    private final List<ExecutionJobVertex> jobVerticesInTopologyOrder = new ArrayList<>();

    private final List<ExecutionVertex> allTasks = new ArrayList<>();

    private final List<ExecutionVertex> sourceTasks = new ArrayList<>();

    /**
     * TODO Temporary flag to allow checkpoints after tasks finished. This is disabled for regular
     * jobs to keep the current behavior but we want to allow it in tests. This should be removed
     * once all parts of the stack support checkpoints after some tasks finished.
     * TODO 临时标志以允许任务完成后的检查点。
     * 这对于常规作业被禁用以保持当前行为，但我们希望在测试中允许它。
     * 在某些任务完成后，一旦堆栈的所有部分都支持检查点，这应该被删除。
     */
    private boolean allowCheckpointsAfterTasksFinished;

    public DefaultCheckpointPlanCalculator(
            JobID jobId,
            CheckpointPlanCalculatorContext context,
            Iterable<ExecutionJobVertex> jobVerticesInTopologyOrderIterable) {

        this.jobId = checkNotNull(jobId);
        this.context = checkNotNull(context);

        checkNotNull(jobVerticesInTopologyOrderIterable);
        jobVerticesInTopologyOrderIterable.forEach(
                jobVertex -> {
                    jobVerticesInTopologyOrder.add(jobVertex);
                    allTasks.addAll(Arrays.asList(jobVertex.getTaskVertices()));

                    if (jobVertex.getJobVertex().isInputVertex()) {
                        sourceTasks.addAll(Arrays.asList(jobVertex.getTaskVertices()));
                    }
                });
    }

    public void setAllowCheckpointsAfterTasksFinished(boolean allowCheckpointsAfterTasksFinished) {
        this.allowCheckpointsAfterTasksFinished = allowCheckpointsAfterTasksFinished;
    }

    @Override
    public CompletableFuture<CheckpointPlan> calculateCheckpointPlan() {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        if (context.hasFinishedTasks() && !allowCheckpointsAfterTasksFinished) {
                            throw new CheckpointException(
                                    String.format(
                                            "some tasks of job %s has been finished, abort the checkpoint",
                                            jobId),
                                    CheckpointFailureReason.NOT_ALL_REQUIRED_TASKS_RUNNING);
                        }

                        checkAllTasksInitiated();

                        CheckpointPlan result =
                                context.hasFinishedTasks()
                                        ? calculateAfterTasksFinished()
                                        : calculateWithAllTasksRunning();

                        checkTasksStarted(result.getTasksToTrigger());

                        return result;
                    } catch (Throwable throwable) {
                        throw new CompletionException(throwable);
                    }
                },
                context.getMainExecutor());
    }

    /**
     * Checks if all tasks are attached with the current Execution already. This method should be
     * called from JobMaster main thread executor.
     * 检查所有任务是否已经附加到当前执行。 这个方法应该从 JobMaster 主线程执行器中调用。
     *
     * @throws CheckpointException if some tasks do not have attached Execution.
     */
    private void checkAllTasksInitiated() throws CheckpointException {
        for (ExecutionVertex task : allTasks) {
            if (task.getCurrentExecutionAttempt() == null) {
                throw new CheckpointException(
                        String.format(
                                "task %s of job %s is not being executed at the moment. Aborting checkpoint.",
                                task.getTaskNameWithSubtaskIndex(), jobId),
                        CheckpointFailureReason.NOT_ALL_REQUIRED_TASKS_RUNNING);
            }
        }
    }

    /**
     * Checks if all tasks to trigger have already been in RUNNING state. This method should be
     * called from JobMaster main thread executor.
     * 检查所有要触发的任务是否已经处于 RUNNING 状态。 这个方法应该从 JobMaster 主线程执行器中调用。
     *
     * @throws CheckpointException if some tasks to trigger have not turned into RUNNING yet.
     */
    private void checkTasksStarted(List<Execution> toTrigger) throws CheckpointException {
        for (Execution execution : toTrigger) {
            if (execution.getState() != ExecutionState.RUNNING) {
                throw new CheckpointException(
                        String.format(
                                "Checkpoint triggering task %s of job %s is not being executed at the moment. "
                                        + "Aborting checkpoint.",
                                execution.getVertex().getTaskNameWithSubtaskIndex(), jobId),
                        CheckpointFailureReason.NOT_ALL_REQUIRED_TASKS_RUNNING);
            }
        }
    }

    /**
     * Computes the checkpoint plan when all tasks are running. It would simply marks all the source
     * tasks as need to trigger and all the tasks as need to wait and commit.
     * 当所有任务都在运行时计算检查点计划。 它只是将所有源任务标记为需要触发，并将所有任务标记为需要等待和提交。
     *
     * @return The plan of this checkpoint.
     */
    private CheckpointPlan calculateWithAllTasksRunning() {
        List<Execution> executionsToTrigger =
                sourceTasks.stream()
                        .map(ExecutionVertex::getCurrentExecutionAttempt)
                        .collect(Collectors.toList());

        List<Execution> tasksToWaitFor = createTaskToWaitFor(allTasks);

        return new CheckpointPlan(
                Collections.unmodifiableList(executionsToTrigger),
                Collections.unmodifiableList(tasksToWaitFor),
                Collections.unmodifiableList(allTasks),
                Collections.emptyList(),
                Collections.emptyList());
    }

    /**
     * Calculates the checkpoint plan after some tasks have finished. We iterate the job graph to
     * find the task that is still running, but do not has precedent running tasks.
     * 在某些任务完成后计算检查点计划。 我们迭代作业图以找到仍在运行但没有先例运行任务的任务。
     *
     * @return The plan of this checkpoint.
     */
    private CheckpointPlan calculateAfterTasksFinished() {
        // First collect the task running status into BitSet so that we could
        // do JobVertex level judgement for some vertices and avoid time-consuming
        // access to volatile isFinished flag of Execution.
        Map<JobVertexID, BitSet> taskRunningStatusByVertex = collectTaskRunningStatus();

        List<Execution> tasksToTrigger = new ArrayList<>();
        List<Execution> tasksToWaitFor = new ArrayList<>();
        List<ExecutionVertex> tasksToCommitTo = new ArrayList<>();
        List<Execution> finishedTasks = new ArrayList<>();
        List<ExecutionJobVertex> fullyFinishedJobVertex = new ArrayList<>();

        for (ExecutionJobVertex jobVertex : jobVerticesInTopologyOrder) {
            BitSet taskRunningStatus = taskRunningStatusByVertex.get(jobVertex.getJobVertexId());

            if (taskRunningStatus.cardinality() == 0) {
                fullyFinishedJobVertex.add(jobVertex);

                for (ExecutionVertex task : jobVertex.getTaskVertices()) {
                    finishedTasks.add(task.getCurrentExecutionAttempt());
                }

                continue;
            }

            List<JobEdge> prevJobEdges = jobVertex.getJobVertex().getInputs();

            // this is an optimization: we determine at the JobVertex level if some tasks can even
            // be eligible for being in the "triggerTo" set.
            boolean someTasksMustBeTriggered =
                    someTasksMustBeTriggered(taskRunningStatusByVertex, prevJobEdges);

            for (int i = 0; i < jobVertex.getTaskVertices().length; ++i) {
                ExecutionVertex task = jobVertex.getTaskVertices()[i];
                if (taskRunningStatus.get(task.getParallelSubtaskIndex())) {
                    tasksToWaitFor.add(task.getCurrentExecutionAttempt());
                    tasksToCommitTo.add(task);

                    if (someTasksMustBeTriggered) {
                        boolean hasRunningPrecedentTasks =
                                hasRunningPrecedentTasks(
                                        task, prevJobEdges, taskRunningStatusByVertex);

                        if (!hasRunningPrecedentTasks) {
                            tasksToTrigger.add(task.getCurrentExecutionAttempt());
                        }
                    }
                } else {
                    finishedTasks.add(task.getCurrentExecutionAttempt());
                }
            }
        }

        return new CheckpointPlan(
                Collections.unmodifiableList(tasksToTrigger),
                Collections.unmodifiableList(tasksToWaitFor),
                Collections.unmodifiableList(tasksToCommitTo),
                Collections.unmodifiableList(finishedTasks),
                Collections.unmodifiableList(fullyFinishedJobVertex));
    }

    private boolean someTasksMustBeTriggered(
            Map<JobVertexID, BitSet> runningTasksByVertex, List<JobEdge> prevJobEdges) {

        for (JobEdge jobEdge : prevJobEdges) {
            DistributionPattern distributionPattern = jobEdge.getDistributionPattern();
            BitSet upstreamRunningStatus =
                    runningTasksByVertex.get(jobEdge.getSource().getProducer().getID());

            if (hasActiveUpstreamVertex(distributionPattern, upstreamRunningStatus)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Every task must have active upstream tasks if
     * 每个任务都必须有活动的上游任务，如果
     *<ol>
     *     <li>ALL_TO_ALL 连接和一些前辈仍在运行。
     *     <li>POINTWISE 连接和所有前辈仍在运行。
     * </ol>
     * <ol>
     *   <li>ALL_TO_ALL connection and some predecessors are still running.
     *   <li>POINTWISE connection and all predecessors are still running.
     * </ol>
     *
     * @param distribution The distribution pattern between the upstream vertex and the current
     *     vertex.
     * @param upstreamRunningTasks The running tasks of the upstream vertex.
     * @return Whether every task of the current vertex is connected to some active predecessors.
     */
    private boolean hasActiveUpstreamVertex(
            DistributionPattern distribution, BitSet upstreamRunningTasks) {
        return (distribution == DistributionPattern.ALL_TO_ALL
                        && upstreamRunningTasks.cardinality() > 0)
                || (distribution == DistributionPattern.POINTWISE
                        && upstreamRunningTasks.cardinality() == upstreamRunningTasks.size());
    }

    private boolean hasRunningPrecedentTasks(
            ExecutionVertex vertex,
            List<JobEdge> prevJobEdges,
            Map<JobVertexID, BitSet> taskRunningStatusByVertex) {

        InternalExecutionGraphAccessor executionGraphAccessor = vertex.getExecutionGraphAccessor();

        for (int i = 0; i < prevJobEdges.size(); ++i) {
            if (prevJobEdges.get(i).getDistributionPattern() == DistributionPattern.POINTWISE) {
                for (IntermediateResultPartitionID consumedPartitionId :
                        vertex.getConsumedPartitionGroup(i)) {
                    ExecutionVertex precedentTask =
                            executionGraphAccessor
                                    .getResultPartitionOrThrow(consumedPartitionId)
                                    .getProducer();
                    BitSet precedentVertexRunningStatus =
                            taskRunningStatusByVertex.get(precedentTask.getJobvertexId());

                    if (precedentVertexRunningStatus.get(precedentTask.getParallelSubtaskIndex())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Collects the task running status for each job vertex.
     * 收集每个作业顶点的任务运行状态。
     *
     * @return The task running status for each job vertex.
     */
    @VisibleForTesting
    Map<JobVertexID, BitSet> collectTaskRunningStatus() {
        Map<JobVertexID, BitSet> runningStatusByVertex = new HashMap<>();

        for (ExecutionJobVertex vertex : jobVerticesInTopologyOrder) {
            BitSet runningTasks = new BitSet(vertex.getTaskVertices().length);

            for (int i = 0; i < vertex.getTaskVertices().length; ++i) {
                if (!vertex.getTaskVertices()[i].getCurrentExecutionAttempt().isFinished()) {
                    runningTasks.set(i);
                }
            }

            runningStatusByVertex.put(vertex.getJobVertexId(), runningTasks);
        }

        return runningStatusByVertex;
    }

    private List<Execution> createTaskToWaitFor(List<ExecutionVertex> tasks) {
        List<Execution> tasksToAck = new ArrayList<>(tasks.size());
        for (ExecutionVertex task : tasks) {
            tasksToAck.add(task.getCurrentExecutionAttempt());
        }

        return tasksToAck;
    }
}
