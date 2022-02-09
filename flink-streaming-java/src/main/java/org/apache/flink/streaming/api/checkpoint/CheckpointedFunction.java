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

package org.apache.flink.streaming.api.checkpoint;

import org.apache.flink.annotation.Public;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.state.KeyedStateStore;
import org.apache.flink.api.common.state.OperatorStateStore;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;

/**
 * This is the core interface for <i>stateful transformation functions</i>, meaning functions that
 * maintain state across individual stream records. While more lightweight interfaces exist as
 * shortcuts for various types of state, this interface offer the greatest flexibility in managing
 * both <i>keyed state</i> and <i>operator state</i>.
 * 这是<i>有状态转换函数</i>的核心接口，这意味着在各个流记录中维护状态的函数。
 * 虽然存在更多轻量级接口作为各种类型状态的快捷方式，但该接口在管理<i>keyed state</i>和<i>operator state</i>方面提供了最大的灵活性。
 *
 * <p>The section <a href="#shortcuts">Shortcuts</a> illustrates the common lightweight ways to
 * setup stateful functions typically used instead of the full fledged abstraction represented by
 * this interface.
 * <a href="#shortcuts">Shortcuts</a> 部分说明了设置有状态函数的常见轻量级方法，这些方法通常用于代替此接口表示的完整抽象。
 *
 * <h1>Initialization</h1>
 *
 * <p>The {@link CheckpointedFunction#initializeState(FunctionInitializationContext)} is called when
 * the parallel instance of the transformation function is created during distributed execution. The
 * method gives access to the {@link FunctionInitializationContext} which in turn gives access to
 * the to the {@link OperatorStateStore} and {@link KeyedStateStore}.
 * {@link CheckpointedFunction#initializeState(FunctionInitializationContext)} 在分布式执行期间创建转换函数的并行实例时调用。
 * 该方法可以访问 {@link FunctionInitializationContext}，而FunctionInitializationContext又可以访问 {@link OperatorStateStore} 和 {@link KeyedStateStore}。
 *
 * <p>The {@code OperatorStateStore} and {@code KeyedStateStore} give access to the data structures
 * in which state should be stored for Flink to transparently manage and checkpoint it, such as
 * {@link org.apache.flink.api.common.state.ValueState} or {@link
 * org.apache.flink.api.common.state.ListState}.
 * {@code OperatorStateStore} 和 {@code KeyedStateStore} 允许访问应存储状态的数据结构，以便 Flink 透明地管理和检查它，
 * 例如 {@link org.apache.flink.api.common.state.ValueState} 或 {@link org.apache.flink.api.common.state.ListState}。
 *
 * <p><b>Note:</b> The {@code KeyedStateStore} can only be used when the transformation supports
 * <i>keyed state</i>, i.e., when it is applied on a keyed stream (after a {@code keyBy(...)}).
 * <b>注意：</b> {@code KeyedStateStore} 只能在转换支持 <i>keyed state</i> 时使用，即当它应用于键控流时（在 {@code keyBy (...)})。
 *
 * <h1>Snapshot</h1>
 *
 * <p>The {@link CheckpointedFunction#snapshotState(FunctionSnapshotContext)} is called whenever a
 * checkpoint takes a state snapshot of the transformation function. Inside this method, functions
 * typically make sure that the checkpointed data structures (obtained in the initialization phase)
 * are up to date for a snapshot to be taken. The given snapshot context gives access to the
 * metadata of the checkpoint.
 * 每当检查点获取转换函数的状态快照时，就会调用 {@link CheckpointedFunction#snapshotState(FunctionSnapshotContext)}。
 * 在此方法中，函数通常确保检查点数据结构（在初始化阶段获得）是最新的，以便拍摄快照。 给定的快照上下文允许访问检查点的元数据。
 *
 * <p>In addition, functions can use this method as a hook to flush/commit/synchronize with external
 * systems.
 * 此外，函数可以使用此方法作为钩子与外部系统刷新/提交/同步。
 *
 * <h1>Example</h1>
 *
 * <p>The code example below illustrates how to use this interface for a function that keeps counts
 * of events per key and per parallel partition (parallel instance of the transformation function
 * during distributed execution). The example also changes of parallelism, which affect the
 * count-per-parallel-partition by adding up the counters of partitions that get merged on
 * scale-down. Note that this is a toy example, but should illustrate the basic skeleton for a
 * stateful function.
 * 下面的代码示例说明了如何将此接口用于保持每个键和每个并行分区（分布式执行期间转换函数的并行实例）的事件计数的函数。
 * 该示例还更改了并行性，它通过将按比例缩小合并的分区的计数器相加来影响每个并行分区的计数。 请注意，这是一个玩具示例，但应该说明有状态函数的基本框架。
 *
 * <pre>{@code
 * public class MyFunction<T> implements MapFunction<T, T>, CheckpointedFunction {
 *
 *     private ReducingState<Long> countPerKey;
 *     private ListState<Long> countPerPartition;
 *
 *     private long localCount;
 *
 *     public void initializeState(FunctionInitializationContext context) throws Exception {
 *         // get the state data structure for the per-key state
 *         countPerKey = context.getKeyedStateStore().getReducingState(
 *                 new ReducingStateDescriptor<>("perKeyCount", new AddFunction<>(), Long.class));
 *
 *         // get the state data structure for the per-partition state
 *         countPerPartition = context.getOperatorStateStore().getOperatorState(
 *                 new ListStateDescriptor<>("perPartitionCount", Long.class));
 *
 *         // initialize the "local count variable" based on the operator state
 *         for (Long l : countPerPartition.get()) {
 *             localCount += l;
 *         }
 *     }
 *
 *     public void snapshotState(FunctionSnapshotContext context) throws Exception {
 *         // the keyed state is always up to date anyways
 *         // just bring the per-partition state in shape
 *         // 无论如何，键控状态始终是最新的，只是使每个分区的状态保持不变
 *         countPerPartition.clear();
 *         countPerPartition.add(localCount);
 *     }
 *
 *     public T map(T value) throws Exception {
 *         // update the states
 *         countPerKey.add(1L);
 *         localCount++;
 *
 *         return value;
 *     }
 * }
 * }</pre>
 *
 * <hr>
 *
 * <h1><a name="shortcuts">Shortcuts</a></h1>
 *
 * <p>There are various ways that transformation functions can use state without implementing the
 * full-fledged {@code CheckpointedFunction} interface:
 * 转换函数可以通过多种方式在不实现完整的 {@code CheckpointedFunction} 接口的情况下使用状态：
 *
 * <h4>Operator State</h4>
 *
 * <p>Checkpointing some state that is part of the function object itself is possible in a simpler
 * way by directly implementing the {@link ListCheckpointed} interface.
 * 通过直接实现 {@link ListCheckpointed} 接口，可以以更简单的方式检查属于函数对象本身的某些状态。
 *
 * <h4>Keyed State</h4>
 *
 * <p>Access to keyed state is possible via the {@link RuntimeContext}'s methods:
 * 可以通过 {@link RuntimeContext} 的方法访问键控状态：
 *
 * <pre>{@code
 * public class CountPerKeyFunction<T> extends RichMapFunction<T, T> {
 *
 *     private ValueState<Long> count;
 *
 *     public void open(Configuration cfg) throws Exception {
 *         count = getRuntimeContext().getState(new ValueStateDescriptor<>("myCount", Long.class));
 *     }
 *
 *     public T map(T value) throws Exception {
 *         Long current = count.get();
 *         count.update(current == null ? 1L : current + 1);
 *
 *         return value;
 *     }
 * }
 * }</pre>
 *
 * @see ListCheckpointed
 * @see RuntimeContext
 */
@Public
public interface CheckpointedFunction {

    /**
     * This method is called when a snapshot for a checkpoint is requested. This acts as a hook to
     * the function to ensure that all state is exposed by means previously offered through {@link
     * FunctionInitializationContext} when the Function was initialized, or offered now by {@link
     * FunctionSnapshotContext} itself.
     * 当请求检查点的快照时调用此方法。 这充当函数的钩子，以确保所有状态都通过先前在函数初始化时通过
     * {@link FunctionInitializationContext} 提供的方式公开，或者现在由 {@link FunctionSnapshotContext} 本身提供。
     *
     * @param context the context for drawing a snapshot of the operator
     * @throws Exception Thrown, if state could not be created ot restored.
     */
    void snapshotState(FunctionSnapshotContext context) throws Exception;

    /**
     * This method is called when the parallel function instance is created during distributed
     * execution. Functions typically set up their state storing data structures in this method.
     * 在分布式执行期间创建并行函数实例时调用此方法。 函数通常在此方法中设置其状态存储数据结构。
     *
     * @param context the context for initializing the operator
     * @throws Exception Thrown, if state could not be created ot restored.
     */
    void initializeState(FunctionInitializationContext context) throws Exception;
}
