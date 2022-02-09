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

package org.apache.flink.runtime.query;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.jobmaster.KvStateLocationOracle;

import javax.annotation.Nullable;

/**
 * An interface for the Queryable State Client Proxy running on each Task Manager in the cluster.
 * 集群中每个任务管理器上运行的可查询状态客户端代理的接口。
 *
 * <p>This proxy is where the Queryable State Client (potentially running outside your Flink
 * cluster) connects to, and his responsibility is to forward the client's requests to the rest of
 * the entities participating in fetching the requested state, and running within the cluster.
 * 这个代理是 Queryable State Client（可能在 Flink 集群之外运行）连接的地方，
 * 他的职责是将客户端的请求转发给参与获取请求状态并在集群内运行的其他实体。
 *
 * <p>These are:
 *<ol>
 *     <li>{@link org.apache.flink.runtime.jobmaster.JobMaster Job Manager}，
 *     负责发送存储请求状态的{@link org.apache.flink.runtime.taskexecutor.TaskExecutor Task Manager}， 和
 *     <li>拥有状态本身的任务管理器。
 * </ol>
 * <ol>
 *   <li>the {@link org.apache.flink.runtime.jobmaster.JobMaster Job Manager}, which is responsible
 *       for sending the {@link org.apache.flink.runtime.taskexecutor.TaskExecutor Task Manager}
 *       storing the requested state, and
 *   <li>the Task Manager having the state itself.
 * </ol>
 */
public interface KvStateClientProxy extends KvStateServer {

    /**
     * Updates the active {@link org.apache.flink.runtime.jobmaster.JobMaster Job Manager} in case
     * of change.
     * 如果发生变化，更新活动的 {@link org.apache.flink.runtime.jobmaster.JobMaster Job Manager}。
     *
     * <p>This is useful in settings where high-availability is enabled and a failed Job Manager is
     * replaced by a new one.
     * 这在启用高可用性并将失败的作业管理器替换为新作业管理器的设置中很有用。
     *
     * <p><b>IMPORTANT: </b> this method may be called by a different thread than the {@link
     * #getKvStateLocationOracle(JobID)}.
     * <b>重要提示：</b>此方法可能由与 {@link #getKvStateLocationOracle(JobID)} 不同的线程调用。
     *
     * @param jobId identifying the job for which to update the key-value state location oracle
     * @param kvStateLocationOracle the key-value state location oracle for the given {@link JobID},
     *     or null if there is no oracle anymore
     */
    void updateKvStateLocationOracle(
            JobID jobId, @Nullable KvStateLocationOracle kvStateLocationOracle);

    /**
     * Retrieves a future containing the currently leading key-value state location oracle.
     * 检索包含当前领先的键值状态位置预言机的未来。
     *
     * <p><b>IMPORTANT: </b> this method may be called by a different thread than the {@link
     * #updateKvStateLocationOracle(JobID, KvStateLocationOracle)}.
     * <b>重要提示：</b>此方法可能由与 {@link #updateKvStateLocationOracle(JobID, KvStateLocationOracle)} 不同的线程调用。
     *
     * @param jobId identifying the job for which to request the key-value state location oracle
     * @return The key-value state location oracle for the given {@link JobID} or null if none.
     */
    @Nullable
    KvStateLocationOracle getKvStateLocationOracle(JobID jobId);
}
