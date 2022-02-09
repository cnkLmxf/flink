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

package org.apache.flink.runtime.blob;

import org.apache.flink.api.common.JobID;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A service to retrieve permanent binary large objects (BLOBs).
 * 检索永久二进制大对象 (BLOB) 的服务。
 *
 * <p>These may include per-job BLOBs that are covered by high-availability (HA) mode, e.g. a job's
 * JAR files or (parts of) an off-loaded {@link
 * org.apache.flink.runtime.deployment.TaskDeploymentDescriptor} or files in the {@link
 * org.apache.flink.api.common.cache.DistributedCache}.
 * 这些可能包括高可用性 (HA) 模式涵盖的每个作业 BLOB，例如 作业的 JAR 文件或（部分）卸载的
 * {@link org.apache.flink.runtime.deployment.TaskDeploymentDescriptor} 或
 * {@link org.apache.flink.api.common.cache.DistributedCache} 中的文件。
 */
public interface PermanentBlobService extends Closeable {

    /**
     * Returns the path to a local copy of the file associated with the provided job ID and blob
     * key.
     * 返回与提供的作业 ID 和 blob 键关联的文件的本地副本的路径。
     *
     * @param jobId ID of the job this blob belongs to
     * @param key BLOB key associated with the requested file
     * @return The path to the file.
     * @throws java.io.FileNotFoundException if the BLOB does not exist;
     * @throws IOException if any other error occurs when retrieving the file
     */
    File getFile(JobID jobId, PermanentBlobKey key) throws IOException;
}
