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
 * The interface for CoGroup functions. CoGroup functions combine two data sets by first grouping
 * each data set after a key and then "joining" the groups by calling this function with the two
 * sets for each key. If a key is present in only one of the two inputs, it may be that one of the
 * groups is empty.
 * CoGroup 函数的接口。 CoGroup 函数通过首先将每个data sets分组到一个键之后，
 * 然后通过使用每个键的两个集调用此函数来“加入”这些组，从而组合两个数据集。
 * 如果一个键仅出现在两个输入之一中，则可能是其中一个组为空。
 *
 * <p>The basic syntax for using CoGroup on two data sets is as follows:
 * 在两个数据集上使用 CoGroup 的基本语法如下：
 *
 * <pre>{@code
 * DataSet<X> set1 = ...;
 * DataSet<Y> set2 = ...;
 *
 * set1.coGroup(set2).where(<key-definition>).equalTo(<key-definition>).with(new MyCoGroupFunction());
 * }</pre>
 *
 * <p>{@code set1} is here considered the first input, {@code set2} the second input.
 * {@code set1} 在这里被视为第一个输入，{@code set2} 被视为第二个输入。
 *
 * <p>Some keys may only be contained in one of the two original data sets. In that case, the
 * CoGroup function is invoked with in empty input for the side of the data set that did not contain
 * elements with that specific key.
 * <p>某些键可能只包含在两个原始数据集之一中。
 * 在这种情况下，CoGroup 函数将在不包含具有该特定键的元素的数据集一侧以空输入调用。
 *
 * @param <IN1> The data type of the first input data set.
 * @param <IN2> The data type of the second input data set.
 * @param <O> The data type of the returned elements.
 */
@Public
@FunctionalInterface
public interface CoGroupFunction<IN1, IN2, O> extends Function, Serializable {

    /**
     * This method must be implemented to provide a user implementation of a coGroup. It is called
     * for each pair of element groups where the elements share the same key.
     * 必须实现此方法以提供 coGroup 的用户实现。 为元素共享相同密钥的每对元素组调用它。
     *
     * @param first The records from the first input.
     * @param second The records from the second.
     * @param out A collector to return elements.
     * @throws Exception The function may throw Exceptions, which will cause the program to cancel,
     *     and may trigger the recovery logic.
     */
    void coGroup(Iterable<IN1> first, Iterable<IN2> second, Collector<O> out) throws Exception;
}
