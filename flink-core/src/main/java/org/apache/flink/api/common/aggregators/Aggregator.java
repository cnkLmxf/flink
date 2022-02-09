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

package org.apache.flink.api.common.aggregators;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.types.Value;

import java.io.Serializable;

/**
 * Aggregators are a means of aggregating values across parallel instances of a function.
 * Aggregators collect simple statistics (such as the number of processed elements) about the actual
 * work performed in a function. Aggregators are specific to iterations and are commonly used to
 * check the convergence of an iteration by using a {@link ConvergenceCriterion}. In contrast to the
 * {@link org.apache.flink.api.common.accumulators.Accumulator} (whose result is available at the
 * end of a job, the aggregators are computed once per iteration superstep. Their value can be used
 * to check for convergence (at the end of the iteration superstep) and it can be accessed in the
 * next iteration superstep.
 * 聚合器是一种跨函数的并行实例聚合值的方法。 聚合器收集有关在函数中执行的实际工作的简单统计信息（例如已处理元素的数量）。
 * 聚合器特定于迭代，通常用于通过使用 {@link ConvergenceCriterion} 检查迭代的收敛性。
 * 与 {@link org.apache.flink.api.common.accumulators.Accumulator}
 * （其结果在作业结束时可用，聚合器在每个迭代超步计算一次。它们的值可用于检查 收敛（在迭代超步结束时）并且可以在下一个迭代超步中访问。
 *
 * <p>Aggregators must be registered at the iteration inside which they are used via the function.
 * In the Java API, the method is "IterativeDataSet.registerAggregator(...)" or
 * "IterativeDataSet.registerAggregationConvergenceCriterion(...)" when using the aggregator
 * together with a convergence criterion. Aggregators are always registered under a name. That name
 * can be used to access the aggregator at runtime from within a function. The following code
 * snippet shows a typical case. Here, it count across all parallel instances how many elements are
 * filtered out by a function.
 * 必须在通过函数使用聚合器的迭代中注册聚合器。 在 Java API 中，当将聚合器与收敛标准一起使用时，
 * 该方法是“IterativeDataSet.registerAggregator(...)”或“IterativeDataSet.registerAggregationConvergenceCriterion(...)”。
 * 聚合器始终以名称注册。 该名称可用于在运行时从函数内部访问聚合器。 以下代码片段显示了一个典型案例。
 * 在这里，它计算所有并行实例有多少元素被函数过滤掉。
 *
 * <pre>
 * // the user-defined function
 * public class MyFilter extends FilterFunction&lt;Double&gt; {
 *     private LongSumAggregator agg;
 *
 *     public void open(Configuration parameters) {
 *         agg = getIterationRuntimeContext().getIterationAggregator("numFiltered");
 *     }
 *
 *     public boolean filter (Double value) {
 *         if (value &gt; 1000000.0) {
 *             agg.aggregate(1);
 *             return false
 *         }
 *
 *         return true;
 *     }
 * }
 *
 * // the iteration where the aggregator is registered
 * // 注册聚合器的迭代
 * IterativeDataSet&lt;Double&gt; iteration = input.iterate(100).registerAggregator("numFiltered", LongSumAggregator.class);
 * ...
 * DataSet&lt;Double&gt; filtered = someIntermediateResult.filter(new MyFilter);
 * ...
 * DataSet&lt;Double&gt; result = iteration.closeWith(filtered);
 * ...
 * </pre>
 *
 * <p>Aggregators must be <i>distributive</i>: An aggregator must be able to pre-aggregate values
 * and it must be able to aggregate these pre-aggregated values to form the final aggregate. Many
 * aggregation functions fulfill this condition (sum, min, max) and others can be brought into that
 * form: One can expressing <i>count</i> as a sum over values of one, and one can express
 * <i>average</i> through a sum and a count.
 * 聚合器必须<i>分布式</i>：聚合器必须能够预聚合值，并且必须能够聚合这些预聚合值以形成最终聚合。
 * 许多聚合函数都满足这一条件（sum、min、max），而其他聚合函数可以采用这种形式：
 * 可以将 <i>count</i> 表示为 1 值的总和，还可以表示 <i>average< /i> 通过总和和计数。
 *
 * @param <T> The type of the aggregated value.
 */
@PublicEvolving
public interface Aggregator<T extends Value> extends Serializable {

    /**
     * Gets the aggregator's current aggregate.
     * 获取聚合器的当前聚合。
     *
     * @return The aggregator's current aggregate.
     */
    T getAggregate();

    /**
     * Aggregates the given element. In the case of a <i>sum</i> aggregator, this method adds the
     * given value to the sum.
     * 聚合给定的元素。 在 <i>sum</i> 聚合器的情况下，此方法将给定值添加到总和中。
     *
     * @param element The element to aggregate.
     */
    void aggregate(T element);

    /**
     * Resets the internal state of the aggregator. This must bring the aggregator into the same
     * state as if it was newly initialized.
     * 重置聚合器的内部状态。 这必须使聚合器进入与新初始化相同的状态。
     */
    void reset();
}
