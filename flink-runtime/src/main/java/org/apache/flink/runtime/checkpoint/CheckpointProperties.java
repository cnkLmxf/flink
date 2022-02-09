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
import org.apache.flink.api.common.JobStatus;

import java.io.Serializable;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The configuration of a checkpoint. This describes whether
 * 检查点的配置。 这描述了是否
 *<ul>
 *     <li>检查点是常规检查点或保存点。
 *     <li>何时应该对检查点进行垃圾收集。
 * </ul>
 * <ul>
 *   <li>The checkpoint is s regular checkpoint or a savepoint.
 *   <li>When the checkpoint should be garbage collected.
 * </ul>
 */
public class CheckpointProperties implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Type - checkpoint / savepoint. */
    private final CheckpointType checkpointType;

    /**
     * This has a misleading name and actually means whether the snapshot must be triggered, or
     * whether it may be rejected by the checkpoint coordinator if too many checkpoints are
     * currently in progress.
     * 这有一个误导性的名称，实际上意味着是否必须触发快照，或者如果当前正在进行的检查点过多，检查点协调器是否可能会拒绝它。
     */
    private final boolean forced;

    private final boolean discardSubsumed;
    private final boolean discardFinished;
    private final boolean discardCancelled;
    private final boolean discardFailed;
    private final boolean discardSuspended;

    @VisibleForTesting
    CheckpointProperties(
            boolean forced,
            CheckpointType checkpointType,
            boolean discardSubsumed,
            boolean discardFinished,
            boolean discardCancelled,
            boolean discardFailed,
            boolean discardSuspended) {

        this.forced = forced;
        this.checkpointType = checkNotNull(checkpointType);
        this.discardSubsumed = discardSubsumed;
        this.discardFinished = discardFinished;
        this.discardCancelled = discardCancelled;
        this.discardFailed = discardFailed;
        this.discardSuspended = discardSuspended;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns whether the checkpoint should be forced.
     * 返回是否应该强制检查点。
     *
     * <p>Forced checkpoints ignore the configured maximum number of concurrent checkpoints and
     * minimum time between checkpoints. Furthermore, they are not subsumed by more recent
     * checkpoints as long as they are pending.
     * 强制检查点忽略配置的最大并发检查点数和检查点之间的最小时间。
     * 此外，只要它们处于未决状态，它们就不会被最近的检查点所包含。
     *
     * @return <code>true</code> if the checkpoint should be forced; <code>false</code> otherwise.
     * @see CheckpointCoordinator
     * @see PendingCheckpoint
     */
    boolean forceCheckpoint() {
        return forced;
    }

    // ------------------------------------------------------------------------
    // Garbage collection behaviour
    // ------------------------------------------------------------------------

    /**
     * Returns whether the checkpoint should be discarded when it is subsumed.
     * 返回在包含检查点时是否应丢弃检查点。
     *
     * <p>A checkpoint is subsumed when the maximum number of retained checkpoints is reached and a
     * more recent checkpoint completes..
     * 当达到最大保留检查点数并且更新的检查点完成时，将包含一个检查点。
     *
     * @return <code>true</code> if the checkpoint should be discarded when it is subsumed; <code>
     *     false</code> otherwise.
     * @see CompletedCheckpointStore
     */
    boolean discardOnSubsumed() {
        return discardSubsumed;
    }

    /**
     * Returns whether the checkpoint should be discarded when the owning job reaches the {@link
     * JobStatus#FINISHED} state.
     * 返回当拥有的作业达到 {@link JobStatus#FINISHED} 状态时是否应丢弃检查点。
     *
     * @return <code>true</code> if the checkpoint should be discarded when the owning job reaches
     *     the {@link JobStatus#FINISHED} state; <code>false</code> otherwise.
     * @see CompletedCheckpointStore
     */
    boolean discardOnJobFinished() {
        return discardFinished;
    }

    /**
     * Returns whether the checkpoint should be discarded when the owning job reaches the {@link
     * JobStatus#CANCELED} state.
     * 返回当拥有的作业达到 {@link JobStatus#CANCELED} 状态时是否应丢弃检查点。
     *
     * @return <code>true</code> if the checkpoint should be discarded when the owning job reaches
     *     the {@link JobStatus#CANCELED} state; <code>false</code> otherwise.
     * @see CompletedCheckpointStore
     */
    boolean discardOnJobCancelled() {
        return discardCancelled;
    }

    /**
     * Returns whether the checkpoint should be discarded when the owning job reaches the {@link
     * JobStatus#FAILED} state.
     * 返回当拥有的作业达到 {@link JobStatus#FAILED} 状态时是否应丢弃检查点。
     *
     * @return <code>true</code> if the checkpoint should be discarded when the owning job reaches
     *     the {@link JobStatus#FAILED} state; <code>false</code> otherwise.
     * @see CompletedCheckpointStore
     */
    boolean discardOnJobFailed() {
        return discardFailed;
    }

    /**
     * Returns whether the checkpoint should be discarded when the owning job reaches the {@link
     * JobStatus#SUSPENDED} state.
     * 返回当拥有的作业达到 {@link JobStatus#SUSPENDED} 状态时是否应丢弃检查点。
     *
     * @return <code>true</code> if the checkpoint should be discarded when the owning job reaches
     *     the {@link JobStatus#SUSPENDED} state; <code>false</code> otherwise.
     * @see CompletedCheckpointStore
     */
    boolean discardOnJobSuspended() {
        return discardSuspended;
    }

    /** Gets the type of the checkpoint (checkpoint / savepoint).
     * 获取检查点的类型（检查点/保存点）。
     * */
    public CheckpointType getCheckpointType() {
        return checkpointType;
    }

    /**
     * Returns whether the checkpoint properties describe a standard savepoint.
     *
     * @return <code>true</code> if the properties describe a savepoint, <code>false</code>
     *     otherwise.
     */
    public boolean isSavepoint() {
        return checkpointType.isSavepoint();
    }

    /**
     * Returns whether the checkpoint properties describe a synchronous savepoint/checkpoint.
     *
     * @return <code>true</code> if the properties describe a synchronous operation, <code>false
     *     </code> otherwise.
     */
    public boolean isSynchronous() {
        return checkpointType.isSynchronous();
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CheckpointProperties that = (CheckpointProperties) o;
        return forced == that.forced
                && checkpointType == that.checkpointType
                && discardSubsumed == that.discardSubsumed
                && discardFinished == that.discardFinished
                && discardCancelled == that.discardCancelled
                && discardFailed == that.discardFailed
                && discardSuspended == that.discardSuspended;
    }

    @Override
    public int hashCode() {
        int result = (forced ? 1 : 0);
        result = 31 * result + checkpointType.hashCode();
        result = 31 * result + (discardSubsumed ? 1 : 0);
        result = 31 * result + (discardFinished ? 1 : 0);
        result = 31 * result + (discardCancelled ? 1 : 0);
        result = 31 * result + (discardFailed ? 1 : 0);
        result = 31 * result + (discardSuspended ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CheckpointProperties{"
                + "forced="
                + forced
                + ", checkpointType="
                + checkpointType
                + ", discardSubsumed="
                + discardSubsumed
                + ", discardFinished="
                + discardFinished
                + ", discardCancelled="
                + discardCancelled
                + ", discardFailed="
                + discardFailed
                + ", discardSuspended="
                + discardSuspended
                + '}';
    }

    // ------------------------------------------------------------------------
    //  Factories and pre-configured properties
    // ------------------------------------------------------------------------

    private static final CheckpointProperties SAVEPOINT =
            new CheckpointProperties(
                    true, CheckpointType.SAVEPOINT, false, false, false, false, false);

    private static final CheckpointProperties SAVEPOINT_NO_FORCE =
            new CheckpointProperties(
                    false, CheckpointType.SAVEPOINT, false, false, false, false, false);

    private static final CheckpointProperties CHECKPOINT_NEVER_RETAINED =
            new CheckpointProperties(
                    false,
                    CheckpointType.CHECKPOINT,
                    true,
                    true, // Delete on success
                    true, // Delete on cancellation
                    true, // Delete on failure
                    true); // Delete on suspension

    private static final CheckpointProperties CHECKPOINT_RETAINED_ON_FAILURE =
            new CheckpointProperties(
                    false,
                    CheckpointType.CHECKPOINT,
                    true,
                    true, // Delete on success
                    true, // Delete on cancellation
                    false, // Retain on failure
                    true); // Delete on suspension

    private static final CheckpointProperties CHECKPOINT_RETAINED_ON_CANCELLATION =
            new CheckpointProperties(
                    false,
                    CheckpointType.CHECKPOINT,
                    true,
                    true, // Delete on success
                    false, // Retain on cancellation
                    false, // Retain on failure
                    false); // Retain on suspension

    /**
     * Creates the checkpoint properties for a (manually triggered) savepoint.
     * 为（手动触发的）保存点创建检查点属性。
     *
     * <p>Savepoints are not queued due to time trigger limits. They have to be garbage collected
     * manually.
     * 由于时间触发限制，保存点未排队。 它们必须手动进行垃圾收集。
     *
     * @return Checkpoint properties for a (manually triggered) savepoint.
     */
    public static CheckpointProperties forSavepoint(boolean forced) {
        return forced ? SAVEPOINT : SAVEPOINT_NO_FORCE;
    }

    public static CheckpointProperties forSyncSavepoint(boolean forced, boolean terminate) {
        return new CheckpointProperties(
                forced,
                terminate ? CheckpointType.SAVEPOINT_TERMINATE : CheckpointType.SAVEPOINT_SUSPEND,
                false,
                false,
                false,
                false,
                false);
    }

    /**
     * Creates the checkpoint properties for a checkpoint.
     * 为检查点创建检查点属性。
     *
     * <p>Checkpoints may be queued in case too many other checkpoints are currently happening. They
     * are garbage collected automatically, except when the owning job terminates in state {@link
     * JobStatus#FAILED}. The user is required to configure the clean up behaviour on job
     * cancellation.
     * 如果当前发生的其他检查点过多，检查点可能会排队。
     * 它们会自动被垃圾收集，除非拥有的作业以状态 {@link JobStatus#FAILED} 终止。
     * 用户需要配置取消作业时的清理行为。
     *
     * @return Checkpoint properties for an external checkpoint.
     */
    public static CheckpointProperties forCheckpoint(CheckpointRetentionPolicy policy) {
        switch (policy) {
            case NEVER_RETAIN_AFTER_TERMINATION:
                return CHECKPOINT_NEVER_RETAINED;
            case RETAIN_ON_FAILURE:
                return CHECKPOINT_RETAINED_ON_FAILURE;
            case RETAIN_ON_CANCELLATION:
                return CHECKPOINT_RETAINED_ON_CANCELLATION;
            default:
                throw new IllegalArgumentException("unknown policy: " + policy);
        }
    }
}
