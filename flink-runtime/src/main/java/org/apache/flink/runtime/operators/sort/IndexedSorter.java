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

package org.apache.flink.runtime.operators.sort;

public interface IndexedSorter {

    /**
     * Sort the items accessed through the given IndexedSortable over the given range of logical
     * indices. From the perspective of the sort algorithm, each index between l (inclusive) and r
     * (exclusive) is an addressable entry.
     * 在给定的逻辑索引范围内对通过给定的 IndexedSortable 访问的项目进行排序。
     * 从排序算法的角度来看，l（包括）和r（不包括）之间的每个索引都是一个可寻址条目。
     *
     * @see IndexedSortable#compare
     * @see IndexedSortable#swap
     */
    void sort(IndexedSortable s, int l, int r);

    void sort(IndexedSortable s);
}
