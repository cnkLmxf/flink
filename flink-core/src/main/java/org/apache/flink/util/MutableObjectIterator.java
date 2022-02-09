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

package org.apache.flink.util;

import org.apache.flink.annotation.Internal;

import java.io.IOException;

/**
 * A simple iterator interface. The key differences to the {@link java.util.Iterator} are
 * 一个简单的迭代器接口。 {@link java.util.Iterator} 的主要区别是
 *<ul>
 *     <li>它有两个不同的 <code>next()</code>，其中一个变体允许传递一个可以重用的对象，如果类型是可变的。
 *     <li>它将逻辑整合到单个 <code>next()</code> 函数中，而不是将其拆分为两个不同的函数，
 *     例如 <code>hasNext()</code> 和 <code>next()< /代码>
 *   </ul>
 * <ul>
 *   <li>It has two distinct <code>next()</code>, where one variant allows to pass an object that
 *       may be reused, if the type is mutable.
 *   <li>It consolidates the logic in a single <code>next()</code> function, rather than splitting
 *       it over two different functions such as <code>hasNext()</code> and <code>next()</code>
 * </ul>
 *
 * @param <E> The element type of the collection iterated over.
 */
@Internal
public interface MutableObjectIterator<E> {

    /**
     * Gets the next element from the collection. The contents of that next element is put into the
     * given reuse object, if the type is mutable.
     * 从集合中获取下一个元素。 如果类型是可变的，则将下一个元素的内容放入给定的重用对象中。
     *
     * @param reuse The target object into which to place next element if E is mutable.
     * @return The filled object or <code>null</code> if the iterator is exhausted.
     * @throws IOException Thrown, if a problem occurred in the underlying I/O layer or in the
     *     serialization / deserialization logic
     */
    E next(E reuse) throws IOException;

    /**
     * Gets the next element from the collection. The iterator implementation must obtain a new
     * instance.
     * 从集合中获取下一个元素。 迭代器实现必须获得一个新实例。
     *
     * @return The object or <code>null</code> if the iterator is exhausted.
     * @throws IOException Thrown, if a problem occurred in the underlying I/O layer or in the
     *     serialization / deserialization logic
     */
    E next() throws IOException;
}
