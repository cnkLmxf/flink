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

/**
 * Interface which should be implemented to respond to leader changes in {@link
 * LeaderElectionDriver}.
 * 应实施以响应 {@link LeaderElectionDriver} 中的领导者更改的接口。
 *
 * <p><strong>Important</strong>: The {@link LeaderElectionDriver} could not guarantee that there is
 * no {@link LeaderElectionEventHandler} callbacks happen after {@link
 * LeaderElectionDriver#close()}. This means that the implementor of {@link
 * LeaderElectionEventHandler} is responsible for filtering out spurious callbacks(e.g. after close
 * has been called on {@link LeaderElectionDriver}).
 * <strong>重要提示</strong>：{@link LeaderElectionDriver} 无法保证在 {@link LeaderElectionDriver#close()}
 * 之后不会发生 {@link LeaderElectionEventHandler} 回调。
 * 这意味着 {@link LeaderElectionEventHandler} 的实现者负责过滤掉虚假回调
 * （例如，在 {@link LeaderElectionDriver} 上调用 close 之后）。
 */
public interface LeaderElectionEventHandler {

    /** Called by specific {@link LeaderElectionDriver} when the leadership is granted.
     * 授予领导权时由特定的 {@link LeaderElectionDriver} 调用。
     * */
    void onGrantLeadership();

    /** Called by specific {@link LeaderElectionDriver} when the leadership is revoked.
     * 当领导被撤销时由特定的 {@link LeaderElectionDriver} 调用。
     * */
    void onRevokeLeadership();

    /**
     * Called by specific {@link LeaderElectionDriver} when the leader information is changed. Then
     * the {@link LeaderElectionService} could write the leader information again if necessary. This
     * method should only be called when {@link LeaderElectionDriver#hasLeadership()} is true.
     * Duplicated leader change events could happen, so the implementation should check whether the
     * passed leader information is really different with internal confirmed leader information.
     * 当领导者信息发生变化时，由特定的 {@link LeaderElectionDriver} 调用。
     * 然后 {@link LeaderElectionService} 可以在必要时再次写入领导者信息。
     * 仅当 {@link LeaderElectionDriver#hasLeadership()} 为 true 时才应调用此方法。
     * 可能会发生重复的领导者变更事件，因此实施应检查传递的领导者信息是否与内部确认的领导者信息确实不同。
     *
     * @param leaderInformation leader information which contains leader session id and leader
     *     address.
     */
    void onLeaderInformationChange(LeaderInformation leaderInformation);
}
