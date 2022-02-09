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

package org.apache.flink.runtime.jobmanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.jobgraph.JobGraph;

/** Allows to store and remove job graphs.
 * 允许存储和删除作业图。
 * */
public interface JobGraphWriter {
    /**
     * Adds the {@link JobGraph} instance.
     * 添加 {@link JobGraph} 实例。
     *
     * <p>If a job graph with the same {@link JobID} exists, it is replaced.
     * 如果存在具有相同 {@link JobID} 的作业图，则将其替换。
     */
    void putJobGraph(JobGraph jobGraph) throws Exception;

    /** Removes the {@link JobGraph} with the given {@link JobID} if it exists.
     * 删除具有给定 {@link JobID} 的 {@link JobGraph}（如果存在）。
     * */
    void removeJobGraph(JobID jobId) throws Exception;

    /**
     * Releases the locks on the specified {@link JobGraph}.
     * 释放指定 {@link JobGraph} 上的锁。
     *
     * <p>Releasing the locks allows that another instance can delete the job from the {@link
     * JobGraphStore}.
     * 释放锁允许另一个实例可以从 {@link JobGraphStore} 中删除作业。
     *
     * @param jobId specifying the job to release the locks for
     * @throws Exception if the locks cannot be released
     */
    void releaseJobGraph(JobID jobId) throws Exception;
}
