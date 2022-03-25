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
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.concurrent.ScheduledExecutor;
import org.apache.flink.runtime.instance.InstanceID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.metrics.MetricNames;
import org.apache.flink.runtime.metrics.groups.SlotManagerMetricGroup;
import org.apache.flink.runtime.resourcemanager.ResourceManagerId;
import org.apache.flink.runtime.resourcemanager.WorkerResourceSpec;
import org.apache.flink.runtime.resourcemanager.registration.TaskExecutorConnection;
import org.apache.flink.runtime.rest.messages.taskmanager.SlotInfo;
import org.apache.flink.runtime.slots.ResourceRequirement;
import org.apache.flink.runtime.slots.ResourceRequirements;
import org.apache.flink.runtime.taskexecutor.SlotReport;
import org.apache.flink.runtime.taskexecutor.SlotStatus;
import org.apache.flink.runtime.taskexecutor.TaskExecutorGateway;
import org.apache.flink.runtime.taskexecutor.exceptions.SlotOccupiedException;
import org.apache.flink.runtime.util.ResourceCounter;
import org.apache.flink.util.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Implementation of {@link SlotManager} supporting declarative slot management.
 * 支持声明式插槽管理的 {@link SlotManager} 的实现。
 * */
public class DeclarativeSlotManager implements SlotManager {
    private static final Logger LOG = LoggerFactory.getLogger(DeclarativeSlotManager.class);

    private final SlotTracker slotTracker;
    private final ResourceTracker resourceTracker;
    private final BiFunction<Executor, ResourceActions, TaskExecutorManager>
            taskExecutorManagerFactory;
    @Nullable private TaskExecutorManager taskExecutorManager;

    /** Timeout for slot requests to the task manager. */
    private final Time taskManagerRequestTimeout;

    private final SlotMatchingStrategy slotMatchingStrategy;

    private final SlotManagerMetricGroup slotManagerMetricGroup;

    private final Map<JobID, String> jobMasterTargetAddresses = new HashMap<>();
    private final Map<SlotID, AllocationID> pendingSlotAllocations;

    private boolean sendNotEnoughResourceNotifications = true;

    /** ResourceManager's id. */
    @Nullable private ResourceManagerId resourceManagerId;

    /** Executor for future callbacks which have to be "synchronized".
     * 必须“同步”的未来回调的执行者。
     * */
    @Nullable private Executor mainThreadExecutor;

    /** Callbacks for resource (de-)allocations. */
    @Nullable private ResourceActions resourceActions;

    /** True iff the component has been started. */
    private boolean started;

    public DeclarativeSlotManager(
            ScheduledExecutor scheduledExecutor,
            SlotManagerConfiguration slotManagerConfiguration,
            SlotManagerMetricGroup slotManagerMetricGroup,
            ResourceTracker resourceTracker,
            SlotTracker slotTracker) {

        Preconditions.checkNotNull(slotManagerConfiguration);
        this.taskManagerRequestTimeout = slotManagerConfiguration.getTaskManagerRequestTimeout();
        this.slotManagerMetricGroup = Preconditions.checkNotNull(slotManagerMetricGroup);
        this.resourceTracker = Preconditions.checkNotNull(resourceTracker);

        pendingSlotAllocations = new HashMap<>(16);

        this.slotTracker = Preconditions.checkNotNull(slotTracker);
        slotTracker.registerSlotStatusUpdateListener(createSlotStatusUpdateListener());

        slotMatchingStrategy = slotManagerConfiguration.getSlotMatchingStrategy();

        taskExecutorManagerFactory =
                (executor, resourceActions) ->
                        new TaskExecutorManager(
                                slotManagerConfiguration.getDefaultWorkerResourceSpec(),
                                slotManagerConfiguration.getNumSlotsPerWorker(),
                                slotManagerConfiguration.getMaxSlotNum(),
                                slotManagerConfiguration.isWaitResultConsumedBeforeRelease(),
                                slotManagerConfiguration.getRedundantTaskManagerNum(),
                                slotManagerConfiguration.getTaskManagerTimeout(),
                                scheduledExecutor,
                                executor,
                                resourceActions);

        resourceManagerId = null;
        resourceActions = null;
        mainThreadExecutor = null;
        taskExecutorManager = null;

        started = false;
    }

