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

import java.io.Serializable;

/**
 * Base interface for Reduce functions. Reduce functions combine groups of elements to a single
 * value, by taking always two elements and combining them into one. Reduce functions may be used on
 * entire data sets, or on grouped data sets. In the latter case, each group is reduced
 * individually.
 * Reduce 函数的基本接口。 Reduce 函数通过始终采用两个元素并将它们组合为一个来将元素组组合为一个值。
 * Reduce 函数可用于整个数据集或分组数据集。 在后一种情况下，每个组都单独减少。
 *
 * <p>For a reduce functions that work on an entire group at the same time (such as the
 * MapReduce/Hadoop-style reduce), see {@link GroupReduceFunction}. In the general case,
 * ReduceFunctions are considered faster, because they allow the system to use more efficient
 * execution strategies.
 * 对于同时作用于整个组的 reduce 函数（例如 MapReduce/Hadoop-style reduce），请参见 {@link GroupReduceFunction}。
 * 在一般情况下，ReduceFunctions 被认为更快，因为它们允许系统使用更有效的执行策略。
 *
 * <p>The basic syntax for using a grouped ReduceFunction is as follows:
 *
 * <pre>{@code
 * DataSet<X> input = ...;
 *
 * DataSet<X> result = input.groupBy(<key-definition>).reduce(new MyReduceFunction());
 * }</pre>
 *
 * <p>Like all functions, the ReduceFunction needs to be serializable, as defined in {@link
 * java.io.Serializable}.
 * 与所有函数一样，ReduceFunction 需要可序列化，如 {@link java.io.Serializable} 中所定义。
 *
 * @param <T> Type of the elements that this function processes.
 */
@Public
@FunctionalInterface
public interface ReduceFunction<T> extends Function, Serializable {

    /**
     * The core method of ReduceFunction, combining two values into one value of the same type. The
     * reduce function is consecutively applied to all values of a group until only a single value
     * remains.
     * ReduceFunction 的核心方法，将两个值合并为一个相同类型的值。 reduce 函数连续应用于组的所有值，直到只剩下一个值为止。
     *
     * @param value1 The first value to combine.
     * @param value2 The second value to combine.
     * @return The combined value of both input values.
     * @throws Exception This method may throw exceptions. Throwing an exception will cause the
     *     operation to fail and may trigger recovery.
     */
    T reduce(T value1, T value2) throws Exception;
}
