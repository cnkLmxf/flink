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

import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;

import java.util.Collection;
import java.util.Set;

/** Represents a bulk of physical slot requests.
 * 表示大量物理插槽请求。
 * */
public interface PhysicalSlotRequestBulk {
    /**
     * Returns {@link ResourceProfile}s of pending physical slot requests.
     * 返回待处理的物理槽请求的 {@link ResourceProfile}。
     *
     * <p>If a request is pending, it is not fulfilled and vice versa. {@link
     * #getAllocationIdsOfFulfilledRequests()} should not return a pending request.
     * 如果请求处于未决状态，则它不会被满足，反之亦然。
     * {@link #getAllocationIdsOfFulfilledRequests()} 不应返回待处理的请求。
     */
    Collection<ResourceProfile> getPendingRequests();

    /**
     * Returns {@link AllocationID}s of fulfilled physical slot requests.
     * 返回已完成的物理槽请求的 {@link AllocationID}。
     *
     * <p>If a request is fulfilled, it is not pending and vice versa. {@link #getPendingRequests()}
     * should not return a fulfilled request.
     * 如果一个请求得到满足，它就不是待处理的，反之亦然。 {@link #getPendingRequests()} 不应返回已完成的请求。
     */
    Set<AllocationID> getAllocationIdsOfFulfilledRequests();

    /**
     * Cancels all requests of this bulk.
     * 取消此批量的所有请求。
     *
     * <p>Canceled bulk is not valid and should not be used afterwards.
     * 取消的批量无效，之后不应使用。
     */
    void cancel(Throwable cause);
}
