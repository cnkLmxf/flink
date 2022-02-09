/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.apache.flink.api.common.eventtime;

import org.apache.flink.annotation.Public;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Watermarks are the progress indicators in the data streams. A watermark signifies that no events
 * with a timestamp smaller or equal to the watermark's time will occur after the water. A watermark
 * with timestamp <i>T</i> indicates that the stream's event time has progressed to time <i>T</i>.
 * 水印是数据流中的进度指示器。 水印表示在水之后不会发生时间戳小于或等于水印时间的事件。
 * 带有时间戳 <i>T</i> 的水印表示流的事件时间已进展到时间 <i>T</i>。
 *
 * <p>Watermarks are created at the sources and propagate through the streams and operators.
 * 水印在源头创建并通过流和操作符传播。
 *
 * <p>In some cases a watermark is only a heuristic, meaning some events with a lower timestamp may
 * still follow. In that case, it is up to the logic of the operators to decide what to do with the
 * "late events". Operators can for example ignore these late events, route them to a different
 * stream, or send update to their previously emitted results.
 * 在某些情况下，水印只是一种启发式方法，这意味着某些具有较低时间戳的事件可能仍会出现。
 * 在这种情况下，由操作员的逻辑来决定如何处理“迟到事件”。
 * 例如，操作员可以忽略这些迟到的事件，将它们路由到不同的流，或者将更新发送到之前发出的结果。
 *
 * <p>When a source reaches the end of the input, it emits a final watermark with timestamp {@code
 * Long.MAX_VALUE}, indicating the "end of time".
 * 当源到达输入的末尾时，它会发出带有时间戳 {@code Long.MAX_VALUE} 的最终水印，表示“时间结束”。
 *
 * <p>Note: A stream's time starts with a watermark of {@code Long.MIN_VALUE}. That means that all
 * records in the stream with a timestamp of {@code Long.MIN_VALUE} are immediately late.
 * 注意：流的时间以 {@code Long.MIN_VALUE} 的水印开始。
 * 这意味着流中时间戳为 {@code Long.MIN_VALUE} 的所有记录都会立即延迟。
 */
@Public
public final class Watermark implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Thread local formatter for stringifying the timestamps.
     * 用于字符串化时间戳的线程本地格式化程序。
     * */
    private static final ThreadLocal<SimpleDateFormat> TS_FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));

    // ------------------------------------------------------------------------

    /** The watermark that signifies end-of-event-time.
     * 表示事件时间结束的水印。
     * */
    public static final Watermark MAX_WATERMARK = new Watermark(Long.MAX_VALUE);

    // ------------------------------------------------------------------------

    /** The timestamp of the watermark in milliseconds.
     * 水印的时间戳（以毫秒为单位）。
     * */
    private final long timestamp;

    /** Creates a new watermark with the given timestamp in milliseconds. */
    public Watermark(long timestamp) {
        this.timestamp = timestamp;
    }

    /** Returns the timestamp associated with this Watermark. */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Formats the timestamp of this watermark, assuming it is a millisecond timestamp. The returned
     * format is "yyyy-MM-dd HH:mm:ss.SSS".
     * 格式化此水印的时间戳，假设它是毫秒时间戳。 返回的格式为“yyyy-MM-dd HH:mm:ss.SSS”。
     */
    public String getFormattedTimestamp() {
        return TS_FORMATTER.get().format(new Date(timestamp));
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        return this == o
                || o != null
                        && o.getClass() == Watermark.class
                        && ((Watermark) o).timestamp == this.timestamp;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(timestamp);
    }

    @Override
    public String toString() {
        return "Watermark @ " + timestamp + " (" + getFormattedTimestamp() + ')';
    }
}
