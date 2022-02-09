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

import java.util.Map;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Statistics for a successfully completed checkpoint.
 * 成功完成的检查点的统计信息。
 *
 * <p>The reported statistics are immutable except for the discarded flag, which is updated via the
 * {@link DiscardCallback} and the {@link CompletedCheckpoint} after an instance of this class has
 * been created.
 * 报告的统计信息是不可变的，除了丢弃标志，
 * 在创建此类实例后通过 {@link DiscardCallback} 和 {@link CompletedCheckpoint} 更新。
 */
public class CompletedCheckpointStats extends AbstractCheckpointStats {

    private static final long serialVersionUID = 138833868551861344L;

    /** Total checkpoint state size over all subtasks.
     * 所有子任务的总检查点状态大小。
     * */
    private final long stateSize;

    private final long processedData;

    private final long persistedData;

    /** The latest acknowledged subtask stats.
     * 最新确认的子任务统计信息。
     * */
    private final SubtaskStateStats latestAcknowledgedSubtask;

    /** The external pointer of the checkpoint.
     * 检查点的外部指针。
     * */
    private final String externalPointer;

    /** Flag indicating whether the checkpoint was discarded.
     * 指示检查点是否被丢弃的标志。
     * */
    private volatile boolean discarded;

    /**
     * Creates a tracker for a {@link CompletedCheckpoint}.
     *
     * @param checkpointId ID of the checkpoint.
     * @param triggerTimestamp Timestamp when the checkpoint was triggered.
     * @param props Checkpoint properties of the checkpoint.
     * @param totalSubtaskCount Total number of subtasks for the checkpoint.
     * @param taskStats Task stats for each involved operator.
     * @param numAcknowledgedSubtasks Number of acknowledged subtasks.
     * @param stateSize Total checkpoint state size over all subtasks.
     * @param processedData Processed data during the checkpoint.
     * @param persistedData Persisted data during the checkpoint.
     * @param latestAcknowledgedSubtask The latest acknowledged subtask stats.
     * @param externalPointer Optional external path if persisted externally.
     */
    CompletedCheckpointStats(
            long checkpointId,
            long triggerTimestamp,
            CheckpointProperties props,
            int totalSubtaskCount,
            Map<JobVertexID, TaskStateStats> taskStats,
            int numAcknowledgedSubtasks,
            long stateSize,
            long processedData,
            long persistedData,
            SubtaskStateStats latestAcknowledgedSubtask,
            String externalPointer) {

        super(checkpointId, triggerTimestamp, props, totalSubtaskCount, taskStats);
        checkArgument(
                numAcknowledgedSubtasks == totalSubtaskCount, "Did not acknowledge all subtasks.");
        checkArgument(stateSize >= 0, "Negative state size");
        this.stateSize = stateSize;
        this.processedData = processedData;
        this.persistedData = persistedData;
        this.latestAcknowledgedSubtask = checkNotNull(latestAcknowledgedSubtask);
        this.externalPointer = externalPointer;
    }

    @Override
    public CheckpointStatsStatus getStatus() {
        return CheckpointStatsStatus.COMPLETED;
    }

    @Override
    public int getNumberOfAcknowledgedSubtasks() {
        return numberOfSubtasks;
    }

    @Override
    public long getStateSize() {
        return stateSize;
    }

    @Override
    public long getProcessedData() {
        return processedData;
    }

    @Override
    public long getPersistedData() {
        return persistedData;
    }

    @Override
    @Nullable
    public SubtaskStateStats getLatestAcknowledgedSubtaskStats() {
        return latestAcknowledgedSubtask;
    }

    // ------------------------------------------------------------------------
    // Completed checkpoint specific methods
    // ------------------------------------------------------------------------

    /** Returns the external pointer of this checkpoint.
     * 返回此检查点的外部指针。
     * */
    public String getExternalPath() {
        return externalPointer;
    }

    /**
     * Returns whether the checkpoint has been discarded.
     * 返回检查点是否已被丢弃。
     *
     * @return <code>true</code> if the checkpoint has been discarded, <code>false</code> otherwise.
     */
    public boolean isDiscarded() {
        return discarded;
    }

    /**
     * Returns the callback for the {@link CompletedCheckpoint}.
     * 返回 {@link CompletedCheckpoint} 的回调。
     *
     * @return Callback for the {@link CompletedCheckpoint}.
     */
    DiscardCallback getDiscardCallback() {
        return new DiscardCallback();
    }

    /**
     * Callback for the {@link CompletedCheckpoint} instance to notify about disposal of the
     * checkpoint (most commonly when the checkpoint has been subsumed by a newer one).
     * {@link CompletedCheckpoint} 实例的回调以通知检查点的处置（最常见的是当检查点已被较新的检查点包含时）。
     */
    class DiscardCallback {

        /**
         * Updates the discarded flag of the checkpoint stats.
         * 更新检查点统计信息的废弃标志。
         *
         * <p>After this notification, {@link #isDiscarded()} will return <code>true</code>.
         * 收到此通知后，{@link #isDiscarded()} 将返回 <code>true</code>。
         */
        void notifyDiscardedCheckpoint() {
            discarded = true;
        }
    }

    @Override
    public String toString() {
        return "CompletedCheckpoint(id=" + getCheckpointId() + ")";
    }
}
