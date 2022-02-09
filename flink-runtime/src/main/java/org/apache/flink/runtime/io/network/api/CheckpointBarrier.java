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

package org.apache.flink.runtime.io.network.api;

import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.event.RuntimeEvent;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Checkpoint barriers are used to align checkpoints throughout the streaming topology. The barriers
 * are emitted by the sources when instructed to do so by the JobManager. When operators receive a
 * CheckpointBarrier on one of its inputs, it knows that this is the point between the
 * pre-checkpoint and post-checkpoint data.
 * 检查点障碍用于在整个流拓扑中对齐检查点。 当 JobManager 指示这样做时，源会发出障碍。
 * 当操作员在其输入之一上收到 CheckpointBarrier 时，它知道这是前检查点数据和后检查点数据之间的点。
 *
 * <p>Once an operator has received a checkpoint barrier from all its input channels, it knows that
 * a certain checkpoint is complete. It can trigger the operator specific checkpoint behavior and
 * broadcast the barrier to downstream operators.
 * 一旦操作员从其所有输入通道接收到检查点屏障，它就知道某个检查点已完成。
 * 它可以触发运营商特定的检查点行为并将屏障广播给下游运营商。
 *
 * <p>Depending on the semantic guarantees, may hold off post-checkpoint data until the checkpoint
 * is complete (exactly once).
 * 根据语义保证，可能会推迟检查点后的数据，直到检查点完成（恰好一次）。
 *
 * <p>The checkpoint barrier IDs are strictly monotonous increasing.
 * 检查点屏障 ID 严格单调递增。
 */
public class CheckpointBarrier extends RuntimeEvent {

    private final long id;
    private final long timestamp;
    private final CheckpointOptions checkpointOptions;

    public CheckpointBarrier(long id, long timestamp, CheckpointOptions checkpointOptions) {
        this.id = id;
        this.timestamp = timestamp;
        this.checkpointOptions = checkNotNull(checkpointOptions);
    }

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public CheckpointOptions getCheckpointOptions() {
        return checkpointOptions;
    }

    public CheckpointBarrier withOptions(CheckpointOptions checkpointOptions) {
        return this.checkpointOptions == checkpointOptions
                ? this
                : new CheckpointBarrier(id, timestamp, checkpointOptions);
    }

    // ------------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------------

    //
    //  These methods are inherited form the generic serialization of AbstractEvent
    //  but would require the CheckpointBarrier to be mutable. Since all serialization
    //  for events goes through the EventSerializer class, which has special serialization
    //  for the CheckpointBarrier, we don't need these methods
    // 这些方法继承自 AbstractEvent 的通用序列化，但要求 CheckpointBarrier 是可变的。
    // 由于事件的所有序列化都通过 EventSerializer 类，
    // 该类对 CheckpointBarrier 具有特殊的序列化，因此我们不需要这些方法
    //

    @Override
    public void write(DataOutputView out) throws IOException {
        throw new UnsupportedOperationException("This method should never be called");
    }

    @Override
    public void read(DataInputView in) throws IOException {
        throw new UnsupportedOperationException("This method should never be called");
    }

    // ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32) ^ timestamp ^ (timestamp >>> 32));
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other == null || other.getClass() != CheckpointBarrier.class) {
            return false;
        } else {
            CheckpointBarrier that = (CheckpointBarrier) other;
            return that.id == this.id
                    && that.timestamp == this.timestamp
                    && this.checkpointOptions.equals(that.checkpointOptions);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "CheckpointBarrier %d @ %d Options: %s", id, timestamp, checkpointOptions);
    }

    public boolean isCheckpoint() {
        return !checkpointOptions.getCheckpointType().isSavepoint();
    }

    public CheckpointBarrier asUnaligned() {
        return checkpointOptions.isUnalignedCheckpoint()
                ? this
                : new CheckpointBarrier(
                        getId(), getTimestamp(), getCheckpointOptions().toUnaligned());
    }
}
