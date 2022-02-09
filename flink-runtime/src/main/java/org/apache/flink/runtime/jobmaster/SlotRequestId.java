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

package org.apache.flink.runtime.jobmaster;

import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlot;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlotProvider;
import org.apache.flink.runtime.jobmaster.slotpool.SlotPool;
import org.apache.flink.util.AbstractID;

/**
 * This ID identifies the request for a slot from the Execution to the {@link SlotPool} or {@link
 * PhysicalSlotProvider}. There are various slot types like {@link PhysicalSlot}, {@link
 * LogicalSlot} or {@code SharedSlot} in the case of slot sharing.
 * 此 ID 标识从 Execution 到 {@link SlotPool} 或 {@link PhysicalSlotProvider} 的插槽请求。
 * 在插槽共享的情况下，有各种插槽类型，例如 {@link PhysicalSlot}、{@link LogicalSlot} 或 {@code SharedSlot}。
 *
 * <p>This ID serves a different purpose than the {@link
 * org.apache.flink.runtime.clusterframework.types.AllocationID AllocationID}, which identifies the
 * request of a physical slot, issued from the SlotPool via the ResourceManager to the TaskManager.
 * 此 ID 与 {@link org.apache.flink.runtime.clusterframework.types.AllocationID AllocationID} 的用途不同，
 * 后者标识物理插槽的请求，从 SlotPool 通过 ResourceManager 发出到 TaskManager。
 */
public final class SlotRequestId extends AbstractID {

    private static final long serialVersionUID = -6072105912250154283L;

    public SlotRequestId(long lowerPart, long upperPart) {
        super(lowerPart, upperPart);
    }

    public SlotRequestId() {}

    @Override
    public String toString() {
        return "SlotRequestId{" + super.toString() + '}';
    }
}
