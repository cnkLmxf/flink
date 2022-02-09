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

import org.apache.flink.runtime.clusterframework.ApplicationStatus;
import org.apache.flink.runtime.clusterframework.TaskExecutorProcessSpec;
import org.apache.flink.runtime.clusterframework.types.ResourceIDRetrievable;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.concurrent.ScheduledExecutor;

import javax.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A {@link ResourceManagerDriver} is responsible for requesting and releasing resources from/to a
 * particular external resource manager.
 * {@link ResourceManagerDriver} 负责从/向特定外部资源管理器请求和释放资源。
 */
public interface ResourceManagerDriver<WorkerType extends ResourceIDRetrievable> {

    /**
     * Initialize the deployment specific components.
     * 初始化部署特定的组件。
     *
     * @param resourceEventHandler Handler that handles resource events.
     * @param mainThreadExecutor Rpc main thread executor.
     * @param ioExecutor IO executor.
     */
    void initialize(
            ResourceEventHandler<WorkerType> resourceEventHandler,
            ScheduledExecutor mainThreadExecutor,
            Executor ioExecutor)
            throws Exception;

    /**
     * Terminate the deployment specific components.
     * 终止部署特定的组件。
     *
     * @return A future that will be completed successfully when the driver is terminated, or
     *     exceptionally if it cannot be terminated.
     */
    CompletableFuture<Void> terminate();

    /**
     * This method can be overridden to add a (non-blocking) initialization routine to the
     * ResourceManager that will be called when leadership is granted but before leadership is
     * confirmed.
     * 可以重写此方法以向 ResourceManager 添加（非阻塞）初始化例程，该例程将在授予领导权但在确认领导权之前调用。
     *
     * @return Returns a {@code CompletableFuture} that completes when the computation is finished.
     */
    default CompletableFuture<Void> onGrantLeadership() {
        return FutureUtils.completedVoidFuture();
    }

    /**
     * This method can be overridden to add a (non-blocking) state clearing routine to the
     * ResourceManager that will be called when leadership is revoked.
     * 可以重写此方法以向 ResourceManager 添加（非阻塞）状态清除例程，该例程将在撤销领导权时调用。
     *
     * @return Returns a {@code CompletableFuture} that completes when the state clearing routine is
     *     finished.
     */
    default CompletableFuture<Void> onRevokeLeadership() {
        return FutureUtils.completedVoidFuture();
    }

    /**
     * The deployment specific code to deregister the application. This should report the
     * application's final status.
     * 用于取消注册应用程序的部署特定代码。 这应该报告应用程序的最终状态。
     *
     * <p>This method also needs to make sure all pending containers that are not registered yet are
     * returned.
     * 此方法还需要确保返回所有尚未注册的待处理容器。
     *
     * @param finalStatus The application status to report.
     * @param optionalDiagnostics A diagnostics message or {@code null}.
     * @throws Exception if the application could not be deregistered.
     */
    void deregisterApplication(ApplicationStatus finalStatus, @Nullable String optionalDiagnostics)
            throws Exception;

    /**
     * Request resource from the external resource manager.
     * 从外部资源管理器请求资源。
     *
     * <p>This method request a new resource from the external resource manager, and tries to launch
     * a task manager inside the allocated resource, with respect to the provided
     * taskExecutorProcessSpec. The returned future will be completed with a worker node in the
     * deployment specific type, or exceptionally if the allocation has failed.
     * 此方法从外部资源管理器请求新资源，并尝试根据提供的 taskExecutorProcessSpec 在分配的资源内启动任务管理器。
     * 返回的未来将使用部署特定类型的工作节点完成，或者在分配失败时例外。
     *
     * <p>Note: Completion of the returned future does not necessarily mean the success of resource
     * allocation and task manager launching. Allocation and launching failures can still happen
     * after the future completion. In such cases, {@link ResourceEventHandler#onWorkerTerminated}
     * will be called.
     * 注意：返回的future完成并不一定意味着资源分配和任务管理器启动成功。 未来完成后仍可能发生分配和启动失败。
     * 在这种情况下，将调用 {@link ResourceEventHandler#onWorkerTerminated}。
     *
     * <p>The future is guaranteed to be completed in the rpc main thread, before trying to launch
     * the task manager, thus before the task manager registration. It is also guaranteed that
     * {@link ResourceEventHandler#onWorkerTerminated} will not be called on the requested worker,
     * until the returned future is completed successfully.
     * 未来保证在 rpc 主线程中完成，在尝试启动任务管理器之前，因此在任务管理器注册之前。
     * 还保证不会在请求的工作人员上调用 {@link ResourceEventHandler#onWorkerTerminated}，直到成功完成返回的未来。
     *
     * @param taskExecutorProcessSpec Resource specification of the requested worker.
     * @return Future that wraps worker node of the requested resource, in the deployment specific
     *     type.
     */
    CompletableFuture<WorkerType> requestResource(TaskExecutorProcessSpec taskExecutorProcessSpec);

    /**
     * Release resource to the external resource manager.
     * 将资源释放给外部资源管理器。
     *
     * @param worker Worker node to be released, in the deployment specific type.
     */
    void releaseResource(WorkerType worker);
}
