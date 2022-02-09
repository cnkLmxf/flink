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

package org.apache.flink.runtime.highavailability;

import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.blob.BlobStore;
import org.apache.flink.runtime.blob.BlobStoreService;
import org.apache.flink.runtime.checkpoint.CheckpointRecoveryFactory;
import org.apache.flink.runtime.jobmanager.JobGraphStore;
import org.apache.flink.runtime.leaderelection.LeaderElectionService;
import org.apache.flink.runtime.leaderretrieval.LeaderRetrievalService;
import org.apache.flink.util.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Abstract high availability services based on distributed system(e.g. Zookeeper, Kubernetes). It
 * will help with creating all the leader election/retrieval services and the cleanup. Please return
 * a proper leader name int the implementation of {@link #getLeaderNameForResourceManager}, {@link
 * #getLeaderNameForDispatcher}, {@link #getLeaderNameForJobManager}, {@link
 * #getLeaderNameForRestServer}. The returned leader name is the ConfigMap name in Kubernetes and
 * child path in Zookeeper.
 * 基于分布式系统的抽象高可用服务（例如 Zookeeper、Kubernetes）。
 * 它将有助于创建所有领导者选举/检索服务和清理。
 * 请在 {@link #getLeaderNameForResourceManager}、{@link #getLeaderNameForDispatcher}、
 * {@link #getLeaderNameForJobManager}、{@link #getLeaderNameForRestServer} 的实现中返回正确的领导者名称。
 * 返回的 leader 名称是 Kubernetes 中的 ConfigMap 名称和 Zookeeper 中的子路径。
 *
 * <p>{@link #close()} and {@link #closeAndCleanupAllData()} should be implemented to destroy the
 * resources.
 * 应该实现 {@link #close()} 和 {@link #closeAndCleanupAllData()} 以销毁资源。
 *
 * <p>The abstract class is also responsible for determining which component service should be
 * reused. For example, {@link #runningJobsRegistry} is created once and could be reused many times.
 * 抽象类还负责确定应该重用哪个组件服务。 例如，{@link #runningJobsRegistry} 创建一次，可以重复使用多次。
 */
public abstract class AbstractHaServices implements HighAvailabilityServices {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /** The executor to run external IO operations on.
     * 在其上运行外部 IO 操作的执行程序。
     * */
    protected final Executor ioExecutor;

    /** The runtime configuration. */
    protected final Configuration configuration;

    /** Store for arbitrary blobs.
     * 存储任意 blob。
     * */
    private final BlobStoreService blobStoreService;

    /** The distributed storage based running jobs registry.
     * 基于分布式存储的运行作业注册表。
     * */
    private RunningJobsRegistry runningJobsRegistry;

    public AbstractHaServices(
            Configuration config, Executor ioExecutor, BlobStoreService blobStoreService) {

        this.configuration = checkNotNull(config);
        this.ioExecutor = checkNotNull(ioExecutor);
        this.blobStoreService = checkNotNull(blobStoreService);
    }

    @Override
    public LeaderRetrievalService getResourceManagerLeaderRetriever() {
        return createLeaderRetrievalService(getLeaderNameForResourceManager());
    }

    @Override
    public LeaderRetrievalService getDispatcherLeaderRetriever() {
        return createLeaderRetrievalService(getLeaderNameForDispatcher());
    }

    @Override
    public LeaderRetrievalService getJobManagerLeaderRetriever(JobID jobID) {
        return createLeaderRetrievalService(getLeaderNameForJobManager(jobID));
    }

    @Override
    public LeaderRetrievalService getJobManagerLeaderRetriever(
            JobID jobID, String defaultJobManagerAddress) {
        return getJobManagerLeaderRetriever(jobID);
    }

    @Override
    public LeaderRetrievalService getClusterRestEndpointLeaderRetriever() {
        return createLeaderRetrievalService(getLeaderNameForRestServer());
    }

    @Override
    public LeaderElectionService getResourceManagerLeaderElectionService() {
        return createLeaderElectionService(getLeaderNameForResourceManager());
    }

    @Override
    public LeaderElectionService getDispatcherLeaderElectionService() {
        return createLeaderElectionService(getLeaderNameForDispatcher());
    }

    @Override
    public LeaderElectionService getJobManagerLeaderElectionService(JobID jobID) {
        return createLeaderElectionService(getLeaderNameForJobManager(jobID));
    }

    @Override
    public LeaderElectionService getClusterRestEndpointLeaderElectionService() {
        return createLeaderElectionService(getLeaderNameForRestServer());
    }

    @Override
    public CheckpointRecoveryFactory getCheckpointRecoveryFactory() {
        return createCheckpointRecoveryFactory();
    }

    @Override
    public JobGraphStore getJobGraphStore() throws Exception {
        return createJobGraphStore();
    }

    @Override
    public RunningJobsRegistry getRunningJobsRegistry() {
        if (runningJobsRegistry == null) {
            this.runningJobsRegistry = createRunningJobsRegistry();
        }
        return runningJobsRegistry;
    }

    @Override
    public BlobStore createBlobStore() {
        return blobStoreService;
    }

    @Override
    public void close() throws Exception {
        Throwable exception = null;

        try {
            blobStoreService.close();
        } catch (Throwable t) {
            exception = t;
        }

        try {
            internalClose();
        } catch (Throwable t) {
            exception = ExceptionUtils.firstOrSuppressed(t, exception);
        }

        if (exception != null) {
            ExceptionUtils.rethrowException(
                    exception, "Could not properly close the " + getClass().getSimpleName());
        }
    }

    @Override
    public void closeAndCleanupAllData() throws Exception {
        logger.info("Close and clean up all data for {}.", getClass().getSimpleName());

        Throwable exception = null;

        boolean deletedHAData = false;

        try {
            internalCleanup();
            deletedHAData = true;
        } catch (Exception t) {
            exception = t;
        }

        try {
            internalClose();
        } catch (Throwable t) {
            exception = ExceptionUtils.firstOrSuppressed(t, exception);
        }

        try {
            if (deletedHAData) {
                blobStoreService.closeAndCleanupAllData();
            } else {
                logger.info(
                        "Cannot delete HA blobs because we failed to delete the pointers in the HA store.");
                blobStoreService.close();
            }
        } catch (Throwable t) {
            exception = ExceptionUtils.firstOrSuppressed(t, exception);
        }

        if (exception != null) {
            ExceptionUtils.rethrowException(
                    exception,
                    "Could not properly close and clean up all data of high availability service.");
        }
        logger.info("Finished cleaning up the high availability data.");
    }

    @Override
    public void cleanupJobData(JobID jobID) throws Exception {
        logger.info("Clean up the high availability data for job {}.", jobID);
        internalCleanupJobData(jobID);
        logger.info("Finished cleaning up the high availability data for job {}.", jobID);
    }

    /**
     * Create leader election service with specified leaderName.
     * 创建具有指定leaderName 的leader 选举服务。
     *
     * @param leaderName ConfigMap name in Kubernetes or child node path in Zookeeper.
     * @return Return LeaderElectionService using Zookeeper or Kubernetes.
     */
    protected abstract LeaderElectionService createLeaderElectionService(String leaderName);

    /**
     * Create leader retrieval service with specified leaderName.
     * 创建具有指定leaderName 的leader 检索服务。
     *
     * @param leaderName ConfigMap name in Kubernetes or child node path in Zookeeper.
     * @return Return LeaderRetrievalService using Zookeeper or Kubernetes.
     */
    protected abstract LeaderRetrievalService createLeaderRetrievalService(String leaderName);

    /**
     * Create the checkpoint recovery factory for the job manager.
     * 为作业管理器创建检查点恢复工厂。
     *
     * @return Checkpoint recovery factory
     */
    protected abstract CheckpointRecoveryFactory createCheckpointRecoveryFactory();

    /**
     * Create the submitted job graph store for the job manager.
     * 为作业管理器创建提交的作业图存储。
     *
     * @return Submitted job graph store
     * @throws Exception if the submitted job graph store could not be created
     */
    protected abstract JobGraphStore createJobGraphStore() throws Exception;

    /**
     * Create the registry that holds information about whether jobs are currently running.
     * 创建包含有关作业当前是否正在运行的信息的注册表。
     *
     * @return Running job registry to retrieve running jobs
     */
    protected abstract RunningJobsRegistry createRunningJobsRegistry();

    /**
     * Closes the components which is used for external operations(e.g. Zookeeper Client, Kubernetes
     * Client).
     * 关闭用于外部操作的组件（例如 Zookeeper 客户端、Kubernetes 客户端）。
     */
    protected abstract void internalClose();

    /**
     * Clean up the meta data in the distributed system(e.g. Zookeeper, Kubernetes ConfigMap).
     * 清理分布式系统中的元数据（例如 Zookeeper、Kubernetes ConfigMap）。
     *
     * <p>If an exception occurs during internal cleanup, we will continue the cleanup in {@link
     * #closeAndCleanupAllData} and report exceptions only after all cleanup steps have been
     * attempted.
     * 如果在内部清理过程中发生异常，我们将在 {@link #closeAndCleanupAllData} 中继续清理，
     * 并且仅在尝试了所有清理步骤后才报告异常。
     *
     * @throws Exception when do the cleanup operation on external storage.
     */
    protected abstract void internalCleanup() throws Exception;

    /**
     * Clean up the meta data in the distributed system(e.g. Zookeeper, Kubernetes ConfigMap) for
     * the specified Job.
     * 为指定的 Job 清理分布式系统（例如 Zookeeper、Kubernetes ConfigMap）中的元数据。
     *
     * @param jobID The identifier of the job to cleanup.
     * @throws Exception when do the cleanup operation on external storage.
     */
    protected abstract void internalCleanupJobData(JobID jobID) throws Exception;

    /**
     * Get the leader name for ResourceManager.
     *
     * @return Return the ResourceManager leader name. It is ConfigMap name in Kubernetes or child
     *     node path in Zookeeper.
     *     返回 ResourceManager 领导者名称。 它是 Kubernetes 中的 ConfigMap 名称或 Zookeeper 中的子节点路径。
     */
    protected abstract String getLeaderNameForResourceManager();

    /**
     * Get the leader name for Dispatcher.
     *
     * @return Return the Dispatcher leader name. It is ConfigMap name in Kubernetes or child node
     *     path in Zookeeper.
     */
    protected abstract String getLeaderNameForDispatcher();

    /**
     * Get the leader name for specific JobManager.
     *
     * @param jobID job id
     * @return Return the JobManager leader name for specified job id. It is ConfigMap name in
     *     Kubernetes or child node path in Zookeeper.
     */
    protected abstract String getLeaderNameForJobManager(final JobID jobID);

    /**
     * Get the leader name for RestServer.
     *
     * @return Return the RestServer leader name. It is ConfigMap name in Kubernetes or child node
     *     path in Zookeeper.
     */
    protected abstract String getLeaderNameForRestServer();
}
