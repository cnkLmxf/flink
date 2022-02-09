/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.state;

import org.apache.flink.annotation.Internal;

import java.util.concurrent.CompletableFuture;

/**
 * This interface represents the administrative interface to {@link TaskLocalStateStore}, that only
 * the owner of the object should see. All clients that want to use the service should only see the
 * {@link TaskLocalStateStore} interface.
 * 此接口代表 {@link TaskLocalStateStore} 的管理接口，只有对象的所有者才能看到。
 * 所有想要使用该服务的客户端应该只能看到 {@link TaskLocalStateStore} 接口。
 */
@Internal
public interface OwnedTaskLocalStateStore extends TaskLocalStateStore {

    /**
     * Disposes the task local state store. Disposal can happen asynchronously and completion is
     * signaled through the returned future.
     * 处理任务本地状态存储。 处置可以异步发生，完成通过返回的未来发出信号。
     */
    CompletableFuture<Void> dispose();
}
