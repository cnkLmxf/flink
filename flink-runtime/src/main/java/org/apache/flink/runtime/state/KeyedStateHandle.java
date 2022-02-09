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

package org.apache.flink.runtime.state;

import javax.annotation.Nullable;

/**
 * Base for the handles of the checkpointed states in keyed streams. When recovering from failures,
 * the handle will be passed to all tasks whose key group ranges overlap with it.
 * 键控流中检查点状态句柄的基础。 从故障中恢复时，句柄将传递给所有与其key-group范围重叠的task。
 */
public interface KeyedStateHandle extends CompositeStateHandle {

    /** Returns the range of the key groups contained in the state.
     * 返回状态中包含的键组的范围。
     * */
    KeyGroupRange getKeyGroupRange();

    /**
     * Returns a state over a range that is the intersection between this handle's key-group range
     * and the provided key-group range.
     * 返回一个范围内的状态，该范围是此句柄的key-group范围与提供的key-group范围之间的交集。
     *
     * @param keyGroupRange The key group range to intersect with, will return null if the
     *     intersection of this handle's key-group and the provided key-group is empty.
     */
    @Nullable
    KeyedStateHandle getIntersection(KeyGroupRange keyGroupRange);
}
