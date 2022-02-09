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

import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;

/**
 * This class tracks a fulfillability timeout of a bulk of physical slot requests.
 * 此类跟踪大量物理插槽请求的可履行性超时。
 *
 * <p>The check stops when all pending physical slot requests of {@link PhysicalSlotRequestBulk} are
 * fulfilled by available or newly allocated slots. The bulk is fulfillable if all its physical slot
 * requests can be fulfilled either by available or newly allocated slots or slots which currently
 * used by other job subtasks. The bulk gets canceled if the timeout occurs and the bulk is not
 * fulfillable. The timeout timer is not running while the bulk is fulfillable but not fulfilled
 * yet.
 * 当 {@link PhysicalSlotRequestBulk} 的所有未决物理插槽请求都由可用或新分配的插槽完成时，检查将停止。
 * 如果它的所有物理槽请求都可以通过可用或新分配的槽或其他作业子任务当前使用的槽来满足，则该批量是可满足的。
 * 如果发生超时并且批量无法履行，则批量将被取消。 批量可履行但尚未履行时，超时计时器未运行。
 */
public interface PhysicalSlotRequestBulkChecker {
    /**
     * Starts the bulk checker by initializing the main thread executor.
     * 通过初始化主线程执行器来启动批量检查器。
     *
     * @param mainThreadExecutor the main thread executor of the job master
     */
    void start(ComponentMainThreadExecutor mainThreadExecutor);

    /**
     * Starts tracking the fulfillability of a {@link PhysicalSlotRequestBulk} with timeout.
     * 开始跟踪具有超时的 {@link PhysicalSlotRequestBulk} 的可履行性。
     *
     * @param bulk {@link PhysicalSlotRequestBulk} to track
     * @param timeout timeout after which the bulk should be canceled if it is still not
     *     fulfillable.
     */
    void schedulePendingRequestBulkTimeoutCheck(PhysicalSlotRequestBulk bulk, Time timeout);
}
