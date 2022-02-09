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

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.jobgraph.JobGraph;

import javax.annotation.Nullable;

import java.util.Collection;

/** {@link JobGraph} instances for recovery.
 * {@link JobGraph} 用于恢复的实例。
 * */
public interface JobGraphStore extends JobGraphWriter {

    /** Starts the {@link JobGraphStore} service. */
    void start(JobGraphListener jobGraphListener) throws Exception;

    /** Stops the {@link JobGraphStore} service. */
    void stop() throws Exception;

    /**
     * Returns the {@link JobGraph} with the given {@link JobID} or {@code null} if no job was
     * registered.
     * 如果没有注册作业，则返回具有给定 {@link JobID} 或 {@code null} 的 {@link JobGraph}。
     */
    @Nullable
    JobGraph recoverJobGraph(JobID jobId) throws Exception;

    /**
     * Get all job ids of submitted job graphs to the submitted job graph store.
     * 将提交的作业图的所有作业 id 获取到提交的作业图存储。
     *
     * @return Collection of submitted job ids
     * @throws Exception if the operation fails
     */
    Collection<JobID> getJobIds() throws Exception;

    /**
     * A listener for {@link JobGraph} instances. This is used to react to races between multiple
     * running {@link JobGraphStore} instances (on multiple job managers).
     * {@link JobGraph} 实例的侦听器。
     * 这用于对多个正在运行的 {@link JobGraphStore} 实例（在多个作业管理器上）之间的竞争做出反应。
     */
    interface JobGraphListener {

        /**
         * Callback for {@link JobGraph} instances added by a different {@link JobGraphStore}
         * instance.
         * 由不同的 {@link JobGraphStore} 实例添加的 {@link JobGraph} 实例的回调。
         *
         * <p><strong>Important:</strong> It is possible to get false positives and be notified
         * about a job graph, which was added by this instance.
         * <strong>重要提示：</strong>可能会出现误报并收到有关此实例添加的作业图的通知。
         *
         * @param jobId The {@link JobID} of the added job graph
         */
        void onAddedJobGraph(JobID jobId);

        /**
         * Callback for {@link JobGraph} instances removed by a different {@link JobGraphStore}
         * instance.
         * 由不同的 {@link JobGraphStore} 实例删除的 {@link JobGraph} 实例的回调。
         *
         * @param jobId The {@link JobID} of the removed job graph
         */
        void onRemovedJobGraph(JobID jobId);
    }
}