    private SlotStatusUpdateListener createSlotStatusUpdateListener() {
        return (taskManagerSlot, previous, current, jobId) -> {
            if (previous == SlotState.PENDING) {
                pendingSlotAllocations.remove(taskManagerSlot.getSlotId());
            }

            if (current == SlotState.PENDING) {
                resourceTracker.notifyAcquiredResource(jobId, taskManagerSlot.getResourceProfile());
            }
            if (current == SlotState.FREE) {
                resourceTracker.notifyLostResource(jobId, taskManagerSlot.getResourceProfile());
            }

            if (current == SlotState.ALLOCATED) {
                taskExecutorManager.occupySlot(taskManagerSlot.getInstanceId());
            }
            if (previous == SlotState.ALLOCATED && current == SlotState.FREE) {
                taskExecutorManager.freeSlot(taskManagerSlot.getInstanceId());
            }
        };
    }

    @Override
    public void setFailUnfulfillableRequest(boolean failUnfulfillableRequest) {
        // this sets up a grace period, e.g., when the cluster was started, to give task executors
        // time to connect
        sendNotEnoughResourceNotifications = failUnfulfillableRequest;

        if (failUnfulfillableRequest) {
            checkResourceRequirements();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Component lifecycle methods
    // ---------------------------------------------------------------------------------------------

    /**
     * Starts the slot manager with the given leader id and resource manager actions.
     * 使用给定的领导者 ID 和资源管理器操作启动槽管理器。
     *
     * @param newResourceManagerId to use for communication with the task managers
     * @param newMainThreadExecutor to use to run code in the ResourceManager's main thread
     * @param newResourceActions to use for resource (de-)allocations
     */
    @Override
    public void start(
            ResourceManagerId newResourceManagerId,
            Executor newMainThreadExecutor,
            ResourceActions newResourceActions) {
        LOG.debug("Starting the slot manager.");

        this.resourceManagerId = Preconditions.checkNotNull(newResourceManagerId);
        mainThreadExecutor = Preconditions.checkNotNull(newMainThreadExecutor);
        resourceActions = Preconditions.checkNotNull(newResourceActions);
        taskExecutorManager =
                taskExecutorManagerFactory.apply(newMainThreadExecutor, newResourceActions);

        started = true;

        registerSlotManagerMetrics();
    }

    private void registerSlotManagerMetrics() {
        slotManagerMetricGroup.gauge(
                MetricNames.TASK_SLOTS_AVAILABLE, () -> (long) getNumberFreeSlots());
        slotManagerMetricGroup.gauge(
                MetricNames.TASK_SLOTS_TOTAL, () -> (long) getNumberRegisteredSlots());
    }

    /** Suspends the component. This clears the internal state of the slot manager.
     * 挂起组件。 这将清除槽管理器的内部状态。
     * */
    @Override
    public void suspend() {
        if (!started) {
            return;
        }

        LOG.info("Suspending the slot manager.");

        slotManagerMetricGroup.close();

        resourceTracker.clear();
        if (taskExecutorManager != null) {
            taskExecutorManager.close();

            for (InstanceID registeredTaskManager : taskExecutorManager.getTaskExecutors()) {
                unregisterTaskManager(
                        registeredTaskManager,
                        new SlotManagerException("The slot manager is being suspended."));
            }
        }

        taskExecutorManager = null;
        resourceManagerId = null;
        resourceActions = null;
        started = false;
    }

    /**
     * Closes the slot manager.
     *
     * @throws Exception if the close operation fails
     */
    @Override
    public void close() throws Exception {
        LOG.info("Closing the slot manager.");

        suspend();
    }

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    @Override
    public void clearResourceRequirements(JobID jobId) {
        checkInit();
        maybeReclaimInactiveSlots(jobId);
        jobMasterTargetAddresses.remove(jobId);
        resourceTracker.notifyResourceRequirements(jobId, Collections.emptyList());
    }

    @Override
    public void processResourceRequirements(ResourceRequirements resourceRequirements) {
        checkInit();
        if (resourceRequirements.getResourceRequirements().isEmpty()) {
            LOG.info("Clearing resource requirements of job {}", resourceRequirements.getJobId());
        } else {
            LOG.info(
                    "Received resource requirements from job {}: {}",
                    resourceRequirements.getJobId(),
                    resourceRequirements.getResourceRequirements());
        }

        if (!resourceRequirements.getResourceRequirements().isEmpty()) {
            jobMasterTargetAddresses.put(
                    resourceRequirements.getJobId(), resourceRequirements.getTargetAddress());
        }
        resourceTracker.notifyResourceRequirements(
                resourceRequirements.getJobId(), resourceRequirements.getResourceRequirements());
        checkResourceRequirements();
    }

    private void maybeReclaimInactiveSlots(JobID jobId) {
        if (!resourceTracker.getAcquiredResources(jobId).isEmpty()) {
            final Collection<TaskExecutorConnection> taskExecutorsWithAllocatedSlots =
                    slotTracker.getTaskExecutorsWithAllocatedSlotsForJob(jobId);
            for (TaskExecutorConnection taskExecutorConnection : taskExecutorsWithAllocatedSlots) {
                final TaskExecutorGateway taskExecutorGateway =
                        taskExecutorConnection.getTaskExecutorGateway();
                taskExecutorGateway.freeInactiveSlots(jobId, taskManagerRequestTimeout);
            }
        }
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
    @Override
    public boolean registerTaskManager(
            final TaskExecutorConnection taskExecutorConnection,
            SlotReport initialSlotReport,
            ResourceProfile totalResourceProfile,
            ResourceProfile defaultSlotResourceProfile) {
        checkInit();
        LOG.debug(
                "Registering task executor {} under {} at the slot manager.",
                taskExecutorConnection.getResourceID(),
                taskExecutorConnection.getInstanceID());

        // we identify task managers by their instance id
        if (taskExecutorManager.isTaskManagerRegistered(taskExecutorConnection.getInstanceID())) {
            LOG.debug(
                    "Task executor {} was already registered.",
                    taskExecutorConnection.getResourceID());
            reportSlotStatus(taskExecutorConnection.getInstanceID(), initialSlotReport);
            return false;
        } else {
            if (!taskExecutorManager.registerTaskManager(
                    taskExecutorConnection,
                    initialSlotReport,
                    totalResourceProfile,
                    defaultSlotResourceProfile)) {
                LOG.debug(
                        "Task executor {} could not be registered.",
                        taskExecutorConnection.getResourceID());
                return false;
            }

            // register the new slots
            for (SlotStatus slotStatus : initialSlotReport) {
                slotTracker.addSlot(
                        slotStatus.getSlotID(),
                        slotStatus.getResourceProfile(),
                        taskExecutorConnection,
                        slotStatus.getJobID());
            }

            checkResourceRequirements();
            return true;
        }
    }

    @Override
    public boolean unregisterTaskManager(InstanceID instanceId, Exception cause) {
        checkInit();

        LOG.debug("Unregistering task executor {} from the slot manager.", instanceId);

        if (taskExecutorManager.isTaskManagerRegistered(instanceId)) {
            slotTracker.removeSlots(taskExecutorManager.getSlotsOf(instanceId));
            taskExecutorManager.unregisterTaskExecutor(instanceId);
            checkResourceRequirements();

            return true;
        } else {
            LOG.debug(
                    "There is no task executor registered with instance ID {}. Ignoring this message.",
                    instanceId);

            return false;
        }
    }

    /**
     * Reports the current slot allocations for a task manager identified by the given instance id.
     * 报告给定实例 id 标识的任务管理器的当前槽分配。
     *
     * @param instanceId identifying the task manager for which to report the slot status
     * @param slotReport containing the status for all of its slots
     * @return true if the slot status has been updated successfully, otherwise false
     */
    @Override
    public boolean reportSlotStatus(InstanceID instanceId, SlotReport slotReport) {
        checkInit();

        LOG.debug("Received slot report from instance {}: {}.", instanceId, slotReport);

        if (taskExecutorManager.isTaskManagerRegistered(instanceId)) {
            if (slotTracker.notifySlotStatus(slotReport)) {
                checkResourceRequirements();
            }
            return true;
        } else {
            LOG.debug(
                    "Received slot report for unknown task manager with instance id {}. Ignoring this report.",
                    instanceId);

            return false;
        }
    }

    /**
     * Free the given slot from the given allocation. If the slot is still allocated by the given
     * allocation id, then the slot will be marked as free and will be subject to new slot requests.
     * 从给定的分配中释放给定的插槽。 如果插槽仍然由给定的分配 id 分配，则该插槽将被标记为空闲，并将接受新的插槽请求。
     *
     * @param slotId identifying the slot to free
     * @param allocationId with which the slot is presumably allocated
     */
    @Override
    public void freeSlot(SlotID slotId, AllocationID allocationId) {
        checkInit();
        LOG.debug("Freeing slot {}.", slotId);

        slotTracker.notifyFree(slotId);
        checkResourceRequirements();
    }

    // ---------------------------------------------------------------------------------------------
    // Requirement matching
    // ---------------------------------------------------------------------------------------------

    /**
     * Matches resource requirements against available resources. In a first round requirements are
     * matched against free slot, and any match results in a slot allocation. The remaining
     * unfulfilled requirements are matched against pending slots, allocating more workers if no
     * matching pending slot could be found. If the requirements for a job could not be fulfilled
     * then a notification is sent to the job master informing it as such.
     * 将资源需求与可用资源相匹配。 在第一轮中，要求与空闲槽匹配，任何匹配都会导致槽分配。
     * 剩余未满足的要求与待处理槽匹配，如果找不到匹配的待处理槽，则分配更多工作人员。
     * 如果无法满足作业的要求，则会向作业主管发送通知，通知它。
     *
     * <p>Performance notes: At it's core this method loops, for each job, over all free/pending
     * slots for each required slot, trying to find a matching slot. One should generally go in with
     * the assumption that this runs in numberOfJobsRequiringResources * numberOfRequiredSlots *
     * numberOfFreeOrPendingSlots. This is especially important when dealing with pending slots, as
     * matches between requirements and pending slots are not persisted and recomputed on each call.
     * This may required further refinements in the future; e.g., persisting the matches between
     * requirements and pending slots, or not matching against pending slots at all.
     * 性能说明：该方法的核心是针对每个作业循环遍历每个所需插槽的所有空闲/待处理插槽，以尝试找到匹配的插槽。
     * 通常应该假设这在 numberOfJobsRequiringResources * numberOfRequiredSlots * numberOfFreeOrPendingSlots 中运行。
     * 这在处理挂起的槽时尤其重要，因为需求和挂起的槽之间的匹配不会在每次调用时被持久化和重新计算。
     * 这可能需要在未来进一步完善； 例如，保持需求和待处理槽之间的匹配，或者根本不匹配待处理槽。
     *
     * <p>When dealing with unspecific resource profiles (i.e., {@link ResourceProfile#ANY}/{@link
     * ResourceProfile#UNKNOWN}), then the number of free/pending slots is not relevant because we
     * only need exactly 1 comparison to determine whether a slot can be fulfilled or not, since
     * they are all the same anyway.
     * 在处理不特定的资源配置文件（即{@link ResourceProfile#ANY}/{@link ResourceProfile#UNKNOWN}）时，
     * 空闲/待处理槽的数量是不相关的，因为我们只需要恰好1个比较来确定槽是否可以 是否满足，因为无论如何它们都是一样的。
     *
     * <p>When dealing with specific resource profiles things can be a lot worse, with the classical
     * cases where either no matches are found, or only at the very end of the iteration. In the
     * absolute worst case, with J jobs, requiring R slots each with a unique resource profile such
     * each pair of these profiles is not matching, and S free/pending slots that don't fulfill any
     * requirement, then this method does a total of J*R*S resource profile comparisons.
     * 在处理特定资源配置文件时，情况可能会更糟，在经典情况下，要么找不到匹配项，要么只在迭代结束时找到匹配项。
     * 在绝对最坏的情况下，对于 J 个作业，需要每个具有唯一资源配置文件的 R 个插槽，这样每对这些配置文件都不匹配，
     * 并且 S 个空闲/待定插槽不满足任何要求，那么此方法总共执行 J*R*S 资源配置文件比较。
     */
    private void checkResourceRequirements() {
        final Map<JobID, Collection<ResourceRequirement>> missingResources =
                resourceTracker.getMissingResources();
        if (missingResources.isEmpty()) {
            return;
        }

        final Map<JobID, ResourceCounter> unfulfilledRequirements = new LinkedHashMap<>();
        for (Map.Entry<JobID, Collection<ResourceRequirement>> resourceRequirements :
                missingResources.entrySet()) {
            final JobID jobId = resourceRequirements.getKey();

            final ResourceCounter unfulfilledJobRequirements =
                    tryAllocateSlotsForJob(jobId, resourceRequirements.getValue());
            if (!unfulfilledJobRequirements.isEmpty()) {
                //未满足的要求
                unfulfilledRequirements.put(jobId, unfulfilledJobRequirements);
            }
        }
        if (unfulfilledRequirements.isEmpty()) {
            return;
        }

        ResourceCounter pendingSlots =
                ResourceCounter.withResources(
                        taskExecutorManager.getPendingTaskManagerSlots().stream()
                                .collect(
                                        Collectors.groupingBy(
                                                PendingTaskManagerSlot::getResourceProfile,
                                                Collectors.summingInt(x -> 1))));

        for (Map.Entry<JobID, ResourceCounter> unfulfilledRequirement :
                unfulfilledRequirements.entrySet()) {
            pendingSlots =
                    tryFulfillRequirementsWithPendingSlots(
                            unfulfilledRequirement.getKey(),
                            unfulfilledRequirement.getValue().getResourcesWithCount(),
                            pendingSlots);
        }
    }

    private ResourceCounter tryAllocateSlotsForJob(
            JobID jobId, Collection<ResourceRequirement> missingResources) {
        ResourceCounter outstandingRequirements = ResourceCounter.empty();

        for (ResourceRequirement resourceRequirement : missingResources) {
            int numMissingSlots =
                    internalTryAllocateSlots(
                            jobId, jobMasterTargetAddresses.get(jobId), resourceRequirement);
            if (numMissingSlots > 0) {
                outstandingRequirements =
                        outstandingRequirements.add(
                                resourceRequirement.getResourceProfile(), numMissingSlots);
            }
        }
        return outstandingRequirements;
    }

    /**
     * Tries to allocate slots for the given requirement. If there are not enough slots available,
     * the resource manager is informed to allocate more resources.
     * 尝试为给定的需求分配插槽。 如果没有足够的可用槽位，则通知资源管理器分配更多资源。
     *
     * @param jobId job to allocate slots for
     * @param targetAddress address of the jobmaster
     * @param resourceRequirement required slots
     * @return the number of missing slots
     */
    private int internalTryAllocateSlots(
            JobID jobId, String targetAddress, ResourceRequirement resourceRequirement) {
        final ResourceProfile requiredResource = resourceRequirement.getResourceProfile();
        Collection<TaskManagerSlotInformation> freeSlots = slotTracker.getFreeSlots();

        int numUnfulfilled = 0;
        for (int x = 0; x < resourceRequirement.getNumberOfRequiredSlots(); x++) {

            final Optional<TaskManagerSlotInformation> reservedSlot =
                    slotMatchingStrategy.findMatchingSlot(
                            requiredResource, freeSlots, this::getNumberRegisteredSlotsOf);
            if (reservedSlot.isPresent()) {
                // we do not need to modify freeSlots because it is indirectly modified by the
                // allocation
                allocateSlot(reservedSlot.get(), jobId, targetAddress, requiredResource);
            } else {
                // exit loop early; we won't find a matching slot for this requirement
                int numRemaining = resourceRequirement.getNumberOfRequiredSlots() - x;
                numUnfulfilled += numRemaining;
                break;
            }
        }
        return numUnfulfilled;
    }

    /**
     * Allocates the given slot. This entails sending a registration message to the task manager and
     * treating failures.
     * 分配给定的插槽。 这需要向任务管理器发送注册消息并处理失败。
     *
     * @param taskManagerSlot slot to allocate
     * @param jobId job for which the slot should be allocated for
     * @param targetAddress address of the job master
     * @param resourceProfile resource profile for the requirement for which the slot is used
     */
    private void allocateSlot(
            TaskManagerSlotInformation taskManagerSlot,
            JobID jobId,
            String targetAddress,
            ResourceProfile resourceProfile) {
        final SlotID slotId = taskManagerSlot.getSlotId();
        LOG.debug(
                "Starting allocation of slot {} for job {} with resource profile {}.",
                slotId,
                jobId,
                resourceProfile);

        final InstanceID instanceId = taskManagerSlot.getInstanceId();
        if (!taskExecutorManager.isTaskManagerRegistered(instanceId)) {
            throw new IllegalStateException(
                    "Could not find a registered task manager for instance id " + instanceId + '.');
        }

        final TaskExecutorConnection taskExecutorConnection =
                taskManagerSlot.getTaskManagerConnection();
        final TaskExecutorGateway gateway = taskExecutorConnection.getTaskExecutorGateway();

        final AllocationID allocationId = new AllocationID();

        slotTracker.notifyAllocationStart(slotId, jobId);
        taskExecutorManager.markUsed(instanceId);
        pendingSlotAllocations.put(slotId, allocationId);

        // RPC call to the task manager
        CompletableFuture<Acknowledge> requestFuture =
                gateway.requestSlot(
                        slotId,
                        jobId,
                        allocationId,
                        resourceProfile,
                        targetAddress,
                        resourceManagerId,
                        taskManagerRequestTimeout);

        CompletableFuture<Void> slotAllocationResponseProcessingFuture =
                requestFuture.handleAsync(
                        (Acknowledge acknowledge, Throwable throwable) -> {
                            final AllocationID currentAllocationForSlot =
                                    pendingSlotAllocations.get(slotId);
                            if (currentAllocationForSlot == null
                                    || !currentAllocationForSlot.equals(allocationId)) {
                                LOG.debug(
                                        "Ignoring slot allocation update from task executor {} for slot {} and job {}, because the allocation was already completed or cancelled.",
                                        instanceId,
                                        slotId,
                                        jobId);
                                return null;
                            }
                            if (acknowledge != null) {
                                LOG.trace(
                                        "Completed allocation of slot {} for job {}.",
                                        slotId,
                                        jobId);
                                slotTracker.notifyAllocationComplete(slotId, jobId);
                            } else {
                                if (throwable instanceof SlotOccupiedException) {
                                    SlotOccupiedException exception =
                                            (SlotOccupiedException) throwable;
                                    LOG.debug(
                                            "Tried allocating slot {} for job {}, but it was already allocated for job {}.",
                                            slotId,
                                            jobId,
                                            exception.getJobId());
                                    // report as a slot status to force the state transition
                                    // this could be a problem if we ever assume that the task
                                    // executor always reports about all slots
                                    slotTracker.notifySlotStatus(
                                            Collections.singleton(
                                                    new SlotStatus(
                                                            slotId,
                                                            taskManagerSlot.getResourceProfile(),
                                                            exception.getJobId(),
                                                            exception.getAllocationId())));
                                } else {
                                    LOG.warn(
                                            "Slot allocation for slot {} for job {} failed.",
                                            slotId,
                                            jobId,
                                            throwable);
                                    slotTracker.notifyFree(slotId);
                                }
                                checkResourceRequirements();
                            }
                            return null;
                        },
                        mainThreadExecutor);
        FutureUtils.assertNoException(slotAllocationResponseProcessingFuture);
    }

    private ResourceCounter tryFulfillRequirementsWithPendingSlots(
            JobID jobId,
            Collection<Map.Entry<ResourceProfile, Integer>> missingResources,
            ResourceCounter pendingSlots) {
        for (Map.Entry<ResourceProfile, Integer> missingResource : missingResources) {
            ResourceProfile profile = missingResource.getKey();
            for (int i = 0; i < missingResource.getValue(); i++) {
                final MatchingResult matchingResult =
                        tryFulfillWithPendingSlots(profile, pendingSlots);
                pendingSlots = matchingResult.getNewAvailableResources();
                if (!matchingResult.isSuccessfulMatching()) {
                    final WorkerAllocationResult allocationResult =
                            tryAllocateWorkerAndReserveSlot(profile, pendingSlots);
                    pendingSlots = allocationResult.getNewAvailableResources();
                    if (!allocationResult.isSuccessfulAllocating()
                            && sendNotEnoughResourceNotifications) {
                        LOG.warn(
                                "Could not fulfill resource requirements of job {}. Free slots: {}",
                                jobId,
                                slotTracker.getFreeSlots().size());
                        resourceActions.notifyNotEnoughResourcesAvailable(
                                jobId, resourceTracker.getAcquiredResources(jobId));
                        return pendingSlots;
                    }
                }
            }
        }
        return pendingSlots;
    }

    private MatchingResult tryFulfillWithPendingSlots(
            ResourceProfile resourceProfile, ResourceCounter pendingSlots) {
        Set<ResourceProfile> pendingSlotProfiles = pendingSlots.getResources();

        // short-cut, pretty much only applicable to fine-grained resource management
        if (pendingSlotProfiles.contains(resourceProfile)) {
            pendingSlots = pendingSlots.subtract(resourceProfile, 1);
            return new MatchingResult(true, pendingSlots);
        }

        for (ResourceProfile pendingSlotProfile : pendingSlotProfiles) {
            if (pendingSlotProfile.isMatching(resourceProfile)) {
                pendingSlots = pendingSlots.subtract(pendingSlotProfile, 1);
                return new MatchingResult(true, pendingSlots);
            }
        }

        return new MatchingResult(false, pendingSlots);
    }

    private WorkerAllocationResult tryAllocateWorkerAndReserveSlot(
            ResourceProfile profile, ResourceCounter pendingSlots) {
        Optional<ResourceRequirement> newlyFulfillableRequirements =
                taskExecutorManager.allocateWorker(profile);
        if (newlyFulfillableRequirements.isPresent()) {
            ResourceRequirement newSlots = newlyFulfillableRequirements.get();
            // reserve one of the new slots
            if (newSlots.getNumberOfRequiredSlots() > 1) {
                pendingSlots =
                        pendingSlots.add(
                                newSlots.getResourceProfile(),
                                newSlots.getNumberOfRequiredSlots() - 1);
            }
            return new WorkerAllocationResult(true, pendingSlots);
        } else {
            return new WorkerAllocationResult(false, pendingSlots);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Legacy APIs
    // ---------------------------------------------------------------------------------------------

    @Override
    public int getNumberRegisteredSlots() {
        return taskExecutorManager.getNumberRegisteredSlots();
    }

    @Override
    public int getNumberRegisteredSlotsOf(InstanceID instanceId) {
        return taskExecutorManager.getNumberRegisteredSlotsOf(instanceId);
    }

    @Override
    public int getNumberFreeSlots() {
        return taskExecutorManager.getNumberFreeSlots();
    }

    @Override
    public int getNumberFreeSlotsOf(InstanceID instanceId) {
        return taskExecutorManager.getNumberFreeSlotsOf(instanceId);
    }

    @Override
    public Map<WorkerResourceSpec, Integer> getRequiredResources() {
        return taskExecutorManager.getRequiredWorkers();
    }

    @Override
    public ResourceProfile getRegisteredResource() {
        return taskExecutorManager.getTotalRegisteredResources();
    }

    @Override
    public ResourceProfile getRegisteredResourceOf(InstanceID instanceID) {
        return taskExecutorManager.getTotalRegisteredResourcesOf(instanceID);
    }

    @Override
    public ResourceProfile getFreeResource() {
        return taskExecutorManager.getTotalFreeResources();
    }

    @Override
    public ResourceProfile getFreeResourceOf(InstanceID instanceID) {
        return taskExecutorManager.getTotalFreeResourcesOf(instanceID);
    }

    @Override
    public Collection<SlotInfo> getAllocatedSlotsOf(InstanceID instanceID) {
        // This information is currently not supported for this slot manager.
        return Collections.emptyList();
    }

    @Override
    public int getNumberPendingSlotRequests() {
        // only exists for testing purposes
        throw new UnsupportedOperationException();
    }

    // ---------------------------------------------------------------------------------------------
    // Internal utility methods
    // ---------------------------------------------------------------------------------------------

    private void checkInit() {
        Preconditions.checkState(started, "The slot manager has not been started.");
    }

    private static class MatchingResult {
        private final boolean isSuccessfulMatching;
        private final ResourceCounter newAvailableResources;

        private MatchingResult(
                boolean isSuccessfulMatching, ResourceCounter newAvailableResources) {
            this.isSuccessfulMatching = isSuccessfulMatching;
            this.newAvailableResources = Preconditions.checkNotNull(newAvailableResources);
        }

        private ResourceCounter getNewAvailableResources() {
            return newAvailableResources;
        }

        private boolean isSuccessfulMatching() {
            return isSuccessfulMatching;
        }
    }

    private static class WorkerAllocationResult {
        private final boolean isSuccessfulAllocating;
        private final ResourceCounter newAvailableResources;

        private WorkerAllocationResult(
                boolean isSuccessfulAllocating, ResourceCounter newAvailableResources) {
            this.isSuccessfulAllocating = isSuccessfulAllocating;
            this.newAvailableResources = Preconditions.checkNotNull(newAvailableResources);
        }

        private ResourceCounter getNewAvailableResources() {
            return newAvailableResources;
        }

        private boolean isSuccessfulAllocating() {
            return isSuccessfulAllocating;
        }
    }
}
