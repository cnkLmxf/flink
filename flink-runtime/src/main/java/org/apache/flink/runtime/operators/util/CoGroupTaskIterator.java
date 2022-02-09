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

import org.apache.flink.runtime.memory.MemoryAllocationException;

import java.io.IOException;

/**
 * Interface describing the methods that have to be implemented by local strategies for the CoGroup
 * Pact.
 * 描述必须由 CoGroup Pact 的本地策略实现的方法的接口。
 *
 * @param <T1> The generic type of the first input's data type.
 * @param <T2> The generic type of the second input's data type.
 */
public interface CoGroupTaskIterator<T1, T2> {

    /**
     * General-purpose open method.
     * 通用开放方式。
     *
     * @throws IOException
     * @throws MemoryAllocationException
     * @throws InterruptedException
     */
    void open() throws IOException, MemoryAllocationException, InterruptedException;

    /** General-purpose close method.
     * 通用关闭方法。
     * */
    void close();

    /**
     * Moves the internal pointer to the next key (if present). Returns true if the operation was
     * successful or false if no more keys are present.
     * 将内部指针移动到下一个键（如果存在）。 如果操作成功，则返回 true；如果没有更多键，则返回 false。
     *
     * <p>The key is not necessarily shared by both inputs. In that case an empty iterator is
     * returned by getValues1() or getValues2().
     * 密钥不一定由两个输入共享。 在这种情况下，getValues1() 或 getValues2() 返回一个空的迭代器。
     *
     * @return true on success, false if no more keys are present
     * @throws IOException
     */
    boolean next() throws IOException;

    /**
     * Returns an iterable over the left input values for the current key.
     * 返回当前键的左侧输入值的可迭代对象。
     *
     * @return an iterable over the left input values for the current key.
     */
    Iterable<T1> getValues1();

    /**
     * Returns an iterable over the left input values for the current key.
     * 返回当前键的左侧输入值的可迭代对象。
     *
     * @return an iterable over the left input values for the current key.
     */
    Iterable<T2> getValues2();
}
