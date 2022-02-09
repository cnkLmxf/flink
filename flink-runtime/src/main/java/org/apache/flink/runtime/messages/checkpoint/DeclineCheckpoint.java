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

package org.apache.flink.runtime.messages.checkpoint;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nonnull;

/**
 * This message is sent from the {@link org.apache.flink.runtime.taskexecutor.TaskExecutor} to the
 * {@link org.apache.flink.runtime.jobmaster.JobMaster} to tell the checkpoint coordinator that a
 * checkpoint request could not be heeded. This can happen if a Task is already in RUNNING state but
 * is internally not yet ready to perform checkpoints.
 * 此消息从 {@link org.apache.flink.runtime.taskexecutor.TaskExecutor}
 * 发送到 {@link org.apache.flink.runtime.jobmaster.JobMaster} 以告诉检查点协调器无法执行检查点请求 注意到了。
 * 如果任务已处于 RUNNING 状态但内部尚未准备好执行检查点，则可能会发生这种情况。
 */
public class DeclineCheckpoint extends AbstractCheckpointMessage implements java.io.Serializable {

    private static final long serialVersionUID = 2094094662279578953L;

    /** The serialized reason why the checkpoint was declined.
     * 检查点被拒绝的序列化原因。
     * */
    private final SerializedCheckpointException serializedCheckpointException;

    public DeclineCheckpoint(
            JobID job,
            ExecutionAttemptID taskExecutionId,
            long checkpointId,
            CheckpointException checkpointException) {
        super(job, taskExecutionId, checkpointId);

        Preconditions.checkNotNull(
                checkpointException, "The checkpoint exception must not be null.");
        // replace with a serialized throwable, to be on the safe side
        this.serializedCheckpointException = new SerializedCheckpointException(checkpointException);
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Gets the reason why the checkpoint was declined.
     * 获取检查点被拒绝的原因。
     *
     * @return The reason why the checkpoint was declined
     */
    @Nonnull
    public SerializedCheckpointException getSerializedCheckpointException() {
        return serializedCheckpointException;
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format(
                "Declined Checkpoint %d for (%s/%s): %s",
                getCheckpointId(),
                getJob(),
                getTaskExecutionId(),
                serializedCheckpointException.getCheckpointFailureReason());
    }
}
