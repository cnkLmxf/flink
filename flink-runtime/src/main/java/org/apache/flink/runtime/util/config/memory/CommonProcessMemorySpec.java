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

import java.util.Objects;

/**
 * Common memory components of Flink processes (e.g. JM or TM).
 * Flink 进程的常用内存组件（例如 JM 或 TM）。
 *
 * <p>The process memory consists of the following components.
 * 进程存储器由以下组件组成。
 *
 * <ul>
 *   <li>Total Flink Memory
 *   <li>JVM Metaspace
 *   <li>JVM Overhead
 * </ul>
 *
 * Among all the components, We use the Total Process Memory to refer to all the memory components,
 * while the Total Flink Memory refers to all the internal components except JVM Metaspace and JVM
 * Overhead. The internal components of Total Flink Memory, represented by {@link FlinkMemory}, are
 * specific to concrete Flink process (e.g. JM or TM).
 * 在所有组件中，我们用 Total Process Memory 指代所有的内存组件，
 * 而 Total Flink Memory 指的是除 JVM Metaspace 和 JVM Overhead 之外的所有内部组件。
 * Total Flink Memory 的内部组件，由 {@link FlinkMemory} 表示，特定于具体的 Flink 进程（例如 JM 或 TM）。
 *
 * <p>The relationships of process memory components are shown below.
 * 进程内存组件的关系如下所示。
 *
 * <pre>
 *               ┌ ─ ─ Total Process Memory  ─ ─ ┐
 *               │┌─────────────────────────────┐│
 *                │      Total Flink Memory     │
 *               │└─────────────────────────────┘│
 *               │┌─────────────────────────────┐│
 *                │        JVM Metaspace        │
 *               │└─────────────────────────────┘│
 *                ┌─────────────────────────────┐
 *               ││        JVM Overhead         ││
 *                └─────────────────────────────┘
 *               └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 * </pre>
 */
public class CommonProcessMemorySpec<FM extends FlinkMemory> implements ProcessMemorySpec {
    private static final long serialVersionUID = 1L;

    private final FM flinkMemory;
    private final JvmMetaspaceAndOverhead jvmMetaspaceAndOverhead;

    protected CommonProcessMemorySpec(
            FM flinkMemory, JvmMetaspaceAndOverhead jvmMetaspaceAndOverhead) {
        this.flinkMemory = flinkMemory;
        this.jvmMetaspaceAndOverhead = jvmMetaspaceAndOverhead;
    }

    public FM getFlinkMemory() {
        return flinkMemory;
    }

    public JvmMetaspaceAndOverhead getJvmMetaspaceAndOverhead() {
        return jvmMetaspaceAndOverhead;
    }

    @Override
    public MemorySize getJvmHeapMemorySize() {
        return flinkMemory.getJvmHeapMemorySize();
    }

    @Override
    public MemorySize getJvmDirectMemorySize() {
        return flinkMemory.getJvmDirectMemorySize();
    }

    public MemorySize getJvmMetaspaceSize() {
        return getJvmMetaspaceAndOverhead().getMetaspace();
    }

    public MemorySize getJvmOverheadSize() {
        return getJvmMetaspaceAndOverhead().getOverhead();
    }

    @Override
    public MemorySize getTotalFlinkMemorySize() {
        return flinkMemory.getTotalFlinkMemorySize();
    }

    public MemorySize getTotalProcessMemorySize() {
        return flinkMemory
                .getTotalFlinkMemorySize()
                .add(getJvmMetaspaceSize())
                .add(getJvmOverheadSize());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj != null && getClass().equals(obj.getClass())) {
            CommonProcessMemorySpec<?> that = (CommonProcessMemorySpec<?>) obj;
            return Objects.equals(this.flinkMemory, that.flinkMemory)
                    && Objects.equals(this.jvmMetaspaceAndOverhead, that.jvmMetaspaceAndOverhead);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(flinkMemory, jvmMetaspaceAndOverhead);
    }
}
