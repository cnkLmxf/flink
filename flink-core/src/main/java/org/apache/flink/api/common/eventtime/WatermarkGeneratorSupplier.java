/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.eventtime;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.metrics.MetricGroup;

import java.io.Serializable;

/**
 * A supplier for {@link WatermarkGenerator WatermarkGenerators}. The supplier pattern is used to
 * avoid having to make {@link WatermarkGenerator} {@link Serializable} for use in API methods.
 * {@link WatermarkGenerator WatermarkGenerators} 的供应商。
 * 供应商模式用于避免在 API 方法中使用 {@link WatermarkGenerator} {@link Serializable}。
 *
 * <p>This interface is {@link Serializable} because the supplier may be shipped to workers during
 * distributed execution.
 * 此接口是 {@link Serializable}，因为供应商可能会在分布式执行期间运送给工人。
 */
@PublicEvolving
@FunctionalInterface
public interface WatermarkGeneratorSupplier<T> extends Serializable {

    /** Instantiates a {@link WatermarkGenerator}. */
    WatermarkGenerator<T> createWatermarkGenerator(Context context);

    /**
     * Additional information available to {@link #createWatermarkGenerator(Context)}. This can be
     * access to {@link org.apache.flink.metrics.MetricGroup MetricGroups}, for example.
     * {@link #createWatermarkGenerator(Context)} 可用的附加信息。
     * 例如，这可以访问 {@link org.apache.flink.metrics.MetricGroup MetricGroups}。
     */
    interface Context {

        /**
         * Returns the metric group for the context in which the created {@link WatermarkGenerator}
         * is used.
         * 返回使用创建的 {@link WatermarkGenerator} 的上下文的指标组。
         *
         * <p>Instances of this class can be used to register new metrics with Flink and to create a
         * nested hierarchy based on the group names. See {@link MetricGroup} for more information
         * for the metrics system.
         * 此类的实例可用于向 Flink 注册新指标并基于组名称创建嵌套层次结构。
         * 有关指标系统的更多信息，请参阅 {@link MetricGroup}。
         *
         * @see MetricGroup
         */
        MetricGroup getMetricGroup();
    }
}
