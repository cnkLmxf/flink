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

package org.apache.flink.runtime.jobmaster;

import org.apache.flink.util.AutoCloseableAsync;

import java.util.concurrent.CompletableFuture;

/** Interface which specifies the JobMaster service.
 * 指定 JobMaster 服务的接口。
 * */
public interface JobMasterService extends AutoCloseableAsync {

    /**
     * Get the {@link JobMasterGateway} belonging to this service.
     * 获取属于此服务的 {@link JobMasterGateway}。
     *
     * @return JobMasterGateway belonging to this service
     */
    JobMasterGateway getGateway();

    /**
     * Get the address of the JobMaster service under which it is reachable.
     * 获取其下可达的 JobMaster 服务的地址。
     *
     * @return Address of the JobMaster service
     */
    String getAddress();

    /**
     * Get the termination future of this job master service.
     * 获取此作业主服务的终止未来。
     *
     * @return future which is completed once the JobMasterService completes termination
     */
    CompletableFuture<Void> getTerminationFuture();
}
