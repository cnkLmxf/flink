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

package org.apache.flink.runtime.dispatcher;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.clusterframework.ApplicationStatus;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.rpc.FencedRpcGateway;
import org.apache.flink.runtime.rpc.RpcTimeout;
import org.apache.flink.runtime.webmonitor.RestfulGateway;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** Gateway for the Dispatcher component.
 * gateway的作用是服务的管家，外围系统接收到命令通过gateway调用dispatcher系统，
 * gateway不是客户端的角色，而是站在服务端，声明服务端能提供的服务的角色。其方法调用后会进入到dispather的执行范畴
 * */
public interface DispatcherGateway extends FencedRpcGateway<DispatcherId>, RestfulGateway {

    /**
     * Submit a job to the dispatcher.
     * 向调度员提交工作。
     *
     * @param jobGraph JobGraph to submit
     * @param timeout RPC timeout
     * @return A future acknowledge if the submission succeeded
     */
    CompletableFuture<Acknowledge> submitJob(JobGraph jobGraph, @RpcTimeout Time timeout);

    /**
     * List the current set of submitted jobs.
     * 列出当前提交的作业集。
     *
     * @param timeout RPC timeout
     * @return A future collection of currently submitted jobs
     */
    CompletableFuture<Collection<JobID>> listJobs(@RpcTimeout Time timeout);

    /**
     * Returns the port of the blob server.
     * 返回 blob 服务器的端口。
     *
     * @param timeout of the operation
     * @return A future integer of the blob server port
     */
    CompletableFuture<Integer> getBlobServerPort(@RpcTimeout Time timeout);

    default CompletableFuture<Acknowledge> shutDownCluster(ApplicationStatus applicationStatus) {
        return shutDownCluster();
    }
}
