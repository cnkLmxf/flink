/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.clusterframework.types;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.runtime.jobmaster.SlotContext;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A slot profile describes the profile of a slot into which a task wants to be scheduled. The
 * profile contains attributes such as resource or locality constraints, some of which may be hard
 * or soft. It also contains the information of resources for the physical slot to host this task
 * slot, which can be used to allocate a physical slot when no physical slot is available for this
 * task slot. A matcher can be generated to filter out candidate slots by matching their {@link
 * SlotContext} against the slot profile and, potentially, further requirements.
 * 槽配置文件描述了要安排任务的槽的配置文件。 配置文件包含资源或位置约束等属性，其中一些可能是硬的或软的。
 * 它还包含承载该任务槽的物理槽的资源信息，当没有可用于该任务槽的物理槽时，可以使用该信息分配一个物理槽。
 * 可以生成一个匹配器，通过将它们的 {@link SlotContext} 与槽配置文件以及可能的进一步要求进行匹配来过滤出候选槽。
 */
public class SlotProfile {

    /** Singleton object for a slot profile without any requirements.
     * 没有任何要求的插槽配置文件的单例对象。
     * */
    private static final SlotProfile NO_REQUIREMENTS = noLocality(ResourceProfile.UNKNOWN);

    /** This specifies the desired resource profile for the task slot.
     * 这为任务槽指定了所需的资源配置文件。
     * */
    private final ResourceProfile taskResourceProfile;

    /** This specifies the desired resource profile for the physical slot to host this task slot.
     * 这为托管此任务槽的物理槽指定了所需的资源配置文件。
     * */
    private final ResourceProfile physicalSlotResourceProfile;

    /** This specifies the preferred locations for the slot.
     * 这指定了插槽的首选位置。
     * */
    private final Collection<TaskManagerLocation> preferredLocations;

    /** This contains desired allocation ids of the slot.
     * 这包含插槽的所需分配 ID。
     * */
    private final Collection<AllocationID> preferredAllocations;

    /** This contains all prior allocation ids from the whole execution graph.
     * 这包含整个执行图中的所有先前分配 ID。
     * */
    private final Set<AllocationID> previousExecutionGraphAllocations;

    private SlotProfile(
            final ResourceProfile taskResourceProfile,
            final ResourceProfile physicalSlotResourceProfile,
            final Collection<TaskManagerLocation> preferredLocations,
            final Collection<AllocationID> preferredAllocations,
            final Set<AllocationID> previousExecutionGraphAllocations) {

        this.taskResourceProfile = checkNotNull(taskResourceProfile);
        this.physicalSlotResourceProfile = checkNotNull(physicalSlotResourceProfile);
        this.preferredLocations = checkNotNull(preferredLocations);
        this.preferredAllocations = checkNotNull(preferredAllocations);
        this.previousExecutionGraphAllocations = checkNotNull(previousExecutionGraphAllocations);
    }

    /** Returns the desired resource profile for the task slot. */
    public ResourceProfile getTaskResourceProfile() {
        return taskResourceProfile;
    }

    /** Returns the desired resource profile for the physical slot to host this task slot. */
    public ResourceProfile getPhysicalSlotResourceProfile() {
        return physicalSlotResourceProfile;
    }

    /** Returns the preferred locations for the slot. */
    public Collection<TaskManagerLocation> getPreferredLocations() {
        return preferredLocations;
    }

    /** Returns the desired allocation ids for the slot. */
    public Collection<AllocationID> getPreferredAllocations() {
        return preferredAllocations;
    }

    /**
     * Returns a set of all previous allocation ids from the execution graph.
     *
     * <p>This is optional and can be empty if unused.
     */
    public Set<AllocationID> getPreviousExecutionGraphAllocations() {
        return previousExecutionGraphAllocations;
    }

    /** Returns a slot profile that has no requirements. */
    @VisibleForTesting
    public static SlotProfile noRequirements() {
        return NO_REQUIREMENTS;
    }

    /** Returns a slot profile for the given resource profile, without any locality requirements. */
    @VisibleForTesting
    public static SlotProfile noLocality(ResourceProfile resourceProfile) {
        return preferredLocality(resourceProfile, Collections.emptyList());
    }

    /**
     * Returns a slot profile for the given resource profile and the preferred locations.
     *
     * @param resourceProfile specifying the slot requirements
     * @param preferredLocations specifying the preferred locations
     * @return Slot profile with the given resource profile and preferred locations
     */
    @VisibleForTesting
    public static SlotProfile preferredLocality(
            final ResourceProfile resourceProfile,
            final Collection<TaskManagerLocation> preferredLocations) {

        return priorAllocation(
                resourceProfile,
                resourceProfile,
                preferredLocations,
                Collections.emptyList(),
                Collections.emptySet());
    }

    /**
     * Returns a slot profile for the given resource profile, prior allocations and all prior
     * allocation ids from the whole execution graph.
     * 从整个执行图中返回给定资源配置文件、先前分配和所有先前分配 ID 的槽配置文件。
     *
     * @param taskResourceProfile specifying the required resources for the task slot
     * @param physicalSlotResourceProfile specifying the required resources for the physical slot to
     *     host this task slot
     * @param preferredLocations specifying the preferred locations
     * @param priorAllocations specifying the prior allocations
     * @param previousExecutionGraphAllocations specifying all prior allocation ids from the whole
     *     execution graph
     * @return Slot profile with all the given information
     */
    public static SlotProfile priorAllocation(
            final ResourceProfile taskResourceProfile,
            final ResourceProfile physicalSlotResourceProfile,
            final Collection<TaskManagerLocation> preferredLocations,
            final Collection<AllocationID> priorAllocations,
            final Set<AllocationID> previousExecutionGraphAllocations) {

        return new SlotProfile(
                taskResourceProfile,
                physicalSlotResourceProfile,
                preferredLocations,
                priorAllocations,
                previousExecutionGraphAllocations);
    }
}
