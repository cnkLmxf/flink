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

package org.apache.flink.api.common.state;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.EnumMap;

import static org.apache.flink.api.common.state.StateTtlConfig.CleanupStrategies.EMPTY_STRATEGY;
import static org.apache.flink.api.common.state.StateTtlConfig.IncrementalCleanupStrategy.DEFAULT_INCREMENTAL_CLEANUP_STRATEGY;
import static org.apache.flink.api.common.state.StateTtlConfig.RocksdbCompactFilterCleanupStrategy.DEFAULT_ROCKSDB_COMPACT_FILTER_CLEANUP_STRATEGY;
import static org.apache.flink.api.common.state.StateTtlConfig.StateVisibility.NeverReturnExpired;
import static org.apache.flink.api.common.state.StateTtlConfig.TtlTimeCharacteristic.ProcessingTime;
import static org.apache.flink.api.common.state.StateTtlConfig.UpdateType.OnCreateAndWrite;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Configuration of state TTL logic.
 * 状态 TTL 逻辑的配置。
 *
 * <p>Note: The map state with TTL currently supports {@code null} user values only if the user
 * value serializer can handle {@code null} values. If the serializer does not support {@code null}
 * values, it can be wrapped with {@link
 * org.apache.flink.api.java.typeutils.runtime.NullableSerializer} at the cost of an extra byte in
 * the serialized form.
 * 注意：只有当用户值序列化程序可以处理 {@code null} 值时，具有 TTL 的映射状态当前才支持 {@code null} 用户值。
 * 如果序列化程序不支持 {@code null} 值，则可以用 {@link org.apache.flink.api.java.typeutils.runtime.NullableSerializer} 包装它，
 * 但需要在序列化形式中增加一个字节。
 */
@PublicEvolving
public class StateTtlConfig implements Serializable {

    private static final long serialVersionUID = -7592693245044289793L;

    public static final StateTtlConfig DISABLED =
            newBuilder(Time.milliseconds(Long.MAX_VALUE))
                    .setUpdateType(UpdateType.Disabled)
                    .build();

    /**
     * This option value configures when to update last access timestamp which prolongs state TTL.
     * 此选项值配置何时更新延长状态 TTL 的上次访问时间戳。
     */
    public enum UpdateType {
        /** TTL is disabled. State does not expire.
         * TTL 被禁用。 状态不会过期。
         * */
        Disabled,
        /**
         * Last access timestamp is initialised when state is created and updated on every write
         * operation.
         * 上次访问时间戳在每次写入操作创建和更新状态时初始化。
         */
        OnCreateAndWrite,
        /** The same as <code>OnCreateAndWrite</code> but also updated on read.
         * 与 <code>OnCreateAndWrite</code> 相同，但在读取时也会更新。
         * */
        OnReadAndWrite
    }

    /** This option configures whether expired user value can be returned or not.
     * 该选项配置是否可以返回过期的用户值。
     * */
    public enum StateVisibility {
        /** Return expired user value if it is not cleaned up yet.
         * 如果尚未清理，则返回过期的用户值。
         * */
        ReturnExpiredIfNotCleanedUp,
        /** Never return expired user value.
         * 永远不要返回过期的用户值。
         * */
        NeverReturnExpired
    }

    /** This option configures time scale to use for ttl.
     * 此选项配置时间刻度以用于 ttl。
     * */
    public enum TtlTimeCharacteristic {
        /**
         * Processing time, see also <code>
         * org.apache.flink.streaming.api.TimeCharacteristic.ProcessingTime</code>.
         */
        ProcessingTime
    }

    private final UpdateType updateType;
    private final StateVisibility stateVisibility;
    private final TtlTimeCharacteristic ttlTimeCharacteristic;
    private final Time ttl;
    private final CleanupStrategies cleanupStrategies;

    private StateTtlConfig(
            UpdateType updateType,
            StateVisibility stateVisibility,
            TtlTimeCharacteristic ttlTimeCharacteristic,
            Time ttl,
            CleanupStrategies cleanupStrategies) {
        this.updateType = checkNotNull(updateType);
        this.stateVisibility = checkNotNull(stateVisibility);
        this.ttlTimeCharacteristic = checkNotNull(ttlTimeCharacteristic);
        this.ttl = checkNotNull(ttl);
        this.cleanupStrategies = cleanupStrategies;
        checkArgument(ttl.toMilliseconds() > 0, "TTL is expected to be positive.");
    }

    @Nonnull
    public UpdateType getUpdateType() {
        return updateType;
    }

    @Nonnull
    public StateVisibility getStateVisibility() {
        return stateVisibility;
    }

    @Nonnull
    public Time getTtl() {
        return ttl;
    }

