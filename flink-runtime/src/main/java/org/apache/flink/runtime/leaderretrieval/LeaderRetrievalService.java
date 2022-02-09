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

package org.apache.flink.runtime.leaderretrieval;

/**
 * This interface has to be implemented by a service which retrieves the current leader and notifies
 * a listener about it.
 * 该接口必须由检索当前领导并通知侦听器的服务来实现。
 *
 * <p>Prior to using this service it has to be started by calling the start method. The start method
 * also takes the {@link LeaderRetrievalListener} as an argument. The service can only be started
 * once.
 * 在使用此服务之前，必须通过调用 start 方法来启动它。
 * start 方法还将 {@link LeaderRetrievalListener} 作为参数。 该服务只能启动一次。
 *
 * <p>The service should be stopped by calling the stop method.
 * 应该通过调用 stop 方法来停止服务。
 */
public interface LeaderRetrievalService {

    /**
     * Starts the leader retrieval service with the given listener to listen for new leaders. This
     * method can only be called once.
     * 使用给定的侦听器启动领导者检索服务以侦听新的领导者。 此方法只能调用一次。
     *
     * @param listener The leader retrieval listener which will be notified about new leaders.
     * @throws Exception
     */
    void start(LeaderRetrievalListener listener) throws Exception;

    /**
     * Stops the leader retrieval service.
     * 停止领导者检索服务。
     *
     * @throws Exception
     */
    void stop() throws Exception;
}
