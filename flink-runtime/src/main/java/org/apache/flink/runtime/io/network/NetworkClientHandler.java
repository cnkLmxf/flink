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

package org.apache.flink.runtime.io.network;

import org.apache.flink.runtime.io.network.partition.consumer.InputChannelID;
import org.apache.flink.runtime.io.network.partition.consumer.RemoteInputChannel;

import org.apache.flink.shaded.netty4.io.netty.channel.ChannelHandler;

import javax.annotation.Nullable;

import java.io.IOException;

/** Channel handler to read and write network messages on client side.
 * 在客户端读取和写入网络消息的通道处理程序。
 * */
public interface NetworkClientHandler extends ChannelHandler {

    void addInputChannel(RemoteInputChannel inputChannel) throws IOException;

    void removeInputChannel(RemoteInputChannel inputChannel);

    @Nullable
    RemoteInputChannel getInputChannel(InputChannelID inputChannelId);

    void cancelRequestFor(InputChannelID inputChannelId);

    /**
     * The credit begins to announce after receiving the sender's backlog from buffer response. Than
     * means it should only happen after some interactions with the channel to make sure the context
     * will not be null.
     * 在从缓冲区响应中收到发送方的积压后，信用开始宣布。 这意味着它应该只在与通道进行一些交互后发生，以确保上下文不会为空。
     *
     * @param inputChannel The input channel with unannounced credits.
     */
    void notifyCreditAvailable(final RemoteInputChannel inputChannel);

    /**
     * Resumes data consumption from the producer after an exactly once checkpoint.
     * 在恰好一次检查点之后恢复生产者的数据消耗。
     *
     * @param inputChannel The input channel to resume data consumption.
     */
    void resumeConsumption(RemoteInputChannel inputChannel);
}
