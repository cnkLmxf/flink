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

package org.apache.flink.runtime.rest.handler.job.savepoints;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.runtime.rest.handler.HandlerRequest;
import org.apache.flink.runtime.rest.handler.RestHandlerException;
import org.apache.flink.runtime.rest.handler.async.AbstractAsynchronousOperationHandlers;
import org.apache.flink.runtime.rest.handler.async.TriggerResponse;
import org.apache.flink.runtime.rest.handler.job.AsynchronousJobOperationKey;
import org.apache.flink.runtime.rest.messages.EmptyRequestBody;
import org.apache.flink.runtime.rest.messages.JobIDPathParameter;
import org.apache.flink.runtime.rest.messages.MessageHeaders;
import org.apache.flink.runtime.rest.messages.RequestBody;
import org.apache.flink.runtime.rest.messages.TriggerId;
import org.apache.flink.runtime.rest.messages.TriggerIdPathParameter;
import org.apache.flink.runtime.rest.messages.job.savepoints.SavepointInfo;
import org.apache.flink.runtime.rest.messages.job.savepoints.SavepointStatusHeaders;
import org.apache.flink.runtime.rest.messages.job.savepoints.SavepointStatusMessageParameters;
import org.apache.flink.runtime.rest.messages.job.savepoints.SavepointTriggerHeaders;
import org.apache.flink.runtime.rest.messages.job.savepoints.SavepointTriggerMessageParameters;
import org.apache.flink.runtime.rest.messages.job.savepoints.SavepointTriggerRequestBody;
import org.apache.flink.runtime.rest.messages.job.savepoints.stop.StopWithSavepointRequestBody;
import org.apache.flink.runtime.rest.messages.job.savepoints.stop.StopWithSavepointTriggerHeaders;
import org.apache.flink.runtime.rpc.RpcUtils;
import org.apache.flink.runtime.webmonitor.RestfulGateway;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;
import org.apache.flink.util.SerializedThrowable;

