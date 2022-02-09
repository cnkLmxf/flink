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

/** Type of a result partition.
 * 结果分区的类型。
 * */
public enum ResultPartitionType {

    /**
     * Blocking partitions represent blocking data exchanges, where the data stream is first fully
     * produced and then consumed. This is an option that is only applicable to bounded streams and
     * can be used in bounded stream runtime and recovery.
     * 阻塞分区代表阻塞数据交换，其中数据流首先完全产生然后被消耗。
     * 这是一个仅适用于有界流的选项，可用于有界流运行时和恢复。
     *
     * <p>Blocking partitions can be consumed multiple times and concurrently.
     * 阻塞分区可以同时被多次使用。
     *
     * <p>The partition is not automatically released after being consumed (like for example the
     * {@link #PIPELINED} partitions), but only released through the scheduler, when it determines
     * that the partition is no longer needed.
     * 分区在被消耗后不会自动释放（例如 {@link #PIPELINED} 分区），而是仅在确定不再需要该分区时通过调度程序释放。
     */
    BLOCKING(false, false, false, false, true),

    /**
     * BLOCKING_PERSISTENT partitions are similar to {@link #BLOCKING} partitions, but have a
     * user-specified life cycle.
     * BLOCKING_PERSISTENT 分区类似于 {@link #BLOCKING} 分区，但具有用户指定的生命周期。
     *
     * <p>BLOCKING_PERSISTENT partitions are dropped upon explicit API calls to the JobManager or
     * ResourceManager, rather than by the scheduler.
     * BLOCKING_PERSISTENT 分区在对 JobManager 或 ResourceManager 的显式 API 调用时被删除，而不是由调度程序删除。
     *
     * <p>Otherwise, the partition may only be dropped by safety-nets during failure handling
     * scenarios, like when the TaskManager exits or when the TaskManager looses connection to
     * JobManager / ResourceManager for too long.
     * 否则，分区可能只会在故障处理场景中被安全网丢弃，
     * 例如当 TaskManager 退出或 TaskManager 与 JobManager / ResourceManager 失去连接太长时间时。
     */
    BLOCKING_PERSISTENT(false, false, false, true, true),

    /**
     * A pipelined streaming data exchange. This is applicable to both bounded and unbounded
     * streams.
     * 流水线数据交换。 这适用于有界和无界流。
     *
     * <p>Pipelined results can be consumed only once by a single consumer and are automatically
     * disposed when the stream has been consumed.
     * 流水线结果只能由单个消费者消费一次，并在消费流时自动处置。
     *
     * <p>This result partition type may keep an arbitrary amount of data in-flight, in contrast to
     * the {@link #PIPELINED_BOUNDED} variant.
     * 与 {@link #PIPELINED_BOUNDED} 变体相比，这种结果分区类型可以保持任意数量的数据在传输中。
     */
    PIPELINED(true, true, false, false, false),

    /**
     * Pipelined partitions with a bounded (local) buffer pool.
     * 具有有界（本地）缓冲池的流水线分区。
     *
     * <p>For streaming jobs, a fixed limit on the buffer pool size should help avoid that too much
     * data is being buffered and checkpoint barriers are delayed. In contrast to limiting the
     * overall network buffer pool size, this, however, still allows to be flexible with regards to
     * the total number of partitions by selecting an appropriately big network buffer pool size.
     * 对于流式作业，缓冲池大小的固定限制应该有助于避免缓冲过多数据和延迟检查点屏障。
     * 然而，与限制整个网络缓冲池大小相比，这仍然允许通过选择适当大的网络缓冲池大小来灵活地考虑分区总数。
     *
     * <p>For batch jobs, it will be best to keep this unlimited ({@link #PIPELINED}) since there
     * are no checkpoint barriers.
     * 对于批处理作业，最好保持无限制（{@link #PIPELINED}），因为没有检查点障碍。
     */
    PIPELINED_BOUNDED(true, true, true, false, false),

    /**
     * Pipelined partitions with a bounded (local) buffer pool to support downstream task to
     * continue consuming data after reconnection in Approximate Local-Recovery.
     * 具有有界（本地）缓冲池的流水线分区支持下游任务在 Approximate Local-Recovery 重新连接后继续使用数据。
     *
     * <p>Pipelined results can be consumed only once by a single consumer at one time. {@link
     * #PIPELINED_APPROXIMATE} is different from {@link #PIPELINED} and {@link #PIPELINED_BOUNDED}
     * in that {@link #PIPELINED_APPROXIMATE} partition can be reconnected after down stream task
     * fails.
     * 流水线结果一次只能被一个消费者消费一次。 {@link #PIPELINED_APPROXIMATE} 与 {@link #PIPELINED}
     * 和 {@link #PIPELINED_BOUNDED} 的不同之处在于，在下游任务失败后，可以重新连接 {@link #PIPELINED_APPROXIMATE} 分区。
     */
    PIPELINED_APPROXIMATE(true, true, true, false, true);

    /** Can the partition be consumed while being produced?
     * 分区可以边生产边消费吗？
     * */
    private final boolean isPipelined;

    /** Does the partition produce back pressure when not consumed?
     * 分区不消费时是否产生背压？
     * */
    private final boolean hasBackPressure;

    /** Does this partition use a limited number of (network) buffers?
     * 此分区是否使用有限数量的（网络）缓冲区？
     * */
    private final boolean isBounded;

    /** This partition will not be released after consuming if 'isPersistent' is true.
     * 如果 'isPersistent' 为真，则消费后不会释放此分区。
     * */
    private final boolean isPersistent;

    /**
     * Can the partition be reconnected.
     *
     * <p>Attention: this attribute is introduced temporally for
     * ResultPartitionType.PIPELINED_APPROXIMATE It will be removed afterwards: TODO: 1. Approximate
     * local recovery has its won failover strategy to restart the failed set of tasks instead of
     * restarting downstream of failed tasks depending on {@code
     * RestartPipelinedRegionFailoverStrategy} 2. FLINK-19895: Unify the life cycle of
     * ResultPartitionType Pipelined Family
     * 注意：这个属性是临时为 ResultPartitionType.PIPELINED_APPROXIMATE 引入的，之后会被移除：
     * TODO: 1. 近似本地恢复有其获胜的故障转移策略来重新启动失败的任务集，
     * 而不是根据 {@code RestartPipelinedRegionFailoverStrategy} 重新启动失败任务的下游
     * 2. FLINK-19895：统一ResultPartitionType Pipelined Family的生命周期
     */
    private final boolean isReconnectable;

    /** Specifies the behaviour of an intermediate result partition at runtime.
     * 指定运行时中间结果分区的行为。
     * */
    ResultPartitionType(
            boolean isPipelined,
            boolean hasBackPressure,
            boolean isBounded,
            boolean isPersistent,
            boolean isReconnectable) {
        this.isPipelined = isPipelined;
        this.hasBackPressure = hasBackPressure;
        this.isBounded = isBounded;
        this.isPersistent = isPersistent;
        this.isReconnectable = isReconnectable;
    }

    public boolean hasBackPressure() {
        return hasBackPressure;
    }

    public boolean isBlocking() {
        return !isPipelined;
    }

    public boolean isPipelined() {
        return isPipelined;
    }

    public boolean isReconnectable() {
        return isReconnectable;
    }

    /**
     * Whether this partition uses a limited number of (network) buffers or not.
     * 此分区是否使用有限数量的（网络）缓冲区。
     *
     * @return <tt>true</tt> if the number of buffers should be bound to some limit
     */
    public boolean isBounded() {
        return isBounded;
    }

    public boolean isPersistent() {
        return isPersistent;
    }
}
