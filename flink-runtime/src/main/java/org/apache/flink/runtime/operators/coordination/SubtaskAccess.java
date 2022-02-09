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

package org.apache.flink.runtime.operators.coordination;

import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.util.SerializedValue;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * This interface offers access to a parallel subtask in the scope of the subtask as the target for
 * sending {@link OperatorEvent}s from an {@link OperatorCoordinator}.
 * 此接口提供对子任务范围内的并行子任务的访问，作为从 {@link OperatorCoordinator} 发送 {@link OperatorEvent} 的目标。
 *
 * <p><b>Important:</b> An instance of this access must be bound to one specific execution attempt
 * of the subtask. After that execution attempt failed, that instance must not bind to another
 * execution attempt, but a new instance would need to be created via the {@link
 * SubtaskAccess.SubtaskAccessFactory}.
 * <b>重要提示：</b>此访问的实例必须绑定到子任务的一个特定执行尝试。
 * 在该执行尝试失败后，该实例不得绑定到另一个执行尝试，
 * 但需要通过 {@link SubtaskAccess.SubtaskAccessFactory} 创建一个新实例。
 */
interface SubtaskAccess {

    /**
     * Creates a Callable that, when invoked, sends the event to the execution attempt of the
     * subtask that this {@code SubtaskAccess} instance binds to. The resulting future from the
     * sending is returned by the callable.
     * 创建一个 Callable，当被调用时，将事件发送到此 {@code SubtaskAccess} 实例绑定到的子任务的执行尝试。
     * 发送的结果由可调用返回。
     *
     * <p>This let's the caller target the specific subtask without necessarily sending the event
     * now (for example, the event may be sent at a later point due to checkpoint alignment through
     * the {@link OperatorEventValve}).
     * 这让调用者定位特定的子任务，而不必现在发送事件（例如，由于通过 {@link OperatorEventValve} 对齐检查点，事件可能会在稍后发送）。
     */
    Callable<CompletableFuture<Acknowledge>> createEventSendAction(
            SerializedValue<OperatorEvent> event);

    /** Gets the parallel subtask index of the target subtask.
     * 获取目标子任务的并行子任务索引。
     * */
    int getSubtaskIndex();

    /** Gets the execution attempt ID of the attempt that this instance is bound to.
     * 获取此实例绑定到的尝试的执行尝试 ID。
     * */
    ExecutionAttemptID currentAttempt();

    /**
     * Gets a descriptive name of the operator's subtask , including name, subtask-id, parallelism,
     * and execution attempt.
     * 获取算子子任务的描述性名称，包括名称、子任务ID、并行度和执行尝试。
     */
    String subtaskName();

    /**
     * The future returned here completes once the target subtask is in a running state. As running
     * state classify the states {@link ExecutionState#RUNNING} and {@link
     * ExecutionState#INITIALIZING}.
     * 一旦目标子任务处于运行状态，这里返回的未来就完成了。
     * 作为运行状态分类状态 {@link ExecutionState#RUNNING} 和 {@link ExecutionState#INITIALIZING}。
     */
    CompletableFuture<?> hasSwitchedToRunning();

    /**
     * Checks whether the execution is still in a running state. See {@link #hasSwitchedToRunning()}
     * for details.
     * 检查执行是否仍处于运行状态。 有关详细信息，请参阅 {@link #hasSwitchedToRunning()}。
     */
    boolean isStillRunning();

    /**
     * Triggers a failover for the subtaks execution attempt that this access instance is bound to.
     * 触发此访问实例绑定到的 subtaks 执行尝试的故障转移。
     */
    void triggerTaskFailover(Throwable cause);

    // ------------------------------------------------------------------------

    /**
     * While the {@link SubtaskAccess} is bound to an execution attempt of a subtask (like an {@link
     * org.apache.flink.runtime.executiongraph.Execution}, this factory is bound to the operator as
     * a whole (like in the scope of an {@link
     * org.apache.flink.runtime.executiongraph.ExecutionJobVertex}.
     * 虽然 {@link SubtaskAccess} 绑定到子任务的执行尝试（如 {@link org.apache.flink.runtime.executiongraph.Execution}，
     * 但该工厂绑定到整个操作员（如在范围内） {@link org.apache.flink.runtime.executiongraph.ExecutionJobVertex}。
     */
    interface SubtaskAccessFactory {

        /**
         * Creates an access to the current execution attempt of the subtask with the given
         * subtaskIndex.
         * 使用给定的 subtaskIndex 创建对子任务当前执行尝试的访问。
         */
        SubtaskAccess getAccessForSubtask(int subtaskIndex);
    }
}
