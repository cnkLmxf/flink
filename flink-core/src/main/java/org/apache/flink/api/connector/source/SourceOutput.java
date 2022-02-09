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

package org.apache.flink.api.connector.source;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.eventtime.TimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkOutput;

/**
 * The {@code SourceOutput} is the gateway for a {@link SourceReader}) to emit the produced records
 * and watermarks.
 *{@code SourceOutput} 是 {@link SourceReader}) 发出生成的记录和水印的网关。
 *
 * <p>A {@code SourceReader} may have multiple SourceOutputs, scoped to individual <i>Source
 * Splits</i>. That way, streams of events from different splits can be identified and treated
 * separately, for example for watermark generation, or event-time skew handling.
 * 一个 {@code SourceReader} 可能有多个 SourceOutput，范围为单个 <i>Source Splits</i>。
 * 这样，可以分别识别和处理来自不同拆分的事件流，例如用于水印生成或事件时间偏差处理。
 */
@PublicEvolving
public interface SourceOutput<T> extends WatermarkOutput {

    /**
     * Emit a record without a timestamp.
     * 发出没有时间戳的记录。
     *
     * <p>Use this method if the source system does not have a notion of records with timestamps.
     * 如果源系统没有带有时间戳的记录的概念，请使用此方法。
     *
     * <p>The events later pass through a {@link TimestampAssigner}, which attaches a timestamp to
     * the event based on the event's contents. For example a file source with JSON records would
     * not have a generic timestamp from the file reading and JSON parsing process, and thus use
     * this method to produce initially a record without a timestamp. The {@code TimestampAssigner}
     * in the next step would be used to extract timestamp from a field of the JSON object.
     * 事件稍后通过 {@link TimestampAssigner}，它根据事件的内容将时间戳附加到事件。
     * 例如，具有 JSON 记录的文件源不会具有来自文件读取和 JSON 解析过程的通用时间戳，
     * 因此使用此方法最初生成没有时间戳的记录。 下一步中的 {@code TimestampAssigner} 将用于从 JSON 对象的字段中提取时间戳。
     *
     * @param record the record to emit.
     */
    void collect(T record);

    /**
     * Emit a record with a timestamp.
     * 发出带有时间戳的记录。
     *
     * <p>Use this method if the source system has timestamps attached to records. Typical examples
     * would be Logs, PubSubs, or Message Queues, like Kafka or Kinesis, which store a timestamp
     * with each event.
     * 如果源系统具有附加到记录的时间戳，请使用此方法。
     * 典型示例是日志、PubSub 或消息队列，如 Kafka 或 Kinesis，它们存储每个事件的时间戳。
     *
     * <p>The events typically still pass through a {@link TimestampAssigner}, which may decide to
     * either use this source-provided timestamp, or replace it with a timestamp stored within the
     * event (for example if the event was a JSON object one could configure aTimestampAssigner that
     * extracts one of the object's fields and uses that as a timestamp).
     * 事件通常仍通过 {@link TimestampAssigner}，它可能决定使用此源提供的时间戳，
     * 或将其替换为存储在事件中的时间戳（例如，如果事件是 JSON 对象，
     * 则可以配置 aTimestampAssigner 提取对象的一个字段并将其用作时间戳）。
     *
     * @param record the record to emit.
     * @param timestamp the timestamp of the record.
     */
    void collect(T record, long timestamp);
}
