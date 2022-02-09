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
import org.apache.flink.api.common.ExecutionConfig;

/**
 * The {@code WatermarkGenerator} generates watermarks either based on events or periodically (in a
 * fixed interval).
 * {@code WatermarkGenerator} 基于事件或定期（以固定间隔）生成水印。
 *
 * <p><b>Note:</b> This WatermarkGenerator subsumes the previous distinction between the {@code
 * AssignerWithPunctuatedWatermarks} and the {@code AssignerWithPeriodicWatermarks}.
 * <b>注意：</b> 这个 WatermarkGenerator 包含了之前的 {@code AssignerWithPunctuatedWatermarks}
 * 和 {@code AssignerWithPeriodicWatermarks} 之间的区别。
 */
@Public
public interface WatermarkGenerator<T> {

    /**
     * Called for every event, allows the watermark generator to examine and remember the event
     * timestamps, or to emit a watermark based on the event itself.
     * 为每个事件调用，允许水印生成器检查并记住事件时间戳，或根据事件本身发出水印。
     */
    void onEvent(T event, long eventTimestamp, WatermarkOutput output);

    /**
     * Called periodically, and might emit a new watermark, or not.
     * 定期调用，并且可能会发出或不发出新的水印。
     *
     * <p>The interval in which this method is called and Watermarks are generated depends on {@link
     * ExecutionConfig#getAutoWatermarkInterval()}.
     * 调用此方法和生成 Watermarks 的时间间隔取决于 {@link ExecutionConfig#getAutoWatermarkInterval()}。
     */
    void onPeriodicEmit(WatermarkOutput output);
}
