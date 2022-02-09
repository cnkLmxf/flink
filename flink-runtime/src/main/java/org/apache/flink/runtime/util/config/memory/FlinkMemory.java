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

package org.apache.flink.runtime.util.config.memory;

import org.apache.flink.configuration.MemorySize;

import java.io.Serializable;

/**
 * Memory components which constitute the Total Flink Memory.
 * 构成 Total Flink Memory 的内存组件。
 *
 * <p>The relationships of Flink JVM and rest memory components are shown below.
 * Flink JVM 和剩余内存组件的关系如下图所示。
 *
 * <pre>
 *               ┌ ─ ─  Total Flink Memory - ─ ─ ┐
 *                 ┌───────────────────────────┐
 *               | │       JVM Heap Memory     │ |
 *                 └───────────────────────────┘
 *               |┌ ─ ─ - - - Off-Heap  - - ─ ─ ┐|
 *                │┌───────────────────────────┐│
 *               │ │     JVM Direct Memory     │ │
 *                │└───────────────────────────┘│
 *               │ ┌───────────────────────────┐ │
 *                ││   Rest Off-Heap Memory    ││
 *               │ └───────────────────────────┘ │
 *                └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 *               └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 * </pre>
 *
 * <p>The JVM and rest memory components can consist of further concrete Flink memory components
 * depending on the process type. The Flink memory components can be derived from either its total
 * size or a subset of configured required fine-grained components. Check the implementations for
 * details about the concrete components.
 * JVM 和剩余内存组件可以由更多具体的 Flink 内存组件组成，具体取决于进程类型。
 * Flink 内存组件可以从其总大小或配置的所需细粒度组件的子集派生。 检查实现以获取有关具体组件的详细信息。
 */
public interface FlinkMemory extends Serializable {
    MemorySize getJvmHeapMemorySize();

    MemorySize getJvmDirectMemorySize();

    MemorySize getTotalFlinkMemorySize();
}
