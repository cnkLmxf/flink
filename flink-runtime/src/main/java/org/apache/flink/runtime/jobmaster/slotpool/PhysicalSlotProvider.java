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

import org.apache.flink.runtime.jobmaster.SlotRequestId;

import java.util.concurrent.CompletableFuture;

/** The provider serves physical slot requests.
 * 提供者服务于物理槽请求。
 * */
public interface PhysicalSlotProvider {

    /**
     * Submit a request to allocate a physical slot.
     * 提交分配物理槽的请求。
     *
     * <p>The physical slot can be either allocated from the slots, which are already available for
     * the job, or a new one can be requeted from the resource manager.
     * 物理槽可以从已经可用于作业的槽中分配，也可以从资源管理器重新请求一个新槽。
     *
     * @param physicalSlotRequest slot requirements
     * @return a future of the allocated slot
     */
    CompletableFuture<PhysicalSlotRequest.Result> allocatePhysicalSlot(
            PhysicalSlotRequest physicalSlotRequest);

    /**
     * Cancels the slot request with the given {@link SlotRequestId}.
     * 使用给定的 {@link SlotRequestId} 取消槽请求。
     *
     * <p>If the request is already fulfilled with a physical slot, the slot will be released.
     * 如果请求已经通过物理槽完成，则该槽将被释放。
     *
     * @param slotRequestId identifying the slot request to cancel
     * @param cause of the cancellation
     */
    void cancelSlotRequest(SlotRequestId slotRequestId, Throwable cause);
}
