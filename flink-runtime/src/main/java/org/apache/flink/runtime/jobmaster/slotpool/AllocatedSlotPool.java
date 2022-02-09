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
import org.apache.flink.runtime.jobmaster.SlotInfo;

import java.util.Collection;
import java.util.Optional;

/** The slot pool is responsible for maintaining a set of {@link AllocatedSlot AllocatedSlots}.
 * 槽池负责维护一组{@link AllocatedSlot AllocatedSlots}。
 * */
public interface AllocatedSlotPool {

    /**
     * Adds the given collection of slots to the slot pool.
     * 将给定的槽集合添加到槽池中。
     *
     * @param slots slots to add to the slot pool
     * @param currentTime currentTime when the slots have been added to the slot pool
     * @throws IllegalStateException if the slot pool already contains a to be added slot
     */
    void addSlots(Collection<AllocatedSlot> slots, long currentTime);

    /**
     * Removes the slot with the given allocationId from the slot pool.
     * 从槽池中移除具有给定 allocationId 的槽。
     *
     * @param allocationId allocationId identifying the slot to remove from the slot pool
     * @return the removed slot if there was a slot with the given allocationId; otherwise {@link
     *     Optional#empty()}
     */
    Optional<AllocatedSlot> removeSlot(AllocationID allocationId);

    /**
     * Removes all slots belonging to the owning TaskExecutor identified by owner.
     * 删除属于所有者标识的拥有 TaskExecutor 的所有槽。
     *
     * @param owner owner identifies the TaskExecutor whose slots shall be removed
     * @return the collection of removed slots
     */
    Collection<AllocatedSlot> removeSlots(ResourceID owner);

    /**
     * Checks whether the slot pool contains at least one slot belonging to the specified owner.
     * 检查槽池是否包含至少一个属于指定所有者的槽。
     *
     * @param owner owner for which to check whether the slot pool contains slots
     * @return {@code true} if the slot pool contains a slot from the given owner; otherwise {@code
     *     false}
     */
    boolean containsSlots(ResourceID owner);

    /**
     * Checks whether the slot pool contains a slot with the given allocationId.
     * 检查槽池是否包含具有给定 allocationId 的槽。
     *
     * @param allocationId allocationId identifying the slot for which to check whether it is
     *     contained
     * @return {@code true} if the slot pool contains the slot with the given allocationId;
     *     otherwise {@code false}
     */
    boolean containsSlot(AllocationID allocationId);

    /**
     * Checks whether the slot pool contains a slot with the given {@link AllocationID} and if it is
     * free.
     * 检查槽池是否包含具有给定 {@link AllocationID} 的槽以及它是否空闲。
     *
     * @param allocationId allocationId specifies the slot to check for
     * @return {@code true} if the slot pool contains a free slot registered under the given
     *     allocation id; otherwise {@code false}
     */
    boolean containsFreeSlot(AllocationID allocationId);

    /**
     * Reserves the free slot specified by the given allocationId.
     * 保留给定的 allocationId 指定的空闲槽。
     *
     * @param allocationId allocationId identifying the free slot to reserve
     * @return the {@link AllocatedSlot} which has been reserved
     * @throws IllegalStateException if there is no free slot with the given allocationId
     */
    AllocatedSlot reserveFreeSlot(AllocationID allocationId);

    /**
     * Frees the reserved slot, adding it back into the set of free slots.
     * 释放保留的插槽，将其重新添加到空闲插槽集中。
     *
     * @param allocationId identifying the reserved slot to freed
     * @param currentTime currentTime when the slot has been freed
     * @return the freed {@link AllocatedSlot} if there was an allocated with the given
     *     allocationId; otherwise {@link Optional#empty()}.
     */
    Optional<AllocatedSlot> freeReservedSlot(AllocationID allocationId, long currentTime);

    /**
     * Returns information about all currently free slots.
     * 返回有关所有当前空闲插槽的信息。
     *
     * @return collection of free slot information
     */
    Collection<FreeSlotInfo> getFreeSlotsInformation();

    /**
     * Returns information about all slots in this pool.
     * 返回有关此池中所有插槽的信息。
     *
     * @return collection of all slot information
     */
    Collection<? extends SlotInfo> getAllSlotsInformation();

    /** Information about a free slot. */
    interface FreeSlotInfo {
        SlotInfoWithUtilization asSlotInfo();

        /**
         * Returns since when this slot is free.
         * 自此插槽空闲时返回。
         *
         * @return the time since when the slot is free
         */
        long getFreeSince();

        default AllocationID getAllocationId() {
            return asSlotInfo().getAllocationId();
        }
    }
}
