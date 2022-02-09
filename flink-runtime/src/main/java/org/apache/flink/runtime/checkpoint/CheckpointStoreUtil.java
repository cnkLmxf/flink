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

package org.apache.flink.runtime.checkpoint;

/**
 * {@link CompletedCheckpointStore} utility interfaces. For example, convert a name(e.g. ZooKeeper
 * path, key name in Kubernetes ConfigMap) to checkpoint id in {@link Long} format, or vice versa.
 * {@link CompletedCheckpointStore} 实用程序接口。
 * 例如，将名称（例如 ZooKeeper 路径、Kubernetes ConfigMap 中的键名）转换为 {@link Long} 格式的检查点 id，反之亦然。
 */
public interface CheckpointStoreUtil {

    long INVALID_CHECKPOINT_ID = -1L;

    /**
     * Get the name in external storage from checkpoint id.
     * 从检查点 id 获取外部存储中的名称。
     *
     * @param checkpointId checkpoint id
     * @return Key name in ConfigMap or child path name in ZooKeeper
     */
    String checkpointIDToName(long checkpointId);

    /**
     * Get the checkpoint id from name.
     * 从名称中获取检查点 ID。
     *
     * @param name Key name in ConfigMap or child path name in ZooKeeper
     * @return parsed checkpoint id. Or {@link #INVALID_CHECKPOINT_ID} when parsing failed.
     */
    long nameToCheckpointID(String name);
}
