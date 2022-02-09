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
 * Interface for Cross functions. Cross functions are applied to the Cartesian product of their
 * inputs and are called for each pair of elements.
 * 交叉功能的接口。 交叉函数应用于其输入的笛卡尔积，并为每对元素调用。
 *
 * <p>They are optional, a means of convenience that can be used to directly manipulate the pair of
 * elements instead of producing 2-tuples containing the pairs.
 * 它们是可选的，一种方便的方式，可用于直接操作元素对，而不是生成包含元素对的 2 元组。
 *
 * <p>The basic syntax for using Cross on two data sets is as follows:
 * 在两个数据集上使用 Cross 的基本语法如下：
 *
 * <pre>{@code
 * DataSet<X> set1 = ...;
 * DataSet<Y> set2 = ...;
 *
 * set1.cross(set2).with(new MyCrossFunction());
 * }</pre>
 *
 * <p>{@code set1} is here considered the first input, {@code set2} the second input.
 * {@code set1} 在这里被视为第一个输入，{@code set2} 被视为第二个输入。
 *
 * @param <IN1> The type of the elements in the first input.
 * @param <IN2> The type of the elements in the second input.
 * @param <OUT> The type of the result elements.
 */
@Public
@FunctionalInterface
public interface CrossFunction<IN1, IN2, OUT> extends Function, Serializable {

    /**
     * Cross UDF method. Called once per pair of elements in the Cartesian product of the inputs.
     * 交叉UDF方法。 输入的笛卡尔积中的每对元素调用一次。
     *
     * @param val1 Element from first input.
     * @param val2 Element from the second input.
     * @return The result element.
     * @throws Exception The function may throw Exceptions, which will cause the program to cancel,
     *     and may trigger the recovery logic.
     */
    OUT cross(IN1 val1, IN2 val2) throws Exception;
}
