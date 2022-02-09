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

package org.apache.flink.api.common.accumulators;

import org.apache.flink.annotation.Public;

import java.io.Serializable;

/**
 * Accumulators collect distributed statistics or aggregates in a from user functions and operators.
 * Each parallel instance creates and updates its own accumulator object, and the different parallel
 * instances of the accumulator are later merged. merged by the system at the end of the job. The
 * result can be obtained from the result of a job execution, or from the web runtime monitor.
 * 累加器从用户函数和运算符收集分布式统计信息或聚合。 每个并行实例创建并更新自己的累加器对象，累加器的不同并行实例随后被合并。
 * 在作业结束时由系统合并。 结果可以从作业执行的结果中获得，也可以从 Web 运行时监视器中获得。
 *
 * <p>The accumulators are inspired by the Hadoop/MapReduce counters.
 * 累加器的灵感来自 Hadoop/MapReduce 计数器。
 *
 * <p>The type added to the accumulator might differ from the type returned. This is the case e.g.
 * for a set-accumulator: We add single objects, but the result is a set of objects.
 * 添加到累加器的类型可能与返回的类型不同。 这是这种情况，例如 对于集合累加器：我们添加单个对象，但结果是一组对象。
 *
 * @param <V> Type of values that are added to the accumulator
 * @param <R> Type of the accumulator result as it will be reported to the client
 */
@Public
public interface Accumulator<V, R extends Serializable> extends Serializable, Cloneable {
    /** @param value The value to add to the accumulator object */
    void add(V value);

    /** @return local The local value from the current UDF context */
    R getLocalValue();

    /** Reset the local value. This only affects the current UDF context. */
    void resetLocal();

    /**
     * Used by system internally to merge the collected parts of an accumulator at the end of the
     * job.
     * 由系统内部使用以在作业结束时合并累加器的收集部分。
     *
     * @param other Reference to accumulator to merge in.
     */
    void merge(Accumulator<V, R> other);

    /**
     * Duplicates the accumulator. All subclasses need to properly implement cloning and cannot
     * throw a {@link java.lang.CloneNotSupportedException}
     * 复制累加器。 所有子类都需要正确实现克隆并且不能抛出 {@link java.lang.CloneNotSupportedException}
     *
     * @return The duplicated accumulator.
     */
    Accumulator<V, R> clone();
}
