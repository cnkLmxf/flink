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

package org.apache.flink.runtime.taskexecutor;

import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.flink.util.SerializedValue;

import java.util.concurrent.CompletableFuture;

/**
 * The gateway through which the {@link OperatorCoordinator} can send an event to an Operator on the
 * Task Manager side.
 * {@link OperatorCoordinator} 可以通过它向任务管理器端的 Operator 发送事件的网关。
 */
public interface TaskExecutorOperatorEventGateway {

    /**
     * Sends an operator event to an operator in a task executed by the Task Manager (Task
     * Executor).
     * 向任务管理器（Task Executor）执行的任务中的操作员发送操作员事件。
     *
     * <p>The reception is acknowledged (future is completed) when the event has been dispatched to
     * the {@link
     * org.apache.flink.runtime.jobgraph.tasks.AbstractInvokable#dispatchOperatorEvent(OperatorID,
     * SerializedValue)} method. It is not guaranteed that the event is processed successfully
     * within the implementation. These cases are up to the task and event sender to handle (for
     * example with an explicit response message upon success, or by triggering failure/recovery
     * upon exception).
     * 当事件被分派到 {@link org.apache.flink.runtime.jobgraph.tasks.AbstractInvokable#dispatchOperatorEvent(OperatorID, SerializedValue)}
     * 方法时，接收被确认（未来完成）。 不能保证在实现中成功处理事件。
     * 这些情况取决于要处理的任务和事件发送者（例如，成功时使用显式响应消息，或者在异常时触发失败/恢复）。
     */
    CompletableFuture<Acknowledge> sendOperatorEventToTask(
            ExecutionAttemptID task, OperatorID operator, SerializedValue<OperatorEvent> evt);
}
