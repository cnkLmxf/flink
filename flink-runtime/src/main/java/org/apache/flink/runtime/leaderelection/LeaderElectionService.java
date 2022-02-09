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

package org.apache.flink.runtime.leaderelection;

import javax.annotation.Nonnull;

import java.util.UUID;

/**
 * Interface for a service which allows to elect a leader among a group of contenders.
 * 允许在一组竞争者中选举领导者的服务接口。
 *
 * <p>Prior to using this service, it has to be started calling the start method. The start method
 * takes the contender as a parameter. If there are multiple contenders, then each contender has to
 * instantiate its own leader election service.
 * 在使用此服务之前，必须调用 start 方法来启动它。 start 方法将竞争者作为参数。
 * 如果有多个竞争者，那么每个竞争者都必须实例化自己的领导者选举服务。
 *
 * <p>Once a contender has been granted leadership he has to confirm the received leader session ID
 * by calling the method {@link #confirmLeadership(UUID, String)}. This will notify the leader
 * election service, that the contender has accepted the leadership specified and that the leader
 * session id as well as the leader address can now be published for leader retrieval services.
 * 一旦竞争者被授予领导权，他必须通过调用方法 {@link #confirmLeadership(UUID, String)} 来确认收到的领导者会话 ID。
 * 这将通知领导选举服务，竞争者已接受指定的领导，并且领导会话 id 以及领导地址现在可以为领导检索服务发布。
 */
public interface LeaderElectionService {

    /**
     * Starts the leader election service. This method can only be called once.
     * 启动领导选举服务。 此方法只能调用一次。
     *
     * @param contender LeaderContender which applies for the leadership
     * @throws Exception
     */
    void start(LeaderContender contender) throws Exception;

    /**
     * Stops the leader election service.
     *
     * @throws Exception
     */
    void stop() throws Exception;

    /**
     * Confirms that the {@link LeaderContender} has accepted the leadership identified by the given
     * leader session id. It also publishes the leader address under which the leader is reachable.
     * 确认 {@link LeaderContender} 已接受由给定领导者会话 ID 标识的领导。 它还发布领导者可以访问的领导者地址。
     *
     * <p>The rational behind this method is to establish an order between setting the new leader
     * session ID in the {@link LeaderContender} and publishing the new leader session ID as well as
     * the leader address to the leader retrieval services.
     * 此方法背后的原因是在 {@link LeaderContender} 中设置新的领导者会话 ID
     * 和将新的领导者会话 ID 以及领导者地址发布到领导者检索服务之间建立顺序。
     *
     * @param leaderSessionID The new leader session ID
     * @param leaderAddress The address of the new leader
     */
    void confirmLeadership(UUID leaderSessionID, String leaderAddress);

    /**
     * Returns true if the {@link LeaderContender} with which the service has been started owns
     * currently the leadership under the given leader session id.
     * 如果已启动服务的 {@link LeaderContender} 当前拥有给定领导会话 ID 下的领导，则返回 true。
     *
     * @param leaderSessionId identifying the current leader
     * @return true if the associated {@link LeaderContender} is the leader, otherwise false
     */
    boolean hasLeadership(@Nonnull UUID leaderSessionId);
}
