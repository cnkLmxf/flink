/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.resourcemanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.jobmaster.JobMasterId;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service which retrieves for a registered job the current job leader id (the leader id of the job
 * manager responsible for the job). The leader id will be exposed as a future via the {@link
 * #getLeaderId(JobID)}. The future will only be completed with an exception in case the service
 * will be stopped.
 * 为已注册的作业检索当前作业领导者 ID（负责该作业的作业经理的领导者 ID）的服务。
 * 领导者 ID 将通过 {@link #getLeaderId(JobID)} 公开为未来。 未来只会在服务停止的情况下完成。
 */
public interface JobLeaderIdService {

    /**
     * Start the service with the given job leader actions.
     * 使用给定的作业领导操作启动服务。
     *
     * @param initialJobLeaderIdActions to use for job leader id actions
     * @throws Exception which is thrown when clearing up old state
     */
    void start(JobLeaderIdActions initialJobLeaderIdActions) throws Exception;

    /**
     * Stop the service.
     *
     * @throws Exception which is thrown in case a retrieval service cannot be stopped properly
     */
    void stop() throws Exception;

    /**
     * Stop and clear the currently registered job leader id listeners.
     * 停止并清除当前注册的作业负责人 id 侦听器。
     *
     * @throws Exception which is thrown in case a retrieval service cannot be stopped properly
     */
    void clear() throws Exception;

    /**
     * Add a job to be monitored to retrieve the job leader id.
     * 添加要监控的作业以检索作业领导者 ID。
     *
     * @param jobId identifying the job to monitor
     * @throws Exception if the job could not be added to the service
     */
    void addJob(JobID jobId) throws Exception;

    /**
     * Remove the given job from being monitored by the service.
     * 从服务监视中删除给定的作业。
     *
     * @param jobId identifying the job to remove from monitor
     * @throws Exception if removing the job fails
     */
    void removeJob(JobID jobId) throws Exception;

    /**
     * Check whether the given job is being monitored or not.
     * 检查给定的作业是否正在被监视。
     *
     * @param jobId identifying the job
     * @return True if the job is being monitored; otherwise false
     */
    boolean containsJob(JobID jobId);

    /**
     * Get the leader's {@link JobMasterId} future for the given job.
     * 获取给定工作的领导者 {@link JobMasterId} 未来。
     *
     * @param jobId jobId specifying for which job to retrieve the {@link JobMasterId}
     * @return Future with the current leader's {@link JobMasterId}
     * @throws Exception if retrieving the {@link JobMasterId} cannot be started
     */
    CompletableFuture<JobMasterId> getLeaderId(JobID jobId) throws Exception;

    /**
     * Checks whether the given timeoutId for the given jobId is valid or not.
     * 检查给定 jobId 的给定 timeoutId 是否有效。
     *
     * @param jobId jobId identifying the job for which the timeout should be checked
     * @param timeoutId timeoutId specifying the timeout which should be checked for its validity
     * @return {@code true} if the timeout is valid; otherwise {@code false}
     */
    boolean isValidTimeout(JobID jobId, UUID timeoutId);
}
