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

package org.apache.flink.runtime.io.network.api.writer;

import org.apache.flink.core.io.IOReadableWritable;

/**
 * The {@link ChannelSelector} determines to which logical channels a record should be written to.
 * {@link ChannelSelector} 确定记录应写入哪些逻辑通道。
 *
 * @param <T> the type of record which is sent through the attached output gate
 */
public interface ChannelSelector<T extends IOReadableWritable> {

    /**
     * Initializes the channel selector with the number of output channels.
     * 使用输出通道数初始化通道选择器。
     *
     * @param numberOfChannels the total number of output channels which are attached to respective
     *     output gate.
     */
    void setup(int numberOfChannels);

    /**
     * Returns the logical channel index, to which the given record should be written. It is illegal
     * to call this method for broadcast channel selectors and this method can remain not
     * implemented in that case (for example by throwing {@link UnsupportedOperationException}).
     * 返回应该写入给定记录的逻辑通道索引。 为广播频道选择器调用此方法是非法的，并且在这种情况下此方法可能保持不实现
     * （例如通过抛出 {@link UnsupportedOperationException}）。
     *
     * @param record the record to determine the output channels for.
     * @return an integer number which indicates the index of the output channel through which the
     *     record shall be forwarded.
     */
    int selectChannel(T record);

    /**
     * Returns whether the channel selector always selects all the output channels.
     * 返回通道选择器是否始终选择所有输出通道。
     *
     * @return true if the selector is for broadcast mode.
     */
    boolean isBroadcast();
}
