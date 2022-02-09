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

import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Interface for components that manage cluster partitions.
 * 管理集群分区的组件接口。
 * */
public interface ClusterPartitionManager {

    /**
     * Returns all datasets for which partitions are being tracked.
     * 返回正在跟踪其分区的所有数据集。
     *
     * @return tracked datasets
     */
    CompletableFuture<Map<IntermediateDataSetID, DataSetMetaInfo>> listDataSets();

    /**
     * Releases all partitions associated with the given dataset.
     * 释放与给定数据集关联的所有分区。
     *
     * @param dataSetToRelease dataset for which all associated partitions should be released
     * @return future that is completed once all partitions have been released
     */
    CompletableFuture<Void> releaseClusterPartitions(IntermediateDataSetID dataSetToRelease);
}
