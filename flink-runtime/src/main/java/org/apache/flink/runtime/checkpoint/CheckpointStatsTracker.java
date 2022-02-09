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

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.tasks.CheckpointCoordinatorConfiguration;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Tracker for checkpoint statistics.
 * 检查点统计信息的跟踪器。
 *
 * <p>This is tightly integrated with the {@link CheckpointCoordinator} in order to ease the
 * gathering of fine-grained statistics.
 * 这与 {@link CheckpointCoordinator} 紧密集成，以简化细粒度统计信息的收集。
 *
 * <p>The tracked stats include summary counts, a detailed history of recent and in progress
 * checkpoints as well as summaries about the size, duration and more of recent checkpoints.
 * 跟踪的统计数据包括摘要计数、最近和正在进行的检查点的详细历史记录以及有关最近检查点的大小、持续时间和更多内容的摘要。
 *
 * <p>Data is gathered via callbacks in the {@link CheckpointCoordinator} and related classes like
 * {@link PendingCheckpoint} and {@link CompletedCheckpoint}, which receive the raw stats data in
 * the first place.
 * 数据通过 {@link CheckpointCoordinator} 和相关类
 * （如 {@link PendingCheckpoint} 和 {@link CompletedCheckpoint}）中的回调收集，它们首先接收原始统计数据。
 *
 * <p>The statistics are accessed via {@link #createSnapshot()} and exposed via both the web
 * frontend and the {@link Metric} system.
 * 统计信息通过 {@link #createSnapshot()} 访问，并通过 Web 前端和 {@link Metric} 系统公开。
 */
public class CheckpointStatsTracker {

    /**
     * Lock used to update stats and creating snapshots. Updates always happen from a single Thread
     * at a time and there can be multiple concurrent read accesses to the latest stats snapshot.
     * 锁用于更新统计信息和创建快照。 更新总是一次从一个线程发生，并且可以有多个并发读取访问最新的统计数据快照。
     *
     * <p>Currently, writes are executed by whatever Thread executes the coordinator actions (which
     * already happens in locked scope). Reads can come from multiple concurrent Netty event loop
     * Threads of the web runtime monitor.
     * 目前，写入由执行协调器操作的任何线程执行（已在锁定范围内发生）。
     * 读取可以来自 Web 运行时监视器的多个并发 Netty 事件循环线程。
     */
    private final ReentrantLock statsReadWriteLock = new ReentrantLock();

    /** Snapshotting settings created from the CheckpointConfig.
     * 从 CheckpointConfig 创建的快照设置。
     * */
    private final CheckpointCoordinatorConfiguration jobCheckpointingConfiguration;

    /** Checkpoint counts. */
    private final CheckpointStatsCounts counts = new CheckpointStatsCounts();

    /** A summary of the completed checkpoint stats. */
    private final CompletedCheckpointStatsSummary summary = new CompletedCheckpointStatsSummary();

    /** History of checkpoints. */
    private final CheckpointStatsHistory history;

    /** The latest restored checkpoint. */
    @Nullable private RestoredCheckpointStats latestRestoredCheckpoint;

    /** Latest created snapshot. */
    private volatile CheckpointStatsSnapshot latestSnapshot;

    /**
     * Flag indicating whether a new snapshot needs to be created. This is true if a new checkpoint
     * was triggered or updated (completed successfully or failed).
     * 指示是否需要创建新快照的标志。 如果触发或更新了新的检查点（成功完成或失败），则为 true。
     */
    private volatile boolean dirty;

    /** The latest completed checkpoint. Used by the latest completed checkpoint metrics.
     * 最新完成的检查点。 由最新完成的检查点指标使用。
     * */
    @Nullable private volatile CompletedCheckpointStats latestCompletedCheckpoint;

    /**
     * Creates a new checkpoint stats tracker.
     *
     * @param numRememberedCheckpoints Maximum number of checkpoints to remember, including in
     *     progress ones.
     * @param jobCheckpointingConfiguration Checkpointing configuration.
     * @param metricGroup Metric group for exposed metrics
     */
    public CheckpointStatsTracker(
            int numRememberedCheckpoints,
            CheckpointCoordinatorConfiguration jobCheckpointingConfiguration,
            MetricGroup metricGroup) {

        checkArgument(numRememberedCheckpoints >= 0, "Negative number of remembered checkpoints");
        this.history = new CheckpointStatsHistory(numRememberedCheckpoints);
        this.jobCheckpointingConfiguration = checkNotNull(jobCheckpointingConfiguration);

        // Latest snapshot is empty
        latestSnapshot =
                new CheckpointStatsSnapshot(
                        counts.createSnapshot(),
                        summary.createSnapshot(),
                        history.createSnapshot(),
                        null);

        // Register the metrics
        registerMetrics(metricGroup);
    }

    /**
     * Returns the job's checkpointing configuration which is derived from the CheckpointConfig.
     * 返回从 CheckpointConfig 派生的作业的检查点配置。
     *
     * @return The job's checkpointing configuration.
     */
    public CheckpointCoordinatorConfiguration getJobCheckpointingConfiguration() {
        return jobCheckpointingConfiguration;
    }

    /**
     * Creates a new snapshot of the available stats.
     *
     * @return The latest statistics snapshot.
     */
    public CheckpointStatsSnapshot createSnapshot() {
        CheckpointStatsSnapshot snapshot = latestSnapshot;

        // Only create a new snapshot if dirty and no update in progress,
        // because we don't want to block the coordinator.
        if (dirty && statsReadWriteLock.tryLock()) {
            try {
                // Create a new snapshot
                snapshot =
                        new CheckpointStatsSnapshot(
                                counts.createSnapshot(),
                                summary.createSnapshot(),
                                history.createSnapshot(),
                                latestRestoredCheckpoint);

                latestSnapshot = snapshot;

                dirty = false;
            } finally {
                statsReadWriteLock.unlock();
            }
        }

        return snapshot;
    }

    // ------------------------------------------------------------------------
    // Callbacks
    // ------------------------------------------------------------------------

    /**
     * Creates a new pending checkpoint tracker.
     *
     * @param checkpointId ID of the checkpoint.
     * @param triggerTimestamp Trigger timestamp of the checkpoint.
     * @param props The checkpoint properties.
     * @param vertexToDop mapping of {@link JobVertexID} to DOP
     * @return Tracker for statistics gathering.
     */
    PendingCheckpointStats reportPendingCheckpoint(
            long checkpointId,
            long triggerTimestamp,
            CheckpointProperties props,
            Map<JobVertexID, Integer> vertexToDop) {

        PendingCheckpointStats pending =
                new PendingCheckpointStats(
                        checkpointId,
                        triggerTimestamp,
                        props,
                        vertexToDop,
                        PendingCheckpointStatsCallback.proxyFor(this));

        statsReadWriteLock.lock();
        try {
            counts.incrementInProgressCheckpoints();
            history.addInProgressCheckpoint(pending);

            dirty = true;
        } finally {
            statsReadWriteLock.unlock();
        }

        return pending;
    }

    /**
     * Callback when a checkpoint is restored.
     * 恢复检查点时的回调。
     *
     * @param restored The restored checkpoint stats.
     */
    void reportRestoredCheckpoint(RestoredCheckpointStats restored) {
        checkNotNull(restored, "Restored checkpoint");

        statsReadWriteLock.lock();
        try {
            counts.incrementRestoredCheckpoints();
            latestRestoredCheckpoint = restored;

            dirty = true;
        } finally {
            statsReadWriteLock.unlock();
        }
    }

    /**
     * Callback when a checkpoint completes.
     * 检查点完成时的回调。
     *
     * @param completed The completed checkpoint stats.
     */
    private void reportCompletedCheckpoint(CompletedCheckpointStats completed) {
        statsReadWriteLock.lock();
        try {
            latestCompletedCheckpoint = completed;

            counts.incrementCompletedCheckpoints();
            history.replacePendingCheckpointById(completed);

            summary.updateSummary(completed);

            dirty = true;
        } finally {
            statsReadWriteLock.unlock();
        }
    }

    /**
     * Callback when a checkpoint fails.
     * 检查点失败时的回调。
     *
     * @param failed The failed checkpoint stats.
     */
    private void reportFailedCheckpoint(FailedCheckpointStats failed) {
        statsReadWriteLock.lock();
        try {
            counts.incrementFailedCheckpoints();
            history.replacePendingCheckpointById(failed);

            dirty = true;
        } finally {
            statsReadWriteLock.unlock();
        }
    }

    public PendingCheckpointStats getPendingCheckpointStats(long checkpointId) {
        statsReadWriteLock.lock();
        try {
            AbstractCheckpointStats stats = history.getCheckpointById(checkpointId);
            return stats instanceof PendingCheckpointStats ? (PendingCheckpointStats) stats : null;
        } finally {
            statsReadWriteLock.unlock();
        }
    }

    public void reportIncompleteStats(
            long checkpointId, ExecutionVertex vertex, CheckpointMetrics metrics) {
        statsReadWriteLock.lock();
        try {
            AbstractCheckpointStats stats = history.getCheckpointById(checkpointId);
            if (stats instanceof PendingCheckpointStats) {
                ((PendingCheckpointStats) stats)
                        .reportSubtaskStats(
                                vertex.getJobvertexId(),
                                new SubtaskStateStats(
                                        vertex.getParallelSubtaskIndex(),
                                        System.currentTimeMillis(),
                                        metrics.getTotalBytesPersisted(),
                                        metrics.getSyncDurationMillis(),
                                        metrics.getAsyncDurationMillis(),
                                        metrics.getBytesProcessedDuringAlignment(),
                                        metrics.getBytesPersistedDuringAlignment(),
                                        metrics.getAlignmentDurationNanos() / 1_000_000,
                                        metrics.getCheckpointStartDelayNanos() / 1_000_000,
                                        metrics.getUnalignedCheckpoint(),
                                        false));
                dirty = true;
            }
        } finally {
            statsReadWriteLock.unlock();
        }
    }

    /** Callback for finalization of a pending checkpoint.
     * 用于完成待处理检查点的回调。
     * */
    interface PendingCheckpointStatsCallback {
        /**
         * Report a completed checkpoint.
         *
         * @param completed The completed checkpoint.
         */
        void reportCompletedCheckpoint(CompletedCheckpointStats completed);

        /**
         * Report a failed checkpoint.
         *
         * @param failed The failed checkpoint.
         */
        void reportFailedCheckpoint(FailedCheckpointStats failed);

        static PendingCheckpointStatsCallback proxyFor(
                CheckpointStatsTracker checkpointStatsTracker) {
            return new PendingCheckpointStatsCallback() {
                @Override
                public void reportCompletedCheckpoint(CompletedCheckpointStats completed) {
                    checkpointStatsTracker.reportCompletedCheckpoint(completed);
                }

                @Override
                public void reportFailedCheckpoint(FailedCheckpointStats failed) {
                    checkpointStatsTracker.reportFailedCheckpoint(failed);
                }
            };
        }
    }

    // ------------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------------

    @VisibleForTesting
    static final String NUMBER_OF_CHECKPOINTS_METRIC = "totalNumberOfCheckpoints";

    @VisibleForTesting
    static final String NUMBER_OF_IN_PROGRESS_CHECKPOINTS_METRIC = "numberOfInProgressCheckpoints";

    @VisibleForTesting
    static final String NUMBER_OF_COMPLETED_CHECKPOINTS_METRIC = "numberOfCompletedCheckpoints";

    @VisibleForTesting
    static final String NUMBER_OF_FAILED_CHECKPOINTS_METRIC = "numberOfFailedCheckpoints";

    @VisibleForTesting
    static final String LATEST_RESTORED_CHECKPOINT_TIMESTAMP_METRIC =
            "lastCheckpointRestoreTimestamp";

    @VisibleForTesting
    static final String LATEST_COMPLETED_CHECKPOINT_SIZE_METRIC = "lastCheckpointSize";

    @VisibleForTesting
    static final String LATEST_COMPLETED_CHECKPOINT_DURATION_METRIC = "lastCheckpointDuration";

    @VisibleForTesting
    static final String LATEST_COMPLETED_CHECKPOINT_PROCESSED_DATA_METRIC =
            "lastCheckpointProcessedData";

    @VisibleForTesting
    static final String LATEST_COMPLETED_CHECKPOINT_PERSISTED_DATA_METRIC =
            "lastCheckpointPersistedData";

    @VisibleForTesting
    static final String LATEST_COMPLETED_CHECKPOINT_EXTERNAL_PATH_METRIC =
            "lastCheckpointExternalPath";

    /**
     * Register the exposed metrics.
     *
     * @param metricGroup Metric group to use for the metrics.
     */
    private void registerMetrics(MetricGroup metricGroup) {
        metricGroup.gauge(NUMBER_OF_CHECKPOINTS_METRIC, new CheckpointsCounter());
        metricGroup.gauge(
                NUMBER_OF_IN_PROGRESS_CHECKPOINTS_METRIC, new InProgressCheckpointsCounter());
        metricGroup.gauge(
                NUMBER_OF_COMPLETED_CHECKPOINTS_METRIC, new CompletedCheckpointsCounter());
        metricGroup.gauge(NUMBER_OF_FAILED_CHECKPOINTS_METRIC, new FailedCheckpointsCounter());
        metricGroup.gauge(
                LATEST_RESTORED_CHECKPOINT_TIMESTAMP_METRIC,
                new LatestRestoredCheckpointTimestampGauge());
        metricGroup.gauge(
                LATEST_COMPLETED_CHECKPOINT_SIZE_METRIC, new LatestCompletedCheckpointSizeGauge());
        metricGroup.gauge(
                LATEST_COMPLETED_CHECKPOINT_DURATION_METRIC,
                new LatestCompletedCheckpointDurationGauge());
        metricGroup.gauge(
                LATEST_COMPLETED_CHECKPOINT_PROCESSED_DATA_METRIC,
                new LatestCompletedCheckpointProcessedDataGauge());
        metricGroup.gauge(
                LATEST_COMPLETED_CHECKPOINT_PERSISTED_DATA_METRIC,
                new LatestCompletedCheckpointPersistedDataGauge());
        metricGroup.gauge(
                LATEST_COMPLETED_CHECKPOINT_EXTERNAL_PATH_METRIC,
                new LatestCompletedCheckpointExternalPathGauge());
    }

    private class CheckpointsCounter implements Gauge<Long> {
        @Override
        public Long getValue() {
            return counts.getTotalNumberOfCheckpoints();
        }
    }

    private class InProgressCheckpointsCounter implements Gauge<Integer> {
        @Override
        public Integer getValue() {
            return counts.getNumberOfInProgressCheckpoints();
        }
    }

    private class CompletedCheckpointsCounter implements Gauge<Long> {
        @Override
        public Long getValue() {
            return counts.getNumberOfCompletedCheckpoints();
        }
    }

    private class FailedCheckpointsCounter implements Gauge<Long> {
        @Override
        public Long getValue() {
            return counts.getNumberOfFailedCheckpoints();
        }
    }

    private class LatestRestoredCheckpointTimestampGauge implements Gauge<Long> {
        @Override
        public Long getValue() {
            RestoredCheckpointStats restored = latestRestoredCheckpoint;
            if (restored != null) {
                return restored.getRestoreTimestamp();
            } else {
                return -1L;
            }
        }
    }

    private class LatestCompletedCheckpointSizeGauge implements Gauge<Long> {
        @Override
        public Long getValue() {
            CompletedCheckpointStats completed = latestCompletedCheckpoint;
            if (completed != null) {
                return completed.getStateSize();
            } else {
                return -1L;
            }
        }
    }

    private class LatestCompletedCheckpointDurationGauge implements Gauge<Long> {
        @Override
        public Long getValue() {
            CompletedCheckpointStats completed = latestCompletedCheckpoint;
            if (completed != null) {
                return completed.getEndToEndDuration();
            } else {
                return -1L;
            }
        }
    }

    private class LatestCompletedCheckpointProcessedDataGauge implements Gauge<Long> {
        @Override
        public Long getValue() {
            CompletedCheckpointStats completed = latestCompletedCheckpoint;
            if (completed != null) {
                return completed.getProcessedData();
            } else {
                return -1L;
            }
        }
    }

    private class LatestCompletedCheckpointPersistedDataGauge implements Gauge<Long> {
        @Override
        public Long getValue() {
            CompletedCheckpointStats completed = latestCompletedCheckpoint;
            if (completed != null) {
                return completed.getPersistedData();
            } else {
                return -1L;
            }
        }
    }

    private class LatestCompletedCheckpointExternalPathGauge implements Gauge<String> {
        @Override
        public String getValue() {
            CompletedCheckpointStats completed = latestCompletedCheckpoint;
            if (completed != null && completed.getExternalPath() != null) {
                return completed.getExternalPath();
            } else {
                return "n/a";
            }
        }
    }
}
