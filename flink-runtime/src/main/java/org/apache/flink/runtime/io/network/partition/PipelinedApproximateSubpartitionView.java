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

package org.apache.flink.runtime.io.network.partition;

/** View over a pipelined in-memory only subpartition allowing reconnecting.
 * 查看仅允许重新连接的流水线内存子分区。
 * */
public class PipelinedApproximateSubpartitionView extends PipelinedSubpartitionView {

    PipelinedApproximateSubpartitionView(
            PipelinedApproximateSubpartition parent, BufferAvailabilityListener listener) {
        super(parent, listener);
    }

    /**
     * Pipelined ResultPartition relies on its subpartition view's release to decide whether the
     * partition is ready to release. In contrast, Approximate Pipelined ResultPartition is put into
     * the JobMaster's Partition Tracker and relies on the tracker to release partitions after the
     * job is finished. Hence in the approximate pipelined case, no resource related to view is
     * needed to be released.
     * Pipelined ResultPartition 依赖于其子分区视图的释放来决定分区是否准备好释放。
     * 相比之下，Approximate Pipelined ResultPartition 被放入 JobMaster 的 Partition Tracker 中，
     * 并在作业完成后依靠 Tracker 释放分区。 因此，在近似流水线的情况下，不需要释放与视图相关的资源。
     */
    @Override
    public void releaseAllResources() {
        isReleased.compareAndSet(false, true);
    }
}
