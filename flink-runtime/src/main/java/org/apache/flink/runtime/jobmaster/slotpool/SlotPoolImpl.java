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

package org.apache.flink.runtime.jobmaster.slotpool;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.executiongraph.ExecutionGraph;
import org.apache.flink.runtime.jobmanager.scheduler.NoResourceAvailableException;
import org.apache.flink.runtime.jobmanager.slots.TaskManagerGateway;
import org.apache.flink.runtime.jobmaster.AllocatedSlotInfo;
import org.apache.flink.runtime.jobmaster.AllocatedSlotReport;
import org.apache.flink.runtime.jobmaster.JobMasterId;
import org.apache.flink.runtime.jobmaster.SlotInfo;
import org.apache.flink.runtime.jobmaster.SlotRequestId;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.resourcemanager.ResourceManagerGateway;
import org.apache.flink.runtime.resourcemanager.SlotRequest;
import org.apache.flink.runtime.resourcemanager.exceptions.UnfulfillableSlotRequestException;
import org.apache.flink.runtime.taskexecutor.slot.SlotOffer;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;
import org.apache.flink.runtime.util.DualKeyLinkedMap;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FlinkException;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.clock.Clock;

import org.apache.flink.shaded.guava18.com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The slot pool serves slot request issued by {@link ExecutionGraph}. It will attempt to acquire
 * new slots from the ResourceManager when it cannot serve a slot request. If no ResourceManager is
 * currently available, or it gets a decline from the ResourceManager, or a request times out, it
 * fails the slot request. The slot pool also holds all the slots that were offered to it and
 * accepted, and can thus provides registered free slots even if the ResourceManager is down. The
 * slots will only be released when they are useless, e.g. when the job is fully running but we
 * still have some free slots.
 * 槽池服务于 {@link ExecutionGraph} 发出的槽请求。 当它无法服务槽请求时，它将尝试从 ResourceManager 获取新槽。
 * 如果当前没有 ResourceManager 可用，或者 ResourceManager 拒绝，或者请求超时，则槽请求失败。
 * 插槽池还保存所有提供给它并接受的插槽，因此即使 ResourceManager 已关闭，也可以提供已注册的空闲插槽。
 * 插槽只有在无用时才会被释放，例如 当作业完全运行但我们仍有一些空闲插槽时。
 *
 * <p>All the allocation or the slot offering will be identified by self generated AllocationID, we
 * will use it to eliminate ambiguities.
 * 所有分配或插槽提供将由自行生成的 AllocationID 标识，我们将使用它来消除歧义。
 *
 * <p>TODO : Make pending requests location preference aware TODO : Make pass location preferences
 * to ResourceManager when sending a slot request
 * TODO : 使挂起的请求知道位置首选项
 * TODO : 在发送槽请求时将位置首选项传递给 ResourceManager
 */
