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

import org.apache.flink.runtime.rpc.FatalErrorHandler;

/** Factory for creating {@link LeaderElectionDriver} with different implementation.
 * 用于创建具有不同实现的 {@link LeaderElectionDriver} 的工厂。
 * */
public interface LeaderElectionDriverFactory {

    /**
     * Create a specific {@link LeaderElectionDriver} and start the necessary services. For example,
     * LeaderLatch and NodeCache in Zookeeper, KubernetesLeaderElector and ConfigMap watcher in
     * Kubernetes.
     * 创建一个特定的 {@link LeaderElectionDriver} 并启动必要的服务。
     * 例如 Zookeeper 中的 LeaderLatch 和 NodeCache，Kubernetes 中的 KubernetesLeaderElector 和 ConfigMap watcher。
     *
     * @param leaderEventHandler handler for the leader election driver to process leader events.
     * @param leaderContenderDescription leader contender description.
     * @param fatalErrorHandler fatal error handler
     * @throws Exception when create a specific {@link LeaderElectionDriver} implementation and
     *     start the necessary services.
     */
    LeaderElectionDriver createLeaderElectionDriver(
            LeaderElectionEventHandler leaderEventHandler,
            FatalErrorHandler fatalErrorHandler,
            String leaderContenderDescription)
            throws Exception;
}
