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

package org.apache.flink.runtime.iterative.io;

import org.apache.flink.runtime.operators.hash.CompactingHashTable;
import org.apache.flink.util.Collector;

import java.io.IOException;

/**
 * A {@link Collector} to update the solution set of a workset iteration.
 * 用于更新工作集迭代的解决方案集的 {@link Collector}。
 *
 * <p>The records are written to a HashTable hash table to allow in-memory point updates.
 * 记录被写入 HashTable 哈希表以允许内存点更新。
 *
 * <p>Records will only be collected, if there is a match after probing the hash table. If the build
 * side iterator is already positioned for the update, use {@link
 * SolutionSetFastUpdateOutputCollector} to the save re-probing.
 * 只有在探测哈希表后存在匹配项时才会收集记录。
 * 如果构建端迭代器已为更新定位，请使用 {@link SolutionSetFastUpdateOutputCollector} 进行保存重新探测。
 *
 * @see SolutionSetFastUpdateOutputCollector
 */
public class SolutionSetUpdateOutputCollector<T> implements Collector<T> {

    private final Collector<T> delegate;

    private final CompactingHashTable<T> solutionSet;

    public SolutionSetUpdateOutputCollector(CompactingHashTable<T> solutionSet) {
        this(solutionSet, null);
    }

    public SolutionSetUpdateOutputCollector(
            CompactingHashTable<T> solutionSet, Collector<T> delegate) {
        this.solutionSet = solutionSet;
        this.delegate = delegate;
    }

    @Override
    public void collect(T record) {
        try {
            solutionSet.insertOrReplaceRecord(record);
            if (delegate != null) {
                delegate.collect(record);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }
}
