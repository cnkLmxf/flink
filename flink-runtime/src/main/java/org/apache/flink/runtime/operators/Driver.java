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

package org.apache.flink.runtime.operators;

import org.apache.flink.api.common.functions.Function;

/**
 * The interface to be implemented by all drivers that run alone (or as the primary driver) in a
 * task. A driver implements the actual code to perform a batch operation, like <i>map()</i>,
 * <i>reduce()</i>, <i>join()</i>, or <i>coGroup()</i>.
 * 由任务中单独（或作为主要驱动程序）运行的所有驱动程序实现的接口。
 * 驱动程序实现了执行批处理操作的实际代码，例如 <i>map()</i>、<i>reduce()</i>、<i>join()</i> 或 <i> 协组（）</i>。
 *
 * @see TaskContext
 * @param <S> The type of stub driven by this driver.
 * @param <OT> The data type of the records produced by this driver.
 */
public interface Driver<S extends Function, OT> {

    void setup(TaskContext<S, OT> context);

    /**
     * Gets the number of inputs that the task has.
     * 获取任务具有的输入数。
     *
     * @return The number of inputs.
     */
    int getNumberOfInputs();

    /**
     * Gets the number of comparators required for this driver.
     * 获取此驱动程序所需的比较器数量。
     *
     * @return The number of comparators required for this driver.
     */
    int getNumberOfDriverComparators();

    /**
     * Gets the class of the stub type that is run by this task. For example, a <tt>MapTask</tt>
     * should return <code>MapFunction.class</code>.
     * 获取此任务运行的存根类型的类。 例如，<tt>MapTask</tt> 应该返回 <code>MapFunction.class</code>。
     *
     * @return The class of the stub type run by the task.
     */
    Class<S> getStubType();

    /**
     * This method is called before the user code is opened. An exception thrown by this method
     * signals failure of the task.
     * 在打开用户代码之前调用此方法。 此方法抛出的异常表示任务失败。
     *
     * @throws Exception Exceptions may be forwarded and signal task failure.
     */
    void prepare() throws Exception;

    /**
     * The main operation method of the task. It should call the user code with the data subsets
     * until the input is depleted.
     * 任务的主要操作方法。 它应该使用数据子集调用用户代码，直到输入耗尽。
     *
     * @throws Exception Any exception thrown by this method signals task failure. Because
     *     exceptions in the user code typically signal situations where this instance in unable to
     *     proceed, exceptions from the user code should be forwarded.
     */
    void run() throws Exception;

    /**
     * This method is invoked in any case (clean termination and exception) at the end of the tasks
     * operation.
     * 在任何情况下（干净终止和异常）都会在任务操作结束时调用此方法。
     *
     * @throws Exception Exceptions may be forwarded.
     */
    void cleanup() throws Exception;

    /**
     * This method is invoked when the driver must aborted in mid processing. It is invoked
     * asynchronously by a different thread.
     * 当驱动程序必须在中间处理中中止时调用此方法。 它由不同的线程异步调用。
     *
     * @throws Exception Exceptions may be forwarded.
     */
    void cancel() throws Exception;
}
