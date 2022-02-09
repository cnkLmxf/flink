/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.resourcemanager.slotmanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.slots.ResourceRequirement;
import org.apache.flink.runtime.slots.ResourceRequirements;

import java.util.Collection;
import java.util.Map;

/** Tracks for each job how many resource are required/acquired.
 * 跟踪每个作业需要/获取多少资源。
 * */
public interface ResourceTracker {

    /**
     * Notifies the tracker about a new or updated {@link ResourceRequirements}.
     * 通知跟踪器有关新的或更新的 {@link ResourceRequirements}。
     *
     * @param jobId the job that that the resource requirements belongs to
     * @param resourceRequirements new resource requirements
     */
    void notifyResourceRequirements(
            JobID jobId, Collection<ResourceRequirement> resourceRequirements);

    /**
     * Notifies the tracker about the acquisition of a resource with the given resource profile, for
     * the given job.
     * 通知跟踪器有关给定作业获取具有给定资源配置文件的资源的信息。
     *
     * @param jobId the job that acquired the resource
     * @param resourceProfile profile of the resource
     */
    void notifyAcquiredResource(JobID jobId, ResourceProfile resourceProfile);

    /**
     * Notifies the tracker about the loss of a resource with the given resource profile, for the
     * given job.
     * 通知跟踪器有关给定作业的具有给定资源配置文件的资源的丢失。
     *
     * @param jobId the job that lost the resource
     * @param resourceProfile profile of the resource
     */
    void notifyLostResource(JobID jobId, ResourceProfile resourceProfile);

    /**
     * Returns a collection of {@link ResourceRequirements} that describe which resources the
     * corresponding job is missing.
     * 返回描述相应作业缺少哪些资源的 {@link ResourceRequirements} 集合。
     *
     * @return missing resources for each jobs
     */
    Map<JobID, Collection<ResourceRequirement>> getMissingResources();

    /**
     * Returns a collection of {@link ResourceRequirement}s that describe which resources have been
     * assigned to a job.
     * 返回描述已分配给作业的资源的 {@link ResourceRequirement} 集合。
     *
     * @param jobId job ID
     * @return required/exceeding resources for each jobs
     */
    Collection<ResourceRequirement> getAcquiredResources(JobID jobId);

    /** Removes all state from the tracker.
     * 从跟踪器中删除所有状态。
     * */
    void clear();
}
