/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.runtime.taskexecutor.partition.ClusterPartitionReport;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Utility for tracking and releasing partitions on the ResourceManager.
 * 用于在 ResourceManager 上跟踪和释放分区的实用程序。
 * */
public interface ResourceManagerPartitionTracker {

    /**
     * Processes {@link ClusterPartitionReport} of a task executor. Updates the tracking information
     * for the respective task executor. Any partition no longer being hosted on the task executor
     * is considered lost, corrupting the corresponding data set. For any such data set this method
     * issues partition release calls to all task executors that are hosting partitions of this data
     * set.
     * 处理任务执行器的 {@link ClusterPartitionReport}。 更新各个任务执行者的跟踪信息。
     * 不再托管在任务执行器上的任何分区都被视为丢失，从而破坏了相应的数据集。
     * 对于任何此类数据集，此方法向所有托管此数据集分区的任务执行器发出分区释放调用。
     *
     * @param taskExecutorId origin of the report
     * @param clusterPartitionReport partition report
     */
    void processTaskExecutorClusterPartitionReport(
            ResourceID taskExecutorId, ClusterPartitionReport clusterPartitionReport);

    /**
     * Processes the shutdown of task executor. Removes all tracking information for the given
     * executor, determines datasets that may be corrupted by the shutdown (and implied loss of
     * partitions). For any such data set this method issues partition release calls to all task
     * executors that are hosting partitions of this data set, and issues release calls.
     * 处理任务执行器的关闭。 删除给定执行程序的所有跟踪信息，确定可能因关闭而损坏的数据集（以及隐含的分区丢失）。
     * 对于任何此类数据集，此方法向所有托管此数据集分区的任务执行器发出分区释放调用，并发出释放调用。
     *
     * @param taskExecutorId task executor that shut down
     */
    void processTaskExecutorShutdown(ResourceID taskExecutorId);

    /**
     * Issues a release calls to all task executors that are hosting partitions of the given data
     * set.
     * 向所有托管给定数据集分区的任务执行器发出发布调用。
     *
     * @param dataSetId data set to release
     */
    CompletableFuture<Void> releaseClusterPartitions(IntermediateDataSetID dataSetId);

    /**
     * Returns all data sets for which partitions are being tracked.
     * 返回正在跟踪分区的所有数据集。
     *
     * @return tracked datasets
     */
    Map<IntermediateDataSetID, DataSetMetaInfo> listDataSets();
}
