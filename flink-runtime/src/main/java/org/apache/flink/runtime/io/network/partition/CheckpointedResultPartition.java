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

import java.io.IOException;

/**
 * Interface for partitions that are checkpointed, meaning they store data as part of unaligned
 * checkpoints.
 * 带有检查点的分区的接口，这意味着它们将数据存储为未对齐检查点的一部分。
 */
public interface CheckpointedResultPartition {

    /** Gets the checkpointed subpartition with the given subpartitionIndex.
     * 获取具有给定 subpartitionIndex 的检查点子分区。
     * */
    CheckpointedResultSubpartition getCheckpointedSubpartition(int subpartitionIndex);

    void finishReadRecoveredState(boolean notifyAndBlockOnCompletion) throws IOException;
}
