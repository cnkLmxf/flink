/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.executiongraph.failover.flip1;

import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * Result containing the tasks to restart upon a task failure. Also contains the reason of the
 * failure and the vertices to restart if the failure is recoverable (in contrast to non-recoverable
 * failure type or restarting suppressed by restart strategy).
 * 包含在任务失败时重新启动的任务的结果。
 * 还包含失败的原因以及如果失败是可恢复的则要重新启动的顶点（与不可恢复的失败类型或重新启动策略抑制的重新启动相反）。
 */
public class FailureHandlingResult {

    /**
     * Task vertices to restart to recover from the failure or {@code null} if the failure is not
     * recoverable.
     * 要重新启动以从故障中恢复的任务顶点，如果故障不可恢复，则返回 {@code null}。
     */
    private final Set<ExecutionVertexID> verticesToRestart;

    /** Delay before the restarting can be conducted.
     * 可以进行重新启动之前的延迟。
     * */
    private final long restartDelayMS;

    /**
     * The {@link ExecutionVertexID} refering to the {@link ExecutionVertex} the failure is
     * originating from or {@code null} if it's a global failure.
     * {@link ExecutionVertexID} 指的是故障源自的 {@link ExecutionVertex} 或 {@code null} 如果它是全局故障。
     */
    @Nullable private final ExecutionVertexID failingExecutionVertexId;

    /** Failure reason. {@code @Nullable} because of FLINK-21376. */
    @Nullable private final Throwable error;

    /** Failure timestamp. */
    private final long timestamp;

    /** True if the original failure was a global failure.
     * 如果原始故障是全局故障，则为真。
     * */
    private final boolean globalFailure;

    /**
     * Creates a result of a set of tasks to restart to recover from the failure.
     * 创建一组任务的结果以重新启动以从故障中恢复。
     *
     * @param failingExecutionVertexId the {@link ExecutionVertexID} referring to the {@link
     *     ExecutionVertex} the failure is originating from. Passing {@code null} as a value
     *     indicates that the failure was issued by Flink itself.
     * @param cause the exception that caused this failure.
     * @param timestamp the time the failure was handled.
     * @param verticesToRestart containing task vertices to restart to recover from the failure.
     *     {@code null} indicates that the failure is not restartable.
     * @param restartDelayMS indicate a delay before conducting the restart
     */
    private FailureHandlingResult(
            @Nullable ExecutionVertexID failingExecutionVertexId,
            @Nullable Throwable cause,
            long timestamp,
            @Nullable Set<ExecutionVertexID> verticesToRestart,
            long restartDelayMS,
            boolean globalFailure) {
        checkState(restartDelayMS >= 0);

        this.verticesToRestart = Collections.unmodifiableSet(checkNotNull(verticesToRestart));
        this.restartDelayMS = restartDelayMS;
        this.failingExecutionVertexId = failingExecutionVertexId;
        this.error = cause;
        this.timestamp = timestamp;
        this.globalFailure = globalFailure;
    }

    /**
     * Creates a result that the failure is not recoverable and no restarting should be conducted.
     * 创建故障不可恢复且不应重新启动的结果。
     *
     * @param failingExecutionVertexId the {@link ExecutionVertexID} referring to the {@link
     *     ExecutionVertex} the failure is originating from. Passing {@code null} as a value
     *     indicates that the failure was issued by Flink itself.
     * @param error reason why the failure is not recoverable
     * @param timestamp the time the failure was handled.
     */
    private FailureHandlingResult(
            @Nullable ExecutionVertexID failingExecutionVertexId,
            @Nonnull Throwable error,
            long timestamp,
            boolean globalFailure) {
        this.verticesToRestart = null;
        this.restartDelayMS = -1;
        this.failingExecutionVertexId = failingExecutionVertexId;
        this.error = checkNotNull(error);
        this.timestamp = timestamp;
        this.globalFailure = globalFailure;
    }

    /**
     * Returns the tasks to restart.
     *
     * @return the tasks to restart
     */
    public Set<ExecutionVertexID> getVerticesToRestart() {
        if (canRestart()) {
            return verticesToRestart;
        } else {
            throw new IllegalStateException(
                    "Cannot get vertices to restart when the restarting is suppressed.");
        }
    }

