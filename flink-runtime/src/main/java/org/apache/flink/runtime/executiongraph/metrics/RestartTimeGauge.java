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

package org.apache.flink.runtime.executiongraph.metrics;

import org.apache.flink.api.common.JobStatus;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.runtime.executiongraph.JobStatusProvider;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Gauge which returns the last restarting time.
 * 返回上次重新启动时间的仪表。
 *
 * <p>Restarting time is the time between {@link JobStatus#RESTARTING} and {@link
 * JobStatus#RUNNING}, or a terminal state if {@link JobStatus#RUNNING} was not reached.
 * 重新启动时间是 {@link JobStatus#RESTARTING} 和 {@link JobStatus#RUNNING} 之间的时间，
 * 如果未达到 {@link JobStatus#RUNNING}，则为终端状态。
 *
 * <p>If the job has not yet reached either of these states, then the time is measured since
 * reaching {@link JobStatus#RESTARTING}. If it is still the initial job execution, then the gauge
 * will return 0.
 * 如果作业尚未达到这些状态中的任何一个，则测量自达到 {@link JobStatus#RESTARTING} 以来的时间。
 * 如果仍然是初始作业执行，则仪表将返回 0。
 */
public class RestartTimeGauge implements Gauge<Long> {

    public static final String METRIC_NAME = "restartingTime";

    // ------------------------------------------------------------------------

    private final JobStatusProvider jobStatusProvider;

    public RestartTimeGauge(JobStatusProvider jobStatusProvider) {
        this.jobStatusProvider = checkNotNull(jobStatusProvider);
    }

    // ------------------------------------------------------------------------

    @Override
    public Long getValue() {
        final JobStatus status = jobStatusProvider.getState();

        final long restartingTimestamp = jobStatusProvider.getStatusTimestamp(JobStatus.RESTARTING);

        final long switchToRunningTimestamp;
        final long lastRestartTime;

        if (restartingTimestamp <= 0) {
            // we haven't yet restarted our job
            return 0L;
        } else if ((switchToRunningTimestamp =
                        jobStatusProvider.getStatusTimestamp(JobStatus.RUNNING))
                >= restartingTimestamp) {
            // we have transitioned to RUNNING since the last restart
            lastRestartTime = switchToRunningTimestamp - restartingTimestamp;
        } else if (status.isTerminalState()) {
            // since the last restart we've switched to a terminal state without touching
            // the RUNNING state (e.g. failing from RESTARTING)
            lastRestartTime = jobStatusProvider.getStatusTimestamp(status) - restartingTimestamp;
        } else {
            // we're still somewhere between RESTARTING and RUNNING
            lastRestartTime = System.currentTimeMillis() - restartingTimestamp;
        }

        // we guard this with 'Math.max' to avoid negative timestamps when clocks re-sync
        return Math.max(lastRestartTime, 0);
    }
}
