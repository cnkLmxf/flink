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

package org.apache.flink.runtime.state;

import java.io.IOException;

/**
 * The CompletedCheckpointStorageLocation describes the storage aspect of a completed checkpoint. It
 * can be used to obtain access to the metadata, get a reference pointer to the checkpoint, or to
 * dispose the storage location.
 * CompletedCheckpointStorageLocation 描述已完成检查点的存储方面。
 * 它可用于获取对元数据的访问、获取指向检查点的引用指针或处置存储位置。
 */
public interface CompletedCheckpointStorageLocation extends java.io.Serializable {

    /**
     * Gets the external pointer to the checkpoint. The pointer can be used to resume a program from
     * the savepoint or checkpoint, and is typically passed as a command line argument, an HTTP
     * request parameter, or stored in a system like ZooKeeper.
     * 获取指向检查点的外部指针。 指针可用于从保存点或检查点恢复程序，通常作为命令行参数、HTTP 请求参数传递，
     * 或存储在 ZooKeeper 等系统中。
     */
    String getExternalPointer();

    /** Gets the state handle to the checkpoint's metadata.
     * 获取检查点元数据的状态句柄。
     * */
    StreamStateHandle getMetadataHandle();

    /**
     * Disposes the storage location. This method should be called after all state objects have been
     * released. It typically disposes the base structure of the checkpoint storage, like the
     * checkpoint directory.
     * 配置存储位置。 这个方法应该在所有状态对象被释放后调用。 它通常处理检查点存储的基本结构，如检查点目录。
     */
    void disposeStorageLocation() throws IOException;
}
