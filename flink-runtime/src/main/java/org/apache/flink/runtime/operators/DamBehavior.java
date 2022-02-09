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

package org.apache.flink.runtime.operators;

/**
 * Enumeration for the different dam behaviors of an algorithm or a driver strategy. The dam
 * behavior describes whether records pass through the algorithm (no dam), whether all records are
 * collected before the first is returned (full dam) or whether a certain large amount is collected
 * before the algorithm returns records.
 * 枚举算法或驱动策略的不同大坝行为。 dam行为描述了记录是否通过算法（no dam），
 * 是否在第一个返回之前收集了所有记录（full dam），或者在算法返回记录之前是否收集了一定的大量记录。
 */
public enum DamBehavior {

    /**
     * Constant indicating that the algorithm does not come with any form of dam and records pass
     * through in a pipelined fashion.
     * 常量表示该算法不附带任何形式的大坝，并且记录以流水线方式通过。
     */
    PIPELINED,

    /**
     * Constant indicating that the algorithm materialized (some) records, but may return records
     * before all records are read.
     * 常量表示算法具体化（一些）记录，但可能在读取所有记录之前返回记录。
     */
    MATERIALIZING,

    /** Constant indicating that the algorithm collects all records before returning any.
     * 表示算法在返回任何记录之前收集所有记录的常量。
     * */
    FULL_DAM;

    /**
     * Checks whether this enumeration represents some form of materialization, either with a full
     * dam or without.
     * 检查此枚举是否代表某种形式的物化，有或没有满坝。
     *
     * @return True, if this enumeration constant represents a materializing behavior, false
     *     otherwise.
     */
    public boolean isMaterializing() {
        return this != PIPELINED;
    }
}
