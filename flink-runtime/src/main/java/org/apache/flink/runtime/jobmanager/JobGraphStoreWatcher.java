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

package org.apache.flink.runtime.jobmanager;

/**
 * A watcher on {@link JobGraphStore}. It could monitor all the changes on the job graph store and
 * notify the {@link JobGraphStore} via {@link JobGraphStore.JobGraphListener}.
 * {@link JobGraphStore} 上的观察者。
 * 它可以监视作业图存储上的所有更改，并通过 {@link JobGraphStore.JobGraphListener} 通知 {@link JobGraphStore}。
 *
 * <p><strong>Important</strong>: The {@link JobGraphStoreWatcher} could not guarantee that there is
 * no {@link JobGraphStore.JobGraphListener} callbacks happen after {@link #stop()}. So the
 * implementor is responsible for filtering out these spurious callbacks.
 * <strong>重要提示</strong>：{@link JobGraphStoreWatcher} 无法保证在 {@link #stop()} 之后不会发生
 * {@link JobGraphStore.JobGraphListener} 回调。 所以实现者负责过滤掉这些虚假的回调。
 */
public interface JobGraphStoreWatcher {

    /**
     * Start the watcher on {@link JobGraphStore}.
     * 在 {@link JobGraphStore} 上启动观察者。
     *
     * @param jobGraphListener use jobGraphListener to notify the {@link DefaultJobGraphStore}
     * @throws Exception when start internal services
     */
    void start(JobGraphStore.JobGraphListener jobGraphListener) throws Exception;

    /**
     * Stop the watcher on {@link JobGraphStore}.
     * 在 {@link JobGraphStore} 上停止观察者。
     *
     * @throws Exception when stop internal services
     */
    void stop() throws Exception;
}
