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

package org.apache.flink.runtime.operators.sort;

import org.apache.flink.core.memory.MemorySegment;

import java.util.List;

/** Class representing buffers that circulate between the reading, sorting and spilling stages.
 * 表示在读取、排序和溢出阶段之间循环的缓冲区的类。
 * */
final class CircularElement<E> {

    private final int id;
    private final InMemorySorter<E> buffer;
    private final List<MemorySegment> memory;

    public CircularElement(int id) {
        this.id = id;
        this.buffer = null;
        this.memory = null;
    }

    public CircularElement(int id, InMemorySorter<E> buffer, List<MemorySegment> memory) {
        this.id = id;
        this.buffer = buffer;
        this.memory = memory;
    }

    public int getId() {
        return id;
    }

    public InMemorySorter<E> getBuffer() {
        return buffer;
    }

    public List<MemorySegment> getMemory() {
        return memory;
    }

    /** The element that is passed as marker for the end of data.
     * 作为数据结束标记传递的元素。
     * */
    static final CircularElement<Object> EOF_MARKER = new CircularElement<>(-1);

    /** The element that is passed as marker for signal beginning of spilling.
     * 作为信号开始溢出的标记传递的元素。
     * */
    static final CircularElement<Object> SPILLING_MARKER = new CircularElement<>(-2);

    /**
     * Gets the element that is passed as marker for the end of data.
     * 获取作为数据结束标记传递的元素。
     *
     * @return The element that is passed as marker for the end of data.
     */
    static <T> CircularElement<T> endMarker() {
        @SuppressWarnings("unchecked")
        CircularElement<T> c = (CircularElement<T>) EOF_MARKER;
        return c;
    }

    /**
     * Gets the element that is passed as marker for signal beginning of spilling.
     * 获取作为溢出信号开始的标记传递的元素。
     *
     * @return The element that is passed as marker for signal beginning of spilling.
     */
    static <T> CircularElement<T> spillingMarker() {
        @SuppressWarnings("unchecked")
        CircularElement<T> c = (CircularElement<T>) SPILLING_MARKER;
        return c;
    }
}
