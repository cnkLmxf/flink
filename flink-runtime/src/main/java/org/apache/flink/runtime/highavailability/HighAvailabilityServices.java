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
import org.apache.flink.runtime.blob.BlobStore;
import org.apache.flink.runtime.checkpoint.CheckpointRecoveryFactory;
import org.apache.flink.runtime.jobmanager.JobGraphStore;
import org.apache.flink.runtime.leaderelection.LeaderElectionService;
import org.apache.flink.runtime.leaderretrieval.LeaderRetrievalService;

import java.io.IOException;
import java.util.UUID;

/**
 * The HighAvailabilityServices give access to all services needed for a highly-available setup. In
 * particular, the services provide access to highly available storage and registries, as well as
 * distributed counters and leader election.
 * HighAvailabilityServices 允许访问高可用性设置所需的所有服务。
 * 特别是，这些服务提供对高可用性存储和注册表的访问，以及分布式计数器和领导者选举。
 * <ul>
 *      <li>ResourceManager 领导选举和领导检索
 *      <li>JobManager 领导选举和领导检索
 *      <li>检查点元数据的持久性
 *      <li>注册最近完成的检查点
 *      <li>BLOB 存储的持久性
 *      <li>标记作业状态的注册表
 *      <li>RPC 端点的命名
 *</ul>
 * <ul>
 *   <li>ResourceManager leader election and leader retrieval
 *   <li>JobManager leader election and leader retrieval
 *   <li>Persistence for checkpoint metadata
 *   <li>Registering the latest completed checkpoint(s)
 *   <li>Persistence for the BLOB store
 *   <li>Registry that marks a job's status
 *   <li>Naming of RPC endpoints
 * </ul>
 */
public interface HighAvailabilityServices extends ClientHighAvailabilityServices {

    // ------------------------------------------------------------------------
    //  Constants
    // ------------------------------------------------------------------------

    /**
     * This UUID should be used when no proper leader election happens, but a simple pre-configured
     * leader is used. That is for example the case in non-highly-available standalone setups.
     * 当没有发生适当的领导者选举时，应该使用此 UUID，但使用简单的预配置领导者。 例如，在非高可用性独立设置中就是这种情况。
     */
    UUID DEFAULT_LEADER_ID = new UUID(0, 0);

    /**
     * This JobID should be used to identify the old JobManager when using the {@link
     * HighAvailabilityServices}. With the new mode every JobMaster will have a distinct JobID
     * assigned.
     * 在使用 {@link HighAvailabilityServices} 时，应使用此 JobID 来识别旧的 JobManager。
     * 在新模式下，每个 JobMaster 都将分配一个不同的 JobID。
     */
    JobID DEFAULT_JOB_ID = new JobID(0L, 0L);

    // ------------------------------------------------------------------------
    //  Services
    // ------------------------------------------------------------------------

    /** Gets the leader retriever for the cluster's resource manager.
     * 获取集群资源管理器的领导检索器。
     * */
    LeaderRetrievalService getResourceManagerLeaderRetriever();

    /**
     * Gets the leader retriever for the dispatcher. This leader retrieval service is not always
     * accessible.
     * 获取调度程序的领导检索器。 此领导者检索服务并不总是可访问的。
     */
    LeaderRetrievalService getDispatcherLeaderRetriever();

    /**
     * Gets the leader retriever for the job JobMaster which is responsible for the given job.
     * 获取负责给定作业的作业 JobMaster 的领导检索器。
     *
     * @param jobID The identifier of the job.
     * @return Leader retrieval service to retrieve the job manager for the given job
     * @deprecated This method should only be used by the legacy code where the JobManager acts as
     *     the master.
     */
    @Deprecated
    LeaderRetrievalService getJobManagerLeaderRetriever(JobID jobID);

    /**
     * Gets the leader retriever for the job JobMaster which is responsible for the given job.
     * 获取负责给定作业的作业 JobMaster 的领导检索器。
     *
     * @param jobID The identifier of the job.
     * @param defaultJobManagerAddress JobManager address which will be returned by a static leader
     *     retrieval service.
     * @return Leader retrieval service to retrieve the job manager for the given job
     */
    LeaderRetrievalService getJobManagerLeaderRetriever(
            JobID jobID, String defaultJobManagerAddress);

    /**
     * This retriever should no longer be used on the cluster side. The web monitor retriever is
     * only required on the client-side and we have a dedicated high-availability services for the
     * client, named {@link ClientHighAvailabilityServices}. See also FLINK-13750.
     * 此检索器不应再在集群端使用。
     * Web 监视器检索器仅在客户端需要，我们为客户端提供专用的高可用性服务，
     * 名为 {@link ClientHighAvailabilityServices}。 另请参阅 FLINK-13750。
     *
     * @return the leader retriever for web monitor
     * @deprecated just use {@link #getClusterRestEndpointLeaderRetriever()} instead of this method.
     */
    @Deprecated
    default LeaderRetrievalService getWebMonitorLeaderRetriever() {
        throw new UnsupportedOperationException(
                "getWebMonitorLeaderRetriever should no longer be used. Instead use "
                        + "#getClusterRestEndpointLeaderRetriever to instantiate the cluster "
                        + "rest endpoint leader retriever. If you called this method, then "
                        + "make sure that #getClusterRestEndpointLeaderRetriever has been "
                        + "implemented by your HighAvailabilityServices implementation.");
    }

    /**
     * Gets the leader election service for the cluster's resource manager.
     * 获取集群资源管理器的领导者选举服务。
     *
     * @return Leader election service for the resource manager leader election
     */
    LeaderElectionService getResourceManagerLeaderElectionService();