public class SlotPoolImpl implements SlotPool, SlotPoolService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The interval (in milliseconds) in which the SlotPool writes its slot distribution on debug
     * level.
     * SlotPool 在调试级别写入其插槽分布的时间间隔（以毫秒为单位）。
     */
    private static final long STATUS_LOG_INTERVAL_MS = 60_000;

    private final JobID jobId;

    /**
     * All registered TaskManagers, slots will be accepted and used only if the resource is
     * registered.
     * 仅当资源已注册时，所有已注册的任务管理器、插槽才会被接受和使用。
     */
    private final HashSet<ResourceID> registeredTaskManagers;

    /** The book-keeping of all allocated slots.
     * 所有分配的插槽的簿记。
     * */
    private final AllocatedSlots allocatedSlots;

    /** The book-keeping of all available slots.
     * 所有可用插槽的簿记。
     * */
    private final AvailableSlots availableSlots;

    /** All pending requests waiting for slots.
     * 所有等待槽的待处理请求。
     * */
    private final DualKeyLinkedMap<SlotRequestId, AllocationID, PendingRequest> pendingRequests;

    /** The requests that are waiting for the resource manager to be connected.
     * 等待资源管理器连接的请求。
     * */
    private final LinkedHashMap<SlotRequestId, PendingRequest> waitingForResourceManager;

    /** Timeout for external request calls (e.g. to the ResourceManager or the TaskExecutor).
     * 外部请求调用超时（例如对 ResourceManager 或 TaskExecutor）。
     * */
    private final Time rpcTimeout;

    /** Timeout for releasing idle slots.
     * 释放空闲槽超时。
     * */
    private final Time idleSlotTimeout;

    /** Timeout for batch slot requests.
     * 批处理槽请求超时。
     * */
    private final Time batchSlotTimeout;

    private final Clock clock;

    /** the fencing token of the job manager.
     * job manager的围栏令牌。
     * */
    private JobMasterId jobMasterId;

    /** The gateway to communicate with resource manager.
     * 与资源管理器通信的网关。
     * */
    @Nullable private ResourceManagerGateway resourceManagerGateway;

    private String jobManagerAddress;

    private ComponentMainThreadExecutor componentMainThreadExecutor;

    protected boolean batchSlotRequestTimeoutCheckEnabled;

    // ------------------------------------------------------------------------

    public SlotPoolImpl(
            JobID jobId,
            Clock clock,
            Time rpcTimeout,
            Time idleSlotTimeout,
            Time batchSlotTimeout) {

        this.jobId = checkNotNull(jobId);
        this.clock = checkNotNull(clock);
        this.rpcTimeout = checkNotNull(rpcTimeout);
        this.idleSlotTimeout = checkNotNull(idleSlotTimeout);
        this.batchSlotTimeout = checkNotNull(batchSlotTimeout);

        this.registeredTaskManagers = new HashSet<>(16);
        this.allocatedSlots = new AllocatedSlots();
        this.availableSlots = new AvailableSlots();
        this.pendingRequests = new DualKeyLinkedMap<>(16);
        this.waitingForResourceManager = new LinkedHashMap<>(16);

        this.jobMasterId = null;
        this.resourceManagerGateway = null;
        this.jobManagerAddress = null;

        this.componentMainThreadExecutor = null;

        this.batchSlotRequestTimeoutCheckEnabled = true;
    }

    // ------------------------------------------------------------------------
    //  Getters
    // ------------------------------------------------------------------------

    @Override
    public Collection<SlotInfo> getAllocatedSlotsInformation() {
        return allocatedSlots.listSlotInfo();
    }

    @VisibleForTesting
    AllocatedSlots getAllocatedSlots() {
        return allocatedSlots;
    }

    @VisibleForTesting
    AvailableSlots getAvailableSlots() {
        return availableSlots;
    }

    @VisibleForTesting
    public DualKeyLinkedMap<SlotRequestId, AllocationID, PendingRequest> getPendingRequests() {
        return pendingRequests;
    }

    @VisibleForTesting
    Map<SlotRequestId, PendingRequest> getWaitingForResourceManager() {
        return waitingForResourceManager;
    }

    // ------------------------------------------------------------------------
    //  Starting and Stopping
    // ------------------------------------------------------------------------

    /**
     * Start the slot pool to accept RPC calls.
     *
     * @param jobMasterId The necessary leader id for running the job.
     * @param newJobManagerAddress for the slot requests which are sent to the resource manager
     * @param componentMainThreadExecutor The main thread executor for the job master's main thread.
     */
    public void start(
            @Nonnull JobMasterId jobMasterId,
            @Nonnull String newJobManagerAddress,
            @Nonnull ComponentMainThreadExecutor componentMainThreadExecutor)
            throws Exception {

        this.jobMasterId = jobMasterId;
        this.jobManagerAddress = newJobManagerAddress;
        this.componentMainThreadExecutor = componentMainThreadExecutor;

        scheduleRunAsync(this::checkIdleSlot, idleSlotTimeout);
        scheduleRunAsync(this::checkBatchSlotTimeout, batchSlotTimeout);

        if (log.isDebugEnabled()) {
            scheduleRunAsync(
                    this::scheduledLogStatus, STATUS_LOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelPendingSlotRequests() {
        if (resourceManagerGateway != null) {
            // cancel all pending allocations --> we can request these slots
            // again after we regained the leadership
            Set<AllocationID> allocationIds = pendingRequests.keySetB();

            for (AllocationID allocationId : allocationIds) {
                resourceManagerGateway.cancelSlotRequest(allocationId);
            }
        }
    }

    @Override
    public void close() {
        log.info("Stopping SlotPool.");

        cancelPendingSlotRequests();

        // release all registered slots by releasing the corresponding TaskExecutors
        for (ResourceID taskManagerResourceId : registeredTaskManagers) {
            final FlinkException cause =
                    new FlinkException(
                            "Releasing TaskManager "
                                    + taskManagerResourceId
                                    + ", because of stopping of SlotPool");
            releaseTaskManagerInternal(taskManagerResourceId, cause);
        }

        clear();
    }

    // ------------------------------------------------------------------------
    //  Resource Manager Connection
    // ------------------------------------------------------------------------

    @Override
    public void connectToResourceManager(@Nonnull ResourceManagerGateway resourceManagerGateway) {
        this.resourceManagerGateway = checkNotNull(resourceManagerGateway);

        // work on all slots waiting for this connection
        for (PendingRequest pendingRequest : waitingForResourceManager.values()) {
            requestSlotFromResourceManager(resourceManagerGateway, pendingRequest);
        }

        // all sent off
        waitingForResourceManager.clear();
    }

    @Override
    public void disconnectResourceManager() {
        this.resourceManagerGateway = null;
    }

    // ------------------------------------------------------------------------
    //  Slot Allocation
    // ------------------------------------------------------------------------

    /**
     * Requests a new slot from the ResourceManager. If there is currently not ResourceManager
     * connected, then the request is stashed and send once a new ResourceManager is connected.
     * 从 ResourceManager 请求一个新插槽。
     * 如果当前没有连接 ResourceManager，那么一旦连接了新的 ResourceManager，请求就会被隐藏并发送。
     *
     * @param pendingRequest pending slot request
     * @return An {@link AllocatedSlot} future which is completed once the slot is offered to the
     *     {@link SlotPool}
     */
    @Nonnull
    private CompletableFuture<AllocatedSlot> requestNewAllocatedSlotInternal(
            PendingRequest pendingRequest) {

        if (resourceManagerGateway == null) {
            stashRequestWaitingForResourceManager(pendingRequest);
        } else {
            requestSlotFromResourceManager(resourceManagerGateway, pendingRequest);
        }

        return pendingRequest.getAllocatedSlotFuture();
    }

    private void requestSlotFromResourceManager(
            final ResourceManagerGateway resourceManagerGateway,
            final PendingRequest pendingRequest) {

        checkNotNull(resourceManagerGateway);
        checkNotNull(pendingRequest);

        final AllocationID allocationId = new AllocationID();
        pendingRequest.setAllocationId(allocationId);

        pendingRequests.put(pendingRequest.getSlotRequestId(), allocationId, pendingRequest);

        pendingRequest
                .getAllocatedSlotFuture()
                .whenComplete(
                        (AllocatedSlot allocatedSlot, Throwable throwable) -> {
                            if (throwable != null) {
                                // the allocation id can be remapped so we need to get it from the
                                // pendingRequest
                                // where it will be updated timely
                                final Optional<AllocationID> updatedAllocationId =
                                        pendingRequest.getAllocationId();

                                if (updatedAllocationId.isPresent()) {
                                    // cancel the slot request if there is a failure
                                    resourceManagerGateway.cancelSlotRequest(
                                            updatedAllocationId.get());
                                }
                            }
                        });

        log.info(
                "Requesting new slot [{}] and profile {} with allocation id {} from resource manager.",
                pendingRequest.getSlotRequestId(),
                pendingRequest.getResourceProfile(),
                allocationId);

        CompletableFuture<Acknowledge> rmResponse =
                resourceManagerGateway.requestSlot(
                        jobMasterId,
                        new SlotRequest(
                                jobId,
                                allocationId,
                                pendingRequest.getResourceProfile(),
                                jobManagerAddress),
                        rpcTimeout);

        FutureUtils.whenCompleteAsyncIfNotDone(
                rmResponse,
                componentMainThreadExecutor,
                (Acknowledge ignored, Throwable failure) -> {
                    // on failure, fail the request future
                    if (failure != null) {
                        slotRequestToResourceManagerFailed(
                                pendingRequest.getSlotRequestId(), failure);
                    }
                });
    }

    private void slotRequestToResourceManagerFailed(
            SlotRequestId slotRequestID, Throwable failure) {
        final PendingRequest request = pendingRequests.getValueByKeyA(slotRequestID);
        if (request != null) {
            if (isBatchRequestAndFailureCanBeIgnored(request, failure)) {
                log.debug(
                        "Ignoring failed request to the resource manager for a batch slot request.");
            } else {
                removePendingRequest(slotRequestID);
                request.getAllocatedSlotFuture()
                        .completeExceptionally(
                                new NoResourceAvailableException(
                                        "No pooled slot available and request to ResourceManager for new slot failed",
                                        failure));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Unregistered slot request [{}] failed.", slotRequestID, failure);
            }
        }
    }

    private void stashRequestWaitingForResourceManager(final PendingRequest pendingRequest) {

        log.info(
                "Cannot serve slot request, no ResourceManager connected. "
                        + "Adding as pending request [{}]",
                pendingRequest.getSlotRequestId());

        waitingForResourceManager.put(pendingRequest.getSlotRequestId(), pendingRequest);
    }

    private boolean isBatchRequestAndFailureCanBeIgnored(
            PendingRequest request, Throwable failure) {
        return request.isBatchRequest
                && !ExceptionUtils.findThrowable(failure, UnfulfillableSlotRequestException.class)
                        .isPresent();
    }

    // ------------------------------------------------------------------------
    //  Slot releasing & offering
    // ------------------------------------------------------------------------

    @Override
    public void releaseSlot(@Nonnull SlotRequestId slotRequestId, @Nullable Throwable cause) {

        componentMainThreadExecutor.assertRunningInMainThread();

        log.debug(
                "Releasing slot [{}] because: {}",
                slotRequestId,
                cause != null ? cause.getMessage() : "null");
        releaseSingleSlot(slotRequestId, cause);
    }

    @Override
    public Optional<PhysicalSlot> allocateAvailableSlot(
            @Nonnull SlotRequestId slotRequestId,
            @Nonnull AllocationID allocationID,
            @Nonnull ResourceProfile requirementProfile) {

        componentMainThreadExecutor.assertRunningInMainThread();

        AllocatedSlot allocatedSlot = availableSlots.tryRemove(allocationID);
        if (allocatedSlot != null) {
            allocatedSlots.add(slotRequestId, allocatedSlot);
            return Optional.of(allocatedSlot);
        } else {
            return Optional.empty();
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<PhysicalSlot> requestNewAllocatedSlot(
            @Nonnull SlotRequestId slotRequestId,
            @Nonnull ResourceProfile resourceProfile,
            @Nullable Time timeout) {

        componentMainThreadExecutor.assertRunningInMainThread();

        final PendingRequest pendingRequest =
                PendingRequest.createStreamingRequest(slotRequestId, resourceProfile);

        if (timeout != null) {
            // register request timeout
            FutureUtils.orTimeout(
                            pendingRequest.getAllocatedSlotFuture(),
                            timeout.toMilliseconds(),
                            TimeUnit.MILLISECONDS,
                            componentMainThreadExecutor)
                    .whenComplete(
                            (AllocatedSlot ignored, Throwable throwable) -> {
                                if (throwable instanceof TimeoutException) {
                                    timeoutPendingSlotRequest(slotRequestId);
                                }
                            });
        }

        return requestNewAllocatedSlotInternal(pendingRequest).thenApply((Function.identity()));
    }

    @Nonnull
    @Override
    public CompletableFuture<PhysicalSlot> requestNewAllocatedBatchSlot(
            @Nonnull SlotRequestId slotRequestId, @Nonnull ResourceProfile resourceProfile) {

        componentMainThreadExecutor.assertRunningInMainThread();

        final PendingRequest pendingRequest =
                PendingRequest.createBatchRequest(slotRequestId, resourceProfile);

        return requestNewAllocatedSlotInternal(pendingRequest).thenApply(Function.identity());
    }

    @Override
    public void disableBatchSlotRequestTimeoutCheck() {
        batchSlotRequestTimeoutCheckEnabled = false;
    }

    @Override
    @Nonnull
    public Collection<SlotInfoWithUtilization> getAvailableSlotsInformation() {
        final Map<ResourceID, Set<AllocatedSlot>> availableSlotsByTaskManager =
                availableSlots.getSlotsByTaskManager();
        final Map<ResourceID, Set<AllocatedSlot>> allocatedSlotsByTaskManager =
                allocatedSlots.getSlotsByTaskManager();

        return availableSlotsByTaskManager.entrySet().stream()
                .flatMap(
                        entry -> {
                            final int numberAllocatedSlots =
                                    allocatedSlotsByTaskManager
                                            .getOrDefault(entry.getKey(), Collections.emptySet())
                                            .size();
                            final int numberAvailableSlots = entry.getValue().size();
                            final double taskExecutorUtilization =
                                    (double) numberAllocatedSlots
                                            / (numberAllocatedSlots + numberAvailableSlots);

                            return entry.getValue().stream()
                                    .map(
                                            slot ->
                                                    SlotInfoWithUtilization.from(
                                                            slot, taskExecutorUtilization));
                        })
                .collect(Collectors.toList());
    }

    private void releaseSingleSlot(SlotRequestId slotRequestId, Throwable cause) {
        final PendingRequest pendingRequest = removePendingRequest(slotRequestId);

        if (pendingRequest != null) {
            failPendingRequest(
                    pendingRequest,
                    new FlinkException(
                            "Pending slot request with " + slotRequestId + " has been released.",
                            cause));
        } else {
            final AllocatedSlot allocatedSlot = allocatedSlots.remove(slotRequestId);

            if (allocatedSlot != null) {
                allocatedSlot.releasePayload(cause);
                tryFulfillSlotRequestOrMakeAvailable(allocatedSlot);
            } else {
                log.debug(
                        "There is no allocated slot [{}]. Ignoring the release slot request.",
                        slotRequestId);
            }
        }
    }

    /**
     * Checks whether there exists a pending request with the given slot request id and removes it
     * from the internal data structures.
     * 检查是否存在具有给定槽请求 ID 的待处理请求，并将其从内部数据结构中删除。
     *
     * @param requestId identifying the pending request
     * @return pending request if there is one, otherwise null
     */
    @Nullable
    private PendingRequest removePendingRequest(SlotRequestId requestId) {
        PendingRequest result = waitingForResourceManager.remove(requestId);

        if (result != null) {
            // sanity check
            assert !pendingRequests.containsKeyA(requestId)
                    : "A pending requests should only be part of either "
                            + "the pendingRequests or waitingForResourceManager but not both.";

            return result;
        } else {
            return pendingRequests.removeKeyA(requestId);
        }
    }

    private void failPendingRequest(PendingRequest pendingRequest, Exception e) {
        checkNotNull(pendingRequest);
        checkNotNull(e);

        if (!pendingRequest.getAllocatedSlotFuture().isDone()) {
            log.info(
                    "Failing pending slot request [{}]: {}",
                    pendingRequest.getSlotRequestId(),
                    e.getMessage());
            pendingRequest.getAllocatedSlotFuture().completeExceptionally(e);
        }
    }

    /**
     * Tries to fulfill with the given allocated slot a pending slot request or add the allocated
     * slot to the set of available slots if no matching request is available.
     * 如果没有匹配的请求可用，则尝试使用给定的已分配插槽满足待处理的插槽请求或将已分配的插槽添加到可用插槽集合中。
     *
     * @param allocatedSlot which shall be returned
     */
    private void tryFulfillSlotRequestOrMakeAvailable(AllocatedSlot allocatedSlot) {
        Preconditions.checkState(!allocatedSlot.isUsed(), "Provided slot is still in use.");

        final PendingRequest pendingRequest = findMatchingPendingRequest(allocatedSlot);

        if (pendingRequest != null) {
            log.debug(
                    "Fulfilling pending slot request [{}] with slot [{}]",
                    pendingRequest.getSlotRequestId(),
                    allocatedSlot.getAllocationId());

            removePendingRequest(pendingRequest.getSlotRequestId());

            allocatedSlots.add(pendingRequest.getSlotRequestId(), allocatedSlot);
            pendingRequest.getAllocatedSlotFuture().complete(allocatedSlot);

            // this allocation may become orphan once its corresponding request is removed
            final Optional<AllocationID> allocationIdOfRequest = pendingRequest.getAllocationId();

            // the allocation id can be null if the request was fulfilled by a slot directly offered
            // by a reconnected TaskExecutor before the ResourceManager is connected
            if (allocationIdOfRequest.isPresent()) {
                maybeRemapOrphanedAllocation(
                        allocationIdOfRequest.get(), allocatedSlot.getAllocationId());
            }
        } else {
            log.debug("Adding slot [{}] to available slots", allocatedSlot.getAllocationId());
            availableSlots.add(allocatedSlot, clock.relativeTimeMillis());
        }
    }

    private PendingRequest findMatchingPendingRequest(final AllocatedSlot slot) {
        final ResourceProfile slotResources = slot.getResourceProfile();

        // try the requests sent to the resource manager first
        for (PendingRequest request : pendingRequests.values()) {
            if (slotResources.isMatching(request.getResourceProfile())) {
                return request;
            }
        }

        // try the requests waiting for a resource manager connection next
        for (PendingRequest request : waitingForResourceManager.values()) {
            if (slotResources.isMatching(request.getResourceProfile())) {
                return request;
            }
        }

        // no request pending, or no request matches
        return null;
    }

    private void maybeRemapOrphanedAllocation(
            final AllocationID allocationIdOfRequest, final AllocationID allocationIdOfSlot) {

        // allocation of a request is orphaned if the request is fulfilled by a different allocated
        // slot.
        // if the request of that allocated slot is still pending, it should take over the orphaned
        // allocation.
        // this enables the request to fail fast if the remapped allocation fails.
        if (!allocationIdOfRequest.equals(allocationIdOfSlot)) {
            final PendingRequest requestOfAllocatedSlot =
                    pendingRequests.getValueByKeyB(allocationIdOfSlot);
            if (requestOfAllocatedSlot != null) {
                requestOfAllocatedSlot.setAllocationId(allocationIdOfRequest);

                // this re-insertion of request will not affect its original insertion order
                pendingRequests.put(
                        requestOfAllocatedSlot.getSlotRequestId(),
                        allocationIdOfRequest,
                        requestOfAllocatedSlot);
            } else {
                // request id of the allocated slot can be null if the slot is returned by
                // scheduler.
                // the orphaned allocation will not be adopted in this case, which means it is not
                // needed
                // anymore by any pending requests. we should cancel it to avoid allocating
                // unnecessary slots.
                if (resourceManagerGateway != null) {
                    resourceManagerGateway.cancelSlotRequest(allocationIdOfRequest);
                }
            }
        }
    }

    @Override
    public Collection<SlotOffer> offerSlots(
            TaskManagerLocation taskManagerLocation,
            TaskManagerGateway taskManagerGateway,
            Collection<SlotOffer> offers) {

        ArrayList<SlotOffer> result = new ArrayList<>(offers.size());

        for (SlotOffer offer : offers) {
            if (offerSlot(taskManagerLocation, taskManagerGateway, offer)) {

                result.add(offer);
            }
        }

        return result;
    }

    /**
     * Slot offering by TaskExecutor with AllocationID. The AllocationID is originally generated by
     * this pool and transfer through the ResourceManager to TaskManager. We use it to distinguish
     * the different allocation we issued. Slot offering may be rejected if we find something
     * mismatching or there is actually no pending request waiting for this slot (maybe fulfilled by
     * some other returned slot).
     * TaskExecutor 提供的具有 AllocationID 的插槽。
     * AllocationID 最初是由这个池生成的，并通过 ResourceManager 传递给 TaskManager。
     * 我们用它来区分我们发出的不同分配。
     * 如果我们发现某些不匹配的内容或实际上没有等待此插槽的待处理请求（可能由其他返回的插槽完成），则插槽提供可能会被拒绝。
     *
     * @param taskManagerLocation location from where the offer comes from
     * @param taskManagerGateway TaskManager gateway
     * @param slotOffer the offered slot
     * @return True if we accept the offering
     */
    boolean offerSlot(
            final TaskManagerLocation taskManagerLocation,
            final TaskManagerGateway taskManagerGateway,
            final SlotOffer slotOffer) {

        componentMainThreadExecutor.assertRunningInMainThread();

        // check if this TaskManager is valid
        final ResourceID resourceID = taskManagerLocation.getResourceID();
        final AllocationID allocationID = slotOffer.getAllocationId();

        if (!registeredTaskManagers.contains(resourceID)) {
            log.debug(
                    "Received outdated slot offering [{}] from unregistered TaskManager: {}",
                    slotOffer.getAllocationId(),
                    taskManagerLocation);
            return false;
        }

        // check whether we have already using this slot
        AllocatedSlot existingSlot;
        if ((existingSlot = allocatedSlots.get(allocationID)) != null
                || (existingSlot = availableSlots.get(allocationID)) != null) {

            // we need to figure out if this is a repeated offer for the exact same slot,
            // or another offer that comes from a different TaskManager after the ResourceManager
            // re-tried the request

            // we write this in terms of comparing slot IDs, because the Slot IDs are the
            // identifiers of
            // the actual slots on the TaskManagers
            // Note: The slotOffer should have the SlotID
            final SlotID existingSlotId = existingSlot.getSlotId();
            final SlotID newSlotId =
                    new SlotID(taskManagerLocation.getResourceID(), slotOffer.getSlotIndex());

            if (existingSlotId.equals(newSlotId)) {
                log.info("Received repeated offer for slot [{}]. Ignoring.", allocationID);

                // return true here so that the sender will get a positive acknowledgement to the
                // retry
                // and mark the offering as a success
                return true;
            } else {
                // the allocation has been fulfilled by another slot, reject the offer so the task
                // executor
                // will offer the slot to the resource manager
                return false;
            }
        }

        final AllocatedSlot allocatedSlot =
                new AllocatedSlot(
                        allocationID,
                        taskManagerLocation,
                        slotOffer.getSlotIndex(),
                        slotOffer.getResourceProfile(),
                        taskManagerGateway);

        // use the slot to fulfill pending request, in requested order
        tryFulfillSlotRequestOrMakeAvailable(allocatedSlot);

        // we accepted the request in any case. slot will be released after it idled for
        // too long and timed out
        return true;
    }

    // TODO - periodic (every minute or so) catch slots that were lost (check all slots, if they
    // have any task active)
    // TODO - 定期（每分钟左右）捕获丢失的插槽（检查所有插槽，如果它们有任何活动任务）

    // TODO - release slots that were not used to the resource manager
    // TODO - 释放资源管理器未使用的插槽

    // ------------------------------------------------------------------------
    //  Error Handling
    // ------------------------------------------------------------------------

    /**
     * Fail the specified allocation and release the corresponding slot if we have one. This may
     * triggered by JobManager when some slot allocation failed with rpcTimeout. Or this could be
     * triggered by TaskManager, when it finds out something went wrong with the slot, and decided
     * to take it back.
     * 如果我们有一个指定的分配失败并释放相应的插槽。
     * 当某些插槽分配因 rpcTimeout 失败时，这可能由 JobManager 触发。
     * 或者这可能由 TaskManager 触发，当它发现插槽出现问题并决定将其收回时。
     *
     * @param allocationID Represents the allocation which should be failed
     * @param cause The cause of the failure
     * @return Optional task executor if it has no more slots registered
     */
    @Override
    public Optional<ResourceID> failAllocation(
            final AllocationID allocationID, final Exception cause) {

        componentMainThreadExecutor.assertRunningInMainThread();

        final PendingRequest pendingRequest = pendingRequests.getValueByKeyB(allocationID);
        if (pendingRequest != null) {
            if (isBatchRequestAndFailureCanBeIgnored(pendingRequest, cause)) {
                log.debug(
                        "Ignoring allocation failure for batch slot request {}.",
                        pendingRequest.getSlotRequestId());
            } else {
                // request was still pending
                removePendingRequest(pendingRequest.getSlotRequestId());
                failPendingRequest(pendingRequest, cause);
            }
            return Optional.empty();
        } else {
            return tryFailingAllocatedSlot(allocationID, cause);
        }

        // TODO: add some unit tests when the previous two are ready, the allocation may failed at
        // any phase
    }

    @Override
    public Optional<ResourceID> failAllocation(
            @Nullable ResourceID resourceId, AllocationID allocationID, Exception cause) {
        return failAllocation(allocationID, cause);
    }

    private Optional<ResourceID> tryFailingAllocatedSlot(
            AllocationID allocationID, Exception cause) {
        AllocatedSlot allocatedSlot = availableSlots.tryRemove(allocationID);

        if (allocatedSlot == null) {
            allocatedSlot = allocatedSlots.remove(allocationID);
        }

        if (allocatedSlot != null) {
            log.debug("Failed allocated slot [{}]: {}", allocationID, cause.getMessage());

            // notify TaskExecutor about the failure
            allocatedSlot.getTaskManagerGateway().freeSlot(allocationID, cause, rpcTimeout);
            // release the slot.
            // since it is not in 'allocatedSlots' any more, it will be dropped o return'
            allocatedSlot.releasePayload(cause);

            final ResourceID taskManagerId = allocatedSlot.getTaskManagerId();

            if (!availableSlots.containsTaskManager(taskManagerId)
                    && !allocatedSlots.containResource(taskManagerId)) {
                return Optional.of(taskManagerId);
            }
        }

        return Optional.empty();
    }

    // ------------------------------------------------------------------------
    //  Resource
    // ------------------------------------------------------------------------

    /**
     * Register TaskManager to this pool, only those slots come from registered TaskManager will be
     * considered valid. Also it provides a way for us to keep "dead" or "abnormal" TaskManagers out
     * of this pool.
     * 将 TaskManager 注册到这个池中，只有那些来自已注册 TaskManager 的槽才会被认为是有效的。
     * 它还为我们提供了一种将“死”或“异常”TaskManagers 排除在此池之外的方法。
     *
     * @param resourceID The id of the TaskManager
     */
    @Override
    public boolean registerTaskManager(final ResourceID resourceID) {

        componentMainThreadExecutor.assertRunningInMainThread();

        log.debug("Register new TaskExecutor {}.", resourceID.getStringWithMetadata());
        return registeredTaskManagers.add(resourceID);
    }

    /**
     * Unregister TaskManager from this pool, all the related slots will be released and tasks be
     * canceled. Called when we find some TaskManager becomes "dead" or "abnormal", and we decide to
     * not using slots from it anymore.
     * 从这个池中注销TaskManager，所有相关的slots都将被释放并被取消。
     * 当我们发现一些 TaskManager 变得“死”或“异常”时调用，我们决定不再使用它的插槽。
     *
     * @param resourceId The id of the TaskManager
     * @param cause for the releasing of the TaskManager
     */
    @Override
    public boolean releaseTaskManager(final ResourceID resourceId, final Exception cause) {

        componentMainThreadExecutor.assertRunningInMainThread();

        if (registeredTaskManagers.remove(resourceId)) {
            releaseTaskManagerInternal(resourceId, cause);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public AllocatedSlotReport createAllocatedSlotReport(ResourceID taskManagerId) {
        final Set<AllocatedSlot> availableSlotsForTaskManager =
                availableSlots.getSlotsForTaskManager(taskManagerId);
        final Set<AllocatedSlot> allocatedSlotsForTaskManager =
                allocatedSlots.getSlotsForTaskManager(taskManagerId);

        List<AllocatedSlotInfo> allocatedSlotInfos =
                new ArrayList<>(
                        availableSlotsForTaskManager.size() + allocatedSlotsForTaskManager.size());
        for (AllocatedSlot allocatedSlot :
                Iterables.concat(availableSlotsForTaskManager, allocatedSlotsForTaskManager)) {
            allocatedSlotInfos.add(
                    new AllocatedSlotInfo(
                            allocatedSlot.getPhysicalSlotNumber(),
                            allocatedSlot.getAllocationId()));
        }
        return new AllocatedSlotReport(jobId, allocatedSlotInfos);
    }

    // ------------------------------------------------------------------------
    //  Internal methods
    // ------------------------------------------------------------------------

    @VisibleForTesting
    protected void timeoutPendingSlotRequest(SlotRequestId slotRequestId) {
        log.info("Pending slot request [{}] timed out.", slotRequestId);
        final PendingRequest pendingRequest = removePendingRequest(slotRequestId);

        if (pendingRequest != null) {
            pendingRequest
                    .getAllocatedSlotFuture()
                    .completeExceptionally(
                            new TimeoutException("Pending slot request timed out in SlotPool."));
        }
    }

    private void releaseTaskManagerInternal(final ResourceID resourceId, final Exception cause) {
        final Set<AllocatedSlot> removedSlots =
                new HashSet<>(allocatedSlots.removeSlotsForTaskManager(resourceId));

        for (AllocatedSlot allocatedSlot : removedSlots) {
            allocatedSlot.releasePayload(cause);
        }

        removedSlots.addAll(availableSlots.removeAllForTaskManager(resourceId));

        for (AllocatedSlot removedSlot : removedSlots) {
            TaskManagerGateway taskManagerGateway = removedSlot.getTaskManagerGateway();
            taskManagerGateway.freeSlot(removedSlot.getAllocationId(), cause, rpcTimeout);
        }
    }

    /** Check the available slots, release the slot that is idle for a long time.
     * 检查可用槽位，释放长时间空闲的槽位。
     * */
    protected void checkIdleSlot() {

        // The timestamp in SlotAndTimestamp is relative
        final long currentRelativeTimeMillis = clock.relativeTimeMillis();

        final List<AllocatedSlot> expiredSlots = new ArrayList<>(availableSlots.size());

        for (SlotAndTimestamp slotAndTimestamp : availableSlots.availableSlots.values()) {
            if (currentRelativeTimeMillis - slotAndTimestamp.timestamp
                    > idleSlotTimeout.toMilliseconds()) {
                expiredSlots.add(slotAndTimestamp.slot);
            }
        }

        final FlinkException cause = new FlinkException("Releasing idle slot.");

        for (AllocatedSlot expiredSlot : expiredSlots) {
            final AllocationID allocationID = expiredSlot.getAllocationId();
            if (availableSlots.tryRemove(allocationID) != null) {

                log.info("Releasing idle slot [{}].", allocationID);
                final CompletableFuture<Acknowledge> freeSlotFuture =
                        expiredSlot
                                .getTaskManagerGateway()
                                .freeSlot(allocationID, cause, rpcTimeout);

                FutureUtils.whenCompleteAsyncIfNotDone(
                        freeSlotFuture,
                        componentMainThreadExecutor,
                        (Acknowledge ignored, Throwable throwable) -> {
                            if (throwable != null) {
                                // The slot status will be synced to task manager in next heartbeat.
                                log.debug(
                                        "Releasing slot [{}] of registered TaskExecutor {} failed. Discarding slot.",
                                        allocationID,
                                        expiredSlot.getTaskManagerId(),
                                        throwable);
                            }
                        });
            }
        }

        scheduleRunAsync(this::checkIdleSlot, idleSlotTimeout);
    }

    protected void checkBatchSlotTimeout() {
        if (!batchSlotRequestTimeoutCheckEnabled) {
            return;
        }

        final Collection<PendingRequest> pendingBatchRequests = getPendingBatchRequests();

        if (!pendingBatchRequests.isEmpty()) {
            final Set<ResourceProfile> allocatedResourceProfiles = getAllocatedResourceProfiles();

            final Map<Boolean, List<PendingRequest>> fulfillableAndUnfulfillableRequests =
                    pendingBatchRequests.stream()
                            .collect(
                                    Collectors.partitioningBy(
                                            canBeFulfilledWithAllocatedSlot(
                                                    allocatedResourceProfiles)));

            final List<PendingRequest> fulfillableRequests =
                    fulfillableAndUnfulfillableRequests.get(true);
            final List<PendingRequest> unfulfillableRequests =
                    fulfillableAndUnfulfillableRequests.get(false);

            final long currentTimestamp = clock.relativeTimeMillis();

            for (PendingRequest fulfillableRequest : fulfillableRequests) {
                fulfillableRequest.markFulfillable();
            }

            for (PendingRequest unfulfillableRequest : unfulfillableRequests) {
                unfulfillableRequest.markUnfulfillable(currentTimestamp);

                if (unfulfillableRequest.getUnfulfillableSince() + batchSlotTimeout.toMilliseconds()
                        <= currentTimestamp) {
                    timeoutPendingSlotRequest(unfulfillableRequest.getSlotRequestId());
                }
            }
        }

        scheduleRunAsync(this::checkBatchSlotTimeout, batchSlotTimeout);
    }

    private Set<ResourceProfile> getAllocatedResourceProfiles() {
        return Stream.concat(
                        getAvailableSlotsInformation().stream(),
                        getAllocatedSlotsInformation().stream())
                .map(SlotInfo::getResourceProfile)
                .collect(Collectors.toSet());
    }

    private Collection<PendingRequest> getPendingBatchRequests() {
        return Stream.concat(
                        pendingRequests.values().stream(),
                        waitingForResourceManager.values().stream())
                .filter(PendingRequest::isBatchRequest)
                .collect(Collectors.toList());
    }

    private Predicate<PendingRequest> canBeFulfilledWithAllocatedSlot(
            Set<ResourceProfile> allocatedResourceProfiles) {
        return pendingRequest -> {
            for (ResourceProfile allocatedResourceProfile : allocatedResourceProfiles) {
                if (allocatedResourceProfile.isMatching(pendingRequest.getResourceProfile())) {
                    return true;
                }
            }

            return false;
        };
    }

    /** Clear the internal state of the SlotPool.
     * 清除 SlotPool 的内部状态。
     * */
    private void clear() {
        availableSlots.clear();
        allocatedSlots.clear();
        pendingRequests.clear();
        waitingForResourceManager.clear();
        registeredTaskManagers.clear();
    }

    // ------------------------------------------------------------------------
    //  Methods for tests
    // ------------------------------------------------------------------------

    private void scheduledLogStatus() {
        log.debug(printStatus());
        scheduleRunAsync(this::scheduledLogStatus, STATUS_LOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private String printStatus() {

        final StringBuilder builder = new StringBuilder(1024).append("Slot Pool Status:\n");

        builder.append("\tstatus: ");
        if (resourceManagerGateway != null) {
            builder.append("connected to ")
                    .append(resourceManagerGateway.getAddress())
                    .append('\n');
        } else {
            builder.append("unconnected and waiting for ResourceManager ")
                    .append(waitingForResourceManager)
                    .append('\n');
        }

        builder.append("\tregistered TaskManagers: ").append(registeredTaskManagers).append('\n');

        builder.append("\tavailable slots: ").append(availableSlots.printAllSlots()).append('\n');
        builder.append("\tallocated slots: ").append(allocatedSlots.printAllSlots()).append('\n');

        builder.append("\tpending requests: ").append(pendingRequests.values()).append('\n');

        builder.append("\t}\n");
        return builder.toString();
    }

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint.
     * 在底层 RPC 端点的主线程中执行 runnable。
     *
     * @param runnable Runnable to be executed in the main thread of the underlying RPC endpoint
     */
    protected void runAsync(Runnable runnable) {
        componentMainThreadExecutor.execute(runnable);
    }

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint, with a delay of the
     * given number of milliseconds.
     * 在底层 RPC 端点的主线程中执行 runnable，延迟为给定的毫秒数。
     *
     * @param runnable Runnable to be executed
     * @param delay The delay after which the runnable will be executed
     */
    protected void scheduleRunAsync(Runnable runnable, Time delay) {
        scheduleRunAsync(runnable, delay.getSize(), delay.getUnit());
    }

    /**
     * Execute the runnable in the main thread of the underlying RPC endpoint, with a delay of the
     * given number of milliseconds.
     * 在底层 RPC 端点的主线程中执行 runnable，延迟为给定的毫秒数。
     *
     * @param runnable Runnable to be executed
     * @param delay The delay after which the runnable will be executed
     */
    protected void scheduleRunAsync(Runnable runnable, long delay, TimeUnit unit) {
        componentMainThreadExecutor.schedule(runnable, delay, unit);
    }

    // ------------------------------------------------------------------------
    //  Helper classes
    // ------------------------------------------------------------------------

    /** Organize allocated slots from different points of view.
     * 从不同的角度组织分配的插槽。
     * */
    static class AllocatedSlots {

        /** All allocated slots organized by TaskManager's id. */
        private final Map<ResourceID, Set<AllocatedSlot>> allocatedSlotsByTaskManager;

        /** All allocated slots organized by AllocationID. */
        private final DualKeyLinkedMap<AllocationID, SlotRequestId, AllocatedSlot>
                allocatedSlotsById;

        AllocatedSlots() {
            this.allocatedSlotsByTaskManager = new HashMap<>(16);
            this.allocatedSlotsById = new DualKeyLinkedMap<>(16);
        }

        /**
         * Adds a new slot to this collection.
         * 向此集合添加一个新插槽。
         *
         * @param allocatedSlot The allocated slot
         */
        void add(SlotRequestId slotRequestId, AllocatedSlot allocatedSlot) {
            allocatedSlotsById.put(allocatedSlot.getAllocationId(), slotRequestId, allocatedSlot);

            final ResourceID resourceID = allocatedSlot.getTaskManagerLocation().getResourceID();

            Set<AllocatedSlot> slotsForTaskManager =
                    allocatedSlotsByTaskManager.computeIfAbsent(
                            resourceID, resourceId -> new HashSet<>(4));

            slotsForTaskManager.add(allocatedSlot);
        }

        /**
         * Get allocated slot with allocation id.
         * 使用分配 ID 获取分配的插槽。
         *
         * @param allocationID The allocation id
         * @return The allocated slot, null if we can't find a match
         */
        @Nullable
        AllocatedSlot get(final AllocationID allocationID) {
            return allocatedSlotsById.getValueByKeyA(allocationID);
        }

        /**
         * Check whether we have allocated this slot.
         * 检查我们是否已经分配了这个槽。
         *
         * @param slotAllocationId The allocation id of the slot to check
         * @return True if we contains this slot
         */
        boolean contains(AllocationID slotAllocationId) {
            return allocatedSlotsById.containsKeyA(slotAllocationId);
        }

        /**
         * Removes the allocated slot specified by the provided slot allocation id.
         * 删除由提供的槽分配 id 指定的分配槽。
         *
         * @param allocationID identifying the allocated slot to remove
         * @return The removed allocated slot or null.
         */
        @Nullable
        AllocatedSlot remove(final AllocationID allocationID) {
            AllocatedSlot allocatedSlot = allocatedSlotsById.removeKeyA(allocationID);

            if (allocatedSlot != null) {
                removeAllocatedSlot(allocatedSlot);
            }

            return allocatedSlot;
        }

        /**
         * Removes the allocated slot specified by the provided slot request id.
         * 删除由提供的槽请求 ID 指定的分配槽。
         *
         * @param slotRequestId identifying the allocated slot to remove
         * @return The removed allocated slot or null.
         */
        @Nullable
        AllocatedSlot remove(final SlotRequestId slotRequestId) {
            final AllocatedSlot allocatedSlot = allocatedSlotsById.removeKeyB(slotRequestId);

            if (allocatedSlot != null) {
                removeAllocatedSlot(allocatedSlot);
            }

            return allocatedSlot;
        }

        private void removeAllocatedSlot(final AllocatedSlot allocatedSlot) {
            Preconditions.checkNotNull(allocatedSlot);
            final ResourceID taskManagerId = allocatedSlot.getTaskManagerLocation().getResourceID();
            Set<AllocatedSlot> slotsForTM = allocatedSlotsByTaskManager.get(taskManagerId);

            slotsForTM.remove(allocatedSlot);

            if (slotsForTM.isEmpty()) {
                allocatedSlotsByTaskManager.remove(taskManagerId);
            }
        }

        /**
         * Get all allocated slot from same TaskManager.
         * 从同一个 TaskManager 获取所有分配的插槽。
         *
         * @param resourceID The id of the TaskManager
         * @return Set of slots which are allocated from the same TaskManager
         */
        Set<AllocatedSlot> removeSlotsForTaskManager(final ResourceID resourceID) {
            Set<AllocatedSlot> slotsForTaskManager = allocatedSlotsByTaskManager.remove(resourceID);
            if (slotsForTaskManager != null) {
                for (AllocatedSlot allocatedSlot : slotsForTaskManager) {
                    allocatedSlotsById.removeKeyA(allocatedSlot.getAllocationId());
                }
                return slotsForTaskManager;
            } else {
                return Collections.emptySet();
            }
        }

        void clear() {
            allocatedSlotsById.clear();
            allocatedSlotsByTaskManager.clear();
        }

        String printAllSlots() {
            return allocatedSlotsByTaskManager.values().toString();
        }

        @VisibleForTesting
        boolean containResource(final ResourceID resourceID) {
            return allocatedSlotsByTaskManager.containsKey(resourceID);
        }

        @VisibleForTesting
        int size() {
            return allocatedSlotsById.size();
        }

        @VisibleForTesting
        Set<AllocatedSlot> getSlotsForTaskManager(ResourceID resourceId) {
            return allocatedSlotsByTaskManager.getOrDefault(resourceId, Collections.emptySet());
        }

        Collection<SlotInfo> listSlotInfo() {
            return new ArrayList<>(allocatedSlotsById.values());
        }

        Map<ResourceID, Set<AllocatedSlot>> getSlotsByTaskManager() {
            return Collections.unmodifiableMap(allocatedSlotsByTaskManager);
        }
    }

    // ------------------------------------------------------------------------

    /** Organize all available slots from different points of view.
     * 从不同的角度组织所有可用的插槽。
     * */
    protected static class AvailableSlots {

        /** All available slots organized by TaskManager.
         * TaskManager 组织的所有可用插槽。
         * */
        private final HashMap<ResourceID, Set<AllocatedSlot>> availableSlotsByTaskManager;

        /** All available slots organized by host.
         * 由主机组织的所有可用插槽。
         * */
        private final HashMap<String, Set<AllocatedSlot>> availableSlotsByHost;

        /** The available slots, with the time when they were inserted.
         * 可用的插槽，以及它们插入的时间。
         * */
        private final HashMap<AllocationID, SlotAndTimestamp> availableSlots;

        AvailableSlots() {
            this.availableSlotsByTaskManager = new HashMap<>();
            this.availableSlotsByHost = new HashMap<>();
            this.availableSlots = new HashMap<>();
        }

        /**
         * Adds an available slot.
         * 添加一个可用的插槽。
         *
         * @param slot The slot to add
         */
        void add(final AllocatedSlot slot, final long timestamp) {
            checkNotNull(slot);

            SlotAndTimestamp previous =
                    availableSlots.put(
                            slot.getAllocationId(), new SlotAndTimestamp(slot, timestamp));

            if (previous == null) {
                final ResourceID resourceID = slot.getTaskManagerLocation().getResourceID();
                final String host = slot.getTaskManagerLocation().getFQDNHostname();

                Set<AllocatedSlot> slotsForTaskManager =
                        availableSlotsByTaskManager.computeIfAbsent(
                                resourceID, k -> new HashSet<>());
                slotsForTaskManager.add(slot);

                Set<AllocatedSlot> slotsForHost =
                        availableSlotsByHost.computeIfAbsent(host, k -> new HashSet<>());
                slotsForHost.add(slot);
            } else {
                throw new IllegalStateException("slot already contained");
            }
        }

        /** Check whether we have this slot.
         * 检查我们是否有这个插槽。
         * */
        boolean contains(AllocationID slotId) {
            return availableSlots.containsKey(slotId);
        }

        AllocatedSlot get(AllocationID allocationID) {
            SlotAndTimestamp slotAndTimestamp = availableSlots.get(allocationID);
            if (slotAndTimestamp != null) {
                return slotAndTimestamp.slot();
            } else {
                return null;
            }
        }

        /**
         * Remove all available slots come from specified TaskManager.
         * 删除所有来自指定 TaskManager 的可用插槽。
         *
         * @param taskManager The id of the TaskManager
         * @return The set of removed slots for the given TaskManager
         */
        Set<AllocatedSlot> removeAllForTaskManager(final ResourceID taskManager) {
            // remove from the by-TaskManager view
            final Set<AllocatedSlot> slotsForTm = availableSlotsByTaskManager.remove(taskManager);

            if (slotsForTm != null && slotsForTm.size() > 0) {
                final String host =
                        slotsForTm.iterator().next().getTaskManagerLocation().getFQDNHostname();
                final Set<AllocatedSlot> slotsForHost = availableSlotsByHost.get(host);

                // remove from the base set and the by-host view
                for (AllocatedSlot slot : slotsForTm) {
                    availableSlots.remove(slot.getAllocationId());
                    slotsForHost.remove(slot);
                }

                if (slotsForHost.isEmpty()) {
                    availableSlotsByHost.remove(host);
                }

                return slotsForTm;
            } else {
                return Collections.emptySet();
            }
        }

        AllocatedSlot tryRemove(AllocationID slotId) {
            final SlotAndTimestamp sat = availableSlots.remove(slotId);
            if (sat != null) {
                final AllocatedSlot slot = sat.slot();
                final ResourceID resourceID = slot.getTaskManagerLocation().getResourceID();
                final String host = slot.getTaskManagerLocation().getFQDNHostname();

                final Set<AllocatedSlot> slotsForTm = availableSlotsByTaskManager.get(resourceID);
                final Set<AllocatedSlot> slotsForHost = availableSlotsByHost.get(host);

                slotsForTm.remove(slot);
                slotsForHost.remove(slot);

                if (slotsForTm.isEmpty()) {
                    availableSlotsByTaskManager.remove(resourceID);
                }
                if (slotsForHost.isEmpty()) {
                    availableSlotsByHost.remove(host);
                }

                return slot;
            } else {
                return null;
            }
        }

        @Nonnull
        List<SlotInfo> listSlotInfo() {
            return availableSlots.values().stream()
                    .map(SlotAndTimestamp::slot)
                    .collect(Collectors.toList());
        }

        private void remove(AllocationID slotId) throws IllegalStateException {
            if (tryRemove(slotId) == null) {
                throw new IllegalStateException("slot not contained");
            }
        }

        String printAllSlots() {
            return availableSlots.values().toString();
        }

        @VisibleForTesting
        boolean containsTaskManager(ResourceID resourceID) {
            return availableSlotsByTaskManager.containsKey(resourceID);
        }

        @VisibleForTesting
        public int size() {
            return availableSlots.size();
        }

        @VisibleForTesting
        void clear() {
            availableSlots.clear();
            availableSlotsByTaskManager.clear();
            availableSlotsByHost.clear();
        }

        Set<AllocatedSlot> getSlotsForTaskManager(ResourceID resourceId) {
            return availableSlotsByTaskManager.getOrDefault(resourceId, Collections.emptySet());
        }

        Map<ResourceID, Set<AllocatedSlot>> getSlotsByTaskManager() {
            return Collections.unmodifiableMap(availableSlotsByTaskManager);
        }
    }

    // ------------------------------------------------------------------------

    /** A pending request for a slot. */
    protected static class PendingRequest {

        private final SlotRequestId slotRequestId;

        private final ResourceProfile resourceProfile;

        private final boolean isBatchRequest;

        private final CompletableFuture<AllocatedSlot> allocatedSlotFuture;

        @Nullable private AllocationID allocationId;

        private long unfillableSince;

        private PendingRequest(
                SlotRequestId slotRequestId,
                ResourceProfile resourceProfile,
                boolean isBatchRequest) {
            this(slotRequestId, resourceProfile, isBatchRequest, new CompletableFuture<>());
        }

        private PendingRequest(
                SlotRequestId slotRequestId,
                ResourceProfile resourceProfile,
                boolean isBatchRequest,
                CompletableFuture<AllocatedSlot> allocatedSlotFuture) {
            this.slotRequestId = Preconditions.checkNotNull(slotRequestId);
            this.resourceProfile = Preconditions.checkNotNull(resourceProfile);
            this.isBatchRequest = isBatchRequest;
            this.allocatedSlotFuture = Preconditions.checkNotNull(allocatedSlotFuture);
            this.unfillableSince = Long.MAX_VALUE;
        }

        static PendingRequest createStreamingRequest(
                SlotRequestId slotRequestId, ResourceProfile resourceProfile) {
            return new PendingRequest(slotRequestId, resourceProfile, false);
        }

        static PendingRequest createBatchRequest(
                SlotRequestId slotRequestId, ResourceProfile resourceProfile) {
            return new PendingRequest(slotRequestId, resourceProfile, true);
        }

        public SlotRequestId getSlotRequestId() {
            return slotRequestId;
        }

        public CompletableFuture<AllocatedSlot> getAllocatedSlotFuture() {
            return allocatedSlotFuture;
        }

        public boolean isBatchRequest() {
            return isBatchRequest;
        }

        public ResourceProfile getResourceProfile() {
            return resourceProfile;
        }

        @Override
        public String toString() {
            return "PendingRequest{"
                    + "slotRequestId="
                    + slotRequestId
                    + ", resourceProfile="
                    + resourceProfile
                    + ", allocatedSlotFuture="
                    + allocatedSlotFuture
                    + '}';
        }

        void markFulfillable() {
            unfillableSince = Long.MAX_VALUE;
        }

        void markUnfulfillable(long currentTimestamp) {
            if (isFulfillable()) {
                unfillableSince = currentTimestamp;
            }
        }

        private boolean isFulfillable() {
            return unfillableSince == Long.MAX_VALUE;
        }

        long getUnfulfillableSince() {
            return unfillableSince;
        }

        void setAllocationId(final AllocationID allocationId) {
            this.allocationId = allocationId;
        }

        Optional<AllocationID> getAllocationId() {
            return Optional.ofNullable(allocationId);
        }
    }

    // ------------------------------------------------------------------------

    /** A slot, together with the timestamp when it was added.
     * 一个插槽，以及添加它时的时间戳。
     * */
    private static class SlotAndTimestamp {

        private final AllocatedSlot slot;

        private final long timestamp;

        SlotAndTimestamp(AllocatedSlot slot, long timestamp) {
            this.slot = slot;
            this.timestamp = timestamp;
        }

        public AllocatedSlot slot() {
            return slot;
        }

        public long timestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return slot + " @ " + timestamp;
        }
    }
}
