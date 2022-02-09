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

import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.runtime.checkpoint.CheckpointMetaData;
import org.apache.flink.runtime.checkpoint.CheckpointMetrics;
import org.apache.flink.runtime.checkpoint.InflightDataRescalingDescriptor;
import org.apache.flink.runtime.checkpoint.PrioritizedOperatorSubtaskState;
import org.apache.flink.runtime.checkpoint.TaskStateSnapshot;
import org.apache.flink.runtime.checkpoint.channel.SequentialChannelStateReader;
import org.apache.flink.runtime.jobgraph.OperatorID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This interface provides methods to report and retrieve state for a task.
 * 此接口提供报告和检索任务状态的方法。
 *
 * <p>When a checkpoint or savepoint is triggered on a task, it will create snapshots for all stream
 * operator instances it owns. All operator snapshots from the task are then reported via this
 * interface. A typical implementation will dispatch and forward the reported state information to
 * interested parties such as the checkpoint coordinator or a local state store.
 * 当在任务上触发检查点或保存点时，它将为其拥有的所有流操作符实例创建快照。
 * 然后通过该界面报告来自任务的所有操作员快照。 典型的实现会将报告的状态信息分发并转发给感兴趣的各方，
 * 例如检查点协调器或本地状态存储。
 *
 * <p>This interface also offers the complementary method that provides access to previously saved
 * state of operator instances in the task for restore purposes.
 * 该接口还提供了补充方法，可以访问任务中先前保存的操作员实例状态以进行恢复。
 */
public interface TaskStateManager extends CheckpointListener, AutoCloseable {

    /**
     * Report the state snapshots for the operator instances running in the owning task.
     * 报告在拥有任务中运行的操作员实例的状态快照。
     *
     * @param checkpointMetaData meta data from the checkpoint request.
     * @param checkpointMetrics task level metrics for the checkpoint.
     * @param acknowledgedState the reported states to acknowledge to the job manager.
     * @param localState the reported states for local recovery.
     */
    void reportTaskStateSnapshots(
            @Nonnull CheckpointMetaData checkpointMetaData,
            @Nonnull CheckpointMetrics checkpointMetrics,
            @Nullable TaskStateSnapshot acknowledgedState,
            @Nullable TaskStateSnapshot localState);

    InflightDataRescalingDescriptor getInputRescalingDescriptor();

    InflightDataRescalingDescriptor getOutputRescalingDescriptor();

    /**
     * Report the stats for state snapshots for an aborted checkpoint.
     * 报告中止检查点的状态快照的统计信息。
     *
     * @param checkpointMetaData meta data from the checkpoint request.
     * @param checkpointMetrics task level metrics for the checkpoint.
     */
    void reportIncompleteTaskStateSnapshots(
            CheckpointMetaData checkpointMetaData, CheckpointMetrics checkpointMetrics);

    /**
     * Returns means to restore previously reported state of an operator running in the owning task.
     * 返回意味着恢复先前报告的运行在所属任务中的操作员的状态。
     *
     * @param operatorID the id of the operator for which we request state.
     * @return Previous state for the operator. The previous state can be empty if the operator had
     *     no previous state.
     *     操作员的先前状态。 如果操作员没有先前的状态，则先前的状态可以为空。
     */
    @Nonnull
    PrioritizedOperatorSubtaskState prioritizedOperatorState(OperatorID operatorID);

    /**
     * Returns the configuration for local recovery, i.e. the base directories for all file-based
     * local state of the owning subtask and the general mode for local recovery.
     * 返回本地恢复的配置，即拥有子任务的所有基于文件的本地状态的基本目录和本地恢复的一般模式。
     */
    @Nonnull
    LocalRecoveryConfig createLocalRecoveryConfig();

    SequentialChannelStateReader getSequentialChannelStateReader();
}
