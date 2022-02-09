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
 * limitations under the License
 */

package org.apache.flink.runtime.executiongraph.failover.flip1.partitionrelease;

import org.apache.flink.runtime.scheduler.strategy.ConsumedPartitionGroup;
import org.apache.flink.runtime.scheduler.strategy.SchedulingPipelinedRegion;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This view maintains the finished progress of consumer {@link SchedulingPipelinedRegion}s for each
 * {@link ConsumedPartitionGroup}.
 * 此视图维护每个 {@link ConsumedPartitionGroup} 的消费者 {@link SchedulingPipelinedRegion} 的完成进度。
 */
public class ConsumerRegionGroupExecutionView implements Iterable<SchedulingPipelinedRegion> {

    private final Set<SchedulingPipelinedRegion> unfinishedConsumerRegions;

    public ConsumerRegionGroupExecutionView() {
        this.unfinishedConsumerRegions = new HashSet<>();
    }

    @Override
    public Iterator<SchedulingPipelinedRegion> iterator() {
        return unfinishedConsumerRegions.iterator();
    }

    void add(SchedulingPipelinedRegion region) {
        unfinishedConsumerRegions.add(region);
    }

    void regionFinished(SchedulingPipelinedRegion region) {
        unfinishedConsumerRegions.remove(region);
    }

    void regionUnfinished(SchedulingPipelinedRegion region) {
        unfinishedConsumerRegions.add(region);
    }

    boolean isFinished() {
        return unfinishedConsumerRegions.isEmpty();
    }
}
