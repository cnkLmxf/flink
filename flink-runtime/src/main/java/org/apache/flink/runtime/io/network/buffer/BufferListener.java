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

package org.apache.flink.runtime.io.network.buffer;

/**
 * Interface of the availability of buffers. Listeners can opt for a one-time only notification or
 * to be notified repeatedly.
 * 缓冲区可用性的接口。 听众可以选择一次性通知或重复通知。
 */
public interface BufferListener {

    /** Status of the notification result from the buffer listener.
     * 来自缓冲区侦听器的通知结果的状态。
     * */
    enum NotificationResult {
        BUFFER_NOT_USED(false, false),
        BUFFER_USED_NO_NEED_MORE(true, false),
        BUFFER_USED_NEED_MORE(true, true);

        private final boolean isBufferUsed;
        private final boolean needsMoreBuffers;

        NotificationResult(boolean isBufferUsed, boolean needsMoreBuffers) {
            this.isBufferUsed = isBufferUsed;
            this.needsMoreBuffers = needsMoreBuffers;
        }

        /**
         * Whether the notified buffer is accepted to use by the listener.
         * 通知的缓冲区是否被侦听器接受使用。
         *
         * @return <tt>true</tt> if the notified buffer is accepted.
         */
        boolean isBufferUsed() {
            return isBufferUsed;
        }

        /**
         * Whether the listener still needs more buffers to be notified.
         * 侦听器是否仍需要通知更多缓冲区。
         *
         * @return <tt>true</tt> if the listener is still waiting for more buffers.
         */
        boolean needsMoreBuffers() {
            return needsMoreBuffers;
        }
    }

    /**
     * Notification callback if a buffer is recycled and becomes available in buffer pool.
     * 如果缓冲区被回收并在缓冲池中可用，则通知回调。
     *
     * <p>Note: responsibility on recycling the given buffer is transferred to this implementation,
     * including any errors that lead to exceptions being thrown!
     * 注意：回收给定缓冲区的责任转移到此实现，包括导致抛出异常的任何错误！
     *
     * <p><strong>BEWARE:</strong> since this may be called from outside the thread that relies on
     * the listener's logic, any exception that occurs with this handler should be forwarded to the
     * responsible thread for handling and otherwise ignored in the processing of this method. The
     * buffer pool forwards any {@link Throwable} from here upwards to a potentially unrelated call
     * stack!
     * <strong>注意：</strong> 因为这可能是从依赖于侦听器逻辑的线程外部调用的，
     * 所以此处理程序发生的任何异常都应转发给负责的线程进行处理，否则在处理此方法时忽略 .
     * 缓冲池将任何 {@link Throwable} 从这里向上转发到可能不相关的调用堆栈！
     *
     * @param buffer buffer that becomes available in buffer pool.
     * @return NotificationResult if the listener wants to be notified next time.
     */
    NotificationResult notifyBufferAvailable(Buffer buffer);

    /** Notification callback if the buffer provider is destroyed.
     * 如果缓冲区提供程序被销毁，则通知回调。
     * */
    void notifyBufferDestroyed();
}