import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponseStatus;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP handlers for asynchronous triggering of savepoints.
 * 用于异步触发保存点的 HTTP 处理程序。
 *
 * <p>Drawing savepoints is a potentially long-running operation. To avoid blocking HTTP
 * connections, savepoints must be drawn in two steps. First, an HTTP request is issued to trigger
 * the savepoint asynchronously. The request will be assigned a {@link TriggerId}, which is returned
 * in the response body. Next, the returned id should be used to poll the status of the savepoint
 * until it is finished.
 * 绘制保存点是一项可能需要长时间运行的操作。 为避免阻塞 HTTP 连接，必须分两步绘制保存点。
 * 首先，发出一个 HTTP 请求以异步触发保存点。 该请求将被分配一个 {@link TriggerId}，它在响应正文中返回。
 * 接下来，应该使用返回的 id 来轮询保存点的状态，直到完成。
 *
 * <p>A savepoint is triggered by sending an HTTP {@code POST} request to {@code
 * /jobs/:jobid/savepoints}. The HTTP request may contain a JSON body to specify the target
 * directory of the savepoint, e.g.,
 * 通过向 {@code /jobs/:jobid/savepoints} 发送 HTTP {@code POST} 请求来触发保存点。
 * HTTP 请求可能包含一个 JSON 正文来指定保存点的目标目录，例如，
 *
 * <pre>
 * { "target-directory": "/tmp" }
 * </pre>
 *
 * <p>If the body is omitted, or the field {@code target-property} is {@code null}, the default
 * savepoint directory as specified by {@link CheckpointingOptions#SAVEPOINT_DIRECTORY} will be
 * used. As written above, the response will contain a request id, e.g.,
 * 如果正文被省略，或者 {@code target-property} 字段为 {@code null}，
 * 则将使用 {@link CheckpointingOptions#SAVEPOINT_DIRECTORY} 指定的默认保存点目录。
 * 如上所述，响应将包含一个请求 id，例如，
 *
 * <pre>
 * { "request-id": "7d273f5a62eb4730b9dea8e833733c1e" }
 * </pre>
 *
 * <p>To poll for the status of an ongoing savepoint, an HTTP {@code GET} request is issued to
 * {@code /jobs/:jobid/savepoints/:savepointtriggerid}. If the specified savepoint is still ongoing,
 * the response will be
 * 为了轮询正在进行的保存点的状态，
 * 向 {@code /jobs/:jobid/savepoints/:savepointtriggerid} 发出 HTTP {@code GET} 请求。
 * 如果指定的保存点仍在进行中，则响应将是
 *
 * <pre>
 * {
 *     "status": {
 *         "id": "IN_PROGRESS"
 *     }
 * }
 * </pre>
 *
 * <p>If the specified savepoint has completed, the status id will transition to {@code COMPLETED},
 * and the response will additionally contain information about the savepoint, such as the location:
 * 如果指定的保存点已完成，状态 id 将转换为 {@code COMPLETED}，并且响应将另外包含有关保存点的信息，例如位置：
 *
 * <pre>
 * {
 *     "status": {
 *         "id": "COMPLETED"
 *     },
 *     "operation": {
 *         "location": "/tmp/savepoint-d9813b-8a68e674325b"
 *     }
 * }
 * </pre>
 */
public class SavepointHandlers
        extends AbstractAsynchronousOperationHandlers<AsynchronousJobOperationKey, String> {

    @Nullable private final String defaultSavepointDir;

    public SavepointHandlers(@Nullable final String defaultSavepointDir) {
        this.defaultSavepointDir = defaultSavepointDir;
    }

    private abstract class SavepointHandlerBase<T extends RequestBody>
            extends TriggerHandler<RestfulGateway, T, SavepointTriggerMessageParameters> {

        SavepointHandlerBase(
                final GatewayRetriever<? extends RestfulGateway> leaderRetriever,
                final Time timeout,
                Map<String, String> responseHeaders,
                final MessageHeaders<T, TriggerResponse, SavepointTriggerMessageParameters>
                        messageHeaders) {
            super(leaderRetriever, timeout, responseHeaders, messageHeaders);
        }

        @Override
        protected AsynchronousJobOperationKey createOperationKey(
                final HandlerRequest<T, SavepointTriggerMessageParameters> request) {
            final JobID jobId = request.getPathParameter(JobIDPathParameter.class);
            return AsynchronousJobOperationKey.of(new TriggerId(), jobId);
        }
    }

    /** HTTP handler to stop a job with a savepoint.
     * HTTP 处理程序使用保存点停止作业。
     * */
    public class StopWithSavepointHandler
            extends SavepointHandlerBase<StopWithSavepointRequestBody> {

        public StopWithSavepointHandler(
                final GatewayRetriever<? extends RestfulGateway> leaderRetriever,
                final Time timeout,
                final Map<String, String> responseHeaders) {
            super(
                    leaderRetriever,
                    timeout,
                    responseHeaders,
                    StopWithSavepointTriggerHeaders.getInstance());
        }

        @Override
        protected CompletableFuture<String> triggerOperation(
                final HandlerRequest<
                                StopWithSavepointRequestBody, SavepointTriggerMessageParameters>
                        request,
                final RestfulGateway gateway)
                throws RestHandlerException {

            final JobID jobId = request.getPathParameter(JobIDPathParameter.class);
            final String requestedTargetDirectory = request.getRequestBody().getTargetDirectory();

            if (requestedTargetDirectory == null && defaultSavepointDir == null) {
                throw new RestHandlerException(
                        String.format(
                                "Config key [%s] is not set. Property [%s] must be provided.",
                                CheckpointingOptions.SAVEPOINT_DIRECTORY.key(),
                                StopWithSavepointRequestBody.FIELD_NAME_TARGET_DIRECTORY),
                        HttpResponseStatus.BAD_REQUEST);
            }

            final boolean shouldDrain = request.getRequestBody().shouldDrain();
            final String targetDirectory =
                    requestedTargetDirectory != null
                            ? requestedTargetDirectory
                            : defaultSavepointDir;
            return gateway.stopWithSavepoint(
                    jobId, targetDirectory, shouldDrain, RpcUtils.INF_TIMEOUT);
        }
    }

    /** HTTP handler to trigger savepoints. */
    public class SavepointTriggerHandler extends SavepointHandlerBase<SavepointTriggerRequestBody> {

        public SavepointTriggerHandler(
                final GatewayRetriever<? extends RestfulGateway> leaderRetriever,
                final Time timeout,
                final Map<String, String> responseHeaders) {
            super(leaderRetriever, timeout, responseHeaders, SavepointTriggerHeaders.getInstance());
        }

        @Override
        protected CompletableFuture<String> triggerOperation(
                HandlerRequest<SavepointTriggerRequestBody, SavepointTriggerMessageParameters>
                        request,
                RestfulGateway gateway)
                throws RestHandlerException {
            final JobID jobId = request.getPathParameter(JobIDPathParameter.class);
            final String requestedTargetDirectory = request.getRequestBody().getTargetDirectory();

            if (requestedTargetDirectory == null && defaultSavepointDir == null) {
                throw new RestHandlerException(
                        String.format(
                                "Config key [%s] is not set. Property [%s] must be provided.",
                                CheckpointingOptions.SAVEPOINT_DIRECTORY.key(),
                                SavepointTriggerRequestBody.FIELD_NAME_TARGET_DIRECTORY),
                        HttpResponseStatus.BAD_REQUEST);
            }

            final boolean cancelJob = request.getRequestBody().isCancelJob();
            final String targetDirectory =
                    requestedTargetDirectory != null
                            ? requestedTargetDirectory
                            : defaultSavepointDir;
            return gateway.triggerSavepoint(
                    jobId, targetDirectory, cancelJob, RpcUtils.INF_TIMEOUT);
        }
    }

    /** HTTP handler to query for the status of the savepoint.
     * 用于查询保存点状态的 HTTP 处理程序。
     * */
    public class SavepointStatusHandler
            extends StatusHandler<RestfulGateway, SavepointInfo, SavepointStatusMessageParameters> {

        public SavepointStatusHandler(
                final GatewayRetriever<? extends RestfulGateway> leaderRetriever,
                final Time timeout,
                final Map<String, String> responseHeaders) {
            super(leaderRetriever, timeout, responseHeaders, SavepointStatusHeaders.getInstance());
        }

        @Override
        protected AsynchronousJobOperationKey getOperationKey(
                HandlerRequest<EmptyRequestBody, SavepointStatusMessageParameters> request) {
            final TriggerId triggerId = request.getPathParameter(TriggerIdPathParameter.class);
            final JobID jobId = request.getPathParameter(JobIDPathParameter.class);
            return AsynchronousJobOperationKey.of(triggerId, jobId);
        }

        @Override
        protected SavepointInfo exceptionalOperationResultResponse(Throwable throwable) {
            return new SavepointInfo(null, new SerializedThrowable(throwable));
        }

        @Override
        protected SavepointInfo operationResultResponse(String operationResult) {
            return new SavepointInfo(operationResult, null);
        }
    }
}
