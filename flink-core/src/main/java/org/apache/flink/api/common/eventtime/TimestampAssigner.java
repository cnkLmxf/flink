/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.eventtime;

import org.apache.flink.annotation.Public;

/**
 * A {@code TimestampAssigner} assigns event time timestamps to elements. These timestamps are used
 * by all functions that operate on event time, for example event time windows.
 * {@code TimestampAssigner} 为元素分配事件时间时间戳。 所有对事件时间进行操作的函数都使用这些时间戳，例如事件时间窗口。
 *
 * <p>Timestamps can be an arbitrary {@code long} value, but all built-in implementations represent
 * it as the milliseconds since the Epoch (midnight, January 1, 1970 UTC), the same way as {@link
 * System#currentTimeMillis()} does it.
 * 时间戳可以是任意的 {@code long} 值，但所有内置实现都将其表示为自纪元（UTC 1970 年 1 月 1 日午夜）以来的毫秒数，
 * 与 {@link System#currentTimeMillis()} 相同 它。
 *
 * @param <T> The type of the elements to which this assigner assigns timestamps.
 */
@Public
@FunctionalInterface
public interface TimestampAssigner<T> {

    /**
     * The value that is passed to {@link #extractTimestamp} when there is no previous timestamp
     * attached to the record.
     * 当记录没有附加时间戳时传递给 {@link #extractTimestamp} 的值。
     */
    long NO_TIMESTAMP = Long.MIN_VALUE;

    /**
     * Assigns a timestamp to an element, in milliseconds since the Epoch. This is independent of
     * any particular time zone or calendar.
     * 为元素分配时间戳，以 Epoch 以来的毫秒数为单位。 这独立于任何特定的时区或日历。
     *
     * <p>The method is passed the previously assigned timestamp of the element. That previous
     * timestamp may have been assigned from a previous assigner. If the element did not carry a
     * timestamp before, this value is {@link #NO_TIMESTAMP} (= {@code Long.MIN_VALUE}: {@value
     * Long#MIN_VALUE}).
     * 该方法传递元素的先前分配的时间戳。 先前的时间戳可能是从先前的分配者分配的。
     * 如果元素之前没有携带时间戳，则此值为 {@link #NO_TIMESTAMP} (= {@code Long.MIN_VALUE}: {@value Long#MIN_VALUE})。
     *
     * @param element The element that the timestamp will be assigned to.
     * @param recordTimestamp The current internal timestamp of the element, or a negative value, if
     *     no timestamp has been assigned yet.
     * @return The new timestamp.
     */
    long extractTimestamp(T element, long recordTimestamp);
}
