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

package org.apache.flink.core.memory;

import org.apache.flink.annotation.Public;

import java.io.DataOutput;
import java.io.IOException;

/**
 * This interface defines a view over some memory that can be used to sequentially write contents to
 * the memory. The view is typically backed by one or more {@link
 * org.apache.flink.core.memory.MemorySegment}.
 * 该接口定义了一些内存的视图，可用于将内容顺序写入内存。
 * 视图通常由一个或多个 {@link org.apache.flink.core.memory.MemorySegment} 支持。
 */
@Public
public interface DataOutputView extends DataOutput {

    /**
     * Skips {@code numBytes} bytes memory. If some program reads the memory that was skipped over,
     * the results are undefined.
     * 跳过 {@code numBytes} 字节内存。 如果某个程序读取了被跳过的内存，则结果是不确定的。
     *
     * @param numBytes The number of bytes to skip.
     * @throws IOException Thrown, if any I/O related problem occurred such that the view could not
     *     be advanced to the desired position.
     */
    void skipBytesToWrite(int numBytes) throws IOException;

    /**
     * Copies {@code numBytes} bytes from the source to this view.
     * 将 {@code numBytes} 个字节从源复制到此视图。
     *
     * @param source The source to copy the bytes from.
     * @param numBytes The number of bytes to copy.
     * @throws IOException Thrown, if any I/O related problem occurred, such that either the input
     *     view could not be read, or the output could not be written.
     */
    void write(DataInputView source, int numBytes) throws IOException;
}
