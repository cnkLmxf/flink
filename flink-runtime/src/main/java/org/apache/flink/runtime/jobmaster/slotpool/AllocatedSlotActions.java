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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Interface for components which have to perform actions on allocated slots.
 * 必须对分配的插槽执行操作的组件的接口。
 * */
public interface AllocatedSlotActions {

    /**
     * Releases the slot with the given {@link SlotRequestId}. Additionally, one can provide a cause
     * for the slot release.
     * 释放具有给定 {@link SlotRequestId} 的插槽。 此外，还可以提供插槽释放的原因。
     *
     * @param slotRequestId identifying the slot to release
     * @param cause of the slot release, null if none
     */
    void releaseSlot(@Nonnull SlotRequestId slotRequestId, @Nullable Throwable cause);
}
