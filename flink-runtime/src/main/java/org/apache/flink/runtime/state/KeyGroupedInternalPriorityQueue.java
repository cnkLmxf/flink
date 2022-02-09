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

package org.apache.flink.runtime.state;

import javax.annotation.Nonnull;

import java.util.Set;

/**
 * This interface exists as (temporary) adapter between the new {@link InternalPriorityQueue} and
 * the old way in which timers are written in a snapshot. This interface can probably go away once
 * timer state becomes part of the keyed state backend snapshot.
 * 此接口作为新的 {@link InternalPriorityQueue} 和在快照中写入计时器的旧方式之间的（临时）适配器存在。
 * 一旦计时器状态成为键控状态后端快照的一部分，此接口可能会消失。
 */
public interface KeyGroupedInternalPriorityQueue<T> extends InternalPriorityQueue<T> {

    /**
     * Returns the subset of elements in the priority queue that belongs to the given key-group,
     * within the operator's key-group range.
     * 返回优先级队列中属于给定键组的元素子集，在运算符的键组范围内。
     */
    @Nonnull
    Set<T> getSubsetForKeyGroup(int keyGroupId);
}
