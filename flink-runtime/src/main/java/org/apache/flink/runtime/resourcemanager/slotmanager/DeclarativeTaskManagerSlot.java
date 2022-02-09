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
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.instance.InstanceID;
import org.apache.flink.runtime.resourcemanager.registration.TaskExecutorConnection;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nullable;

/**
 * A DeclarativeTaskManagerSlot represents a slot located in a TaskExecutor. It contains the
 * necessary information for initiating the allocation of the slot, and keeps track of the state of
 * the slot.
 * DeclarativeTaskManagerSlot 表示位于 TaskExecutor 中的插槽。 它包含启动槽分配的必要信息，并跟踪槽的状态。
 *
 * <p>This class is the declarative-resource-management version of the {@link TaskManagerSlot}.
 * 此类是 {@link TaskManagerSlot} 的声明性资源管理版本。
 */
class DeclarativeTaskManagerSlot implements TaskManagerSlotInformation {

    /** The unique identification of this slot. */
    private final SlotID slotId;

    /** The resource profile of this slot. */
    private final ResourceProfile resourceProfile;

    /** Gateway to the TaskExecutor which owns the slot.
     * 拥有该插槽的 TaskExecutor 的网关。
     * */
    private final TaskExecutorConnection taskManagerConnection;

    /** Job id for which this slot has been allocated. */
    @Nullable private JobID jobId;

    private SlotState state = SlotState.FREE;

    private long allocationStartTimeStamp;

    public DeclarativeTaskManagerSlot(
            SlotID slotId,
            ResourceProfile resourceProfile,
            TaskExecutorConnection taskManagerConnection) {
        this.slotId = slotId;
        this.resourceProfile = resourceProfile;
        this.taskManagerConnection = taskManagerConnection;
    }

    @Override
    public SlotState getState() {
        return state;
    }

    @Override
    public SlotID getSlotId() {
        return slotId;
    }

    @Override
    public AllocationID getAllocationId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResourceProfile getResourceProfile() {
        return resourceProfile;
    }

    @Override
    public TaskExecutorConnection getTaskManagerConnection() {
        return taskManagerConnection;
    }

    @Nullable
    @Override
    public JobID getJobId() {
        return jobId;
    }

    @Override
    public InstanceID getInstanceId() {
        return taskManagerConnection.getInstanceID();
    }

    public long getAllocationStartTimestamp() {
        return allocationStartTimeStamp;
    }

    public void startAllocation(JobID jobId) {
        Preconditions.checkState(
                state == SlotState.FREE, "Slot must be free to be assigned a slot request.");

        this.jobId = jobId;
        this.state = SlotState.PENDING;
        this.allocationStartTimeStamp = System.currentTimeMillis();
    }

    public void completeAllocation() {
        Preconditions.checkState(
                state == SlotState.PENDING,
                "In order to complete an allocation, the slot has to be allocated.");

        this.state = SlotState.ALLOCATED;
    }

    public void freeSlot() {
        Preconditions.checkState(
                state == SlotState.PENDING || state == SlotState.ALLOCATED,
                "Slot must be allocated or pending before freeing it.");

        this.jobId = null;
        this.state = SlotState.FREE;
        this.allocationStartTimeStamp = 0;
    }

    @Override
    public String toString() {
        return "DeclarativeTaskManagerSlot{"
                + "slotId="
                + slotId
                + ", resourceProfile="
                + resourceProfile
                + ", taskManagerConnection="
                + taskManagerConnection
                + ", jobId="
                + jobId
                + ", state="
                + state
                + ", allocationStartTimeStamp="
                + allocationStartTimeStamp
                + '}';
    }
}
