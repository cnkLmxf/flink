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

package org.apache.flink.runtime.instance;

import org.apache.flink.runtime.util.Hardware;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/** A hardware description describes the resources available to a task manager.
 * 硬件描述描述了任务管理器可用的资源。
 * */
public final class HardwareDescription implements Serializable {

    private static final long serialVersionUID = 3380016608300325361L;

    public static final String FIELD_NAME_CPU_CORES = "cpuCores";

    public static final String FIELD_NAME_SIZE_PHYSICAL_MEMORY = "physicalMemory";

    public static final String FIELD_NAME_SIZE_JVM_HEAP = "freeMemory";

    public static final String FIELD_NAME_SIZE_MANAGED_MEMORY = "managedMemory";

    /** The number of CPU cores available to the JVM on the compute node.
     * 计算节点上 JVM 可用的 CPU 内核数。
     * */
    @JsonProperty(FIELD_NAME_CPU_CORES)
    private final int numberOfCPUCores;

    /** The size of physical memory in bytes available on the compute node.
     * 计算节点上可用的物理内存大小（以字节为单位）。
     * */
    @JsonProperty(FIELD_NAME_SIZE_PHYSICAL_MEMORY)
    private final long sizeOfPhysicalMemory;

    /** The size of the JVM heap memory
     * JVM堆内存的大小
     * */
    @JsonProperty(FIELD_NAME_SIZE_JVM_HEAP)
    private final long sizeOfJvmHeap;

    /** The size of the memory managed by the system for caching, hashing, sorting, ...
     * 系统管理的内存大小，用于缓存、散列、排序……
     * */
    @JsonProperty(FIELD_NAME_SIZE_MANAGED_MEMORY)
    private final long sizeOfManagedMemory;

    /**
     * Constructs a new hardware description object.
     * 构造一个新的硬件描述对象。
     *
     * @param numberOfCPUCores The number of CPU cores available to the JVM on the compute node.
     * @param sizeOfPhysicalMemory The size of physical memory in bytes available on the compute
     *     node.
     * @param sizeOfJvmHeap The size of the JVM heap memory.
     * @param sizeOfManagedMemory The size of the memory managed by the system for caching, hashing,
     *     sorting, ...
     */
    @JsonCreator
    public HardwareDescription(
            @JsonProperty(FIELD_NAME_CPU_CORES) int numberOfCPUCores,
            @JsonProperty(FIELD_NAME_SIZE_PHYSICAL_MEMORY) long sizeOfPhysicalMemory,
            @JsonProperty(FIELD_NAME_SIZE_JVM_HEAP) long sizeOfJvmHeap,
            @JsonProperty(FIELD_NAME_SIZE_MANAGED_MEMORY) long sizeOfManagedMemory) {
        this.numberOfCPUCores = numberOfCPUCores;
        this.sizeOfPhysicalMemory = sizeOfPhysicalMemory;
        this.sizeOfJvmHeap = sizeOfJvmHeap;
        this.sizeOfManagedMemory = sizeOfManagedMemory;
    }

    /**
     * Returns the number of CPU cores available to the JVM on the compute node.
     * 返回计算节点上 JVM 可用的 CPU 内核数。
     *
     * @return the number of CPU cores available to the JVM on the compute node
     */
    public int getNumberOfCPUCores() {
        return this.numberOfCPUCores;
    }

    /**
     * Returns the size of physical memory in bytes available on the compute node.
     * 返回计算节点上可用的物理内存大小（以字节为单位）。
     *
     * @return the size of physical memory in bytes available on the compute node
     */
    public long getSizeOfPhysicalMemory() {
        return this.sizeOfPhysicalMemory;
    }

    /**
     * Returns the size of the JVM heap memory
     * 返回 JVM 堆内存的大小
     *
     * @return The size of the JVM heap memory
     */
    public long getSizeOfJvmHeap() {
        return this.sizeOfJvmHeap;
    }

    /**
     * Returns the size of the memory managed by the system for caching, hashing, sorting, ...
     * 返回系统管理的内存大小，用于缓存、散列、排序……
     *
     * @return The size of the memory managed by the system.
     */
    public long getSizeOfManagedMemory() {
        return this.sizeOfManagedMemory;
    }

    // --------------------------------------------------------------------------------------------
    // Utils
    // --------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HardwareDescription that = (HardwareDescription) o;
        return numberOfCPUCores == that.numberOfCPUCores
                && sizeOfPhysicalMemory == that.sizeOfPhysicalMemory
                && sizeOfJvmHeap == that.sizeOfJvmHeap
                && sizeOfManagedMemory == that.sizeOfManagedMemory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                numberOfCPUCores, sizeOfPhysicalMemory, sizeOfJvmHeap, sizeOfManagedMemory);
    }

    @Override
    public String toString() {
        return String.format(
                "cores=%d, physMem=%d, heap=%d, managed=%d",
                numberOfCPUCores, sizeOfPhysicalMemory, sizeOfJvmHeap, sizeOfManagedMemory);
    }

    // --------------------------------------------------------------------------------------------
    // Factory
    // --------------------------------------------------------------------------------------------

    public static HardwareDescription extractFromSystem(long managedMemory) {
        final int numberOfCPUCores = Hardware.getNumberCPUCores();
        final long sizeOfJvmHeap = Runtime.getRuntime().maxMemory();
        final long sizeOfPhysicalMemory = Hardware.getSizeOfPhysicalMemory();

        return new HardwareDescription(
                numberOfCPUCores, sizeOfPhysicalMemory, sizeOfJvmHeap, managedMemory);
    }
}
