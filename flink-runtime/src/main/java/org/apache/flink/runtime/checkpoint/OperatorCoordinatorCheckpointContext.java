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

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.runtime.operators.coordination.OperatorInfo;

import javax.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * This context is the interface through which the {@link CheckpointCoordinator} interacts with an
 * {@link OperatorCoordinator} during checkpointing and checkpoint restoring.
 * 此上下文是在检查点和检查点恢复期间 {@link CheckpointCoordinator} 与 {@link OperatorCoordinator} 交互的接口。
 */
public interface OperatorCoordinatorCheckpointContext extends OperatorInfo, CheckpointListener {

    void checkpointCoordinator(long checkpointId, CompletableFuture<byte[]> result)
            throws Exception;

    void afterSourceBarrierInjection(long checkpointId);

    void abortCurrentTriggering();

    /**
     * We override the method here to remove the checked exception. Please check the Java docs of
     * {@link CheckpointListener#notifyCheckpointComplete(long)} for more detail semantic of the
     * method.
     * 我们在此处重写该方法以删除已检查的异常。
     * 请查看 {@link CheckpointListener#notifyCheckpointComplete(long)} 的 Java 文档以获取该方法的更多详细语义。
     */
    @Override
    void notifyCheckpointComplete(long checkpointId);

    /**
     * We override the method here to remove the checked exception. Please check the Java docs of
     * {@link CheckpointListener#notifyCheckpointAborted(long)} for more detail semantic of the
     * method.
     * 我们在此处重写该方法以删除已检查的异常。
     * 请查看 {@link CheckpointListener#notifyCheckpointAborted(long)} 的 Java 文档以了解该方法的更多详细语义。
     */
    @Override
    default void notifyCheckpointAborted(long checkpointId) {}

    /**
     * Resets the coordinator to the checkpoint with the given state.
     * 将协调器重置为具有给定状态的检查点。
     *
     * <p>This method is called with a null state argument in the following situations:
     * 在以下情况下，使用 null 状态参数调用此方法：
     *<ul>
     *     <li>正在恢复，但尚未完成检查点。
     *     <li>已从已完成的检查点/保存点恢复，但它不包含协调器的状态。
     *  </ul>
     * <ul>
     *   <li>There is a recovery and there was no completed checkpoint yet.
     *   <li>There is a recovery from a completed checkpoint/savepoint but it contained no state for
     *       the coordinator.
     * </ul>
     *
     * <p>In both cases, the coordinator should reset to an empty (new) state.
     * 在这两种情况下，协调器都应重置为空（新）状态。
     */
    void resetToCheckpoint(long checkpointId, @Nullable byte[] checkpointData) throws Exception;

    /**
     * Called if a task is recovered as part of a <i>partial failover</i>, meaning a failover
     * handled by the scheduler's failover strategy (by default recovering a pipelined region). The
     * method is invoked for each subtask involved in that partial failover.
     * 如果任务作为<i>部分故障转移</i>的一部分被恢复，则调用，
     * 这意味着由调度程序的故障转移策略处理的故障转移（默认情况下恢复流水线区域）。
     * 对该部分故障转移中涉及的每个子任务调用该方法。
     *
     * <p>In contrast to this method, the {@link #resetToCheckpoint(long, byte[])} method is called
     * in the case of a global failover, which is the case when the coordinator (JobManager) is
     * recovered.
     * 与此方法相比，{@link #resetToCheckpoint(long, byte[])} 方法在全局故障转移的情况下被调用，
     * 这是协调器（JobManager）恢复时的情况。
     */
    void subtaskReset(int subtask, long checkpointId);
}
