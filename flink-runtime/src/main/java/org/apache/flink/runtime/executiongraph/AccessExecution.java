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

import org.apache.flink.runtime.accumulators.StringifiedAccumulatorResult;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;

import java.util.Optional;

/** Common interface for the runtime {@link Execution} and {@link ArchivedExecution}.
 * 运行时 {@link Execution} 和 {@link ArchivedExecution} 的通用接口。
 * */
public interface AccessExecution {
    /**
     * Returns the {@link ExecutionAttemptID} for this Execution.
     *
     * @return ExecutionAttemptID for this execution
     */
    ExecutionAttemptID getAttemptId();

    /**
     * Returns the attempt number for this execution.
     *
     * @return attempt number for this execution.
     */
    int getAttemptNumber();

    /**
     * Returns the timestamps for every {@link ExecutionState}.
     *
     * @return timestamps for each state
     */
    long[] getStateTimestamps();

    /**
     * Returns the current {@link ExecutionState} for this execution.
     *
     * @return execution state for this execution
     */
    ExecutionState getState();

    /**
     * Returns the {@link TaskManagerLocation} for this execution.
     *
     * @return taskmanager location for this execution.
     */
    TaskManagerLocation getAssignedResourceLocation();

    /**
     * Returns the exception that caused the job to fail. This is the first root exception that was
     * not recoverable and triggered job failure.
     * 返回导致作业失败的异常。 这是第一个不可恢复并触发作业失败的根异常。
     *
     * @return an {@code Optional} of {@link ErrorInfo} containing the {@code Throwable} and the
     *     time it was registered if an error occurred. If no error occurred an empty {@code
     *     Optional} will be returned.
     */
    Optional<ErrorInfo> getFailureInfo();

    /**
     * Returns the timestamp for the given {@link ExecutionState}.
     *
     * @param state state for which the timestamp should be returned
     * @return timestamp for the given state
     */
    long getStateTimestamp(ExecutionState state);

    /**
     * Returns the user-defined accumulators as strings.
     * 将用户定义的累加器作为字符串返回。
     *
     * @return user-defined accumulators as strings.
     */
    StringifiedAccumulatorResult[] getUserAccumulatorsStringified();

    /**
     * Returns the subtask index of this execution.
     *
     * @return subtask index of this execution.
     */
    int getParallelSubtaskIndex();

    IOMetrics getIOMetrics();
}
