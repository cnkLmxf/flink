/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.partition;

import java.util.Collection;

/**
 * Utility for tracking partitions.
 * 用于跟踪分区的实用程序。
 *
 * <p>This interface deliberately does not have a method to start tracking partitions, so that
 * implementation are flexible in their definitions for this method (otherwise one would end up with
 * multiple methods, with one part likely being unused).
 * 这个接口故意没有一个方法来开始跟踪分区，所以实现在他们对这个方法的定义上是灵活的（否则最终会得到多个方法，其中一部分可能没有被使用）。
 */
public interface PartitionTracker<K, M> {

    /** Stops the tracking of all partitions for the given key.
     * 停止跟踪给定键的所有分区。
     * */
    Collection<PartitionTrackerEntry<K, M>> stopTrackingPartitionsFor(K key);

    /** Stops the tracking of the given partitions.
     * 停止对给定分区的跟踪。
     * */
    Collection<PartitionTrackerEntry<K, M>> stopTrackingPartitions(
            Collection<ResultPartitionID> resultPartitionIds);

    /** Returns whether any partition is being tracked for the given key.
     * 返回是否正在跟踪给定键的任何分区。
     * */
    boolean isTrackingPartitionsFor(K key);

    /** Returns whether the given partition is being tracked.
     * 返回是否正在跟踪给定的分区。
     * */
    boolean isPartitionTracked(ResultPartitionID resultPartitionID);
}
