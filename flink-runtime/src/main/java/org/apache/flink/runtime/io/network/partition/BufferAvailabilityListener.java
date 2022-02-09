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

package org.apache.flink.runtime.io.network.partition;

/**
 * Listener interface implemented by consumers of {@link ResultSubpartitionView} that want to be
 * notified of availability of further buffers.
 * 由 {@link ResultSubpartitionView} 的使用者实现的侦听器接口，他们希望被通知更多缓冲区的可用性。
 */
public interface BufferAvailabilityListener {

    /** Called whenever there might be new data available.
     * 每当有新数据可用时调用。
     * */
    void notifyDataAvailable();

    /**
     * Called when the first priority event is added to the head of the buffer queue.
     * 当第一个优先级事件添加到缓冲区队列的头部时调用。
     *
     * @param prioritySequenceNumber the sequence number that identifies the priority buffer.
     */
    default void notifyPriorityEvent(int prioritySequenceNumber) {}
}
