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

import java.io.IOException;

/**
 * The connection manager manages physical connections for the (logical) remote input channels at
 * runtime.
 * 连接管理器在运行时管理（逻辑）远程输入通道的物理连接。
 */
public interface ConnectionManager {

    /**
     * Starts the internal related components for network connection and communication.
     * 启动内部相关组件进行网络连接和通信。
     *
     * @return a port to connect to the task executor for shuffle data exchange, -1 if only local
     *     connection is possible.
     *     连接到任务执行器以进行随机数据交换的端口，如果只能进行本地连接，则为-1。
     */
    int start() throws IOException;

    /** Creates a {@link PartitionRequestClient} instance for the given {@link ConnectionID}.
     * 为给定的 {@link ConnectionID} 创建一个 {@link PartitionRequestClient} 实例。
     * */
    PartitionRequestClient createPartitionRequestClient(ConnectionID connectionId)
            throws IOException, InterruptedException;

    /** Closes opened ChannelConnections in case of a resource release.
     * 在资源释放的情况下关闭打开的 ChannelConnections。
     * */
    void closeOpenChannelConnections(ConnectionID connectionId);

    int getNumberOfActiveConnections();

    void shutdown() throws IOException;
}
