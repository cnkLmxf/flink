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

package org.apache.flink.api.common.io;

import org.apache.flink.annotation.Internal;
import org.apache.flink.core.fs.Path;
import org.apache.flink.util.Preconditions;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class for determining if a particular file should be included or excluded based on a set of
 * include and exclude glob filters.
 * 用于根据一组包含和排除 glob 过滤器确定是否应包含或排除特定文件的类。
 *
 * <p>Glob filter support the following expressions:
 * Glob 过滤器支持以下表达式：
 *<ul>
 *     <li>* - 匹配任意数量的任意字符，包括无
 *     <li>** - 匹配所有子目录中的任何文件
 *     <li>？ - 匹配任何单个字符
 *     <li>[abc] - 匹配括号中列出的字符之一
 *     <li>[a-z] - 匹配括号中给定范围内的一个字符
 * </ul>
 * <ul>
 *   <li>* - matches any number of any characters including none
 *   <li>** - matches any file in all subdirectories
 *   <li>? - matches any single character
 *   <li>[abc] - matches one of the characters listed in a brackets
 *   <li>[a-z] - matches one character from the range given in the brackets
 * </ul>
 *
 *
 * <p>If does not match an include pattern it is excluded. If it matches and include pattern but
 * also matches an exclude pattern it is excluded.
 * 如果与包含模式不匹配，则将其排除。 如果它匹配并包含模式但也匹配排除模式，则将其排除。
 *
 * <p>If no patterns are provided all files are included
 */
@Internal
public class GlobFilePathFilter extends FilePathFilter {

    private static final long serialVersionUID = 1L;

    private final List<String> includePatterns;
    private final List<String> excludePatterns;

    // Path matchers are not serializable so we are delaying their
    // creation until they are used
    // 路径匹配器是不可序列化的，所以我们会延迟它们的创建直到它们被使用
    private transient ArrayList<PathMatcher> includeMatchers;
    private transient ArrayList<PathMatcher> excludeMatchers;

    /** Constructor for GlobFilePathFilter that will match all files
     * 将匹配所有文件的 GlobFilePathFilter 的构造函数
     * */
    public GlobFilePathFilter() {
        this(Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    /**
     * Constructor for GlobFilePathFilter
     *
     * @param includePatterns glob patterns for files to include
     * @param excludePatterns glob patterns for files to exclude
     */
    public GlobFilePathFilter(List<String> includePatterns, List<String> excludePatterns) {
        this.includePatterns = Preconditions.checkNotNull(includePatterns);
        this.excludePatterns = Preconditions.checkNotNull(excludePatterns);
    }

    private ArrayList<PathMatcher> buildPatterns(List<String> patterns) {
        FileSystem fileSystem = FileSystems.getDefault();
        ArrayList<PathMatcher> matchers = new ArrayList<>(patterns.size());

        for (String patternStr : patterns) {
            matchers.add(fileSystem.getPathMatcher("glob:" + patternStr));
        }

        return matchers;
    }

    @Override
    public boolean filterPath(Path filePath) {
        if (getIncludeMatchers().isEmpty() && getExcludeMatchers().isEmpty()) {
            return false;
        }

        // compensate for the fact that Flink paths are slashed
        final String path =
                filePath.hasWindowsDrive() ? filePath.getPath().substring(1) : filePath.getPath();

        final java.nio.file.Path nioPath = Paths.get(path);

        for (PathMatcher matcher : getIncludeMatchers()) {
            if (matcher.matches(nioPath)) {
                return shouldExclude(nioPath);
            }
        }

        return true;
    }

    private ArrayList<PathMatcher> getIncludeMatchers() {
        if (includeMatchers == null) {
            includeMatchers = buildPatterns(includePatterns);
        }
        return includeMatchers;
    }

    private ArrayList<PathMatcher> getExcludeMatchers() {
        if (excludeMatchers == null) {
            excludeMatchers = buildPatterns(excludePatterns);
        }
        return excludeMatchers;
    }

    private boolean shouldExclude(java.nio.file.Path nioPath) {
        for (PathMatcher matcher : getExcludeMatchers()) {
            if (matcher.matches(nioPath)) {
                return true;
            }
        }
        return false;
    }
}
