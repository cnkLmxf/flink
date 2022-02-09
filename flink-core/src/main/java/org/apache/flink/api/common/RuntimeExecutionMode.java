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

package org.apache.flink.api.common;

import org.apache.flink.annotation.PublicEvolving;

/**
 * Runtime execution mode of DataStream programs. Among other things, this controls task scheduling,
 * network shuffle behavior, and time semantics. Some operations will also change their record
 * emission behaviour based on the configured execution mode.
 * DataStream 程序的运行时执行模式。 除此之外，它还控制任务调度、网络洗牌行为和时间语义。
 * 一些操作也会根据配置的执行模式改变它们的记录发射行为。
 *
 * @see <a
 *     href="https://cwiki.apache.org/confluence/display/FLINK/FLIP-134%3A+Batch+execution+for+the+DataStream+API">
 *     https://cwiki.apache.org/confluence/display/FLINK/FLIP-134%3A+Batch+execution+for+the+DataStream+API</a>
 */
@PublicEvolving
public enum RuntimeExecutionMode {

    /**
     * The Pipeline will be executed with Streaming Semantics. All tasks will be deployed before
     * execution starts, checkpoints will be enabled, and both processing and event time will be
     * fully supported.
     * Pipeline 将使用 Streaming Semantics 执行。
     * 所有任务都将在执行开始之前部署，检查点将被启用，处理和事件时间都将得到完全支持。
     */
    STREAMING,

    /**
     * The Pipeline will be executed with Batch Semantics. Tasks will be scheduled gradually based
     * on the scheduling region they belong, shuffles between regions will be blocking, watermarks
     * are assumed to be "perfect" i.e. no late data, and processing time is assumed to not advance
     * during execution.
     * 流水线将使用批处理语义执行。 任务将根据它们所属的调度区域逐步调度，
     * 区域之间的混洗将被阻塞，水印被假定为“完美”，即没有延迟数据，并且在执行期间假定处理时间不会提前。
     */
    BATCH,

    /**
     * Flink will set the execution mode to {@link RuntimeExecutionMode#BATCH} if all sources are
     * bounded, or {@link RuntimeExecutionMode#STREAMING} if there is at least one source which is
     * unbounded.
     * 如果所有源都是有界的，Flink 会将执行模式设置为 {@link RuntimeExecutionMode#BATCH}，
     * 如果至少有一个源是无界的，则设置为 {@link RuntimeExecutionMode#STREAMING}。
     */
    AUTOMATIC
}
