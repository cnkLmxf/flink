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

package org.apache.flink.api.common;

import org.apache.flink.annotation.Public;

/**
 * The execution mode specifies how a batch program is executed in terms of data exchange:
 * pipelining or batched.
 * 执行模式指定批处理程序在数据交换方面的执行方式：流水线或批处理。
 */
@Public
public enum ExecutionMode {

    /**
     * Executes the program in a pipelined fashion (including shuffles and broadcasts), except for
     * data exchanges that are susceptible to deadlocks when pipelining. These data exchanges are
     * performed in a batch manner.
     * 以流水线方式执行程序（包括洗牌和广播），但在流水线时容易出现死锁的数据交换除外。 这些数据交换以批处理方式执行。
     *
     * <p>An example of situations that are susceptible to deadlocks (when executed in a pipelined
     * manner) are data flows that branch (one data set consumed by multiple operations) and re-join
     * later:
     * 容易发生死锁的情况示例（当以流水线方式执行时）是分支的数据流（一个数据集被多个操作消耗）并稍后重新加入：
     *
     * <pre>{@code
     * DataSet data = ...;
     * DataSet mapped1 = data.map(new MyMapper());
     * DataSet mapped2 = data.map(new AnotherMapper());
     * mapped1.join(mapped2).where(...).equalTo(...);
     * }</pre>
     */
    PIPELINED,

    /**
     * Executes the program in a pipelined fashion (including shuffles and broadcasts),
     * <strong>including</strong> data exchanges that are susceptible to deadlocks when executed via
     * pipelining.
     * 以流水线方式执行程序（包括洗牌和广播），<strong>包括</strong>在通过流水线执行时容易发生死锁的数据交换。
     *
     * <p>Usually, {@link #PIPELINED} is the preferable option, which pipelines most data exchanges
     * and only uses batch data exchanges in situations that are susceptible to deadlocks.
     * 通常，{@link #PIPELINED} 是更可取的选项，它对大多数数据交换进行管道化，并且仅在容易发生死锁的情况下使用批处理数据交换。
     *
     * <p>This option should only be used with care and only in situations where the programmer is
     * sure that the program is safe for full pipelining and that Flink was too conservative when
     * choosing the batch exchange at a certain point.
     * 此选项仅应谨慎使用，并且仅在程序员确定程序对于完整流水线操作是安全的并且 Flink 在某个点选择批处理交换时过于保守的情况下使用。
     */
    PIPELINED_FORCED,

    //	This is for later, we are missing a bit of infrastructure for this.
    // 这是为了以后，我们缺少一些基础设施。
    //	/**
    //	 * The execution mode starts executing the program in a pipelined fashion
    //	 * (except for deadlock prone situations), similar to the {@link #PIPELINED}
    //	 * option. In the case of a task failure, re-execution happens in a batched
    //	 * mode, as defined for the {@link #BATCH} option.
    //   * 执行模式以流水线方式开始执行程序（死锁容易发生的情况除外），类似于 {@link #PIPELINED} 选项。
    //   * 在任务失败的情况下，按照 {@link #BATCH} 选项的定义，以批处理模式重新执行。
    //	 */
    //	PIPELINED_WITH_BATCH_FALLBACK,

    /**
     * This mode executes all shuffles and broadcasts in a batch fashion, while pipelining data
     * between operations that exchange data only locally between one producer and one consumer.
     * 此模式以批处理方式执行所有 shuffle 和广播，同时在一个生产者和一个消费者之间仅在本地交换数据的操作之间流水线化数据。
     */
    BATCH,

    /**
     * This mode executes the program in a strict batch way, including all points where data is
     * forwarded locally from one producer to one consumer. This mode is typically more expensive to
     * execute than the {@link #BATCH} mode. It does guarantee that no successive operations are
     * ever executed concurrently.
     * 此模式以严格的批处理方式执行程序，包括数据从一个生产者本地转发到一个消费者的所有点。
     * 这种模式的执行成本通常比 {@link #BATCH} 模式高。 它确实保证不会同时执行任何后续操作。
     */
    BATCH_FORCED
}
