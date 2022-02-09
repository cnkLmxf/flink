/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.api.common.io;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.fs.Path;

import java.io.Serializable;

/**
 * The {@link #filterPath(Path)} method is responsible for deciding if a path is eligible for
 * further processing or not. This can serve to exclude temporary or partial files that are still
 * being written.
 * {@link #filterPath(Path)} 方法负责决定路径是否适合进一步处理。 这可以用于排除仍在写入的临时或部分文件。
 */
@PublicEvolving
public abstract class FilePathFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Name of an unfinished Hadoop file */
    public static final String HADOOP_COPYING = "_COPYING_";

    /**
     * Returns {@code true} if the {@code filePath} given is to be ignored when processing a
     * directory, e.g.
     * 如果在处理目录时忽略给定的 {@code filePath}，则返回 {@code true}，例如
     *
     * <pre>{@code
     * public boolean filterPaths(Path filePath) {
     *     return filePath.getName().startsWith(".") || filePath.getName().contains("_COPYING_");
     * }
     * }</pre>
     */
    public abstract boolean filterPath(Path filePath);

    /**
     * Returns the default filter, which excludes the following files:
     * 返回默认过滤器，它排除以下文件：
     * <ul>
     *     <li>以“_”开头的文件
     *     <li>以“.”开头的文件
     *     <li>包含字符串“_COPYING_”的文件
     * </ul>
     *
     * <ul>
     *   <li>Files starting with &quot;_&quot;
     *   <li>Files starting with &quot;.&quot;
     *   <li>Files containing the string &quot;_COPYING_&quot;
     * </ul>
     *
     * @return The singleton instance of the default file path filter.
     */
    public static FilePathFilter createDefaultFilter() {
        return DefaultFilter.INSTANCE;
    }

    // ------------------------------------------------------------------------
    //  The default filter
    // ------------------------------------------------------------------------

    /**
     * The default file path filtering method and is used if no other such function is provided.
     * This filter leaves out files starting with ".", "_", and "_COPYING_".
     * 默认文件路径过滤方法，如果没有提供其他此类功能则使用。 此过滤器会排除以“.”、“_”和“_COPYING_”开头的文件。
     */
    public static class DefaultFilter extends FilePathFilter {

        private static final long serialVersionUID = 1L;

        static final DefaultFilter INSTANCE = new DefaultFilter();

        DefaultFilter() {}

        @Override
        public boolean filterPath(Path filePath) {
            return filePath == null
                    || filePath.getName().startsWith(".")
                    || filePath.getName().startsWith("_")
                    || filePath.getName().contains(HADOOP_COPYING);
        }
    }
}
