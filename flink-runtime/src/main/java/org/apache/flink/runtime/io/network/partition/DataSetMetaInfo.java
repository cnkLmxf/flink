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

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.util.Preconditions;

import java.util.Optional;

/** Container for meta-data of a data set.
 * 数据集元数据的容器。
 * */
public final class DataSetMetaInfo {
    private static final int UNKNOWN = -1;

    private final int numRegisteredPartitions;
    private final int numTotalPartitions;

    private DataSetMetaInfo(int numRegisteredPartitions, int numTotalPartitions) {
        this.numRegisteredPartitions = numRegisteredPartitions;
        this.numTotalPartitions = numTotalPartitions;
    }

    public Optional<Integer> getNumRegisteredPartitions() {
        return numRegisteredPartitions == UNKNOWN
                ? Optional.empty()
                : Optional.of(numRegisteredPartitions);
    }

    public int getNumTotalPartitions() {
        return numTotalPartitions;
    }

    static DataSetMetaInfo withoutNumRegisteredPartitions(int numTotalPartitions) {
        return new DataSetMetaInfo(UNKNOWN, numTotalPartitions);
    }

    @VisibleForTesting
    public static DataSetMetaInfo withNumRegisteredPartitions(
            int numRegisteredPartitions, int numTotalPartitions) {
        Preconditions.checkArgument(numRegisteredPartitions > 0);
        return new DataSetMetaInfo(numRegisteredPartitions, numTotalPartitions);
    }
}
