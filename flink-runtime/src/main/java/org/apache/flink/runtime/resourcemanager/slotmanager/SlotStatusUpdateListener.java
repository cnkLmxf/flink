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

package org.apache.flink.runtime.resourcemanager.slotmanager;

import org.apache.flink.api.common.JobID;

/**
 * Interface for components that want to listen to updates to the status of a slot.
 * 想要监听插槽状态更新的组件的接口。
 *
 * <p>This interface must only be used for updating data-structures, NOT for initiating new resource
 * allocations. The event that caused the state transition may also have triggered a series of
 * transitions, which new allocations would interfere with.
 * 该接口只能用于更新数据结构，不能用于启动新的资源分配。
 * 导致状态转换的事件也可能触发了一系列转换，新分配会干扰这些转换。
 */
interface SlotStatusUpdateListener {

    /**
     * Notification for the status of a slot having changed.
     * 插槽状态已更改的通知。
     *
     * <p>If the slot is being freed ({@code current == FREE} then {@code jobId} is that of the job
     * the slot was allocated for. If the slot was already acquired by a job ({@code current !=
     * FREE}, then {@code jobId} is the ID of this very job.
     * 如果插槽正在被释放（{@code current == FREE}，那么 {@code jobId} 是分配插槽的作业。
     * 如果插槽已被作业获取（{@code current != FREE} ，那么 {@code jobId} 就是这个工作的 ID。
     *
     * @param slot slot whose status has changed
     * @param previous state before the change
     * @param current state after the change
     * @param jobId job for which the slot was/is allocated for
     */
    void notifySlotStatusChange(
            TaskManagerSlotInformation slot, SlotState previous, SlotState current, JobID jobId);
}
