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

package org.apache.flink.runtime.scheduler;

import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlotRequestBulk;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlotRequestBulkChecker;
import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Implementation of {@link PhysicalSlotRequestBulk} for {@link SlotSharingExecutionSlotAllocator}.
 *
 * <p>The bulk tracks the pending and fulfilled requests by the {@link ExecutionSlotSharingGroup}
 * for which the physical slot is being allocated. {@link SlotSharingExecutionSlotAllocator} calls
 * {@link #markFulfilled(ExecutionSlotSharingGroup, AllocationID)} to move a pending request to
 * fulfilled.
 * 批量跟踪正在为其分配物理槽的 {@link ExecutionSlotSharingGroup} 的未决和已完成的请求。
 * {@link SlotSharingExecutionSlotAllocator} 调用
 * {@link #markFulfilled(ExecutionSlotSharingGroup, AllocationID)} 将待处理的请求移至已完成。
 *
 * <p>Additionally the bulk keeps execution lists for each {@link ExecutionSlotSharingGroup} they
 * belong to. If the {@link #cancel(Throwable)} method is called (due to fulfillability check
 * timeout in {@link PhysicalSlotRequestBulkChecker}) then the bulk calls back the {@link
 * SlotSharingExecutionSlotAllocator} to cancel all logical slots requests for executions of all
 * bulk's {@link ExecutionSlotSharingGroup}s.
 * 此外，批量保留它们所属的每个 {@link ExecutionSlotSharingGroup} 的执行列表。
 * 如果调用 {@link #cancel(Throwable)} 方法（由于 {@link PhysicalSlotRequestBulkChecker} 中的可履行性检查超时），
 * 则批量回调 {@link SlotSharingExecutionSlotAllocator} 以取消所有逻辑槽请求以执行所有批量的 { @link ExecutionSlotSharingGroup}。
 */
class SharingPhysicalSlotRequestBulk implements PhysicalSlotRequestBulk {
    private final Map<ExecutionSlotSharingGroup, List<ExecutionVertexID>> executions;

    private final Map<ExecutionSlotSharingGroup, ResourceProfile> pendingRequests;

    private final Map<ExecutionSlotSharingGroup, AllocationID> fulfilledRequests;

    private final BiConsumer<ExecutionVertexID, Throwable> logicalSlotRequestCanceller;

    SharingPhysicalSlotRequestBulk(
            Map<ExecutionSlotSharingGroup, List<ExecutionVertexID>> executions,
            Map<ExecutionSlotSharingGroup, ResourceProfile> pendingRequests,
            BiConsumer<ExecutionVertexID, Throwable> logicalSlotRequestCanceller) {
        this.executions = checkNotNull(executions);
        this.pendingRequests = checkNotNull(pendingRequests);
        this.fulfilledRequests = new HashMap<>();
        this.logicalSlotRequestCanceller = checkNotNull(logicalSlotRequestCanceller);
    }

    @Override
    public Collection<ResourceProfile> getPendingRequests() {
        return pendingRequests.values();
    }

    @Override
    public Set<AllocationID> getAllocationIdsOfFulfilledRequests() {
        return new HashSet<>(fulfilledRequests.values());
    }

    @Override
    public void cancel(Throwable cause) {
        // pending requests must be canceled first otherwise they might be fulfilled by
        // allocated slots released from this bulk
        for (ExecutionSlotSharingGroup group : pendingRequests.keySet()) {
            for (ExecutionVertexID id : executions.get(group)) {
                logicalSlotRequestCanceller.accept(id, cause);
            }
        }
        for (ExecutionSlotSharingGroup group : fulfilledRequests.keySet()) {
            for (ExecutionVertexID id : executions.get(group)) {
                logicalSlotRequestCanceller.accept(id, cause);
            }
        }
    }

    /**
     * Moves a pending request to fulfilled.
     *
     * @param group {@link ExecutionSlotSharingGroup} of the pending request
     * @param allocationId {@link AllocationID} of the fulfilled request
     */
    void markFulfilled(ExecutionSlotSharingGroup group, AllocationID allocationId) {
        pendingRequests.remove(group);
        fulfilledRequests.put(group, allocationId);
    }

    /**
     * Clear the pending requests.
     *
     * <p>The method can be used to make the bulk fulfilled and stop the fulfillability check in
     * {@link PhysicalSlotRequestBulkChecker}.
     */
    void clearPendingRequests() {
        pendingRequests.clear();
    }
}
