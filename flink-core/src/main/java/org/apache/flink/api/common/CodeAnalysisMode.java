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

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.typeutils.TypeSerializerConfigSnapshot;
import org.apache.flink.api.java.typeutils.runtime.PojoSerializer;

/**
 * @deprecated The code analysis code has been removed and this enum has no effect. <b>NOTE</b> It
 *     can not be removed from the codebase for now, because it had been serialized as part of the
 *     {@link ExecutionConfig} which in turn had been serialized as part of the {@link
 *     PojoSerializer}.
 *     代码分析代码已被删除，此枚举无效。 <b>注意</b> 目前无法从代码库中删除它，
 *     因为它已作为 {@link ExecutionConfig} 的一部分被序列化，而 {@link ExecutionConfig} 又被序列化为
 *     {@link PojoSerializer} 的一部分。
 *     <p>This class can be removed when we drop support for pre 1.8 serializer snapshots that
 *     contained java serialized serializers ({@link TypeSerializerConfigSnapshot}).
 *     当我们放弃对包含 java 序列化序列化器 ({@link TypeSerializerConfigSnapshot}) 的 1.8 之前的序列化器快照的支持时，
 *     可以删除此类。
 */
@PublicEvolving
@Deprecated
public enum CodeAnalysisMode {

    /** Code analysis does not take place. */
    DISABLE,

    /** Hints for improvement of the program are printed to the log.
     * 程序改进的提示会打印到日志中。
     * */
    HINT,

    /** The program will be automatically optimized with knowledge from code analysis.
     * 该程序将根据代码分析的知识自动优化。
     * */
    OPTIMIZE;
}