    /**
     * Gets the leader election service for the cluster's dispatcher.
     * 获取集群调度程序的领导选举服务。
     *
     * @return Leader election service for the dispatcher leader election
     */
    LeaderElectionService getDispatcherLeaderElectionService();

    /**
     * Gets the leader election service for the given job.
     * 获取给定作业的领导者选举服务。
     *
     * @param jobID The identifier of the job running the election.
     * @return Leader election service for the job manager leader election
     */
    LeaderElectionService getJobManagerLeaderElectionService(JobID jobID);

    /**
     * Gets the leader election service for the cluster's rest endpoint.
     * 获取集群的 REST 端点的领导者选举服务。
     *
     * @return the leader election service used by the cluster's rest endpoint
     * @deprecated Use {@link #getClusterRestEndpointLeaderElectionService()} instead.
     */
    @Deprecated
    default LeaderElectionService getWebMonitorLeaderElectionService() {
        throw new UnsupportedOperationException(
                "getWebMonitorLeaderElectionService should no longer be used. Instead use "
                        + "#getClusterRestEndpointLeaderElectionService to instantiate the cluster "
                        + "rest endpoint's leader election service. If you called this method, then "
                        + "make sure that #getClusterRestEndpointLeaderElectionService has been "
                        + "implemented by your HighAvailabilityServices implementation.");
    }

    /**
     * Gets the checkpoint recovery factory for the job manager.
     * 获取作业管理器的检查点恢复工厂。
     *
     * @return Checkpoint recovery factory
     */
    CheckpointRecoveryFactory getCheckpointRecoveryFactory();

    /**
     * Gets the submitted job graph store for the job manager.
     * 获取作业管理器的已提交作业图存储。
     *
     * @return Submitted job graph store
     * @throws Exception if the submitted job graph store could not be created
     */
    JobGraphStore getJobGraphStore() throws Exception;

    /**
     * Gets the registry that holds information about whether jobs are currently running.
     * 获取包含有关作业当前是否正在运行的信息的注册表。
     *
     * @return Running job registry to retrieve running jobs
     */
    RunningJobsRegistry getRunningJobsRegistry() throws Exception;

    /**
     * Creates the BLOB store in which BLOBs are stored in a highly-available fashion.
     * 创建以高可用性方式存储 BLOB 的 BLOB 存储。
     *
     * @return Blob store
     * @throws IOException if the blob store could not be created
     */
    BlobStore createBlobStore() throws IOException;

    /**
     * Gets the leader election service for the cluster's rest endpoint.
     * 获取集群的 REST 端点的领导者选举服务。
     *
     * @return the leader election service used by the cluster's rest endpoint
     */
    default LeaderElectionService getClusterRestEndpointLeaderElectionService() {
        // for backwards compatibility we delegate to getWebMonitorLeaderElectionService
        // all implementations of this interface should override
        // getClusterRestEndpointLeaderElectionService, though
        // 为了向后兼容，我们委托给 getWebMonitorLeaderElectionService
        // 这个接口的所有实现都应该覆盖 getClusterRestEndpointLeaderElectionService
        return getWebMonitorLeaderElectionService();
    }

    @Override
    default LeaderRetrievalService getClusterRestEndpointLeaderRetriever() {
        // for backwards compatibility we delegate to getWebMonitorLeaderRetriever
        // all implementations of this interface should override
        // getClusterRestEndpointLeaderRetriever, though
        // 为了向后兼容，我们委托给 getWebMonitorLeaderRetriever
        // 这个接口的所有实现都应该覆盖 getClusterRestEndpointLeaderRetriever，不过
        return getWebMonitorLeaderRetriever();
    }

    // ------------------------------------------------------------------------
    //  Shutdown and Cleanup
    // ------------------------------------------------------------------------

    /**
     * Closes the high availability services, releasing all resources.
     * 关闭高可用服务，释放所有资源。
     *
     * <p>This method <b>does not delete or clean up</b> any data stored in external stores (file
     * systems, ZooKeeper, etc). Another instance of the high availability services will be able to
     * recover the job.
     * 此方法<b>不会删除或清理</b>任何存储在外部存储（文件系统、ZooKeeper 等）中的数据。
     * 高可用性服务的另一个实例将能够恢复作业。
     *
     * <p>If an exception occurs during closing services, this method will attempt to continue
     * closing other services and report exceptions only after all services have been attempted to
     * be closed.
     * 如果在关闭服务的过程中发生异常，该方法将尝试继续关闭其他服务，并在所有服务都尝试关闭后才报告异常。
     *
     * @throws Exception Thrown, if an exception occurred while closing these services.
     */
    @Override
    void close() throws Exception;

    /**
     * Closes the high availability services (releasing all resources) and deletes all data stored
     * by these services in external stores.
     * 关闭高可用性服务（释放所有资源）并删除这些服务在外部存储中存储的所有数据。
     *
     * <p>After this method was called, the any job or session that was managed by these high
     * availability services will be unrecoverable.
     * 调用此方法后，由这些高可用性服务管理的任何作业或会话都将不可恢复。
     *
     * <p>If an exception occurs during cleanup, this method will attempt to continue the cleanup
     * and report exceptions only after all cleanup steps have been attempted.
     * 如果在清理过程中发生异常，此方法将尝试继续清理并仅在尝试所有清理步骤后报告异常。
     *
     * @throws Exception Thrown, if an exception occurred while closing these services or cleaning
     *     up data stored by them.
     */
    void closeAndCleanupAllData() throws Exception;

    /**
     * Deletes all data for specified job stored by these services in external stores.
     * 删除这些服务在外部存储中存储的指定作业的所有数据。
     *
     * @param jobID The identifier of the job to cleanup.
     * @throws Exception Thrown, if an exception occurred while cleaning data stored by them.
     */
    default void cleanupJobData(JobID jobID) throws Exception {}
}
