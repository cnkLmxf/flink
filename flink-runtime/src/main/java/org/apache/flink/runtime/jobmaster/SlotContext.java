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

package org.apache.flink.runtime.jobmaster;

import org.apache.flink.runtime.jobmanager.slots.TaskManagerGateway;

/**
 * Interface for the context of a {@link LogicalSlot}. This context contains information about the
 * underlying allocated slot and how to communicate with the TaskManager on which it was allocated.
 * {@link LogicalSlot} 上下文的接口。 此上下文包含有关底层分配槽以及如何与分配它的 TaskManager 通信的信息。
 */
public interface SlotContext extends SlotInfo {

    /**
     * Gets the actor gateway that can be used to send messages to the TaskManager.
     * 获取可用于向任务管理器发送消息的参与者网关。
     *
     * <p>This method should be removed once the new interface-based RPC abstraction is in place
     * 一旦新的基于接口的 RPC 抽象到位，就应该删除这个方法
     *
     * @return The gateway that can be used to send messages to the TaskManager.
     */
    TaskManagerGateway getTaskManagerGateway();
}
