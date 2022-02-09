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

package org.apache.flink.runtime.highavailability;

import org.apache.flink.api.common.JobID;

import java.io.IOException;

/**
 * A simple registry that tracks if a certain job is pending execution, running, or completed.
 * 一个简单的注册表，用于跟踪某个作业是否正在等待执行、正在运行或已完成。
 *
 * <p>This registry is used in highly-available setups with multiple master nodes, to determine
 * whether a new leader should attempt to recover a certain job (because the job is still running),
 * or whether the job has already finished successfully (in case of a finite job) and the leader has
 * only been granted leadership because the previous leader quit cleanly after the job was finished.
 * 此注册表用于具有多个主节点的高可用性设置，以确定新领导者是否应该尝试恢复某个作业（因为该作业仍在运行），
 * 或者该作业是否已经成功完成（在有限的情况下） 工作），而领导者之所以被授予领导权，是因为前任领导者在工作完成后干脆辞职了。
 *
 * <p>In addition, the registry can help to determine whether a newly assigned leader JobManager
 * should attempt reconciliation with running TaskManagers, or immediately schedule the job from the
 * latest checkpoint/savepoint.
 * 此外，注册表可以帮助确定新分配的领导者 JobManager 是否应该尝试与正在运行的 TaskManager 协调，
 * 或者立即从最新的检查点/保存点调度作业。
 */
public interface RunningJobsRegistry {

    /** The scheduling status of a job, as maintained by the {@code RunningJobsRegistry}.
     * 作业的调度状态，由 {@code RunningJobsRegistry} 维护。
     * */
    enum JobSchedulingStatus {

        /** Job has not been scheduled, yet. */
        PENDING,

        /** Job has been scheduled and is not yet finished. */
        RUNNING,

        /** Job has been finished, successfully or unsuccessfully. */
        DONE
    }

    // ------------------------------------------------------------------------

    /**
     * Marks a job as running. Requesting the job's status via the {@link
     * #getJobSchedulingStatus(JobID)} method will return {@link JobSchedulingStatus#RUNNING}.
     * 将作业标记为正在运行。 通过 {@link #getJobSchedulingStatus(JobID)} 方法请求作业的状态将返回 {@link JobSchedulingStatus#RUNNING}。
     *
     * @param jobID The id of the job.
     * @throws IOException Thrown when the communication with the highly-available storage or
     *     registry failed and could not be retried.
     */
    void setJobRunning(JobID jobID) throws IOException;

    /**
     * Marks a job as completed. Requesting the job's status via the {@link
     * #getJobSchedulingStatus(JobID)} method will return {@link JobSchedulingStatus#DONE}.
     *
     * @param jobID The id of the job.
     * @throws IOException Thrown when the communication with the highly-available storage or
     *     registry failed and could not be retried.
     */
    void setJobFinished(JobID jobID) throws IOException;

    /**
     * Gets the scheduling status of a job.
     *
     * @param jobID The id of the job to check.
     * @return The job scheduling status.
     * @throws IOException Thrown when the communication with the highly-available storage or
     *     registry failed and could not be retried.
     */
    JobSchedulingStatus getJobSchedulingStatus(JobID jobID) throws IOException;

    /**
     * Clear job state form the registry, usually called after job finish.
     * 从注册表中清除作业状态，通常在作业完成后调用。
     *
     * @param jobID The id of the job to check.
     * @throws IOException Thrown when the communication with the highly-available storage or
     *     registry failed and could not be retried.
     */
    void clearJob(JobID jobID) throws IOException;
}
