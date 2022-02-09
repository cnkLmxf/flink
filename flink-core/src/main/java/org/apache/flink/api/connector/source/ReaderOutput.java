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
import org.apache.flink.api.common.eventtime.Watermark;

/**
 * The interface provided by the Flink runtime to the {@link SourceReader} to emit records, and
 * optionally watermarks, to downstream operators for message processing.
 * Flink 运行时向 {@link SourceReader} 提供的接口，用于向下游操作员发出记录和可选的水印以进行消息处理。
 *
 * <p>The {@code ReaderOutput} is a {@link SourceOutput} and can be used directly to emit the stream
 * of events from the source. This is recommended for source where the SourceReader processes only a
 * single split, or where NO split-specific characteristics are required (like per-split watermarks
 * and idleness, split-specific event-time skew handling, etc.). As a special case, this is true for
 * sources that are purely supporting bounded/batch data processing.
 * {@code ReaderOutput} 是一个 {@link SourceOutput}，可直接用于从源发出事件流。
 * 对于 SourceReader 仅处理单个拆分或不需要拆分特定特征的源（例如每个拆分的水印和空闲、拆分特定的事件时间倾斜处理等），
 * 建议使用此方法。 作为一种特殊情况，这对于纯粹支持有界/批处理数据处理的源来说是正确的。
 *
 * <p>For most streaming sources, the {@code SourceReader} should use split-specific outputs, to
 * allow the processing logic to run per-split watermark generators, idleness detection, etc. To
 * create a split-specific {@code SourceOutput} use the {@link
 * ReaderOutput#createOutputForSplit(String)} method, using the Source Split's ID. Make sure to
 * release the output again once the source has finished processing that split.
 * 对于大多数流媒体源，{@code SourceReader} 应使用特定于拆分的输出，以允许处理逻辑运行每个拆分的水印生成器、空闲检测等。
 * 要创建特定于拆分的 {@code SourceOutput}，请使用 { @link ReaderOutput#createOutputForSplit(String)} 方法，
 * 使用 Source Split 的 ID。 确保在源完成处理该拆分后再次释放输出。
 */
@PublicEvolving
public interface ReaderOutput<T> extends SourceOutput<T> {

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
    @Override
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
    @Override
    void collect(T record, long timestamp);

    /**
     * Emits the given watermark.
     *
     * <p>Emitting a watermark also implicitly marks the stream as <i>active</i>, ending previously
     * marked idleness.
     * 发出水印也隐含地将流标记为<i>活动</i>，结束之前标记的空闲。
     */
    @Override
    void emitWatermark(Watermark watermark);

    /**
     * Marks this output as idle, meaning that downstream operations do not wait for watermarks from
     * this output.
     * 将此输出标记为空闲，这意味着下游操作不会等待此输出的水印。
     *
     * <p>An output becomes active again as soon as the next watermark is emitted.
     * 一旦发出下一个水印，输出就会再次激活。
     */
    @Override
    void markIdle();

    /**
     * Creates a {@code SourceOutput} for a specific Source Split. Use these outputs if you want to
     * run split-local logic, like watermark generation.
     * 为特定的源拆分创建一个 {@code SourceOutput}。 如果您想运行分割本地逻辑，例如水印生成，请使用这些输出。
     *
     * <p>If a split-local output was already created for this split-ID, the method will return that
     * instance, so that only one split-local output exists per split-ID.
     * 如果已为此拆分 ID 创建了拆分本地输出，则该方法将返回该实例，以便每个拆分 ID 仅存在一个拆分本地输出。
     *
     * <p><b>IMPORTANT:</b> After the split has been finished, it is crucial to release the created
     * output again. Otherwise it will continue to contribute to the watermark generation like a
     * perpetually stalling source split, and may hold back the watermark indefinitely.
     * <b>重要提示：</b> 拆分完成后，再次释放创建的输出至关重要。
     * 否则，它将继续对水印的生成做出贡献，就像永远停止的源分裂一样，并且可能无限期地阻止水印。
     *
     * @see #releaseOutputForSplit(String)
     */
    SourceOutput<T> createOutputForSplit(String splitId);

    /**
     * Releases the {@code SourceOutput} created for the split with the given ID.
     *
     * @see #createOutputForSplit(String)
     */
    void releaseOutputForSplit(String splitId);
}
