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

import org.apache.flink.runtime.leaderelection.LeaderInformation;

/**
 * Interface which should be implemented to notify to {@link LeaderInformation} changes in {@link
 * LeaderRetrievalDriver}.
 * 应实现的接口以通知 {@link LeaderRetrievalDriver} 中的 {@link LeaderInformation} 更改。
 *
 * <p><strong>Important</strong>: The {@link LeaderRetrievalDriver} could not guarantee that there
 * is no {@link LeaderRetrievalEventHandler} callbacks happen after {@link
 * LeaderRetrievalDriver#close()}. This means that the implementor of {@link
 * LeaderRetrievalEventHandler} is responsible for filtering out spurious callbacks(e.g. after close
 * has been called on {@link LeaderRetrievalDriver}).
 * <strong>重要提示</strong>：{@link LeaderRetrievalDriver} 无法保证在 {@link LeaderRetrievalDriver#close()}
 * 之后不会发生 {@link LeaderRetrievalEventHandler} 回调。
 * 这意味着 {@link LeaderRetrievalEventHandler} 的实现者负责过滤掉虚假回调
 * （例如，在 {@link LeaderRetrievalDriver} 上调用 close 之后）。
 */
public interface LeaderRetrievalEventHandler {

    /**
     * Called by specific {@link LeaderRetrievalDriver} to notify leader address.
     * 由特定的 {@link LeaderRetrievalDriver} 调用以通知领导地址。
     *
     * <p>Duplicated leader change events could happen, so the implementation should check whether
     * the passed leader information is truly changed with last stored leader information.
     * 可能会发生重复的领导者更改事件，因此实施应检查传递的领导者信息是否真的与最后存储的领导者信息发生了变化。
     *
     * @param leaderInformation the new leader information to notify {@link LeaderRetrievalService}.
     *     It could be {@link LeaderInformation#empty()} if the leader address does not exist in the
     *     external storage.
     */
    void notifyLeaderAddress(LeaderInformation leaderInformation);
}