    @Nonnull
    public TtlTimeCharacteristic getTtlTimeCharacteristic() {
        return ttlTimeCharacteristic;
    }

    public boolean isEnabled() {
        return updateType != UpdateType.Disabled;
    }

    @Nonnull
    public CleanupStrategies getCleanupStrategies() {
        return cleanupStrategies;
    }

    @Override
    public String toString() {
        return "StateTtlConfig{"
                + "updateType="
                + updateType
                + ", stateVisibility="
                + stateVisibility
                + ", ttlTimeCharacteristic="
                + ttlTimeCharacteristic
                + ", ttl="
                + ttl
                + '}';
    }

    @Nonnull
    public static Builder newBuilder(@Nonnull Time ttl) {
        return new Builder(ttl);
    }

    /** Builder for the {@link StateTtlConfig}. */
    public static class Builder {

        private UpdateType updateType = OnCreateAndWrite;
        private StateVisibility stateVisibility = NeverReturnExpired;
        private TtlTimeCharacteristic ttlTimeCharacteristic = ProcessingTime;
        private Time ttl;
        private boolean isCleanupInBackground = true;
        private final EnumMap<CleanupStrategies.Strategies, CleanupStrategies.CleanupStrategy>
                strategies = new EnumMap<>(CleanupStrategies.Strategies.class);

        public Builder(@Nonnull Time ttl) {
            this.ttl = ttl;
        }

        /**
         * Sets the ttl update type.
         *
         * @param updateType The ttl update type configures when to update last access timestamp
         *     which prolongs state TTL.
         */
        @Nonnull
        public Builder setUpdateType(UpdateType updateType) {
            this.updateType = updateType;
            return this;
        }

        @Nonnull
        public Builder updateTtlOnCreateAndWrite() {
            return setUpdateType(UpdateType.OnCreateAndWrite);
        }

        @Nonnull
        public Builder updateTtlOnReadAndWrite() {
            return setUpdateType(UpdateType.OnReadAndWrite);
        }

        /**
         * Sets the state visibility.
         *
         * @param stateVisibility The state visibility configures whether expired user value can be
         *     returned or not.
         */
        @Nonnull
        public Builder setStateVisibility(@Nonnull StateVisibility stateVisibility) {
            this.stateVisibility = stateVisibility;
            return this;
        }

        @Nonnull
        public Builder returnExpiredIfNotCleanedUp() {
            return setStateVisibility(StateVisibility.ReturnExpiredIfNotCleanedUp);
        }

        @Nonnull
        public Builder neverReturnExpired() {
            return setStateVisibility(StateVisibility.NeverReturnExpired);
        }

        /**
         * Sets the time characteristic.
         *
         * @param ttlTimeCharacteristic The time characteristic configures time scale to use for
         *     ttl.
         */
        @Nonnull
        public Builder setTtlTimeCharacteristic(
                @Nonnull TtlTimeCharacteristic ttlTimeCharacteristic) {
            this.ttlTimeCharacteristic = ttlTimeCharacteristic;
            return this;
        }

        @Nonnull
        public Builder useProcessingTime() {
            return setTtlTimeCharacteristic(ProcessingTime);
        }

        /** Cleanup expired state in full snapshot on checkpoint.
         * 在检查点的完整快照中清除过期状态。
         * */
        @Nonnull
        public Builder cleanupFullSnapshot() {
            strategies.put(CleanupStrategies.Strategies.FULL_STATE_SCAN_SNAPSHOT, EMPTY_STRATEGY);
            return this;
        }

