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

package org.apache.flink.api.common.eventtime;

import org.apache.flink.annotation.Public;

import java.io.Serializable;
import java.time.Duration;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The WatermarkStrategy defines how to generate {@link Watermark}s in the stream sources. The
 * WatermarkStrategy is a builder/factory for the {@link WatermarkGenerator} that generates the
 * watermarks and the {@link TimestampAssigner} which assigns the internal timestamp of a record.
 * WatermarkStrategy 定义了如何在流源中生成 {@link Watermark}。
 * WatermarkStrategy 是生成水印的 {@link WatermarkGenerator} 和分配记录内部时间戳的
 * {@link TimestampAssigner} 的构建器/工厂。
 *
 * <p>This interface is split into three parts: 1) methods that an implementor of this interface
 * needs to implement, 2) builder methods for building a {@code WatermarkStrategy} on a base
 * strategy, 3) convenience methods for constructing a {@code WatermarkStrategy} for common built-in
 * strategies or based on a {@link WatermarkGeneratorSupplier}
 * 该接口分为三个部分：1) 该接口的实现者需要实现的方法，
 * 2) 用于在基本策略上构建 {@code WatermarkStrategy} 的构建器方法，
 * 3) 用于构造 {@code WatermarkStrategy} 的便捷方法 用于常见的内置策略或基于 {@link WatermarkGeneratorSupplier}
 *
 * <p>Implementors of this interface need only implement {@link
 * #createWatermarkGenerator(WatermarkGeneratorSupplier.Context)}. Optionally, you can implement
 * {@link #createTimestampAssigner(TimestampAssignerSupplier.Context)}.
 * 此接口的实现者只需实现 {@link #createWatermarkGenerator(WatermarkGeneratorSupplier.Context)}。
 * 或者，您可以实现 {@link #createTimestampAssigner(TimestampAssignerSupplier.Context)}。
 *
 * <p>The builder methods, like {@link #withIdleness(Duration)} or {@link
 * #createTimestampAssigner(TimestampAssignerSupplier.Context)} create a new {@code
 * WatermarkStrategy} that wraps and enriches a base strategy. The strategy on which the method is
 * called is the base strategy.
 * 构建器方法，如 {@link #withIdleness(Duration)} 或
 * {@link #createTimestampAssigner(TimestampAssignerSupplier.Context)}
 * 创建了一个新的 {@code WatermarkStrategy} 来包装和丰富基本策略。 调用该方法的策略是基本策略。
 *
 * <p>The convenience methods, for example {@link #forBoundedOutOfOrderness(Duration)}, create a
 * {@code WatermarkStrategy} for common built in strategies.
 * 方便的方法，例如 {@link #forBoundedOutOfOrderness(Duration)}，
 * 为常见的内置策略创建一个 {@code WatermarkStrategy}。
 *
 * <p>This interface is {@link Serializable} because watermark strategies may be shipped to workers
 * during distributed execution.
 * 此接口是 {@link Serializable}，因为水印策略可能会在分布式执行期间发送给workers。
 */
@Public
public interface WatermarkStrategy<T>
        extends TimestampAssignerSupplier<T>, WatermarkGeneratorSupplier<T> {

    // ------------------------------------------------------------------------
    //  Methods that implementors need to implement.
    // ------------------------------------------------------------------------

    /** Instantiates a WatermarkGenerator that generates watermarks according to this strategy.
     * 实例化根据此策略生成水印的 WatermarkGenerator。
     * */
    @Override
    WatermarkGenerator<T> createWatermarkGenerator(WatermarkGeneratorSupplier.Context context);

    /**
     * Instantiates a {@link TimestampAssigner} for assigning timestamps according to this strategy.
     * 实例化一个 {@link TimestampAssigner} 用于根据此策略分配时间戳。
     */
    @Override
    default TimestampAssigner<T> createTimestampAssigner(
            TimestampAssignerSupplier.Context context) {
        // By default, this is {@link RecordTimestampAssigner},
        // for cases where records come out of a source with valid timestamps, for example from
        // Kafka.
        // 默认情况下，这是 {@link RecordTimestampAssigner}，用于记录来自具有有效时间戳的源的情况，例如来自 Kafka。
        return new RecordTimestampAssigner<>();
    }

    // ------------------------------------------------------------------------
    //  Builder methods for enriching a base WatermarkStrategy
    // ------------------------------------------------------------------------

    /**
     * Creates a new {@code WatermarkStrategy} that wraps this strategy but instead uses the given
     * {@link TimestampAssigner} (via a {@link TimestampAssignerSupplier}).
     * 创建一个新的 {@code WatermarkStrategy} 来包装此策略，
     * 但使用给定的 {@link TimestampAssigner}（通过 {@link TimestampAssignerSupplier}）。
     *
     * <p>You can use this when a {@link TimestampAssigner} needs additional context, for example
     * access to the metrics system.
     * 当 {@link TimestampAssigner} 需要额外的上下文时，您可以使用它，例如访问指标系统。
     *
     * <pre>
     * {@code WatermarkStrategy<Object> wmStrategy = WatermarkStrategy
     *   .forMonotonousTimestamps()
     *   .withTimestampAssigner((ctx) -> new MetricsReportingAssigner(ctx));
     * }</pre>
     */
    default WatermarkStrategy<T> withTimestampAssigner(
            TimestampAssignerSupplier<T> timestampAssigner) {
        checkNotNull(timestampAssigner, "timestampAssigner");
        return new WatermarkStrategyWithTimestampAssigner<>(this, timestampAssigner);
    }

    /**
     * Creates a new {@code WatermarkStrategy} that wraps this strategy but instead uses the given
     * {@link SerializableTimestampAssigner}.
     * 创建一个新的 {@code WatermarkStrategy} 来包装这个策略，而是使用给定的 {@link SerializableTimestampAssigner}。
     *
     * <p>You can use this in case you want to specify a {@link TimestampAssigner} via a lambda
     * function.
     * 如果您想通过 lambda 函数指定 {@link TimestampAssigner}，您可以使用它。
     *
     * <pre>
     * {@code WatermarkStrategy<CustomObject> wmStrategy = WatermarkStrategy
     *   .<CustomObject>forMonotonousTimestamps()
     *   .withTimestampAssigner((event, timestamp) -> event.getTimestamp());
     * }</pre>
     */
    default WatermarkStrategy<T> withTimestampAssigner(
            SerializableTimestampAssigner<T> timestampAssigner) {
        checkNotNull(timestampAssigner, "timestampAssigner");
        return new WatermarkStrategyWithTimestampAssigner<>(
                this, TimestampAssignerSupplier.of(timestampAssigner));
    }

    /**
     * Creates a new enriched {@link WatermarkStrategy} that also does idleness detection in the
     * created {@link WatermarkGenerator}.
     * 创建一个新的丰富的 {@link WatermarkStrategy}，它也在创建的 {@link WatermarkGenerator} 中进行空闲检测。
     *
     * <p>Add an idle timeout to the watermark strategy. If no records flow in a partition of a
     * stream for that amount of time, then that partition is considered "idle" and will not hold
     * back the progress of watermarks in downstream operators.
     * 为水印策略添加空闲超时。 如果在该时间段内没有记录在流的分区中流动，
     * 则该分区被视为“空闲”并且不会阻止下游算子中水印的进度。
     *
     * <p>Idleness can be important if some partitions have little data and might not have events
     * during some periods. Without idleness, these streams can stall the overall event time
     * progress of the application.
     * 如果某些分区的数据很少并且在某些时间段内可能没有事件，则空闲可能很重要。
     * 如果没有空闲，这些流可能会拖延应用程序的整体事件时间进度。
     */
    default WatermarkStrategy<T> withIdleness(Duration idleTimeout) {
        checkNotNull(idleTimeout, "idleTimeout");
        checkArgument(
                !(idleTimeout.isZero() || idleTimeout.isNegative()),
                "idleTimeout must be greater than zero");
        return new WatermarkStrategyWithIdleness<>(this, idleTimeout);
    }

    // ------------------------------------------------------------------------
    //  Convenience methods for common watermark strategies
    // ------------------------------------------------------------------------

    /**
     * Creates a watermark strategy for situations with monotonously ascending timestamps.
     * 为时间戳单调递增的情况创建水印策略。
     *
     * <p>The watermarks are generated periodically and tightly follow the latest timestamp in the
     * data. The delay introduced by this strategy is mainly the periodic interval in which the
     * watermarks are generated.
     * 水印是定期生成的，并严格遵循数据中的最新时间戳。 这种策略引入的延迟主要是产生水印的周期间隔。
     *
     * @see AscendingTimestampsWatermarks
     */
    static <T> WatermarkStrategy<T> forMonotonousTimestamps() {
        return (ctx) -> new AscendingTimestampsWatermarks<>();
    }

    /**
     * Creates a watermark strategy for situations where records are out of order, but you can place
     * an upper bound on how far the events are out of order. An out-of-order bound B means that
     * once the an event with timestamp T was encountered, no events older than {@code T - B} will
     * follow any more.
     * 为记录无序的情况创建水印策略，但您可以为事件无序的程度设置上限。
     * 无序边界 B 意味着一旦遇到时间戳为 T 的事件，就不会再出现比 {@code T - B} 更旧的事件。
     *
     * <p>The watermarks are generated periodically. The delay introduced by this watermark strategy
     * is the periodic interval length, plus the out of orderness bound.
     * 水印是周期性生成的。 这种水印策略引入的延迟是周期间隔长度，加上乱序界限。
     *
     * @see BoundedOutOfOrdernessWatermarks
     */
    static <T> WatermarkStrategy<T> forBoundedOutOfOrderness(Duration maxOutOfOrderness) {
        return (ctx) -> new BoundedOutOfOrdernessWatermarks<>(maxOutOfOrderness);
    }

    /** Creates a watermark strategy based on an existing {@link WatermarkGeneratorSupplier}.
     * 基于现有的 {@link WatermarkGeneratorSupplier} 创建水印策略。
     * */
    static <T> WatermarkStrategy<T> forGenerator(WatermarkGeneratorSupplier<T> generatorSupplier) {
        return generatorSupplier::createWatermarkGenerator;
    }

    /**
     * Creates a watermark strategy that generates no watermarks at all. This may be useful in
     * scenarios that do pure processing-time based stream processing.
     * 创建一个完全不生成水印的水印策略。 这在执行纯基于处理时间的流处理的场景中可能很有用。
     */
    static <T> WatermarkStrategy<T> noWatermarks() {
        return (ctx) -> new NoWatermarksGenerator<>();
    }
}
