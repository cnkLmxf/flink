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

import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.jobmanager.scheduler.Locality;
import org.apache.flink.runtime.jobmanager.slots.TaskManagerGateway;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;

import javax.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * A logical slot represents a resource on a TaskManager into which a single task can be deployed.
 * 逻辑槽表示 TaskManager 上可以部署单个任务的资源。
 */
public interface LogicalSlot {

    Payload TERMINATED_PAYLOAD =
            new Payload() {

                private final CompletableFuture<?> completedTerminationFuture =
                        CompletableFuture.completedFuture(null);

                @Override
                public void fail(Throwable cause) {
                    // ignore
                }

                @Override
                public CompletableFuture<?> getTerminalStateFuture() {
                    return completedTerminationFuture;
                }
            };

    /**
     * Return the TaskManager location of this slot.
     * 返回此插槽的 TaskManager 位置。
     *
     * @return TaskManager location of this slot
     */
    TaskManagerLocation getTaskManagerLocation();

    /**
     * Return the TaskManager gateway to talk to the TaskManager.
     * 返回任务管理器网关以与任务管理器对话。
     *
     * @return TaskManager gateway to talk to the TaskManager
     */
    TaskManagerGateway getTaskManagerGateway();

    /**
     * Gets the locality of this slot.
     * 获取此插槽的位置。
     *
     * @return locality of this slot
     */
    Locality getLocality();

    /**
     * True if the slot is alive and has not been released.
     * 如果插槽处于活动状态且尚未释放，则为真。
     *
     * @return True if the slot is alive, otherwise false if the slot is released
     */
    boolean isAlive();

    /**
     * Tries to assign a payload to this slot. One can only assign a single payload once.
     * 尝试将有效负载分配给此插槽。 一个人只能分配一个有效载荷一次。
     *
     * @param payload to be assigned to this slot.
     * @return true if the payload could be assigned, otherwise false
     */
    boolean tryAssignPayload(Payload payload);

    /**
     * Returns the set payload or null if none.
     * 如果没有，则返回设置的有效负载或 null。
     *
     * @return Payload of this slot of null if none
     */
    @Nullable
    Payload getPayload();

    /**
     * Releases this slot.
     * 释放此插槽。
     *
     * @return Future which is completed once the slot has been released, in case of a failure it is
     *     completed exceptionally
     * @deprecated Added because extended the actual releaseSlot method with cause parameter.
     */
    default CompletableFuture<?> releaseSlot() {
        return releaseSlot(null);
    }

    /**
     * Releases this slot.
     *
     * @param cause why the slot was released or null if none
     * @return future which is completed once the slot has been released
     */
    CompletableFuture<?> releaseSlot(@Nullable Throwable cause);

    /**
     * Gets the allocation id of this slot. Multiple logical slots can share the same allocation id.
     * 获取此插槽的分配 id。 多个逻辑插槽可以共享相同的分配 ID。
     *
     * @return allocation id of this slot
     */
    AllocationID getAllocationId();

    /**
     * Gets the slot request id uniquely identifying the request with which this slot has been
     * allocated.
     * 获取唯一标识已分配此槽的请求的槽请求 ID。
     *
     * @return Unique id identifying the slot request with which this slot was allocated
     */
    SlotRequestId getSlotRequestId();

    /** Payload for a logical slot.
     * 逻辑槽的有效负载。
     * */
    interface Payload {

        /**
         * Fail the payload with the given cause.
         * 以给定的原因使有效负载失败。
         *
         * @param cause of the failure
         */
        void fail(Throwable cause);

        /**
         * Gets the terminal state future which is completed once the payload has reached a terminal
         * state.
         * 获取一旦有效负载达到终端状态就完成的终端状态future。
         *
         * @return Terminal state future
         */
        CompletableFuture<?> getTerminalStateFuture();
    }
}
