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

import javax.annotation.Nullable;

import java.util.UUID;

/**
 * Classes which want to be notified about a changing leader by the {@link LeaderRetrievalService}
 * have to implement this interface.
 * 想要通过 {@link LeaderRetrievalService} 通知领导者变化的类必须实现这个接口。
 */
public interface LeaderRetrievalListener {

    /**
     * This method is called by the {@link LeaderRetrievalService} when a new leader is elected.
     * 此方法由 {@link LeaderRetrievalService} 在选举新领导时调用。
     *
     * <p>If both arguments are null then it signals that leadership was revoked without a new
     * leader having been elected.
     * 如果两个参数都为空，则表明在没有选出新的领导者的情况下领导层被撤销。
     *
     * @param leaderAddress The address of the new leader
     * @param leaderSessionID The new leader session ID
     */
    void notifyLeaderAddress(@Nullable String leaderAddress, @Nullable UUID leaderSessionID);

    /**
     * This method is called by the {@link LeaderRetrievalService} in case of an exception. This
     * assures that the {@link LeaderRetrievalListener} is aware of any problems occurring in the
     * {@link LeaderRetrievalService} thread.
     * 此方法由 {@link LeaderRetrievalService} 在发生异常时调用。
     * 这确保 {@link LeaderRetrievalListener} 知道 {@link LeaderRetrievalService} 线程中发生的任何问题。
     *
     * @param exception
     */
    void handleError(Exception exception);
}
