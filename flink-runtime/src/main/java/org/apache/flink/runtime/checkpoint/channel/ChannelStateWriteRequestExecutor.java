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

package org.apache.flink.runtime.checkpoint.channel;

import java.io.Closeable;

/**
 * Executes {@link ChannelStateWriteRequest}s potentially asynchronously. An exception thrown during
 * the execution should be re-thrown on any next call.
 * 可能异步执行 {@link ChannelStateWriteRequest}。 执行期间抛出的异常应在任何下一次调用时重新抛出。
 */
interface ChannelStateWriteRequestExecutor extends Closeable {

    /** @throws IllegalStateException if called more than once or after {@link #close()}
     * 如果多次调用或在 {@link #close()} 之后调用 @throws IllegalStateException
     * */
    void start() throws IllegalStateException;

    /**
     * Send {@link ChannelStateWriteRequest} to this worker. If this method throws an exception then
     * client must {@link ChannelStateWriteRequest#cancel cancel} it.
     * 向该工作人员发送 {@link ChannelStateWriteRequest}。
     * 如果此方法抛出异常，则客户端必须 {@link ChannelStateWriteRequest#cancel cancel} 它。
     *
     * @throws IllegalStateException if worker is not running
     * @throws Exception if any exception occurred during processing this or other items previously
     */
    void submit(ChannelStateWriteRequest r) throws Exception;

    /**
     * Send {@link ChannelStateWriteRequest} to this worker to be processed first. If this method
     * throws an exception then client must {@link ChannelStateWriteRequest#cancel cancel} it.
     * 将 {@link ChannelStateWriteRequest} 发送给该工作人员以首先进行处理。
     * 如果此方法抛出异常，则客户端必须 {@link ChannelStateWriteRequest#cancel cancel} 它。
     *
     * @throws IllegalStateException if worker is not running
     * @throws Exception if any exception occurred during processing this or other items previously
     */
    void submitPriority(ChannelStateWriteRequest r) throws Exception;
}
