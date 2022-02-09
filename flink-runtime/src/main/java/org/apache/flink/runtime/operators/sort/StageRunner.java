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

package org.apache.flink.runtime.operators.sort;

import org.apache.flink.util.MutableObjectIterator;

/**
 * An interface for different stages of the sorting process. Different stages can communicate via
 * the {@link StageMessageDispatcher}.
 * 用于分拣过程不同阶段的界面。 不同的阶段可以通过 {@link StageMessageDispatcher} 进行通信。
 */
public interface StageRunner extends AutoCloseable {
    /** Starts the stage. */
    void start();

    /** A marker interface for sending messages to different stages.
     * 用于将消息发送到不同阶段的标记接口。
     * */
    enum SortStage {
        READ,
        SORT,
        SPILL
    }

    /**
     * A dispatcher for inter-stage communication. It allows for returning a result to a {@link
     * Sorter} via {@link StageMessageDispatcher#sendResult(MutableObjectIterator)}
     * 用于阶段间通信的调度程序。
     * 它允许通过 {@link StageMessageDispatcher#sendResult(MutableObjectIterator)} 将结果返回给 {@link Sorter}
     */
    interface StageMessageDispatcher<E> extends AutoCloseable {
        /** Sends a message to the given stage. */
        void send(SortStage stage, CircularElement<E> element);

        /**
         * Retrieves and removes the head of the given queue, waiting if necessary until an element
         * becomes available.
         * 检索并删除给定队列的头部，如有必要，等待元素变为可用。
         *
         * @return the head of the queue
         */
        CircularElement<E> take(SortStage stage) throws InterruptedException;

        /**
         * Retrieves and removes the head of the given stage queue, or returns {@code null} if the
         * queue is empty.
         * 检索并删除给定阶段队列的头部，如果队列为空，则返回 {@code null}。
         *
         * @return the head of the queue, or {@code null} if the queue is empty
         */
        CircularElement<E> poll(SortStage stage);

        /** Sends a result to the corresponding {@link Sorter}.
         * 将结果发送到相应的 {@link Sorter}。
         * */
        void sendResult(MutableObjectIterator<E> result);
    }
}
