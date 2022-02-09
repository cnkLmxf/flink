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

package org.apache.flink.api.connector.source;

import org.apache.flink.annotation.Experimental;
import org.apache.flink.annotation.PublicEvolving;

import java.util.Optional;

/**
 * Sources that implement this interface do not trigger checkpoints when receiving a trigger message
 * from the checkpoint coordinator, but when their input data/events indicate that a checkpoint
 * should be triggered.
 * 实现此接口的source在接收到来自检查点协调器的触发消息时不会触发检查点，但是当它们的输入数据/事件表明应该触发检查点时。
 *
 * <p>The ExternallyInducedSourceReader tells the Flink runtime that a checkpoint needs to be made
 * by returning a checkpointId when shouldTriggerCheckpoint() is invoked.
 * ExternallyInducedSourceReader 告诉 Flink 运行时需要在调用 shouldTriggerCheckpoint() 时返回一个 checkpointId 来创建一个检查点。
 *
 * <p>The implementations typically works together with the SplitEnumerator which informs the
 * external system to trigger a checkpoint. The external system also needs to forward the Checkpoint
 * ID to the source, so the source knows which checkpoint to trigger.
 * 这些实现通常与通知外部系统触发检查点的 SplitEnumerator 一起工作。
 * 外部系统还需要将 Checkpoint ID 转发给源，这样源就知道要触发哪个 Checkpoint。
 *
 * <p><b>Important:</b> It is crucial that all parallel source tasks trigger their checkpoints at
 * roughly the same time. Otherwise this leads to performance issues due to long checkpoint
 * alignment phases or large alignment data snapshots.
 * <b>重要提示：</b>所有并行源任务大致同时触发其检查点至关重要。
 * 否则，由于检查点对齐阶段较长或对齐数据快照较大，这会导致性能问题。
 *
 * @param <T> The type of records produced by the source.
 * @param <SplitT> The type of splits handled by the source.
 */
@Experimental
@PublicEvolving
public interface ExternallyInducedSourceReader<T, SplitT extends SourceSplit>
        extends SourceReader<T, SplitT> {

    /**
     * A method that informs the Flink runtime whether a checkpoint should be triggered on this
     * Source.
     * 通知 Flink 运行时是否应在此 Source 上触发检查点的方法。
     *
     * <p>This method is invoked when the previous {@link #pollNext(ReaderOutput)} returns {@link
     * org.apache.flink.core.io.InputStatus#NOTHING_AVAILABLE}, to check if the source needs to be
     * checkpointed.
     * 当前面的 {@link #pollNext(ReaderOutput)} 返回
     * {@link org.apache.flink.core.io.InputStatus#NOTHING_AVAILABLE} 时调用此方法，以检查源是否需要检查点。
     *
     * <p>If a CheckpointId is returned, a checkpoint will be triggered on this source reader.
     * Otherwise, Flink runtime will continue to process the records.
     * 如果返回 CheckpointId，则会在此源阅读器上触发检查点。 否则，Flink 运行时会继续处理这些记录。
     *
     * @return An optional checkpoint ID that Flink runtime should take a checkpoint for.
     */
    Optional<Long> shouldTriggerCheckpoint();
}
