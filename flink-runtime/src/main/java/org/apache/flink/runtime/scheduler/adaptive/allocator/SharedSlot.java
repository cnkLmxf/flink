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

package org.apache.flink.runtime.scheduler.adaptive.allocator;

import org.apache.flink.runtime.jobmanager.scheduler.Locality;
import org.apache.flink.runtime.jobmaster.LogicalSlot;
import org.apache.flink.runtime.jobmaster.SlotOwner;
import org.apache.flink.runtime.jobmaster.SlotRequestId;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlot;
import org.apache.flink.runtime.jobmaster.slotpool.SingleLogicalSlot;
import org.apache.flink.runtime.scheduler.adaptive.AdaptiveScheduler;
import org.apache.flink.util.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared slot implementation for the {@link AdaptiveScheduler}.
 * {@link AdaptiveScheduler} 的共享槽实现。
 *
 * <p>The release process of a shared slot follows one of 2 code paths:
 * 共享槽的释放过程遵循以下两种代码路径之一：
 *
 * <p>1) During normal execution all allocated logical slots will be returned, with the last return
 * triggering the {@code externalReleaseCallback} which must eventually result in a {@link
 * #release(Throwable)} call.
 * 1) 在正常执行期间，所有分配的逻辑槽都将被返回，
 * 最后一次返回触发 {@code externalReleaseCallback} 最终必须导致 {@link #release(Throwable)} 调用。
 *
 * <p>2) If the backing physical is lost (e.g., because the providing TaskManager crashed) then
 * {@link #release(Throwable)} is called without all logical slots having been returned. The runtime
 * relies on this also triggering the release of all logical slots. This will not trigger the {@code
 * externalReleaseCallback}.
 * 2）如果后备物理丢失（例如，因为提供的 TaskManager 崩溃），
 * 则调用 {@link #release(Throwable)} 而没有返回所有逻辑插槽。
 * 运行时依赖于此也触发所有逻辑槽的释放。 这不会触发 {@code externalReleaseCallback}。
 */
class SharedSlot implements SlotOwner, PhysicalSlot.Payload {
    private static final Logger LOG = LoggerFactory.getLogger(SharedSlot.class);

    private final SlotRequestId physicalSlotRequestId;

    private final PhysicalSlot physicalSlot;

    private final Runnable externalReleaseCallback;

    private final Map<SlotRequestId, LogicalSlot> allocatedLogicalSlots;

    private final boolean slotWillBeOccupiedIndefinitely;

    private State state;

    public SharedSlot(
            SlotRequestId physicalSlotRequestId,
            PhysicalSlot physicalSlot,
            boolean slotWillBeOccupiedIndefinitely,
            Runnable externalReleaseCallback) {
        this.physicalSlotRequestId = physicalSlotRequestId;
        this.physicalSlot = physicalSlot;
        this.slotWillBeOccupiedIndefinitely = slotWillBeOccupiedIndefinitely;
        this.externalReleaseCallback = externalReleaseCallback;
        this.allocatedLogicalSlots = new HashMap<>();

        Preconditions.checkState(
                physicalSlot.tryAssignPayload(this),
                "The provided slot (%s) was not free.",
                physicalSlot.getAllocationId());
        this.state = State.ALLOCATED;
    }

    /**
     * Registers an allocation request for a logical slot.
     *
     * @return the logical slot
     */
    public LogicalSlot allocateLogicalSlot() {
        LOG.debug("Allocating logical slot from shared slot ({})", physicalSlotRequestId);
        Preconditions.checkState(
                state == State.ALLOCATED, "The shared slot has already been released.");

        final LogicalSlot slot =
                new SingleLogicalSlot(
                        new SlotRequestId(),
                        physicalSlot,
                        Locality.UNKNOWN,
                        this,
                        slotWillBeOccupiedIndefinitely);

        allocatedLogicalSlots.put(slot.getSlotRequestId(), slot);
        return slot;
    }

    @Override
    public void returnLogicalSlot(LogicalSlot logicalSlot) {
        LOG.debug("Returning logical slot to shared slot ({})", physicalSlotRequestId);
        Preconditions.checkState(
                state != State.RELEASED, "The shared slot has already been released.");

        Preconditions.checkState(!logicalSlot.isAlive(), "Returned logic slot must not be alive.");
        Preconditions.checkState(
                allocatedLogicalSlots.remove(logicalSlot.getSlotRequestId()) != null,
                "Trying to remove a logical slot request which has been either already removed or never created.");
        tryReleaseExternally();
    }

    private void tryReleaseExternally() {
        if (state == State.ALLOCATED && allocatedLogicalSlots.isEmpty()) {
            LOG.debug("Release shared slot externally ({})", physicalSlotRequestId);
            externalReleaseCallback.run();
        }
    }

    @Override
    public void release(Throwable cause) {
        LOG.debug("Release shared slot ({})", physicalSlotRequestId);
        Preconditions.checkState(
                state == State.ALLOCATED, "The shared slot has already been released.");

        // ensures that we won't call the external release callback if there are still logical slots
        // to release
        state = State.RELEASING;

        // copy the logical slot collection to avoid ConcurrentModificationException
        // if logical slot releases cause cancellation of other executions
        // which will try to call returnLogicalSlot and modify allocatedLogicalSlots collection
        final List<LogicalSlot> logicalSlotsToRelease =
                new ArrayList<>(allocatedLogicalSlots.values());
        for (LogicalSlot allocatedLogicalSlot : logicalSlotsToRelease) {
            // this will also cause the logical slot to be returned
            allocatedLogicalSlot.releaseSlot(cause);
        }
        allocatedLogicalSlots.clear();

        state = State.RELEASED;
    }

    @Override
    public boolean willOccupySlotIndefinitely() {
        return slotWillBeOccupiedIndefinitely;
    }

    private enum State {
        ALLOCATED,
        RELEASING,
        RELEASED
    }
}
