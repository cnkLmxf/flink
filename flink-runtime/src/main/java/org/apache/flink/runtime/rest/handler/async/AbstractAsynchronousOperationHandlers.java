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

package org.apache.flink.runtime.rest.handler.async;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.rest.NotFoundException;
import org.apache.flink.runtime.rest.handler.AbstractRestHandler;
import org.apache.flink.runtime.rest.handler.HandlerRequest;
import org.apache.flink.runtime.rest.handler.RestHandlerException;
import org.apache.flink.runtime.rest.messages.EmptyRequestBody;
import org.apache.flink.runtime.rest.messages.MessageHeaders;
import org.apache.flink.runtime.rest.messages.MessageParameters;
import org.apache.flink.runtime.rest.messages.RequestBody;
import org.apache.flink.runtime.webmonitor.RestfulGateway;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;
import org.apache.flink.types.Either;

import javax.annotation.Nonnull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP handlers for asynchronous operations.
 *
 * <p>Some operations are long-running. To avoid blocking HTTP connections, these operations are
 * executed in two steps. First, an HTTP request is issued to trigger the operation asynchronously.
 * The request will be assigned a trigger id, which is returned in the response body. Next, the
 * returned id should be used to poll the status of the operation until it is finished.
 * 一些操作是长期运行的。 为避免阻塞 HTTP 连接，这些操作分两步执行。
 * 首先，发出 HTTP 请求以异步触发操作。 该请求将被分配一个触发器 ID，该 ID 在响应正文中返回。
 * 接下来，应该使用返回的 id 来轮询操作的状态，直到完成。
 *
 * <p>An operation is triggered by sending an HTTP {@code POST} request to a registered {@code url}.
 * The HTTP request may contain a JSON body to specify additional parameters, e.g.,
 * 通过向已注册的 {@code url} 发送 HTTP {@code POST} 请求来触发操作。
 * HTTP 请求可能包含 JSON 正文以指定其他参数，例如，
 *
 * <pre>
 * { "target-directory": "/tmp" }
 * </pre>
 *
 * <p>As written above, the response will contain a request id, e.g.,
 * 如上所述，响应将包含一个请求 id，例如，
 *
 * <pre>
 * { "request-id": "7d273f5a62eb4730b9dea8e833733c1e" }
 * </pre>
 *
 * <p>To poll for the status of an ongoing operation, an HTTP {@code GET} request is issued to
 * {@code url/:triggerid}. If the specified savepoint is still ongoing, the response will be
 * 为了轮询正在进行的操作的状态，向 {@code url/:triggerid} 发出 HTTP {@code GET} 请求。
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
 * <p>If the specified operation has completed, the status id will transition to {@code COMPLETED},
 * and the response will additionally contain information about the operation result:
 * 如果指定的操作已完成，状态 id 将转换为 {@code COMPLETED}，并且响应将额外包含有关操作结果的信息：
 *
 * <pre>
 * {
 *     "status": {
 *         "id": "COMPLETED"
 *     },
 *     "operation": {
 *         "result": "/tmp/savepoint-d9813b-8a68e674325b"
 *     }
 * }
 * </pre>
 *
 * @param <K> type of the operation key under which the result future is stored
 * @param <R> type of the operation result
 */
public abstract class AbstractAsynchronousOperationHandlers<K extends OperationKey, R> {

    private final CompletedOperationCache<K, R> completedOperationCache =
            new CompletedOperationCache<>();

