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

package org.apache.flink.runtime.registration;

import org.apache.flink.util.SerializedThrowable;

import java.io.Serializable;

/** Base class for responses given to registration attempts from {@link RetryingRegistration}.
 * 对来自 {@link RetryingRegistration} 的注册尝试的响应的基类。
 * */
public abstract class RegistrationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------------------------------

    /**
     * Base class for a successful registration. Concrete registration implementations will
     * typically extend this class to attach more information.
     * 成功注册的基类。 具体的注册实现通常会扩展此类以附加更多信息。
     */
    public static class Success extends RegistrationResponse {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return "Registration Successful";
        }
    }

    // ----------------------------------------------------------------------------

    /**
     * A registration failure.
     *
     * <p>A failure indicates a temporary problem which can be solved by retrying the connection
     * attempt. That's why the {@link RetryingRegistration} will retry the registration with the
     * target upon receiving a {@link Failure} response. Consequently, the target should answer with
     * a {@link Failure} if a temporary failure has occurred.
     * 失败表示临时问题，可以通过重试连接尝试来解决。
     * 这就是为什么 {@link RetryingRegistration} 将在收到 {@link Failure} 响应时重试与目标的注册。
     * 因此，如果发生临时故障，目标应以 {@link Failure} 进行回答。
     */
    public static final class Failure extends RegistrationResponse {
        private static final long serialVersionUID = 1L;

        /** The failure reason. */
        private final SerializedThrowable reason;

        /**
         * Creates a new failure message.
         * 创建新的失败消息。
         *
         * @param reason The reason for the failure.
         */
        public Failure(Throwable reason) {
            this.reason = new SerializedThrowable(reason);
        }

        /** Gets the reason for the failure.
         * 获取失败的原因。
         * */
        public SerializedThrowable getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "Registration Failure (" + reason + ')';
        }
    }

    // ----------------------------------------------------------------------------

    /**
     * A rejected (declined) registration.
     *
     * <p>A rejection indicates a permanent problem which prevents the registration between the
     * target and the caller which cannot be solved by retrying the connection. Consequently, the
     * {@link RetryingRegistration} will stop when it receives a {@link Rejection} response from the
     * target. Moreover, a target should respond with {@link Rejection} if it realizes that it
     * cannot work with the caller.
     * 拒绝表示一个永久性问题，该问题阻止了目标和调用者之间的注册，该问题无法通过重试连接来解决。
     * 因此，{@link RetryingRegistration} 将在收到来自目标的 {@link Rejection} 响应时停止。
     * 此外，如果目标意识到它不能与调用者一起工作，它应该以 {@link Rejection} 响应。
     */
    public static class Rejection extends RegistrationResponse {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return "Registration Rejected";
        }
    }
}
