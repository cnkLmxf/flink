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

package org.apache.flink.runtime.heartbeat;

import org.apache.flink.runtime.clusterframework.types.ResourceID;

/**
 * Interface for the interaction with the {@link HeartbeatManager}. The heartbeat listener is used
 * for the following things:
 * 与 {@link HeartbeatManager} 交互的接口。 心跳监听器用于以下事情：
 *<ul>
 *    <li>关于心跳超时的通知
 *    <li>传入检测信号的负载报告
 *    <li>检索传出心跳的有效负载
 *</ul>
 * <ul>
 *   <li>Notifications about heartbeat timeouts
 *   <li>Payload reports of incoming heartbeats
 *   <li>Retrieval of payloads for outgoing heartbeats
 * </ul>
 *
 * @param <I> Type of the incoming payload
 * @param <O> Type of the outgoing payload
 */
public interface HeartbeatListener<I, O> {

    /**
     * Callback which is called if a heartbeat for the machine identified by the given resource ID
     * times out.
     * 如果由给定资源 ID 标识的机器的心跳超时，则会调用回调。
     *
     * @param resourceID Resource ID of the machine whose heartbeat has timed out
     */
    void notifyHeartbeatTimeout(ResourceID resourceID);

    /**
     * Callback which is called whenever a heartbeat with an associated payload is received. The
     * carried payload is given to this method.
     * 每当接收到带有关联有效负载的心跳时调用的回调。 携带的有效载荷被赋予该方法。
     *
     * @param resourceID Resource ID identifying the sender of the payload
     * @param payload Payload of the received heartbeat
     */
    void reportPayload(ResourceID resourceID, I payload);

    /**
     * Retrieves the payload value for the next heartbeat message.
     * 检索下一条心跳消息的有效负载值。
     *
     * @param resourceID Resource ID identifying the receiver of the payload
     * @return The payload for the next heartbeat
     */
    O retrievePayload(ResourceID resourceID);
}
