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

package org.apache.flink.runtime.resourcemanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.jobmaster.JobMasterId;

import java.util.UUID;

/** Interface for actions called by the {@link DefaultJobLeaderIdService}.
 * {@link DefaultJobLeaderIdService} 调用的操作接口。
 * */
public interface JobLeaderIdActions {

    /**
     * Callback when a monitored job leader lost its leadership.
     * 当受监控的工作领导者失去领导权时的回调。
     *
     * @param jobId identifying the job whose leader lost leadership
     * @param oldJobMasterId of the job manager which lost leadership
     */
    void jobLeaderLostLeadership(JobID jobId, JobMasterId oldJobMasterId);

    /**
     * Notify a job timeout. The job is identified by the given JobID. In order to check for the
     * validity of the timeout the timeout id of the triggered timeout is provided.
     * 通知作业超时。 作业由给定的 JobID 标识。 为了检查超时的有效性，提供了触发超时的超时 ID。
     *
     * @param jobId JobID which identifies the timed out job
     * @param timeoutId Id of the calling timeout to differentiate valid from invalid timeouts
     */
    void notifyJobTimeout(JobID jobId, UUID timeoutId);

    /**
     * Callback to report occurring errors.
     * 回调以报告发生的错误。
     *
     * @param error which has occurred
     */
    void handleError(Throwable error);
}
