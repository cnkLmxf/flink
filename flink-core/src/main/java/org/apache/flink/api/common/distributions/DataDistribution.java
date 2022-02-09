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

package org.apache.flink.api.common.distributions;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.core.io.IOReadableWritable;

import java.io.Serializable;

@PublicEvolving
public interface DataDistribution extends IOReadableWritable, Serializable {

    /**
     * Returns the i'th bucket's upper bound, given that the distribution is to be split into {@code
     * totalBuckets} buckets.
     * 返回第 i 个存储桶的上限，假设分布将被拆分为 {@code totalBuckets} 存储桶。
     *
     * <p>Assuming <i>n</i> buckets, let {@code B_i} be the result from calling {@code
     * getBucketBoundary(i, n)}, then the distribution will partition the data domain in the
     * following fashion:
     * 假设 <i>n</i> 个桶，让 {@code B_i} 是调用 {@code getBucketBoundary(i, n)} 的结果，那么分布将按以下方式划分数据域：
     *
     * <pre>
     * (-inf, B_1] (B_1, B_2] ... (B_n-2, B_n-1] (B_n-1, inf)
     * </pre>
     *
     * <p>Note: The last bucket's upper bound is actually discarded by many algorithms. The last
     * bucket is assumed to hold all values <i>v</i> such that {@code v > getBucketBoundary(n-1,
     * n)}, where <i>n</i> is the number of buckets.
     * 注意：最后一个bucket的上限实际上被很多算法丢弃了。
     * 假设最后一个存储桶包含所有值 <i>v</i> 使得 {@code v > getBucketBoundary(n-1, n)}，其中 <i>n</i> 是存储桶的数量。
     *
     * @param bucketNum The number of the bucket for which to get the upper bound.
     * @param totalNumBuckets The number of buckets to split the data into.
     * @return A record whose values act as bucket boundaries for the specified bucket.
     */
    Object[] getBucketBoundary(int bucketNum, int totalNumBuckets);

    /**
     * The number of fields in the (composite) key. This determines how many fields in the records
     * define the bucket. The number of fields must be the size of the array returned by the
     * function {@link #getBucketBoundary(int, int)}.
     * （复合）键中的字段数。 这决定了记录中有多少字段定义了存储桶。
     * 字段的数量必须是函数 {@link #getBucketBoundary(int, int)} 返回的数组的大小。
     *
     * @return The number of fields in the (composite) key.
     */
    int getNumberOfFields();

    /**
     * Gets the type of the key by which the dataSet is partitioned.
     * 获取对数据集进行分区所依据的键的类型。
     *
     * @return The type of the key by which the dataSet is partitioned.
     */
    TypeInformation[] getKeyTypes();
}
