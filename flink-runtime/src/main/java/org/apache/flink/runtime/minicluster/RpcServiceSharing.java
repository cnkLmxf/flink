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

package org.apache.flink.runtime.minicluster;

/**
 * Enum which defines whether the mini cluster components use a shared RpcService or whether every
 * component gets its own dedicated RpcService started.
 * 枚举，它定义了迷你集群组件是使用共享的 RpcService 还是每个组件都启动了自己的专用 RpcService。
 */
public enum RpcServiceSharing {
    // 单个共享 rpc 服务
    SHARED, // a single shared rpc service
    // 每个组件都有自己的专用 rpc 服务
    DEDICATED // every component gets his own dedicated rpc service
}
