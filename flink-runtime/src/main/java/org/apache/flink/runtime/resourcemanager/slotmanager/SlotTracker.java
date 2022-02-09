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
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.resourcemanager.registration.TaskExecutorConnection;
import org.apache.flink.runtime.taskexecutor.SlotStatus;

import javax.annotation.Nullable;

import java.util.Collection;

/** Tracks slots and their {@link SlotState}.
 * 跟踪插槽及其 {@link SlotState}。
 * */
interface SlotTracker {

    /**
     * Registers the given listener with this tracker.
     * 使用此跟踪器注册给定的侦听器。
     *
     * @param slotStatusUpdateListener listener to register
     */
    void registerSlotStatusUpdateListener(SlotStatusUpdateListener slotStatusUpdateListener);

    /**
     * Adds the given slot to this tracker. The given slot may already be allocated for a job. This
     * method must be called before the tracker is notified of any state transition or slot status
     * notification.
     * 将给定的插槽添加到此跟踪器。 给定的插槽可能已经分配给作业。
     * 必须在跟踪器收到任何状态转换或槽状态通知之前调用此方法。
     *
     * @param slotId ID of the slot
     * @param resourceProfile resource of the slot
     * @param taskManagerConnection connection to the hosting task executor
     * @param initialJob job that the slot is allocated for, or null if it is free
     */
    void addSlot(
            SlotID slotId,
            ResourceProfile resourceProfile,
            TaskExecutorConnection taskManagerConnection,
            @Nullable JobID initialJob);

    /**
     * Removes the given set of slots from the slot manager. If a removed slot was not free at the
     * time of removal, then this method will automatically transition the slot to a free state.
     * 从插槽管理器中删除给定的一组插槽。 如果删除的插槽在删除时不是空闲的，则此方法将自动将插槽转换为空闲状态。
     *
     * @param slotsToRemove identifying the slots to remove from the slot manager
     */
    void removeSlots(Iterable<SlotID> slotsToRemove);

    /**
     * Notifies the tracker that the allocation for the given slot, for the given job, has started.
     * 通知跟踪器给定作业的给定槽分配已经开始。
     *
     * @param slotId slot being allocated
     * @param jobId job for which the slot is being allocated
     */
    void notifyAllocationStart(SlotID slotId, JobID jobId);

    /**
     * Notifies the tracker that the allocation for the given slot, for the given job, has completed
     * successfully.
     * 通知跟踪器给定作业的给定槽分配已成功完成。
     *
     * @param slotId slot being allocated
     * @param jobId job for which the slot is being allocated
     */
    void notifyAllocationComplete(SlotID slotId, JobID jobId);

    /**
     * Notifies the tracker that the given slot was freed.
     * 通知跟踪器给定的插槽已被释放。
     *
     * @param slotId slot being freed
     */
    void notifyFree(SlotID slotId);

    /**
     * Notifies the tracker about the slot statuses.
     * 通知跟踪器有关插槽状态的信息。
     *
     * @param slotStatuses slot statues
     * @return whether any slot status has changed
     */
    boolean notifySlotStatus(Iterable<SlotStatus> slotStatuses);

    /**
     * Returns a view over free slots. The returned collection cannot be modified directly, but
     * reflects changes to the set of free slots.
     * 返回空闲槽的视图。 返回的集合不能直接修改，但会反映空闲槽集的更改。
     *
     * @return free slots
     */
    Collection<TaskManagerSlotInformation> getFreeSlots();

    /**
     * Returns all task executors that have at least 1 pending/completed allocation for the given
     * job.
     * 返回给定作业至少有 1 个待处理/已完成分配的所有任务执行器。
     *
     * @param jobId the job for which the task executors must have a slot
     * @return task executors with at least 1 slot for the job
     */
    Collection<TaskExecutorConnection> getTaskExecutorsWithAllocatedSlotsForJob(JobID jobId);
}
