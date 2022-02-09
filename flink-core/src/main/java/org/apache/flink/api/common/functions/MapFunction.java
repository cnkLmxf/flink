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
 * Base interface for Map functions. Map functions take elements and transform them, element wise. A
 * Map function always produces a single result element for each input element. Typical applications
 * are parsing elements, converting data types, or projecting out fields. Operations that produce
 * multiple result elements from a single input element can be implemented using the {@link
 * FlatMapFunction}.
 * map功能的基本接口。 Map 函数接受元素并转换它们，元素明智。 Map 函数始终为每个输入元素生成单个结果元素。
 * 典型的应用是解析元素、转换数据类型或投影出字段。
 * 可以使用 {@link FlatMapFunction} 实现从单个输入元素生成多个结果元素的操作。
 *
 * <p>The basic syntax for using a MapFunction is as follows:
 *
 * <pre>{@code
 * DataSet<X> input = ...;
 *
 * DataSet<Y> result = input.map(new MyMapFunction());
 * }</pre>
 *
 * @param <T> Type of the input elements.
 * @param <O> Type of the returned elements.
 */
@Public
@FunctionalInterface
public interface MapFunction<T, O> extends Function, Serializable {

    /**
     * The mapping method. Takes an element from the input data set and transforms it into exactly
     * one element.
     * 映射方法。 从输入数据集中获取一个元素并将其转换为一个元素。
     *
     * @param value The input value.
     * @return The transformed value
     * @throws Exception This method may throw exceptions. Throwing an exception will cause the
     *     operation to fail and may trigger recovery.
     */
    O map(T value) throws Exception;
}
