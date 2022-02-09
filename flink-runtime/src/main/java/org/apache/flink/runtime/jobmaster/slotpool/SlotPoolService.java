/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.jobmaster.slotpool;

import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.jobmanager.slots.TaskManagerGateway;
import org.apache.flink.runtime.jobmaster.AllocatedSlotReport;
import org.apache.flink.runtime.jobmaster.JobMaster;
import org.apache.flink.runtime.jobmaster.JobMasterId;
import org.apache.flink.runtime.resourcemanager.ResourceManagerGateway;
import org.apache.flink.runtime.slots.ResourceRequirement;
import org.apache.flink.runtime.taskexecutor.slot.SlotOffer;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Optional;

/** Service used by the {@link JobMaster} to manage a slot pool.
 * {@link JobMaster} 用于管理槽池的服务。
 * */
public interface SlotPoolService extends AutoCloseable {

    /**
     * Tries to cast this slot pool service into the given clazz.
     * 尝试将此槽池服务转换为给定的 clazz。
     *
     * @param clazz to cast the slot pool service into
     * @param <T> type of clazz
     * @return {@link Optional#of} the target type if it can be cast; otherwise {@link
     *     Optional#empty()}
     */
    default <T> Optional<T> castInto(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) {
            return Optional.of(clazz.cast(this));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Start the encapsulated slot pool implementation.
     * 启动封装槽池实现。
     *
     * @param jobMasterId jobMasterId to start the service with
     * @param address address of the owner
     * @param mainThreadExecutor mainThreadExecutor to run actions in the main thread
     * @throws Exception if the the service cannot be started
     */
    void start(
            JobMasterId jobMasterId, String address, ComponentMainThreadExecutor mainThreadExecutor)
            throws Exception;

    /** Close the slot pool service. */
    void close();

    /**
     * Offers multiple slots to the {@link SlotPoolService}. The slot offerings can be individually
     * accepted or rejected by returning the collection of accepted slot offers.
     * 为 {@link SlotPoolService} 提供多个插槽。 通过返回已接受插槽报价的集合，可以单独接受或拒绝插槽报价。
     *
     * @param taskManagerLocation from which the slot offers originate
     * @param taskManagerGateway to talk to the slot offerer
     * @param offers slot offers which are offered to the {@link SlotPoolService}
     * @return A collection of accepted slot offers. The remaining slot offers are implicitly
     *     rejected.
     */
    Collection<SlotOffer> offerSlots(
            TaskManagerLocation taskManagerLocation,
            TaskManagerGateway taskManagerGateway,
            Collection<SlotOffer> offers);

    /**
     * Fails the allocation with the given allocationId.
     * 使用给定的 allocationId 分配失败。
     *
     * @param taskManagerId taskManagerId is non-null if the signal comes from a TaskManager; if the
     *     signal comes from the ResourceManager, then it is null
     * @param allocationId allocationId identifies which allocation to fail
     * @param cause cause why the allocation failed
     * @return Optional task executor if it has no more slots registered
     */
    Optional<ResourceID> failAllocation(
            @Nullable ResourceID taskManagerId, AllocationID allocationId, Exception cause);

    /**
     * Registers a TaskExecutor with the given {@link ResourceID} at {@link SlotPoolService}.
     * 在 {@link SlotPoolService} 注册具有给定 {@link ResourceID} 的 TaskExecutor。
     *
     * @param taskManagerId identifying the TaskExecutor to register
     * @return true iff a new resource id was registered
     */
    boolean registerTaskManager(ResourceID taskManagerId);

    /**
     * Releases a TaskExecutor with the given {@link ResourceID} from the {@link SlotPoolService}.
     * 从 {@link SlotPoolService} 释放具有给定 {@link ResourceID} 的 TaskExecutor。
     *
     * @param taskManagerId identifying the TaskExecutor which shall be released from the SlotPool
     * @param cause for the releasing of the TaskManager
     * @return true iff a given registered resource id was removed
     */
    boolean releaseTaskManager(ResourceID taskManagerId, Exception cause);

    /**
     * Connects the SlotPool to the given ResourceManager. After this method is called, the SlotPool
     * will be able to request resources from the given ResourceManager.
     * 将 SlotPool 连接到给定的 ResourceManager。 调用此方法后，SlotPool 将能够从给定的 ResourceManager 请求资源。
     *
     * @param resourceManagerGateway The RPC gateway for the resource manager.
     */
    void connectToResourceManager(ResourceManagerGateway resourceManagerGateway);

    /**
     * Disconnects the slot pool from its current Resource Manager. After this call, the pool will
     * not be able to request further slots from the Resource Manager, and all currently pending
     * requests to the resource manager will be canceled.
     * 断开槽池与其当前资源管理器的连接。 在此调用之后，池将无法从资源管理器请求更多的插槽，
     * 并且所有当前对资源管理器的待处理请求都将被取消。
     *
     * <p>The slot pool will still be able to serve slots from its internal pool.
     * 插槽池仍将能够从其内部池中提供插槽。
     */
    void disconnectResourceManager();

    /**
     * Create report about the allocated slots belonging to the specified task manager.
     * 创建有关属于指定任务管理器的已分配插槽的报告。
     *
     * @param taskManagerId identifies the task manager
     * @return the allocated slots on the task manager
     */
    AllocatedSlotReport createAllocatedSlotReport(ResourceID taskManagerId);

    /**
     * Notifies that not enough resources are available to fulfill the resource requirements.
     * 通知没有足够的资源可用于满足资源要求。
     *
     * @param acquiredResources the resources that have been acquired
     */
    default void notifyNotEnoughResourcesAvailable(
            Collection<ResourceRequirement> acquiredResources) {}
}
