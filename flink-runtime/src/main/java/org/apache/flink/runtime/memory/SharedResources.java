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

package org.apache.flink.runtime.memory;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.util.function.LongFunctionWithException;

import javax.annotation.concurrent.GuardedBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.apache.flink.util.Preconditions.checkState;

/** A map that keeps track of acquired shared resources and handles their allocation disposal.
 * 跟踪获取的共享资源并处理其分配处置的地图
 * */
final class SharedResources {

    private final ReentrantLock lock = new ReentrantLock();

    @GuardedBy("lock")
    private final HashMap<String, LeasedResource<?>> reservedResources = new HashMap<>();

    /**
     * Gets the shared memory resource for the given owner and registers a lease. If the resource
     * does not yet exist, it will be created via the given initializer function.
     * 获取给定所有者的共享内存资源并注册租约。 如果资源尚不存在，它将通过给定的初始化函数创建。
     *
     * <p>The resource must be released when no longer used. That releases the lease. When all
     * leases are released, the resource is disposed.
     * 不再使用时必须释放资源。 这释放了租约。 当所有租约都被释放时，资源被释放。
     */
    <T extends AutoCloseable> ResourceAndSize<T> getOrAllocateSharedResource(
            String type,
            Object leaseHolder,
            LongFunctionWithException<T, Exception> initializer,
            long sizeForInitialization)
            throws Exception {

        // We could be stuck on this lock for a while, in cases where another initialization is
        // currently
        // happening and the initialization is expensive.
        // We lock interruptibly here to allow for faster exit in case of cancellation errors.
        // 我们可能会在这个锁上卡住一段时间，以防当前正在发生另一个初始化并且初始化很昂贵。
        // 我们在这里以中断方式锁定，以便在取消错误的情况下更快地退出。
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MemoryAllocationException("Interrupted while acquiring memory");
        }

        try {
            // we cannot use "computeIfAbsent()" here because the computing function may throw an
            // exception.
            @SuppressWarnings("unchecked")
            LeasedResource<T> resource = (LeasedResource<T>) reservedResources.get(type);
            if (resource == null) {
                resource = createResource(initializer, sizeForInitialization);
                reservedResources.put(type, resource);
            }

            resource.addLeaseHolder(leaseHolder);
            return resource;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases a lease (identified by the lease holder object) for the given type. If no further
     * leases exist, the resource is disposed.
     * 释放给定类型的租约（由租约持有者对象标识）。 如果不存在进一步的租约，则处置资源。
     */
    void release(String type, Object leaseHolder) throws Exception {
        release(type, leaseHolder, (value) -> {});
    }

    /**
     * Releases a lease (identified by the lease holder object) for the given type. If no further
     * leases exist, the resource is disposed.
     * 释放给定类型的租约（由租约持有者对象标识）。 如果不存在进一步的租约，则处置资源。
     *
     * <p>This method takes an additional hook that is called when the resource is disposed.
     * 此方法采用一个额外的钩子，在释放资源时调用该钩子。
     */
    void release(String type, Object leaseHolder, Consumer<Long> releaser) throws Exception {
        lock.lock();
        try {
            final LeasedResource<?> resource = reservedResources.get(type);
            if (resource == null) {
                return;
            }

            if (resource.removeLeaseHolder(leaseHolder)) {
                try {
                    reservedResources.remove(type);
                    resource.dispose();
                } finally {
                    releaser.accept(resource.size());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    int getNumResources() {
        return reservedResources.size();
    }

    private static <T extends AutoCloseable> LeasedResource<T> createResource(
            LongFunctionWithException<T, Exception> initializer, long size) throws Exception {

        final T resource = initializer.apply(size);
        return new LeasedResource<>(resource, size);
    }

    // ------------------------------------------------------------------------

    /** A resource handle with size. */
    interface ResourceAndSize<T extends AutoCloseable> {

        T resourceHandle();

        long size();
    }

    // ------------------------------------------------------------------------

    private static final class LeasedResource<T extends AutoCloseable>
            implements ResourceAndSize<T> {

        private final HashSet<Object> leaseHolders = new HashSet<>();

        private final T resourceHandle;

        private final long size;

        private boolean disposed;

        private LeasedResource(T resourceHandle, long size) {
            this.resourceHandle = resourceHandle;
            this.size = size;
        }

        public T resourceHandle() {
            return resourceHandle;
        }

        public long size() {
            return size;
        }

        void addLeaseHolder(Object leaseHolder) {
            checkState(!disposed);
            leaseHolders.add(leaseHolder);
        }

        boolean removeLeaseHolder(Object leaseHolder) {
            checkState(!disposed);
            leaseHolders.remove(leaseHolder);
            return leaseHolders.isEmpty();
        }

        void dispose() throws Exception {
            if (!disposed) {
                disposed = true;
                resourceHandle.close();
            }
        }
    }
}
