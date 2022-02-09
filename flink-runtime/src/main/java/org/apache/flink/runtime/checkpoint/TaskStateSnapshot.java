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

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.state.CompositeStateHandle;
import org.apache.flink.runtime.state.SharedStateRegistry;
import org.apache.flink.runtime.state.StateUtil;
import org.apache.flink.util.Preconditions;

import org.apache.flink.shaded.guava18.com.google.common.collect.Iterators;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.apache.flink.runtime.checkpoint.InflightDataRescalingDescriptor.NO_RESCALE;

/**
 * This class encapsulates state handles to the snapshots of all operator instances executed within
 * one task. A task can run multiple operator instances as a result of operator chaining, and all
 * operator instances from the chain can register their state under their operator id. Each operator
 * instance is a physical execution responsible for processing a partition of the data that goes
 * through a logical operator. This partitioning happens to parallelize execution of logical
 * operators, e.g. distributing a map function.
 * 此类将状态句柄封装到在一个任务中执行的所有运算符实例的快照。 由于操作员链接，一个任务可以运行多个操作员实例，
 * 并且链中的所有操作员实例都可以在其操作员 ID 下注册它们的状态。 每个运算符实例都是一个物理执行，
 * 负责处理通过逻辑运算符的数据分区。 这种分区恰好并行执行逻辑运算符，例如 分发地图功能。
 *
 * <p>One instance of this class contains the information that one task will send to acknowledge a
 * checkpoint request by the checkpoint coordinator. Tasks run operator instances in parallel, so
 * the union of all {@link TaskStateSnapshot} that are collected by the checkpoint coordinator from
 * all tasks represent the whole state of a job at the time of the checkpoint.
 * 此类的一个实例包含一个任务将发送以确认检查点协调器的检查点请求的信息。
 * 任务并行运行操作员实例，因此检查点协调器从所有任务中收集的所有 {@link TaskStateSnapshot} 的
 * 并集代表了检查点时作业的整个状态。
 *
 * <p>This class should be called TaskState once the old class with this name that we keep for
 * backwards compatibility goes away.
 * 一旦我们为向后兼容而保留的具有此名称的旧类消失后，该类应称为 TaskState。
 */
public class TaskStateSnapshot implements CompositeStateHandle {

    private static final long serialVersionUID = 1L;

    /** Mapping from an operator id to the state of one subtask of this operator.
     * 从操作员 id 映射到该操作员的一个子任务的状态。
     * */
    private final Map<OperatorID, OperatorSubtaskState> subtaskStatesByOperatorID;

    public TaskStateSnapshot() {
        this(10);
    }

    public TaskStateSnapshot(int size) {
        this(new HashMap<>(size));
    }

    public TaskStateSnapshot(Map<OperatorID, OperatorSubtaskState> subtaskStatesByOperatorID) {
        this.subtaskStatesByOperatorID = Preconditions.checkNotNull(subtaskStatesByOperatorID);
    }

    /** Returns the subtask state for the given operator id (or null if not contained).
     * 返回给定操作员 ID 的子任务状态（如果不包含，则返回 null）。
     * */
    @Nullable
    public OperatorSubtaskState getSubtaskStateByOperatorID(OperatorID operatorID) {
        return subtaskStatesByOperatorID.get(operatorID);
    }

    /**
     * Maps the given operator id to the given subtask state. Returns the subtask state of a
     * previous mapping, if such a mapping existed or null otherwise.
     * 将给定的操作员 ID 映射到给定的子任务状态。 如果存在这样的映射，则返回先前映射的子任务状态，否则返回 null。
     */
    public OperatorSubtaskState putSubtaskStateByOperatorID(
            @Nonnull OperatorID operatorID, @Nonnull OperatorSubtaskState state) {

        return subtaskStatesByOperatorID.put(operatorID, Preconditions.checkNotNull(state));
    }

    /** Returns the set of all mappings from operator id to the corresponding subtask state. */
    public Set<Map.Entry<OperatorID, OperatorSubtaskState>> getSubtaskStateMappings() {
        return subtaskStatesByOperatorID.entrySet();
    }

    /**
     * Returns true if at least one {@link OperatorSubtaskState} in subtaskStatesByOperatorID has
     * state.
     */
    public boolean hasState() {
        for (OperatorSubtaskState operatorSubtaskState : subtaskStatesByOperatorID.values()) {
            if (operatorSubtaskState != null && operatorSubtaskState.hasState()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the input channel mapping for rescaling with in-flight data or {@link
     * InflightDataRescalingDescriptor#NO_RESCALE}.
     * 返回输入通道映射以使用飞行中数据或 {@link InflightDataRescalingDescriptor#NO_RESCALE} 重新缩放。
     */
    public InflightDataRescalingDescriptor getInputRescalingDescriptor() {
        return getMapping(OperatorSubtaskState::getInputRescalingDescriptor);
    }

    /**
     * Returns the output channel mapping for rescaling with in-flight data or {@link
     * InflightDataRescalingDescriptor#NO_RESCALE}.
     * 返回输出通道映射以使用飞行数据或 {@link InflightDataRescalingDescriptor#NO_RESCALE} 重新缩放。
     */
    public InflightDataRescalingDescriptor getOutputRescalingDescriptor() {
        return getMapping(OperatorSubtaskState::getOutputRescalingDescriptor);
    }

    @Override
    public void discardState() throws Exception {
        StateUtil.bestEffortDiscardAllStateObjects(subtaskStatesByOperatorID.values());
    }

    @Override
    public long getStateSize() {
        long size = 0L;

        for (OperatorSubtaskState subtaskState : subtaskStatesByOperatorID.values()) {
            if (subtaskState != null) {
                size += subtaskState.getStateSize();
            }
        }

        return size;
    }

    @Override
    public void registerSharedStates(SharedStateRegistry stateRegistry) {
        for (OperatorSubtaskState operatorSubtaskState : subtaskStatesByOperatorID.values()) {
            if (operatorSubtaskState != null) {
                operatorSubtaskState.registerSharedStates(stateRegistry);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TaskStateSnapshot that = (TaskStateSnapshot) o;

        return subtaskStatesByOperatorID.equals(that.subtaskStatesByOperatorID);
    }

    @Override
    public int hashCode() {
        return subtaskStatesByOperatorID.hashCode();
    }

    @Override
    public String toString() {
        return "TaskOperatorSubtaskStates{"
                + "subtaskStatesByOperatorID="
                + subtaskStatesByOperatorID
                + '}';
    }

    /** Returns the only valid mapping as ensured by {@link StateAssignmentOperation}.
     * 返回由 {@link StateAssignmentOperation} 确保的唯一有效映射。
     * */
    private InflightDataRescalingDescriptor getMapping(
            Function<OperatorSubtaskState, InflightDataRescalingDescriptor> mappingExtractor) {
        return Iterators.getOnlyElement(
                subtaskStatesByOperatorID.values().stream()
                        .map(mappingExtractor)
                        .filter(mapping -> !mapping.equals(NO_RESCALE))
                        .iterator(),
                NO_RESCALE);
    }
}
