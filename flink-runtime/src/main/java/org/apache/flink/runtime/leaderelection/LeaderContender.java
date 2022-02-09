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

import java.util.UUID;

/**
 * Interface which has to be implemented to take part in the leader election process of the {@link
 * LeaderElectionService}.
 * 必须实现的接口才能参与 {@link LeaderElectionService} 的领导者选举过程。
 */
public interface LeaderContender {

    /**
     * Callback method which is called by the {@link LeaderElectionService} upon selecting this
     * instance as the new leader. The method is called with the new leader session ID.
     * {@link LeaderElectionService} 在选择此实例作为新领导时调用的回调方法。
     * 使用新的领导者会话 ID 调用该方法。
     *
     * @param leaderSessionID New leader session ID
     */
    void grantLeadership(UUID leaderSessionID);

    /**
     * Callback method which is called by the {@link LeaderElectionService} upon revoking the
     * leadership of a former leader. This might happen in case that multiple contenders have been
     * granted leadership.
     * {@link LeaderElectionService} 在撤销前领导者的领导权时调用的回调方法。
     * 这可能发生在多个竞争者被授予领导权的情况下。
     */
    void revokeLeadership();

    /**
     * Callback method which is called by {@link LeaderElectionService} in case of an error in the
     * service thread.
     * 服务线程出错时由 {@link LeaderElectionService} 调用的回调方法。
     *
     * @param exception Caught exception
     */
    void handleError(Exception exception);

    /**
     * Returns the description of the {@link LeaderContender} for logging purposes.
     * 返回 {@link LeaderContender} 的描述以用于日志记录。
     *
     * @return Description of this contender.
     */
    default String getDescription() {
        return "LeaderContender: " + getClass().getSimpleName();
    }
}
