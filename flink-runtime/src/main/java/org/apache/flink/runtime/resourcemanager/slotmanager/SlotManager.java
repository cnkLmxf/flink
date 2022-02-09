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

package org.apache.flink.runtime.resourcemanager.slotmanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.instance.InstanceID;
import org.apache.flink.runtime.resourcemanager.ResourceManagerId;
import org.apache.flink.runtime.resourcemanager.SlotRequest;
import org.apache.flink.runtime.resourcemanager.WorkerResourceSpec;
import org.apache.flink.runtime.resourcemanager.exceptions.ResourceManagerException;
import org.apache.flink.runtime.resourcemanager.registration.TaskExecutorConnection;
import org.apache.flink.runtime.rest.messages.taskmanager.SlotInfo;
import org.apache.flink.runtime.slots.ResourceRequirements;
import org.apache.flink.runtime.taskexecutor.SlotReport;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The slot manager is responsible for maintaining a view on all registered task manager slots,
 * their allocation and all pending slot requests. Whenever a new slot is registered or an allocated
 * slot is freed, then it tries to fulfill another pending slot request. Whenever there are not
 * enough slots available the slot manager will notify the resource manager about it via {@link
 * ResourceActions#allocateResource(WorkerResourceSpec)}.
 * 槽管理器负责维护所有已注册的任务管理器槽、它们的分配和所有待处理槽请求的视图。
 * 每当注册新插槽或释放分配的插槽时，它都会尝试满足另一个挂起的插槽请求。
 * 每当没有足够的可用插槽时，插槽管理器将通过 {@link ResourceActions#allocateResource(WorkerResourceSpec)} 通知资源管理器。
 *
 * <p>In order to free resources and avoid resource leaks, idling task managers (task managers whose
 * slots are currently not used) and pending slot requests time out triggering their release and
 * failure, respectively.
 * 为了释放资源并避免资源泄漏，空闲任务管理器（当前未使用槽的任务管理器）和挂起的槽请求超时分别触发它们的释放和失败。
 */
public interface SlotManager extends AutoCloseable {
    int getNumberRegisteredSlots();

    int getNumberRegisteredSlotsOf(InstanceID instanceId);

    int getNumberFreeSlots();

    int getNumberFreeSlotsOf(InstanceID instanceId);

    /**
     * Get number of workers SlotManager requested from {@link ResourceActions} that are not yet
     * fulfilled.
     * 获取从 {@link ResourceActions} 请求但尚未完成的工作人员 SlotManager 的数量。
     *
     * @return a map whose key set is all the unique resource specs of the pending workers, and the
     *     corresponding value is number of pending workers of that resource spec.
     */
    Map<WorkerResourceSpec, Integer> getRequiredResources();

    ResourceProfile getRegisteredResource();

    ResourceProfile getRegisteredResourceOf(InstanceID instanceID);

    ResourceProfile getFreeResource();

    ResourceProfile getFreeResourceOf(InstanceID instanceID);

    Collection<SlotInfo> getAllocatedSlotsOf(InstanceID instanceID);

    int getNumberPendingSlotRequests();

    /**
     * Starts the slot manager with the given leader id and resource manager actions.
     * 使用给定的领导者 ID 和资源管理器操作启动槽管理器。
     *
     * @param newResourceManagerId to use for communication with the task managers
     * @param newMainThreadExecutor to use to run code in the ResourceManager's main thread
     * @param newResourceActions to use for resource (de-)allocations
     */
    void start(
            ResourceManagerId newResourceManagerId,
            Executor newMainThreadExecutor,
            ResourceActions newResourceActions);

    /** Suspends the component. This clears the internal state of the slot manager.
     * 挂起组件。 这将清除槽管理器的内部状态。
     * */
    void suspend();

    /**
     * Notifies the slot manager that the resource requirements for the given job should be cleared.
     * The slot manager may assume that no further updates to the resource requirements will occur.
     * 通知槽管理器应该清除给定作业的资源需求。 时隙管理器可能假设不会发生对资源需求的进一步更新。
     *
     * @param jobId job for which to clear the requirements
     */
    void clearResourceRequirements(JobID jobId);

    /**
     * Notifies the slot manager about the resource requirements of a job.
     * 通知槽管理器有关作业的资源需求。
     *
     * @param resourceRequirements resource requirements of a job
     */
    void processResourceRequirements(ResourceRequirements resourceRequirements);

    /**
     * Requests a slot with the respective resource profile.
     * 请求具有相应资源配置文件的插槽。
     *
     * @param slotRequest specifying the requested slot specs
     * @return true if the slot request was registered; false if the request is a duplicate
     * @throws ResourceManagerException if the slot request failed (e.g. not enough resources left)
     */
    default boolean registerSlotRequest(SlotRequest slotRequest) throws ResourceManagerException {
        throw new UnsupportedOperationException();
    }

    /**
     * Cancels and removes a pending slot request with the given allocation id. If there is no such
     * pending request, then nothing is done.
     * 取消并删除具有给定分配 ID 的待处理槽请求。 如果没有这样的未决请求，则什么也不做。
     *
     * @param allocationId identifying the pending slot request
     * @return True if a pending slot request was found; otherwise false
     */
    default boolean unregisterSlotRequest(AllocationID allocationId) {
        throw new UnsupportedOperationException();
    }

    /**
     * Registers a new task manager at the slot manager. This will make the task managers slots
     * known and, thus, available for allocation.
     * 在槽管理器中注册一个新的任务管理器。 这将使任务管理器插槽已知，因此可用于分配。
     *
     * @param taskExecutorConnection for the new task manager
     * @param initialSlotReport for the new task manager
     * @param totalResourceProfile for the new task manager
     * @param defaultSlotResourceProfile for the new task manager
     * @return True if the task manager has not been registered before and is registered
     *     successfully; otherwise false
     */
    boolean registerTaskManager(
            TaskExecutorConnection taskExecutorConnection,
            SlotReport initialSlotReport,
            ResourceProfile totalResourceProfile,
            ResourceProfile defaultSlotResourceProfile);

    /**
     * Unregisters the task manager identified by the given instance id and its associated slots
     * from the slot manager.
     * 从槽管理器中注销由给定实例 ID 及其关联槽标识的任务管理器。
     *
     * @param instanceId identifying the task manager to unregister
     * @param cause for unregistering the TaskManager
     * @return True if there existed a registered task manager with the given instance id
     */
    boolean unregisterTaskManager(InstanceID instanceId, Exception cause);

    /**
     * Reports the current slot allocations for a task manager identified by the given instance id.
     * 报告给定实例 id 标识的任务管理器的当前槽分配。
     *
     * @param instanceId identifying the task manager for which to report the slot status
     * @param slotReport containing the status for all of its slots
     * @return true if the slot status has been updated successfully, otherwise false
     */
    boolean reportSlotStatus(InstanceID instanceId, SlotReport slotReport);

    /**
     * Free the given slot from the given allocation. If the slot is still allocated by the given
     * allocation id, then the slot will be marked as free and will be subject to new slot requests.
     * 从给定的分配中释放给定的插槽。 如果插槽仍然由给定的分配 id 分配，则该插槽将被标记为空闲，并将接受新的插槽请求。
     *
     * @param slotId identifying the slot to free
     * @param allocationId with which the slot is presumably allocated
     */
    void freeSlot(SlotID slotId, AllocationID allocationId);

    void setFailUnfulfillableRequest(boolean failUnfulfillableRequest);
}
