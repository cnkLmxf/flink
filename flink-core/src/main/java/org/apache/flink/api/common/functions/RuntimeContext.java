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

package org.apache.flink.api.common.functions;

import org.apache.flink.annotation.Public;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.accumulators.Accumulator;
import org.apache.flink.api.common.accumulators.DoubleCounter;
import org.apache.flink.api.common.accumulators.Histogram;
import org.apache.flink.api.common.accumulators.IntCounter;
import org.apache.flink.api.common.accumulators.LongCounter;
import org.apache.flink.api.common.cache.DistributedCache;
import org.apache.flink.api.common.externalresource.ExternalResourceInfo;
import org.apache.flink.api.common.state.AggregatingState;
import org.apache.flink.api.common.state.AggregatingStateDescriptor;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.metrics.MetricGroup;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * A RuntimeContext contains information about the context in which functions are executed. Each
 * parallel instance of the function will have a context through which it can access static
 * contextual information (such as the current parallelism) and other constructs like accumulators
 * and broadcast variables.
 * RuntimeContext 包含有关执行函数的上下文的信息。
 * function的每个并行实例都有一个上下文，通过它它可以访问静态上下文信息（例如当前并行度）和其他构造，例如累加器和广播变量。
 *
 * <p>A function can, during runtime, obtain the RuntimeContext via a call to {@link
 * AbstractRichFunction#getRuntimeContext()}.
 * 一个函数可以在运行时通过调用 {@link AbstractRichFunction#getRuntimeContext()} 来获取 RuntimeContext。
 */
@Public
public interface RuntimeContext {

    /**
     * The ID of the current job. Note that Job ID can change in particular upon manual restart. The
     * returned ID should NOT be used for any job management tasks.
     * 当前作业的 ID。 请注意，作业 ID 尤其会在手动重新启动时发生变化。 返回的 ID 不应该用于任何作业管理任务。
     */
    JobID getJobId();

    /**
     * Returns the name of the task in which the UDF runs, as assigned during plan construction.
     * 返回在计划构建期间分配的 UDF 运行任务的名称。
     *
     * @return The name of the task in which the UDF runs.
     */
    String getTaskName();

    /**
     * Returns the metric group for this parallel subtask.
     * 返回此并行子任务的指标组。
     *
     * @return The metric group for this parallel subtask.
     */
    @PublicEvolving
    MetricGroup getMetricGroup();

    /**
     * Gets the parallelism with which the parallel task runs.
     * 获取并行任务运行的并行度。
     *
     * @return The parallelism with which the parallel task runs.
     */
    int getNumberOfParallelSubtasks();

    /**
     * Gets the number of max-parallelism with which the parallel task runs.
     * 获取并行任务运行的最大并行数。
     *
     * @return The max-parallelism with which the parallel task runs.
     */
    @PublicEvolving
    int getMaxNumberOfParallelSubtasks();

    /**
     * Gets the number of this parallel subtask. The numbering starts from 0 and goes up to
     * parallelism-1 (parallelism as returned by {@link #getNumberOfParallelSubtasks()}).
     * 获取此并行子任务的编号。 编号从 0 开始，一直到并行度-1（{@link #getNumberOfParallelSubtasks()} 返回的并行度）。
     *
     * @return The index of the parallel subtask.
     */
    int getIndexOfThisSubtask();

    /**
     * Gets the attempt number of this parallel subtask. First attempt is numbered 0.
     * 获取此并行子任务的尝试次数。 第一次尝试编号为 0。
     *
     * @return Attempt number of the subtask.
     */
    int getAttemptNumber();

    /**
     * Returns the name of the task, appended with the subtask indicator, such as "MyTask (3/6)#1",
     * where 3 would be ({@link #getIndexOfThisSubtask()} + 1), and 6 would be {@link
     * #getNumberOfParallelSubtasks()}, and 1 would be {@link #getAttemptNumber()}.
     * 返回任务名称，附加子任务指示符，例如“MyTask (3/6)#1”，其中 3 为 ({@link #getIndexOfThisSubtask()} + 1)，
     * 6 为 {@link #getNumberOfParallelSubtasks()}，1 将是 {@link #getAttemptNumber()}。
     *
     * @return The name of the task, with subtask indicator.
     */
    String getTaskNameWithSubtasks();

    /**
     * Returns the {@link org.apache.flink.api.common.ExecutionConfig} for the currently executing
     * 返回当前执行的 {@link org.apache.flink.api.common.ExecutionConfig}
     * job.
     */
    ExecutionConfig getExecutionConfig();

    /**
     * Gets the ClassLoader to load classes that are not in system's classpath, but are part of the
     * jar file of a user job.
     * 获取 ClassLoader 以加载不在系统类路径中但属于用户作业的 jar 文件一部分的类。
     *
     * @return The ClassLoader for user code classes.
     */
    ClassLoader getUserCodeClassLoader();

    /**
     * Registers a custom hook for the user code class loader release.
     * 为用户代码类加载器版本注册一个自定义钩子。
     *
     * <p>The release hook is executed just before the user code class loader is being released.
     * Registration only happens if no hook has been registered under this name already.
     * 释放钩子在用户代码类加载器被释放之前执行。 仅当尚未在此名称下注册挂钩时，才会进行注册。
     *
     * @param releaseHookName name of the release hook.
     * @param releaseHook release hook which is executed just before the user code class loader is
     *     being released
     */
    @PublicEvolving
    void registerUserCodeClassLoaderReleaseHookIfAbsent(
            String releaseHookName, Runnable releaseHook);

    // --------------------------------------------------------------------------------------------

    /**
     * Add this accumulator. Throws an exception if the accumulator already exists in the same Task.
     * Note that the Accumulator name must have an unique name across the Flink job. Otherwise you
     * will get an error when incompatible accumulators from different Tasks are combined at the
     * JobManager upon job completion.
     * 添加此累加器。 如果累加器已存在于同一任务中，则引发异常。 请注意，累加器名称必须在整个 Flink 作业中具有唯一的名称。
     * 否则，当来自不同任务的不兼容累加器在作业完成后在 JobManager 中组合时，您将收到错误消息。
     */
    <V, A extends Serializable> void addAccumulator(String name, Accumulator<V, A> accumulator);

    /**
     * Get an existing accumulator object. The accumulator must have been added previously in this
     * local runtime context.
     * 获取现有的累加器对象。 累加器必须先前已添加到此本地运行时上下文中。
     *
     * <p>Throws an exception if the accumulator does not exist or if the accumulator exists, but
     * with different type.
     */
    <V, A extends Serializable> Accumulator<V, A> getAccumulator(String name);

    /** Convenience function to create a counter object for integers.
     * 为整数创建计数器对象的便捷函数。
     * */
    @PublicEvolving
    IntCounter getIntCounter(String name);

    /** Convenience function to create a counter object for longs.
     * 为long创建计数器对象的便捷功能。
     * */
    @PublicEvolving
    LongCounter getLongCounter(String name);

    /** Convenience function to create a counter object for doubles.
     * 为double创建计数器对象的便捷功能。
     * */
    @PublicEvolving
    DoubleCounter getDoubleCounter(String name);

    /** Convenience function to create a counter object for histograms.
     * 为直方图创建计数器对象的便捷功能。
     * */
    @PublicEvolving
    Histogram getHistogram(String name);

    /**
     * Get the specific external resource information by the resourceName.
     * 通过resourceName获取具体的外部资源信息。
     *
     * @param resourceName of the required external resource
     * @return information set of the external resource identified by the resourceName
     */
    @PublicEvolving
    Set<ExternalResourceInfo> getExternalResourceInfos(String resourceName);

    // --------------------------------------------------------------------------------------------

    /**
     * Tests for the existence of the broadcast variable identified by the given {@code name}.
     * 测试由给定的 {@code name} 标识的广播变量是否存在。
     *
     * @param name The name under which the broadcast variable is registered;
     * @return Whether a broadcast variable exists for the given name.
     */
    @PublicEvolving
    boolean hasBroadcastVariable(String name);

    /**
     * Returns the result bound to the broadcast variable identified by the given {@code name}.
     * 返回绑定到由给定 {@code name} 标识的广播变量的结果。
     *
     * <p>IMPORTANT: The broadcast variable data structure is shared between the parallel tasks on
     * one machine. Any access that modifies its internal state needs to be manually synchronized by
     * the caller.
     * 重要提示：广播变量数据结构在一台机器上的并行任务之间共享。 任何修改其内部状态的访问都需要由调用者手动同步。
     *
     * @param name The name under which the broadcast variable is registered;
     * @return The broadcast variable, materialized as a list of elements.
     */
    <RT> List<RT> getBroadcastVariable(String name);

    /**
     * Returns the result bound to the broadcast variable identified by the given {@code name}. The
     * broadcast variable is returned as a shared data structure that is initialized with the given
     * {@link BroadcastVariableInitializer}.
     * 返回绑定到由给定 {@code name} 标识的广播变量的结果。
     * 广播变量作为共享数据结构返回，该结构使用给定的 {@link BroadcastVariableInitializer} 进行初始化。
     *
     * <p>IMPORTANT: The broadcast variable data structure is shared between the parallel tasks on
     * one machine. Any access that modifies its internal state needs to be manually synchronized by
     * the caller.
     * 重要提示：广播变量数据结构在一台机器上的并行任务之间共享。 任何修改其内部状态的访问都需要由调用者手动同步。
     *
     * @param name The name under which the broadcast variable is registered;
     * 注册广播变量的名称；
     * @param initializer The initializer that creates the shared data structure of the broadcast
     *     variable from the sequence of elements.
     *     从元素序列创建广播变量的共享数据结构的初始化程序。
     * @return The broadcast variable, materialized  as a list of elements.
     * 广播变量，具体化为元素列表。
     */
    <T, C> C getBroadcastVariableWithInitializer(
            String name, BroadcastVariableInitializer<T, C> initializer);

    /**
     * Returns the {@link DistributedCache} to get the local temporary file copies of files
     * otherwise not locally accessible.
     * 返回 {@link DistributedCache} 以获取文件的本地临时文件副本，否则无法在本地访问。
     *
     * @return The distributed cache of the worker executing this instance.
     * 执行此实例的工作线程的分布式缓存。
     *
     */
    DistributedCache getDistributedCache();

    // ------------------------------------------------------------------------
    //  Methods for accessing state
    // ------------------------------------------------------------------------

    /**
     * Gets a handle to the system's key/value state. The key/value state is only accessible if the
     * function is executed on a KeyedStream. On each access, the state exposes the value for the
     * key of the element currently processed by the function. Each function may have multiple
     * partitioned states, addressed with different names.
     * 获取系统键/值状态的句柄。 只有在 KeyedStream 上执行函数时，键/值状态才可访问。
     * 每次访问时，状态都会公开函数当前处理的元素的key的值。 每个函数可能有多个分区状态，用不同的名称寻址。
     *
     * <p>Because the scope of each value is the key of the currently processed element, and the
     * elements are distributed by the Flink runtime, the system can transparently scale out and
     * redistribute the state and KeyedStream.
     * 由于每个值的作用域是当前处理元素的key，元素由Flink运行时分发，系统可以透明地向外扩展和重新分发状态和KeyedStream。
     *
     * <p>The following code example shows how to implement a continuous counter that counts how
     * many times elements of a certain key occur, and emits an updated count for that element on
     * each occurrence.
     * 下面的代码示例展示了如何实现一个连续计数器，该计数器计算某个键的元素出现的次数，并在每次出现时为该元素发出更新的计数。
     *
     * <pre>{@code
     * DataStream<MyType> stream = ...;
     * KeyedStream<MyType> keyedStream = stream.keyBy("id");
     *
     * keyedStream.map(new RichMapFunction<MyType, Tuple2<MyType, Long>>() {
     *
     *     private ValueState<Long> state;
     *
     *     public void open(Configuration cfg) {
     *         state = getRuntimeContext().getState(
     *                 new ValueStateDescriptor<Long>("count", LongSerializer.INSTANCE, 0L));
     *     }
     *
     *     public Tuple2<MyType, Long> map(MyType value) {
     *         long count = state.value() + 1;
     *         state.update(count);
     *         return new Tuple2<>(value, count);
     *     }
     * });
     * }</pre>
     *
     * @param stateProperties The descriptor defining the properties of the stats.
     * @param <T> The type of value stored in the state.
     * @return The partitioned state object.
     * @throws UnsupportedOperationException Thrown, if no partitioned state is available for the
     *     function (function is not part of a KeyedStream).
     */
    @PublicEvolving
    <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties);

    /**
     * Gets a handle to the system's key/value list state. This state is similar to the state
     * accessed via {@link #getState(ValueStateDescriptor)}, but is optimized for state that holds
     * lists. One can add elements to the list, or retrieve the list as a whole.
     * 获取系统键/值列表状态的句柄。 此状态类似于通过 {@link #getState(ValueStateDescriptor)} 访问的状态，
     * 但针对包含列表的状态进行了优化。 可以向列表中添加元素，或检索整个列表。
     *
     * <p>This state is only accessible if the function is executed on a KeyedStream.
     * 只有在 KeyedStream 上执行函数时才能访问此状态。
     *
     * <pre>{@code
     * DataStream<MyType> stream = ...;
     * KeyedStream<MyType> keyedStream = stream.keyBy("id");
     *
     * keyedStream.map(new RichFlatMapFunction<MyType, List<MyType>>() {
     *
     *     private ListState<MyType> state;
     *
     *     public void open(Configuration cfg) {
     *         state = getRuntimeContext().getListState(
     *                 new ListStateDescriptor<>("myState", MyType.class));
     *     }
     *
     *     public void flatMap(MyType value, Collector<MyType> out) {
     *         if (value.isDivider()) {
     *             for (MyType t : state.get()) {
     *                 out.collect(t);
     *             }
     *         } else {
     *             state.add(value);
     *         }
     *     }
     * });
     * }</pre>
     *
     * @param stateProperties The descriptor defining the properties of the stats.
     * @param <T> The type of value stored in the state.
     * @return The partitioned state object.
     * @throws UnsupportedOperationException Thrown, if no partitioned state is available for the
     *     function (function is not part os a KeyedStream).
     */
    @PublicEvolving
    <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties);

    /**
     * Gets a handle to the system's key/value reducing state. This state is similar to the state
     * accessed via {@link #getState(ValueStateDescriptor)}, but is optimized for state that
     * aggregates values.
     * 获取系统键/值reducing state的句柄。
     * 此状态类似于通过 {@link #getState(ValueStateDescriptor)} 访问的状态，但针对聚合值的状态进行了优化。
     *
     * <p>This state is only accessible if the function is executed on a KeyedStream.
     * 只有在 KeyedStream 上执行函数时才能访问此状态。
     *
     * <pre>{@code
     * DataStream<MyType> stream = ...;
     * KeyedStream<MyType> keyedStream = stream.keyBy("id");
     *
     * keyedStream.map(new RichMapFunction<MyType, List<MyType>>() {
     *
     *     private ReducingState<Long> state;
     *
     *     public void open(Configuration cfg) {
     *         state = getRuntimeContext().getReducingState(
     *                 new ReducingStateDescriptor<>("sum", (a, b) -> a + b, Long.class));
     *     }
     *
     *     public Tuple2<MyType, Long> map(MyType value) {
     *         state.add(value.count());
     *         return new Tuple2<>(value, state.get());
     *     }
     * });
     *
     * }</pre>
     *
     * @param stateProperties The descriptor defining the properties of the stats.
     * @param <T> The type of value stored in the state.
     * @return The partitioned state object.
     * @throws UnsupportedOperationException Thrown, if no partitioned state is available for the
     *     function (function is not part of a KeyedStream).
     */
    @PublicEvolving
    <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties);

    /**
     * Gets a handle to the system's key/value aggregating state. This state is similar to the state
     * accessed via {@link #getState(ValueStateDescriptor)}, but is optimized for state that
     * aggregates values with different types.
     * 获取系统键/值 aggregating state 的句柄。
     * 此状态类似于通过 {@link #getState(ValueStateDescriptor)} 访问的状态，但针对聚合不同类型值的状态进行了优化。
     *
     * <p>This state is only accessible if the function is executed on a KeyedStream.
     * 只有在 KeyedStream 上执行函数时才能访问此状态。
     *
     * <pre>{@code
     * DataStream<MyType> stream = ...;
     * KeyedStream<MyType> keyedStream = stream.keyBy("id");
     * AggregateFunction<...> aggregateFunction = ...
     *
     * keyedStream.map(new RichMapFunction<MyType, List<MyType>>() {
     *
     *     private AggregatingState<MyType, Long> state;
     *
     *     public void open(Configuration cfg) {
     *         state = getRuntimeContext().getAggregatingState(
     *                 new AggregatingStateDescriptor<>("sum", aggregateFunction, Long.class));
     *     }
     *
     *     public Tuple2<MyType, Long> map(MyType value) {
     *         state.add(value);
     *         return new Tuple2<>(value, state.get());
     *     }
     * });
     *
     * }</pre>
     *
     * @param stateProperties The descriptor defining the properties of the stats.
     * @param <IN> The type of the values that are added to the state.
     * @param <ACC> The type of the accumulator (intermediate aggregation state).
     * @param <OUT> The type of the values that are returned from the state.
     * @return The partitioned state object.
     * @throws UnsupportedOperationException Thrown, if no partitioned state is available for the
     *     function (function is not part of a KeyedStream).
     */
    @PublicEvolving
    <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties);

    /**
     * Gets a handle to the system's key/value map state. This state is similar to the state
     * accessed via {@link #getState(ValueStateDescriptor)}, but is optimized for state that is
     * composed of user-defined key-value pairs
     * 获取系统键/值 map state的句柄。
     * 此状态类似于通过 {@link #getState(ValueStateDescriptor)} 访问的状态，但针对由用户定义的键值对组成的状态进行了优化
     *
     * <p>This state is only accessible if the function is executed on a KeyedStream.
     * 只有在 KeyedStream 上执行函数时才能访问此状态。
     *
     * <pre>{@code
     * DataStream<MyType> stream = ...;
     * KeyedStream<MyType> keyedStream = stream.keyBy("id");
     *
     * keyedStream.map(new RichMapFunction<MyType, List<MyType>>() {
     *
     *     private MapState<MyType, Long> state;
     *
     *     public void open(Configuration cfg) {
     *         state = getRuntimeContext().getMapState(
     *                 new MapStateDescriptor<>("sum", MyType.class, Long.class));
     *     }
     *
     *     public Tuple2<MyType, Long> map(MyType value) {
     *         return new Tuple2<>(value, state.get(value));
     *     }
     * });
     *
     * }</pre>
     *
     * @param stateProperties The descriptor defining the properties of the stats.
     * @param <UK> The type of the user keys stored in the state.
     * @param <UV> The type of the user values stored in the state.
     * @return The partitioned state object.
     * @throws UnsupportedOperationException Thrown, if no partitioned state is available for the
     *     function (function is not part of a KeyedStream).
     */
    @PublicEvolving
    <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties);
}