        /**
         * Cleanup expired state incrementally cleanup local state.
         * 清除过期状态增量清除本地状态。
         *
         * <p>Upon every state access this cleanup strategy checks a bunch of state keys for
         * expiration and cleans up expired ones. It keeps a lazy iterator through all keys with
         * relaxed consistency if backend supports it. This way all keys should be regularly checked
         * and cleaned eventually over time if any state is constantly being accessed.
         * 在每次状态访问时，此清理策略都会检查一组状态密钥是否过期并清理过期的密钥。
         * 如果后端支持它，它会通过所有键保持一个惰性迭代器，并且具有宽松的一致性。
         * 这样，如果不断访问任何状态，则应定期检查并最终清理所有密钥。
         *
         * <p>Additionally to the incremental cleanup upon state access, it can also run per every
         * record. Caution: if there are a lot of registered states using this option, they all will
         * be iterated for every record to check if there is something to cleanup.
         * 除了状态访问时的增量清理之外，它还可以按每条记录运行。
         * 注意：如果有很多注册状态使用此选项，它们都将针对每条记录进行迭代，以检查是否有需要清理的内容。
         *
         * <p>Note: if no access happens to this state or no records are processed in case of {@code
         * runCleanupForEveryRecord}, expired state will persist.
         * 注意：如果在 {@code runCleanupForEveryRecord} 的情况下没有访问此状态或没有处理任何记录，则过期状态将持续存在。
         *
         * <p>Note: Time spent for the incremental cleanup increases record processing latency.
         * 注意：用于增量清理的时间会增加记录处理延迟。
         *
         * <p>Note: At the moment incremental cleanup is implemented only for Heap state backend.
         * Setting it for RocksDB will have no effect.
         * 注意：目前仅对堆状态后端实施增量清理。 为 RocksDB 设置它不会有任何效果。
         *
         * <p>Note: If heap state backend is used with synchronous snapshotting, the global iterator
         * keeps a copy of all keys while iterating because of its specific implementation which
         * does not support concurrent modifications. Enabling of this feature will increase memory
         * consumption then. Asynchronous snapshotting does not have this problem.
         * 注意：如果堆状态后端与同步快照一起使用，全局迭代器会在迭代时保留所有键的副本，因为它的特定实现不支持并发修改。
         * 启用此功能将增加内存消耗。 异步快照没有这个问题。
         *
         * @param cleanupSize max number of keys pulled from queue for clean up upon state touch for
         *     any key
         * @param runCleanupForEveryRecord run incremental cleanup per each processed record
         */
        @Nonnull
        public Builder cleanupIncrementally(
                @Nonnegative int cleanupSize, boolean runCleanupForEveryRecord) {
            strategies.put(
                    CleanupStrategies.Strategies.INCREMENTAL_CLEANUP,
                    new IncrementalCleanupStrategy(cleanupSize, runCleanupForEveryRecord));
            return this;
        }

        /**
         * Cleanup expired state while Rocksdb compaction is running.
         * 在 Rocksdb 压缩运行时清理过期状态。
         *
         * <p>RocksDB compaction filter will query current timestamp, used to check expiration, from
         * Flink every time after processing {@code queryTimeAfterNumEntries} number of state
         * entries. Updating the timestamp more often can improve cleanup speed but it decreases
         * compaction performance because it uses JNI call from native code.
         * RocksDB 压缩过滤器会在每次处理 {@code queryTimeAfterNumEntries} 个状态条目后从 Flink 查询当前时间戳，
         * 用于检查过期时间。
         * 更频繁地更新时间戳可以提高清理速度，但会降低压缩性能，因为它使用来自本机代码的 JNI 调用。
         *
         * @param queryTimeAfterNumEntries number of state entries to process by compaction filter
         *     before updating current timestamp
         */
        @Nonnull
        public Builder cleanupInRocksdbCompactFilter(long queryTimeAfterNumEntries) {
            strategies.put(
                    CleanupStrategies.Strategies.ROCKSDB_COMPACTION_FILTER,
                    new RocksdbCompactFilterCleanupStrategy(queryTimeAfterNumEntries));
            return this;
        }

        /**
         * Disable default cleanup of expired state in background (enabled by default).
         * 在后台禁用过期状态的默认清理（默认启用）。
         *
         * <p>If some specific cleanup is configured, e.g. {@link #cleanupIncrementally(int,
         * boolean)} or {@link #cleanupInRocksdbCompactFilter(long)}, this setting does not disable
         * it.
         * 如果配置了一些特定的清理，例如 {@link #cleanupIncrementally(int, boolean)}
         * 或 {@link #cleanupInRocksdbCompactFilter(long)}，此设置不会禁用它。
         */
        @Nonnull
        public Builder disableCleanupInBackground() {
            isCleanupInBackground = false;
            return this;
        }

        /**
         * Sets the ttl time.
         *
         * @param ttl The ttl time.
         */
        @Nonnull
        public Builder setTtl(@Nonnull Time ttl) {
            this.ttl = ttl;
            return this;
        }

        @Nonnull
        public StateTtlConfig build() {
            return new StateTtlConfig(
                    updateType,
                    stateVisibility,
                    ttlTimeCharacteristic,
                    ttl,
                    new CleanupStrategies(strategies, isCleanupInBackground));
        }
    }

    /**
     * TTL cleanup strategies.
     * TTL 清理策略。
     *
     * <p>This class configures when to cleanup expired state with TTL. By default, state is always
     * cleaned up on explicit read access if found expired. Currently cleanup of state full snapshot
     * can be additionally activated.
     * 此类配置何时使用 TTL 清除过期状态。
     * 默认情况下，如果发现已过期，则始终在显式读取访问时清除状态。 目前可以另外激活状态完整快照的清理。
     */
    public static class CleanupStrategies implements Serializable {
        private static final long serialVersionUID = -1617740467277313524L;

