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

package org.apache.flink.runtime.io.network.partition;

import java.io.File;
import java.io.IOException;

/** The type of the BoundedBlockingSubpartition. Also doubles as the factory.
 * BoundedBlockingSubpartition 的类型。 也兼作工厂。
 * */
public enum BoundedBlockingSubpartitionType {

    /**
     * A BoundedBlockingSubpartition type that simply stores the partition data in a file. Data is
     * eagerly spilled (written to disk) and readers directly read from the file.
     * 一种 BoundedBlockingSubpartition 类型，仅将分区数据存储在文件中。
     * 数据被急切地溢出（写入磁盘），读取器直接从文件中读取。
     */
    FILE {

        @Override
        public BoundedBlockingSubpartition create(
                int index,
                ResultPartition parent,
                File tempFile,
                int readBufferSize,
                boolean sslEnabled)
                throws IOException {

            return BoundedBlockingSubpartition.createWithFileChannel(
                    index, parent, tempFile, readBufferSize, sslEnabled);
        }
    },

    /**
     * A BoundedBlockingSubpartition type that stores the partition data in memory mapped file. Data
     * is written to and read from the mapped memory region. Disk spilling happens lazily, when the
     * OS swaps out the pages from the memory mapped file.
     * 一种 BoundedBlockingSubpartition 类型，将分区数据存储在内存映射文件中。
     * 数据被写入和读取映射的内存区域。 当操作系统从内存映射文件中换出页面时，磁盘溢出会延迟发生。
     */
    MMAP {

        @Override
        public BoundedBlockingSubpartition create(
                int index,
                ResultPartition parent,
                File tempFile,
                int readBufferSize,
                boolean sslEnabled)
                throws IOException {

            return BoundedBlockingSubpartition.createWithMemoryMappedFile(index, parent, tempFile);
        }
    },

    /**
     * Creates a BoundedBlockingSubpartition that stores the partition data in a file and memory
     * maps that file for reading. Data is eagerly spilled (written to disk) and then mapped into
     * memory. The main difference to the {@link BoundedBlockingSubpartitionType#MMAP} variant is
     * that no I/O is necessary when pages from the memory mapped file are evicted.
     * 创建一个 BoundedBlockingSubpartition，它将分区数据存储在一个文件中，并且内存映射该文件以供读取。
     * 数据被急切地溢出（写入磁盘），然后映射到内存中。
     * {@link BoundedBlockingSubpartitionType#MMAP} 变体的主要区别在于，当内存映射文件中的页面被驱逐时，不需要 I/O。
     */
    FILE_MMAP {

        @Override
        public BoundedBlockingSubpartition create(
                int index,
                ResultPartition parent,
                File tempFile,
                int readBufferSize,
                boolean sslEnabled)
                throws IOException {

            return BoundedBlockingSubpartition.createWithFileAndMemoryMappedReader(
                    index, parent, tempFile);
        }
    },

    /**
     * Selects the BoundedBlockingSubpartition type based on the current memory architecture. If
     * 64-bit, the type of {@link BoundedBlockingSubpartitionType#FILE_MMAP} is recommended.
     * Otherwise, the type of {@link BoundedBlockingSubpartitionType#FILE} is by default.
     * 根据当前内存架构选择 BoundedBlockingSubpartition 类型。
     * 如果是 64 位，则建议使用 {@link BoundedBlockingSubpartitionType#FILE_MMAP} 类型。
     * 否则，默认情况下 {@link BoundedBlockingSubpartitionType#FILE} 的类型。
     */
    AUTO {

        @Override
        public BoundedBlockingSubpartition create(
                int index,
                ResultPartition parent,
                File tempFile,
                int readBufferSize,
                boolean sslEnabled)
                throws IOException {

            return ResultPartitionFactory.getBoundedBlockingType()
                    .create(index, parent, tempFile, readBufferSize, sslEnabled);
        }
    };

    // ------------------------------------------------------------------------

    /** Creates BoundedBlockingSubpartition of this type.
     * 创建此类型的 BoundedBlockingSubpartition。
     * */
    public abstract BoundedBlockingSubpartition create(
            int index,
            ResultPartition parent,
            File tempFile,
            int readBufferSize,
            boolean sslEnabled)
            throws IOException;
}
