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

package org.apache.flink.runtime.operators.util;

import org.apache.flink.api.common.functions.FlatJoinFunction;
import org.apache.flink.runtime.memory.MemoryAllocationException;
import org.apache.flink.util.Collector;

import java.io.IOException;

/**
 * Interface of an iterator that performs the logic of a match task. The iterator follows the
 * <i>open/next/close</i> principle. The <i>next</i> logic here calls the match stub with all value
 * pairs that share the same key.
 * 执行匹配任务逻辑的迭代器接口。 迭代器遵循<i>open/next/close</i> 原则。
 * 这里的 <i>next</i> 逻辑使用所有共享相同键的值对调用匹配存根。
 */
public interface JoinTaskIterator<V1, V2, O> {
    /**
     * General-purpose open method. Initializes the internal strategy (for example triggers the
     * sorting of the inputs or starts building hash tables).
     * 通用开放方式。 初始化内部策略（例如触发输入的排序或开始构建哈希表）。
     *
     * @throws IOException Thrown, if an I/O error occurred while preparing the data. An example is
     *     a failing external sort.
     * @throws MemoryAllocationException Thrown, if the internal strategy could not allocate the
     *     memory it needs.
     * @throws InterruptedException Thrown, if the thread was interrupted during the initialization
     *     process.
     */
    void open() throws IOException, MemoryAllocationException, InterruptedException;

    /**
     * General-purpose close method. Works after the principle of best effort. The internal
     * structures are released, but errors that occur on the way are not reported.
     * 通用关闭方法。 按照尽力而为的原则工作。 内部结构被释放，但途中发生的错误不报告。
     */
    void close();

    /**
     * Moves the internal pointer to the next key that both inputs share. It calls the match stub
     * with the cross product of all values that share the same key.
     * 将内部指针移动到两个输入共享的下一个键。 它使用共享相同键的所有值的叉积调用匹配存根。
     *
     * @param matchFunction The match stub containing the match function which is called with the
     *     keys.
     * @param collector The collector to pass the match function.
     * @return True, if a next key exists, false if no more keys exist.
     * @throws Exception Exceptions from the user code are forwarded.
     */
    boolean callWithNextKey(FlatJoinFunction<V1, V2, O> matchFunction, Collector<O> collector)
            throws Exception;

    /**
     * Aborts the matching process. This extra abort method is supplied, because a significant time
     * may pass while calling the match stub with the cross product of all values that share the
     * same key. A call to this abort method signals an interrupt to that procedure.
     * 中止匹配过程。 提供了这个额外的中止方法，因为在使用共享相同键的所有值的叉积调用匹配存根时可能会经过很长时间。
     * 调用此中止方法会向该过程发出中断信号。
     */
    void abort();
}