        static final CleanupStrategy EMPTY_STRATEGY = new EmptyCleanupStrategy();

        private final boolean isCleanupInBackground;

        private final EnumMap<Strategies, CleanupStrategy> strategies;

        /** Fixed strategies ordinals in {@code strategies} config field.
         * 修复了 {@code strategy} 配置字段中的策略序数。
         * */
        enum Strategies {
            FULL_STATE_SCAN_SNAPSHOT,
            INCREMENTAL_CLEANUP,
            ROCKSDB_COMPACTION_FILTER
        }

        /** Base interface for cleanup strategies configurations.
         * 清理策略配置的基本接口。
         * */
        interface CleanupStrategy extends Serializable {}

        static class EmptyCleanupStrategy implements CleanupStrategy {
            private static final long serialVersionUID = 1373998465131443873L;
        }

        private CleanupStrategies(
                EnumMap<Strategies, CleanupStrategy> strategies, boolean isCleanupInBackground) {
            this.strategies = strategies;
            this.isCleanupInBackground = isCleanupInBackground;
        }

        public boolean inFullSnapshot() {
            return strategies.containsKey(Strategies.FULL_STATE_SCAN_SNAPSHOT);
        }

        public boolean isCleanupInBackground() {
            return isCleanupInBackground;
        }

        @Nullable
        public IncrementalCleanupStrategy getIncrementalCleanupStrategy() {
            IncrementalCleanupStrategy defaultStrategy =
                    isCleanupInBackground ? DEFAULT_INCREMENTAL_CLEANUP_STRATEGY : null;
            return (IncrementalCleanupStrategy)
                    strategies.getOrDefault(Strategies.INCREMENTAL_CLEANUP, defaultStrategy);
        }

        public boolean inRocksdbCompactFilter() {
            return getRocksdbCompactFilterCleanupStrategy() != null;
        }

        @Nullable
        public RocksdbCompactFilterCleanupStrategy getRocksdbCompactFilterCleanupStrategy() {
            RocksdbCompactFilterCleanupStrategy defaultStrategy =
                    isCleanupInBackground ? DEFAULT_ROCKSDB_COMPACT_FILTER_CLEANUP_STRATEGY : null;
            return (RocksdbCompactFilterCleanupStrategy)
                    strategies.getOrDefault(Strategies.ROCKSDB_COMPACTION_FILTER, defaultStrategy);
        }
    }

    /** Configuration of cleanup strategy while taking the full snapshot.
     * 拍摄完整快照时配置清理策略。
     * */
    public static class IncrementalCleanupStrategy implements CleanupStrategies.CleanupStrategy {
        private static final long serialVersionUID = 3109278696501988780L;

        static final IncrementalCleanupStrategy DEFAULT_INCREMENTAL_CLEANUP_STRATEGY =
                new IncrementalCleanupStrategy(5, false);

        /** Max number of keys pulled from queue for clean up upon state touch for any key. */
        private final int cleanupSize;

        /** Whether to run incremental cleanup per each processed record. */
        private final boolean runCleanupForEveryRecord;

        private IncrementalCleanupStrategy(int cleanupSize, boolean runCleanupForEveryRecord) {
            Preconditions.checkArgument(
                    cleanupSize > 0,
                    "Number of incrementally cleaned up state entries should be positive.");
            this.cleanupSize = cleanupSize;
            this.runCleanupForEveryRecord = runCleanupForEveryRecord;
        }

        public int getCleanupSize() {
            return cleanupSize;
        }

        public boolean runCleanupForEveryRecord() {
            return runCleanupForEveryRecord;
        }
    }

    /** Configuration of cleanup strategy using custom compaction filter in RocksDB.
     * 在 RocksDB 中使用自定义压缩过滤器配置清理策略。
     * */
    public static class RocksdbCompactFilterCleanupStrategy
            implements CleanupStrategies.CleanupStrategy {
        private static final long serialVersionUID = 3109278796506988980L;

        static final RocksdbCompactFilterCleanupStrategy
                DEFAULT_ROCKSDB_COMPACT_FILTER_CLEANUP_STRATEGY =
                        new RocksdbCompactFilterCleanupStrategy(1000L);

        /**
         * Number of state entries to process by compaction filter before updating current
         * timestamp.
         */
        private final long queryTimeAfterNumEntries;

        private RocksdbCompactFilterCleanupStrategy(long queryTimeAfterNumEntries) {
            this.queryTimeAfterNumEntries = queryTimeAfterNumEntries;
        }

        public long getQueryTimeAfterNumEntries() {
            return queryTimeAfterNumEntries;
        }
    }
}
