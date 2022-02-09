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
import org.apache.flink.util.Collector;

import java.io.Serializable;

/**
 * The interface for group reduce functions. GroupReduceFunctions process groups of elements. They
 * may aggregate them to a single value, or produce multiple result values for each group. The group
 * may be defined by sharing a common grouping key, or the group may simply be all elements of a
 * data set.
 * group reduce 函数的接口。 GroupReduceFunctions 处理元素组。
 * 他们可以将它们聚合为一个值，或者为每个组生成多个结果值。
 * 该组可以通过共享公共分组键来定义，或者该组可以简单地是数据集的所有元素。
 *
 * <p>For a reduce functions that works incrementally by combining always two elements, see {@link
 * ReduceFunction}.
 * 有关通过始终combining两个元素来增量工作的 reduce 函数，请参阅 {@link ReduceFunction}。
 *
 * <p>The basic syntax for using a grouped GroupReduceFunction is as follows:
 *
 * <pre>{@code
 * DataSet<X> input = ...;
 *
 * DataSet<X> result = input.groupBy(<key-definition>).reduceGroup(new MyGroupReduceFunction());
 * }</pre>
 *
 * <p>Partial computation can significantly improve the performance of a {@link
 * GroupReduceFunction}. This technique is also known as applying a Combiner. Implement the {@link
 * GroupCombineFunction} interface to enable partial computations, i.e., a combiner for this {@link
 * GroupReduceFunction}.
 * 部分计算可以显着提高 {@link GroupReduceFunction} 的性能。 这种技术也称为应用组合器。
 * 实现 {@link GroupCombineFunction} 接口以启用部分计算，即此 {@link GroupReduceFunction} 的组合器。
 *
 * @param <T> Type of the elements that this function processes.
 * @param <O> The type of the elements returned by the user-defined function.
 */
@Public
@FunctionalInterface
public interface GroupReduceFunction<T, O> extends Function, Serializable {

    /**
     * The reduce method. The function receives one call per group of elements.
     * 减法。 该函数每组元素接收一次调用。
     *
     * @param values All records that belong to the given input key.
     * @param out The collector to hand results to.
     * @throws Exception This method may throw exceptions. Throwing an exception will cause the
     *     operation to fail and may trigger recovery.
     */
    void reduce(Iterable<T> values, Collector<O> out) throws Exception;
}
