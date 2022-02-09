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

import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.jobgraph.tasks.TaskOperatorEventGateway;
import org.apache.flink.runtime.jobmaster.JobMasterOperatorEventGateway;

/**
 * The gateway through which an Operator can send an {@link OperatorEvent} to the {@link
 * OperatorCoordinator} on the JobManager side.
 * Operator 可以通过它向 JobManager 端的 {@link OperatorCoordinator} 发送 {@link OperatorEvent} 的网关。
 *
 * <p>This is the first step in the chain of sending Operator Events from Operator to Coordinator.
 * Each layer adds further context, so that the inner layers do not need to know about the complete
 * context, which keeps dependencies small and makes testing easier.
 * 这是从 Operator 向 Coordinator 发送 Operator Events 链中的第一步。
 * 每一层都添加了进一步的上下文，因此内层不需要了解完整的上下文，这使得依赖关系很小，并且使测试更容易。
 *<pre>
 *     <li>{@code OperatorEventGateway} 接收事件，使用 {@link OperatorID} 丰富事件，并将其转发到：</li>
 *     <li>{@link TaskOperatorEventGateway} 使用 {@link ExecutionAttemptID} 丰富事件并将其转发给：</li>
 *   <li>{@link JobMasterOperatorEventGateway} 是从 TaskManager 到 JobManager 的 RPC 接口。</li>
 * </pre>
 * <pre>
 *     <li>{@code OperatorEventGateway} takes the event, enriches the event with the {@link OperatorID}, and
 *         forwards it to:</li>
 *     <li>{@link TaskOperatorEventGateway} enriches the event with the {@link ExecutionAttemptID} and
 *         forwards it to the:</li>
 *     <li>{@link JobMasterOperatorEventGateway} which is RPC interface from the TaskManager to the JobManager.</li>
 * </pre>
 */
public interface OperatorEventGateway {

    /**
     * Sends the given event to the coordinator, where it will be handled by the {@link
     * OperatorCoordinator#handleEventFromOperator(int, OperatorEvent)} method.
     * 将给定事件发送到协调器，由 {@link OperatorCoordinator#handleEventFromOperator(int, OperatorEvent)} 方法处理。
     */
    void sendEventToCoordinator(OperatorEvent event);
}
