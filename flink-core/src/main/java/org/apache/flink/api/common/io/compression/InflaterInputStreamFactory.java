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

package org.apache.flink.api.common.io.compression;

import org.apache.flink.annotation.Internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/** Creates a new instance of a certain subclass of {@link java.util.zip.InflaterInputStream}.
 * 创建 {@link java.util.zip.InflaterInputStream} 的某个子类的新实例。
 * */
@Internal
public interface InflaterInputStreamFactory<T extends InputStream> {

    /**
     * Creates a {@link java.util.zip.InflaterInputStream} that wraps the given input stream.
     * 创建一个包装给定输入流的 {@link java.util.zip.InflaterInputStream}。
     *
     * @param in is the compressed input stream
     * @return the inflated input stream
     */
    T create(InputStream in) throws IOException;

    /**
     * Lists a collection of typical file extensions (e.g., "gz", "gzip") that are associated with
     * the compression algorithm in the {@link java.util.zip.InflaterInputStream} {@code T}.
     * 列出与 {@link java.util.zip.InflaterInputStream} {@code T} 中的压缩算法相关联的典型文件扩展名的集合（例如，“gz”、“gzip”）。
     *
     * @return a (possibly empty) collection of lower-case file extensions, without the period
     */
    Collection<String> getCommonFileExtensions();
}
