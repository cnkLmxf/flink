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

package org.apache.flink.runtime.jobgraph.tasks;

import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.jobmaster.JobMasterOperatorEventGateway;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.flink.util.SerializedValue;

/**
 * Gateway to send an {@link OperatorEvent} from a Task to to the {@link OperatorCoordinator}
 * JobManager side.
 * 网关将任务中的 {@link OperatorEvent} 发送到 {@link OperatorCoordinator} JobManager 端。
 *
 * <p>This is the first step in the chain of sending Operator Events from Operator to Coordinator.
 * Each layer adds further context, so that the inner layers do not need to know about the complete
 * context, which keeps dependencies small and makes testing easier.
 * 这是从 Operator 向 Coordinator 发送 Operator Events 链中的第一步。
 * 每一层都添加了进一步的上下文，因此内层不需要了解完整的上下文，这使得依赖关系很小，并且使测试更容易。
 *<pre>
 *     <li>{@code OperatorEventGateway} 接收事件，使用 {@link OperatorID} 丰富事件，并将其转发到：</li>
 *     <li>{@link TaskOperatorEventGateway} 使用 {@link ExecutionAttemptID} 丰富事件并将其转发给：</li>
 *     <li>{@link JobMasterOperatorEventGateway} 是从 TaskManager 到 JobManager 的 RPC 接口。</li>
 *   </pre>
 * <pre>
 *     <li>{@code OperatorEventGateway} takes the event, enriches the event with the {@link OperatorID}, and
 *         forwards it to:</li>
 *     <li>{@link TaskOperatorEventGateway} enriches the event with the {@link ExecutionAttemptID} and
 *         forwards it to the:</li>
 *     <li>{@link JobMasterOperatorEventGateway} which is RPC interface from the TaskManager to the JobManager.</li>
 * </pre>
 */
public interface TaskOperatorEventGateway {

    /**
     * Send an event from the operator (identified by the given operator ID) to the operator
     * coordinator (identified by the same ID).
     * 从操作员（由给定的操作员 ID 标识）向操作员协调器（由相同的 ID 标识）发送一个事件。
     */
    void sendOperatorEventToCoordinator(OperatorID operator, SerializedValue<OperatorEvent> event);
}
