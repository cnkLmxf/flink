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

package org.apache.flink.runtime.jobmaster;

/** Base interface for managers of services that are explicitly connected to / disconnected from.
 * 显式连接/断开的服务管理器的基本接口。
 * */
public interface ServiceConnectionManager<S> {

    /**
     * Connect to the given service.
     * 连接到给定的服务。
     *
     * @param service service to connect to
     */
    void connect(S service);

    /** Disconnect from the current service.
     * 断开与当前服务的连接。
     * */
    void disconnect();

    /** Close the service connection manager. A closed manager must not be used again.
     * 关闭服务连接管理器。 不得再次使用已关闭的管理器。
     * */
    void close();
}
