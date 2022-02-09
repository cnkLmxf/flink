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

package org.apache.flink.runtime.rpc.messages;

import org.apache.flink.runtime.rpc.FencedMainThreadExecutable;
import org.apache.flink.util.Preconditions;

/**
 * Wrapper class indicating a message which is not required to match the fencing token as it is used
 * by the {@link FencedMainThreadExecutable} to run code in the main thread without a valid fencing
 * token. This is required for operations which are not scoped by the current fencing token (e.g.
 * leadership grants).
 * 包装类指示不需要匹配防护令牌的消息，
 * 因为 {@link FencedMainThreadExecutable} 使用它在没有有效防护令牌的情况下在主线程中运行代码。
 * 这对于不受当前围栏令牌范围的操作（例如领导权授予）是必需的。
 *
 * <p>IMPORTANT: This message is only intended to be send locally.
 * 重要提示：此消息仅用于在本地发送。
 *
 * @param <P> type of the payload
 */
public class UnfencedMessage<P> {
    private final P payload;

    public UnfencedMessage(P payload) {
        this.payload = Preconditions.checkNotNull(payload);
    }

    public P getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "UnfencedMessage(" + payload + ')';
    }
}
