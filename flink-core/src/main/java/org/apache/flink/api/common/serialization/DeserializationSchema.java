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

package org.apache.flink.api.common.serialization;

import org.apache.flink.annotation.Public;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.util.Collector;
import org.apache.flink.util.UserCodeClassLoader;

import java.io.IOException;
import java.io.Serializable;

/**
 * The deserialization schema describes how to turn the byte messages delivered by certain data
 * sources (for example Apache Kafka) into data types (Java/Scala objects) that are processed by
 * Flink.
 * 反序列化模式描述了如何将某些数据源（例如 Apache Kafka）传递的字节消息转换为 Flink 处理的数据类型（Java/Scala 对象）。
 *
 * <p>In addition, the DeserializationSchema describes the produced type ({@link
 * #getProducedType()}), which lets Flink create internal serializers and structures to handle the
 * type.
 * 此外，DeserializationSchema 描述了生成的类型（{@link #getProducedType()}），
 * 它允许 Flink 创建内部序列化器和结构来处理该类型。
 *
 * <p><b>Note:</b> In most cases, one should start from {@link AbstractDeserializationSchema}, which
 * takes care of producing the return type information automatically.
 * <b>注意：</b> 在大多数情况下，应该从 {@link AbstractDeserializationSchema} 开始，它负责自动生成返回类型信息。
 *
 * <p>A DeserializationSchema must be {@link Serializable} because its instances are often part of
 * an operator or transformation function.
 * DeserializationSchema 必须是 {@link Serializable}，因为它的实例通常是运算符或转换函数的一部分。
 *
 * @param <T> The type created by the deserialization schema.
 */
@Public
public interface DeserializationSchema<T> extends Serializable, ResultTypeQueryable<T> {
    /**
     * Initialization method for the schema. It is called before the actual working methods {@link
     * #deserialize} and thus suitable for one time setup work.
     * 架构的初始化方法。 它在实际工作方法 {@link #deserialize} 之前调用，因此适合一次性设置工作。
     *
     * <p>The provided {@link InitializationContext} can be used to access additional features such
     * as e.g. registering user metrics.
     * 提供的 {@link InitializationContext} 可用于访问其他功能，例如 注册用户指标。
     *
     * @param context Contextual information that can be used during initialization.
     */
    @PublicEvolving
    default void open(InitializationContext context) throws Exception {}

    /**
     * Deserializes the byte message.
     * 反序列化字节消息。
     *
     * @param message The message, as a byte array.
     * @return The deserialized message as an object (null if the message cannot be deserialized).
     */
    T deserialize(byte[] message) throws IOException;

    /**
     * Deserializes the byte message.
     * 反序列化字节消息。
     *
     * <p>Can output multiple records through the {@link Collector}. Note that number and size of
     * the produced records should be relatively small. Depending on the source implementation
     * records can be buffered in memory or collecting records might delay emitting checkpoint
     * barrier.
     * 可以通过 {@link Collector} 输出多条记录。 请注意，生成的记录的数量和大小应该相对较小。
     * 根据源实现，记录可以缓冲在内存中，或者收集记录可能会延迟发出检查点屏障。
     *
     * @param message The message, as a byte array.
     * @param out The collector to put the resulting messages.
     */
    @PublicEvolving
    default void deserialize(byte[] message, Collector<T> out) throws IOException {
        T deserialize = deserialize(message);
        if (deserialize != null) {
            out.collect(deserialize);
        }
    }

    /**
     * Method to decide whether the element signals the end of the stream. If true is returned the
     * element won't be emitted.
     * 确定元素是否发出流结束信号的方法。 如果返回 true，则不会发出元素。
     *
     * @param nextElement The element to test for the end-of-stream signal.
     * @return True, if the element signals end of stream, false otherwise.
     */
    boolean isEndOfStream(T nextElement);

    /**
     * A contextual information provided for {@link #open(InitializationContext)} method. It can be
     * used to:
     * 为 {@link #open(InitializationContext)} 方法提供的上下文信息。 它可用于：
     *<ul>
     *   <li>通过 {@link InitializationContext#getMetricGroup()} 注册用户指标
     *    <li>访问用户代码类加载器。
     * </ul>
     * <ul>
     *   <li>Register user metrics via {@link InitializationContext#getMetricGroup()}
     *   <li>Access the user code class loader.
     * </ul>
     */
    @PublicEvolving
    interface InitializationContext {
        /**
         * Returns the metric group for the parallel subtask of the source that runs this {@link
         * DeserializationSchema}.
         * 返回运行此 {@link DeserializationSchema} 的source的并行子任务的指标组。
         *
         * <p>Instances of this class can be used to register new metrics with Flink and to create a
         * nested hierarchy based on the group names. See {@link MetricGroup} for more information
         * for the metrics system.
         * 此类的实例可用于向 Flink 注册新指标，并基于组名称创建嵌套层次结构。
         * 有关度量系统的更多信息，请参阅 {@link MetricGroup}。
         *
         * @see MetricGroup
         */
        MetricGroup getMetricGroup();

        /**
         * Gets the {@link UserCodeClassLoader} to load classes that are not in system's classpath,
         * but are part of the jar file of a user job.
         * 获取 {@link UserCodeClassLoader} 以加载不在系统类路径中但属于用户作业的 jar 文件的类。
         *
         * @see UserCodeClassLoader
         */
        UserCodeClassLoader getUserCodeClassLoader();
    }
}
