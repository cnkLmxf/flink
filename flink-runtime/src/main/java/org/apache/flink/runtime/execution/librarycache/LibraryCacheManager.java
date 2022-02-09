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

package org.apache.flink.runtime.execution.librarycache;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.blob.PermanentBlobKey;
import org.apache.flink.util.UserCodeClassLoader;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;

/**
 * The LibraryCacheManager is responsible for creating and managing the user code class loaders.
 * LibraryCacheManager 负责创建和管理用户代码类加载器。
 *
 * <p>In order to obtain a user code class loader, one first needs to obtain a {@link
 * ClassLoaderLease} for a given {@link JobID}. At first, the {@link ClassLoaderLease} is
 * unresolved. In order to obtain the user class loader one needs to resolve it by specifying the
 * required jar files and class paths. The user code class loader for a job is valid as long as
 * there exists a valid {@link ClassLoaderLease}. A {@link ClassLoaderLease} becomes invalid once it
 * gets released.
 * 为了获得用户代码类加载器，首先需要获得给定 {@link JobID} 的 {@link ClassLoaderLease}。
 * 起初，{@link ClassLoaderLease} 未解决。 为了获得用户类加载器，需要通过指定所需的 jar 文件和类路径来解决它。
 * 只要存在有效的 {@link ClassLoaderLease}，作业的用户代码类加载器就有效。
 * {@link ClassLoaderLease} 一旦被释放就变得无效。
 */
public interface LibraryCacheManager {

    /**
     * Registers a new class loader lease for the given jobId. The user code class loader for this
     * job will be valid as long as there exists a valid lease for this job.
     * 为给定的 jobId 注册一个新的类加载器租约。 只要存在此作业的有效租约，此作业的用户代码类加载器就会有效。
     *
     * @param jobId jobId for which to register a new class loader lease
     * @return a new class loader lease for the given job
     */
    ClassLoaderLease registerClassLoaderLease(JobID jobId);

    /**
     * Shuts the library cache manager down. Thereby it will close all open {@link ClassLoaderLease}
     * and release all registered user code class loaders.
     * 关闭库缓存管理器。 因此它将关闭所有打开的 {@link ClassLoaderLease} 并释放所有注册的用户代码类加载器。
     */
    void shutdown();

    /** Handle to retrieve a user code class loader for the associated job.
     * 用于检索关联作业的用户代码类加载器的句柄。
     * */
    interface ClassLoaderHandle {

        /**
         * Gets or resolves the user code class loader for the associated job.
         * 获取或解析关联作业的用户代码类加载器。
         *
         * <p>In order to retrieve the user code class loader the caller has to specify the required
         * jars and class paths. Upon calling this method first for a job, it will make sure that
         * the required jars are present and potentially cache the created user code class loader.
         * Every subsequent call to this method, will ensure that created user code class loader can
         * fulfill the required jar files and class paths.
         * 为了检索用户代码类加载器，调用者必须指定所需的 jar 和类路径。
         * 在首次为作业调用此方法时，它将确保存在所需的 jar，并可能缓存创建的用户代码类加载器。
         * 每次后续调用此方法，将确保创建的用户代码类加载器能够满足所需的 jar 文件和类路径。
         *
         * @param requiredJarFiles requiredJarFiles the user code class loader needs to load
         * @param requiredClasspaths requiredClasspaths the user code class loader needs to be
         *     started with
         * @return the user code class loader fulfilling the requirements
         * @throws IOException if the required jar files cannot be downloaded
         * @throws IllegalStateException if the cached user code class loader does not fulfill the
         *     requirements
         */
        UserCodeClassLoader getOrResolveClassLoader(
                Collection<PermanentBlobKey> requiredJarFiles, Collection<URL> requiredClasspaths)
                throws IOException;
    }

    /** Lease which allows to signal when the user code class loader is no longer needed.
     * Lease 允许在不再需要用户代码类加载器时发出信号。
     * */
    interface ClassLoaderLease extends ClassLoaderHandle {

        /**
         * Releases the lease to the user code class loader for the associated job.
         * 将租约释放给关联作业的用户代码类加载器。
         *
         * <p>This method signals that the lease holder not longer needs the user code class loader
         * for the associated job. Once all leases for a job are released, the library cache manager
         * is allowed to release the associated user code class loader.
         * 此方法表示租赁持有者不再需要相关作业的用户代码类加载器。
         * 一旦一个作业的所有租约都被释放，库缓存管理器就被允许释放相关的用户代码类加载器。
         */
        void release();
    }
}
