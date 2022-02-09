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

package org.apache.flink.runtime.resourcemanager.active;

import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.ResourceIDRetrievable;

import java.util.Collection;

/** Callback interfaces for handling resource events from external resource managers.
 * 用于处理来自外部资源管理器的资源事件的回调接口。
 * */
public interface ResourceEventHandler<WorkerType extends ResourceIDRetrievable> {

    /**
     * Notifies that workers of previous attempt have been recovered from the external resource
     * manager.
     * 通知先前尝试的工作人员已从外部资源管理器中恢复。
     *
     * @param recoveredWorkers Collection of worker nodes, in the deployment specific type.
     */
    void onPreviousAttemptWorkersRecovered(Collection<WorkerType> recoveredWorkers);

    /**
     * Notifies that the worker has been terminated.
     * 通知工作人员已被终止。
     *
     * <p>See also {@link ResourceManagerDriver#requestResource}.
     *
     * @param resourceId Identifier of the terminated worker.
     * @param diagnostics Diagnostic message about the worker termination.
     */
    void onWorkerTerminated(ResourceID resourceId, String diagnostics);

    /**
     * Notifies that an error has occurred that the process cannot proceed.
     * 通知已发生错误，进程无法继续。
     *
     * @param exception Exception that describes the error.
     */
    void onError(Throwable exception);
}
