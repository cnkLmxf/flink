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

import org.apache.flink.runtime.jobgraph.JobVertexID;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Base class for checkpoint statistics.
 * 检查点统计信息的基类。
 * */
public abstract class AbstractCheckpointStats implements Serializable {

    private static final long serialVersionUID = 1041218202028265151L;

    /** ID of this checkpoint. */
    final long checkpointId;

    /** Timestamp when the checkpoint was triggered at the coordinator.
     * 在协调器处触发检查点的时间戳。
     * */
    final long triggerTimestamp;

    /** {@link TaskStateStats} accessible by their ID.
     * {@link TaskStateStats} 可通过其 ID 访问。
     * */
    final Map<JobVertexID, TaskStateStats> taskStats;

    /** Total number of subtasks over all tasks.
     * 所有任务的子任务总数。
     * */
    final int numberOfSubtasks;

    /** Properties of the checkpoint.
     * 检查点的属性。
     * */
    final CheckpointProperties props;

    AbstractCheckpointStats(
            long checkpointId,
            long triggerTimestamp,
            CheckpointProperties props,
            int numberOfSubtasks,
            Map<JobVertexID, TaskStateStats> taskStats) {

        this.checkpointId = checkpointId;
        this.triggerTimestamp = triggerTimestamp;
        this.taskStats = checkNotNull(taskStats);
        checkArgument(taskStats.size() > 0, "Empty task stats");
        checkArgument(numberOfSubtasks > 0, "Non-positive number of subtasks");
        this.numberOfSubtasks = numberOfSubtasks;
        this.props = checkNotNull(props);
    }

    /**
     * Returns the status of this checkpoint.
     *
     * @return Status of this checkpoint
     */
    public abstract CheckpointStatsStatus getStatus();

    /**
     * Returns the number of acknowledged subtasks.
     *
     * @return The number of acknowledged subtasks.
     */
    public abstract int getNumberOfAcknowledgedSubtasks();

    /**
     * Returns the total checkpoint state size over all subtasks.
     * 返回所有子任务的总检查点状态大小。
     *
     * @return Total checkpoint state size over all subtasks.
     */
    public abstract long getStateSize();

    /** @return the total number of processed bytes during the checkpoint.
     *  检查点期间处理的字节总数。
     * */
    public abstract long getProcessedData();

    /** @return the total number of persisted bytes during the checkpoint.
     * 检查点期间的持久字节总数。
     * */
    public abstract long getPersistedData();

    /**
     * Returns the latest acknowledged subtask stats or <code>null</code> if none was acknowledged
     * yet.
     * 返回最新确认的子任务统计信息，如果尚未确认，则返回 <code>null</code>。
     *
     * @return Latest acknowledged subtask stats or <code>null</code>
     * 最新确认的子任务统计信息或 <code>null</code>
     */
    @Nullable
    public abstract SubtaskStateStats getLatestAcknowledgedSubtaskStats();

    /**
     * Returns the ID of this checkpoint.
     *
     * @return ID of this checkpoint.
     */
    public long getCheckpointId() {
        return checkpointId;
    }

    /**
     * Returns the timestamp when the checkpoint was triggered.
     *
     * @return Timestamp when the checkpoint was triggered.
     */
    public long getTriggerTimestamp() {
        return triggerTimestamp;
    }

    /**
     * Returns the properties of this checkpoint.
     *
     * @return Properties of this checkpoint.
     */
    public CheckpointProperties getProperties() {
        return props;
    }

    /**
     * Returns the total number of subtasks involved in this checkpoint.
     *
     * @return Total number of subtasks involved in this checkpoint.
     */
    public int getNumberOfSubtasks() {
        return numberOfSubtasks;
    }

    /**
     * Returns the task state stats for the given job vertex ID or <code>null</code> if no task with
     * such an ID is available.
     *
     * @param jobVertexId Job vertex ID of the task stats to look up.
     * @return The task state stats instance for the given ID or <code>null</code>.
     */
    public TaskStateStats getTaskStateStats(JobVertexID jobVertexId) {
        return taskStats.get(jobVertexId);
    }

    /**
     * Returns all task state stats instances.
     *
     * @return All task state stats instances.
     */
    public Collection<TaskStateStats> getAllTaskStateStats() {
        return taskStats.values();
    }

    /**
     * Returns the ack timestamp of the latest acknowledged subtask or <code>-1</code> if none was
     * acknowledged yet.
     *
     * @return Ack timestamp of the latest acknowledged subtask or <code>-1</code>.
     */
    public long getLatestAckTimestamp() {
        SubtaskStateStats subtask = getLatestAcknowledgedSubtaskStats();
        if (subtask != null) {
            return subtask.getAckTimestamp();
        } else {
            return -1;
        }
    }

    /**
     * Returns the duration of this checkpoint calculated as the time since triggering until the
     * latest acknowledged subtask or <code>-1</code> if no subtask was acknowledged yet.
     * 返回此检查点的持续时间，计算为从触发到最近确认的子任务的时间，如果还没有确认子任务，
     * 则返回 <code>-1</code>。
     *
     * @return Duration of this checkpoint or <code>-1</code> if no subtask was acknowledged yet.
     */
    public long getEndToEndDuration() {
        SubtaskStateStats subtask = getLatestAcknowledgedSubtaskStats();
        if (subtask != null) {
            return Math.max(0, subtask.getAckTimestamp() - triggerTimestamp);
        } else {
            return -1;
        }
    }
}
