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
 *
 */

package org.apache.flink.api.connector.sink;

import org.apache.flink.annotation.Experimental;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.metrics.MetricGroup;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * This interface lets the sink developer build a simple sink topology, which could guarantee the
 * exactly once semantics in both batch and stream execution mode if there is a {@link Committer} or
 * {@link GlobalCommitter}. 1. The {@link SinkWriter} is responsible for producing the committable.
 * 2. The {@link Committer} is responsible for committing a single committable. 3. The {@link
 * GlobalCommitter} is responsible for committing an aggregated committable, which we call the
 * global committable. The {@link GlobalCommitter} is always executed with a parallelism of 1. Note:
 * Developers need to ensure the idempotence of {@link Committer} and {@link GlobalCommitter}.
 * 这个接口让 sink 开发者可以构建一个简单的 sink 拓扑，如果有 {@link Committer} 或 {@link GlobalCommitter}，
 * 它可以保证批处理和流执行模式下的恰好一次语义。 1. {@link SinkWriter} 负责生成可提交。
 *   2. {@link Committer} 负责提交单个可提交。
 *   3. {@link GlobalCommitter} 负责提交一个聚合的可提交表，我们称之为全局可提交表。
 *   {@link GlobalCommitter} 总是以 1 的并行度执行。
 *   注意：开发者需要保证 {@link Committer} 和 {@link GlobalCommitter} 的幂等性。
 *
 * @param <InputT> The type of the sink's input
 * @param <CommT> The type of information needed to commit data staged by the sink
 * @param <WriterStateT> The type of the sink writer's state
 * @param <GlobalCommT> The type of the aggregated committable
 */
@Experimental
public interface Sink<InputT, CommT, WriterStateT, GlobalCommT> extends Serializable {

    /**
     * Create a {@link SinkWriter}.
     *
     * @param context the runtime context.
     * @param states the writer's state.
     * @return A sink writer.
     * @throws IOException if fail to create a writer.
     */
    SinkWriter<InputT, CommT, WriterStateT> createWriter(
            InitContext context, List<WriterStateT> states) throws IOException;

    /**
     * Creates a {@link Committer}.
     *
     * @return A committer.
     * @throws IOException if fail to create a committer.
     */
    Optional<Committer<CommT>> createCommitter() throws IOException;

    /**
     * Creates a {@link GlobalCommitter}.
     *
     * @return A global committer.
     * @throws IOException if fail to create a global committer.
     */
    Optional<GlobalCommitter<CommT, GlobalCommT>> createGlobalCommitter() throws IOException;

    /** Returns the serializer of the committable type. */
    Optional<SimpleVersionedSerializer<CommT>> getCommittableSerializer();

    /** Returns the serializer of the aggregated committable type. */
    Optional<SimpleVersionedSerializer<GlobalCommT>> getGlobalCommittableSerializer();

    /** Return the serializer of the writer's state type. */
    Optional<SimpleVersionedSerializer<WriterStateT>> getWriterStateSerializer();

    /** The interface exposes some runtime info for creating a {@link SinkWriter}.
     * 该接口公开了一些用于创建 {@link SinkWriter} 的运行时信息。
     * */
    interface InitContext {

        /**
         * Returns a {@link ProcessingTimeService} that can be used to get the current time and
         * register timers.
         * 返回可用于获取当前时间和注册计时器的 {@link ProcessingTimeService}。
         */
        ProcessingTimeService getProcessingTimeService();

        /** @return The id of task where the writer is. */
        int getSubtaskId();

        /** @return The metric group this writer belongs to. */
        MetricGroup metricGroup();
    }

    /**
     * A service that allows to get the current processing time and register timers that will
     * execute the given {@link ProcessingTimeCallback} when firing.
     * 允许获取当前处理时间并注册将在触发时执行给定 {@link ProcessingTimeCallback} 的计时器的服务。
     */
    interface ProcessingTimeService {

        /** Returns the current processing time. */
        long getCurrentProcessingTime();

        /**
         * Invokes the given callback at the given timestamp.
         * 在给定的时间戳调用给定的回调。
         *
         * @param time Time when the callback is invoked at
         * @param processingTimerCallback The callback to be invoked.
         */
        void registerProcessingTimer(long time, ProcessingTimeCallback processingTimerCallback);

        /**
         * A callback that can be registered via {@link #registerProcessingTimer(long,
         * ProcessingTimeCallback)}.
         * 可以通过 {@link #registerProcessingTimer(long, ProcessingTimeCallback)} 注册的回调。
         */
        interface ProcessingTimeCallback {

            /**
             * This method is invoked with the time which the callback register for.
             * 使用回调注册的时间调用此方法。
             *
             * @param time The time this callback was registered for.
             */
            void onProcessingTime(long time) throws IOException;
        }
    }
}
