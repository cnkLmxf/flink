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

/**
 * A broadcast variable initializer can be used to transform a broadcast variable into another
 * format during initialization. The transformed variable is shared among the parallel instances of
 * a function inside one TaskManager, the same way as the plain broadcast variables (lists) are
 * shared.
 * 广播变量初始化器可用于在初始化期间将广播变量转换为另一种格式。
 * 转换后的变量在一个 TaskManager 内的函数的并行实例之间共享，与共享普通广播变量（列表）的方式相同。
 *
 * <p>The broadcast variable initializer will in many cases only be executed by one parallel
 * function instance per TaskManager, when acquiring the broadcast variable for the first time
 * inside that particular TaskManager. It is possible that a broadcast variable is read and
 * initialized multiple times, if the tasks that use the variables are not overlapping in their
 * execution time; in such cases, it can happen that one function instance released the broadcast
 * variable, and another function instance materializes it again.
 * 在许多情况下，当在该特定 TaskManager 中第一次获取广播变量时，广播变量初始化器将仅由每个 TaskManager 的一个并行函数实例执行。
 * 如果使用变量的任务在执行时间上不重叠，则广播变量可能会被多次读取和初始化；
 * 在这种情况下，可能会发生一个函数实例释放广播变量，而另一个函数实例再次实现它。
 *
 * <p>This is an example of how to use a broadcast variable initializer, transforming the shared
 * list of values into a shared map.
 * 这是一个如何使用广播变量初始值设定项的示例，将共享值list转换为共享map。
 *
 * <pre>{@code
 * public class MyFunction extends RichMapFunction<Long, String> {
 *
 *     private Map<Long, String> map;
 *
 *     public void open(Configuration cfg) throws Exception {
 *         getRuntimeContext().getBroadcastVariableWithInitializer("mapvar",
 *             new BroadcastVariableInitializer<Tuple2<Long, String>, Map<Long, String>>() {
 *
 *                 public Map<Long, String> initializeBroadcastVariable(Iterable<Tuple2<Long, String>> data) {
 *                     Map<Long, String> map = new HashMap<>();
 *
 *                     for (Tuple2<Long, String> t : data) {
 *                         map.put(t.f0, t.f1);
 *                     }
 *
 *                     return map;
 *                 }
 *             });
 *     }
 *
 *     public String map(Long value) {
 *         // replace the long by the String, based on the map
 *         // 用字符串替换long
 *         return map.get(value);
 *     }
 * }
 *
 * }</pre>
 *
 * @param <T> The type of the elements in the list of the original untransformed broadcast variable.
 * @param <O> The type of the transformed broadcast variable.
 */
@Public
@FunctionalInterface
public interface BroadcastVariableInitializer<T, O> {

    /**
     * The method that reads the data elements from the broadcast variable and creates the
     * transformed data structure.
     * 从广播变量中读取数据元素并创建转换后的数据结构的方法。
     *
     * <p>The iterable with the data elements can be traversed only once, i.e., only the first call
     * to {@code iterator()} will succeed.
     * 带有数据元素的迭代只能遍历一次，即只有第一次调用 {@code iterator()} 会成功。
     *
     * @param data The sequence of elements in the broadcast variable.
     * @return The transformed broadcast variable.
     */
    O initializeBroadcastVariable(Iterable<T> data);
}
