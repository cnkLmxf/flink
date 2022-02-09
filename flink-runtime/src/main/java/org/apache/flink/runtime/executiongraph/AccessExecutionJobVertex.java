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
package org.apache.flink.runtime.executiongraph;

import org.apache.flink.runtime.accumulators.StringifiedAccumulatorResult;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.jobgraph.JobVertexID;

/**
 * Common interface for the runtime {@link ExecutionJobVertex} and {@link
 * ArchivedExecutionJobVertex}.
 * 运行时 {@link ExecutionJobVertex} 和 {@link ArchivedExecutionJobVertex} 的通用接口。
 */
public interface AccessExecutionJobVertex {
    /**
     * Returns the name for this job vertex.
     *
     * @return name for this job vertex.
     */
    String getName();

    /**
     * Returns the parallelism for this job vertex.
     *
     * @return parallelism for this job vertex.
     */
    int getParallelism();

    /**
     * Returns the max parallelism for this job vertex.
     *
     * @return max parallelism for this job vertex.
     */
    int getMaxParallelism();

    /**
     * Returns the resource profile for this job vertex.
     * 返回此作业顶点的资源配置文件。
     *
     * @return resource profile for this job vertex.
     */
    ResourceProfile getResourceProfile();

    /**
     * Returns the {@link JobVertexID} for this job vertex.
     *
     * @return JobVertexID for this job vertex.
     */
    JobVertexID getJobVertexId();

    /**
     * Returns all execution vertices for this job vertex.
     * 返回此作业顶点的所有执行顶点。
     *
     * @return all execution vertices for this job vertex
     */
    AccessExecutionVertex[] getTaskVertices();

    /**
     * Returns the aggregated {@link ExecutionState} for this job vertex.
     * 返回此作业顶点的聚合 {@link ExecutionState}。
     *
     * @return aggregated state for this job vertex
     */
    ExecutionState getAggregateState();

    /**
     * Returns the aggregated user-defined accumulators as strings.
     * 将聚合的用户定义累加器作为字符串返回。
     *
     * @return aggregated user-defined accumulators as strings.
     */
    StringifiedAccumulatorResult[] getAggregatedUserAccumulatorsStringified();
}