    /**
     * Handler which is responsible for triggering an asynchronous operation. After the operation
     * has been triggered, it stores the result future in the {@link #completedOperationCache}.
     * 负责触发异步操作的处理程序。 触发操作后，它将结果 future 存储在 {@link #completedOperationCache} 中。
     *
     * @param <T> type of the gateway
     * @param <B> type of the request
     * @param <M> type of the message parameters
     */
    protected abstract class TriggerHandler<
                    T extends RestfulGateway, B extends RequestBody, M extends MessageParameters>
            extends AbstractRestHandler<T, B, TriggerResponse, M> {

        protected TriggerHandler(
                GatewayRetriever<? extends T> leaderRetriever,
                Time timeout,
                Map<String, String> responseHeaders,
                MessageHeaders<B, TriggerResponse, M> messageHeaders) {
            super(leaderRetriever, timeout, responseHeaders, messageHeaders);
        }

        @Override
        public CompletableFuture<TriggerResponse> handleRequest(
                @Nonnull HandlerRequest<B, M> request, @Nonnull T gateway)
                throws RestHandlerException {
            final CompletableFuture<R> resultFuture = triggerOperation(request, gateway);

            final K operationKey = createOperationKey(request);

            completedOperationCache.registerOngoingOperation(operationKey, resultFuture);

            return CompletableFuture.completedFuture(
                    new TriggerResponse(operationKey.getTriggerId()));
        }

        /**
         * Trigger the asynchronous operation and return its future result.
         * 触发异步操作并返回其未来结果。
         *
         * @param request with which the trigger handler has been called
         * @param gateway to the leader
         * @return Future result of the asynchronous operation
         * @throws RestHandlerException if something went wrong
         */
        protected abstract CompletableFuture<R> triggerOperation(
                HandlerRequest<B, M> request, T gateway) throws RestHandlerException;

        /**
         * Create the operation key under which the result future of the asynchronous operation will
         * be stored.
         * 创建将存储异步操作的结果future的操作键。
         *
         * @param request with which the trigger handler has been called.
         * @return Operation key under which the result future will be stored
         */
        protected abstract K createOperationKey(HandlerRequest<B, M> request);
    }

    /**
     * Handler which will be polled to retrieve the asynchronous operation's result. The handler
     * returns a {@link AsynchronousOperationResult} which indicates whether the operation is still
     * in progress or has completed. In case that the operation has been completed, the {@link
     * AsynchronousOperationResult} contains the operation result.
     * 处理程序将被轮询以检索异步操作的结果。
     * 处理程序返回一个 {@link AsynchronousOperationResult} 指示操作是仍在进行中还是已完成。
     * 如果操作已完成，则 {@link AsynchronousOperationResult} 包含操作结果。
     *
     * @param <T> type of the leader gateway
     * @param <V> type of the operation result
     * @param <M> type of the message headers
     */
    protected abstract class StatusHandler<T extends RestfulGateway, V, M extends MessageParameters>
            extends AbstractRestHandler<T, EmptyRequestBody, AsynchronousOperationResult<V>, M> {

        protected StatusHandler(
                GatewayRetriever<? extends T> leaderRetriever,
                Time timeout,
                Map<String, String> responseHeaders,
                MessageHeaders<EmptyRequestBody, AsynchronousOperationResult<V>, M>
                        messageHeaders) {
            super(leaderRetriever, timeout, responseHeaders, messageHeaders);
        }

        @Override
        public CompletableFuture<AsynchronousOperationResult<V>> handleRequest(
                @Nonnull HandlerRequest<EmptyRequestBody, M> request, @Nonnull T gateway)
                throws RestHandlerException {

            final K key = getOperationKey(request);

            final Either<Throwable, R> operationResultOrError;
            try {
                operationResultOrError = completedOperationCache.get(key);
            } catch (UnknownOperationKeyException e) {
                return FutureUtils.completedExceptionally(
                        new NotFoundException("Operation not found under key: " + key, e));
            }

            if (operationResultOrError != null) {
                if (operationResultOrError.isLeft()) {
                    return CompletableFuture.completedFuture(
                            AsynchronousOperationResult.completed(
                                    exceptionalOperationResultResponse(
                                            operationResultOrError.left())));
                } else {
                    return CompletableFuture.completedFuture(
                            AsynchronousOperationResult.completed(
                                    operationResultResponse(operationResultOrError.right())));
                }
            } else {
                return CompletableFuture.completedFuture(AsynchronousOperationResult.inProgress());
            }
        }

        @Override
        public CompletableFuture<Void> closeHandlerAsync() {
            return completedOperationCache.closeAsync();
        }

        /**
         * Extract the operation key under which the operation result future is stored.
         * 提取存储操作结果future的操作键。
         *
         * @param request with which the status handler has been called
         * @return Operation key under which the operation result future is stored
         */
        protected abstract K getOperationKey(HandlerRequest<EmptyRequestBody, M> request);

        /**
         * Create an exceptional operation result from the given {@link Throwable}. This method is
         * called if the asynchronous operation failed.
         * 从给定的 {@link Throwable} 创建异常操作结果。 如果异步操作失败，则调用此方法。
         *
         * @param throwable failure of the asynchronous operation
         * @return Exceptional operation result
         */
        protected abstract V exceptionalOperationResultResponse(Throwable throwable);

        /**
         * Create the operation result from the given value.
         * 根据给定值创建运算结果。
         *
         * @param operationResult of the asynchronous operation
         * @return Operation result
         */
        protected abstract V operationResultResponse(R operationResult);
    }
}
