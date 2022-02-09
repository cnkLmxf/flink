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

package org.apache.flink.runtime.query;

import java.net.InetSocketAddress;

/**
 * An interface for the Queryable State Server running on each Task Manager in the cluster. This
 * server is responsible for serving requests coming from the {@link KvStateClientProxy Queryable
 * State Proxy} and requesting <b>locally</b> stored state.
 * 集群中每个任务管理器上运行的可查询状态服务器的接口。
 * 该服务器负责处理来自 {@link KvStateClientProxy Queryable State Proxy} 的请求并请求<b>本地</b>存储的状态。
 */
public interface KvStateServer {

    /**
     * Returns the {@link InetSocketAddress address} the server is listening to.
     * 返回服务器正在侦听的 {@link InetSocketAddress 地址}。
     *
     * @return Server address.
     */
    InetSocketAddress getServerAddress();

    /** Starts the server. */
    void start() throws Throwable;

    /** Shuts down the server and all related thread pools.
     * 关闭服务器和所有相关的线程池。
     * */
    void shutdown();
}