    /**
     * Returns the delay before the restarting.
     *
     * @return the delay before the restarting
     */
    public long getRestartDelayMS() {
        if (canRestart()) {
            return restartDelayMS;
        } else {
            throw new IllegalStateException(
                    "Cannot get restart delay when the restarting is suppressed.");
        }
    }

    /**
     * Returns an {@code Optional} with the {@link ExecutionVertexID} of the task causing this
     * failure or an empty {@code Optional} if it's a global failure.
     * 返回一个 {@code Optional}，其中包含导致此失败的任务的 {@link ExecutionVertexID}，
     * 如果是全局失败，则返回一个空的 {@code Optional}。
     *
     * @return The {@code ExecutionVertexID} of the causing task or an empty {@code Optional} if
     *     it's a global failure.
     */
    public Optional<ExecutionVertexID> getExecutionVertexIdOfFailedTask() {
        return Optional.ofNullable(failingExecutionVertexId);
    }

    /**
     * Returns reason why the restarting cannot be conducted.
     *
     * @return reason why the restarting cannot be conducted
     */
    @Nullable
    public Throwable getError() {
        return error;
    }

    /**
     * Returns the time of the failure.
     *
     * @return The timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns whether the restarting can be conducted.
     *
     * @return whether the restarting can be conducted
     */
    public boolean canRestart() {
        return verticesToRestart != null;
    }

    /**
     * Checks if this failure was a global failure, i.e., coming from a "safety net" failover that
     * involved all tasks and should reset also components like the coordinators.
     */
    public boolean isGlobalFailure() {
        return globalFailure;
    }

    /**
     * Creates a result of a set of tasks to restart to recover from the failure.
     * 创建一组任务的结果以重新启动以从故障中恢复。
     *
     * <p>The result can be flagged to be from a global failure triggered by the scheduler, rather
     * than from the failure of an individual task.
     * 结果可以被标记为来自调度程序触发的全局故障，而不是来自单个任务的失败。
     *
     * @param failingExecutionVertexId the {@link ExecutionVertexID} refering to the {@link
     *     ExecutionVertex} the failure is originating from. Passing {@code null} as a value
     *     indicates that the failure was issued by Flink itself.
     * @param cause The reason of the failure.
     * @param timestamp The time of the failure.
     * @param verticesToRestart containing task vertices to restart to recover from the failure.
     *     {@code null} indicates that the failure is not restartable.
     * @param restartDelayMS indicate a delay before conducting the restart
     * @return result of a set of tasks to restart to recover from the failure
     */
    public static FailureHandlingResult restartable(
            @Nullable ExecutionVertexID failingExecutionVertexId,
            @Nullable Throwable cause,
            long timestamp,
            @Nullable Set<ExecutionVertexID> verticesToRestart,
            long restartDelayMS,
            boolean globalFailure) {
        return new FailureHandlingResult(
                failingExecutionVertexId,
                cause,
                timestamp,
                verticesToRestart,
                restartDelayMS,
                globalFailure);
    }

    /**
     * Creates a result that the failure is not recoverable and no restarting should be conducted.
     * 创建故障不可恢复且不应重新启动的结果。
     *
     * <p>The result can be flagged to be from a global failure triggered by the scheduler, rather
     * than from the failure of an individual task.
     * 结果可以被标记为来自调度程序触发的全局故障，而不是来自单个任务的失败。
     *
     * @param failingExecutionVertexId the {@link ExecutionVertexID} refering to the {@link
     *     ExecutionVertex} the failure is originating from. Passing {@code null} as a value
     *     indicates that the failure was issued by Flink itself.
     * @param error reason why the failure is not recoverable
     * @param timestamp The time of the failure.
     * @return result indicating the failure is not recoverable
     */
    public static FailureHandlingResult unrecoverable(
            @Nullable ExecutionVertexID failingExecutionVertexId,
            @Nonnull Throwable error,
            long timestamp,
            boolean globalFailure) {
        return new FailureHandlingResult(failingExecutionVertexId, error, timestamp, globalFailure);
    }
}
