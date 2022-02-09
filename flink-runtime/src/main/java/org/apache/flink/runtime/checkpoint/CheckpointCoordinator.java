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
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.checkpoint.CheckpointType.PostCheckpointAction;
import org.apache.flink.runtime.checkpoint.hooks.MasterHooks;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.concurrent.ScheduledExecutor;
import org.apache.flink.runtime.executiongraph.Execution;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.executiongraph.ExecutionJobVertex;
import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.executiongraph.JobStatusListener;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.jobgraph.tasks.CheckpointCoordinatorConfiguration;
import org.apache.flink.runtime.messages.checkpoint.AcknowledgeCheckpoint;
import org.apache.flink.runtime.messages.checkpoint.DeclineCheckpoint;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.runtime.operators.coordination.OperatorInfo;
import org.apache.flink.runtime.persistence.PossibleInconsistentStateException;
import org.apache.flink.runtime.state.CheckpointStorage;
import org.apache.flink.runtime.state.CheckpointStorageCoordinatorView;
import org.apache.flink.runtime.state.CheckpointStorageLocation;
import org.apache.flink.runtime.state.CompletedCheckpointStorageLocation;
import org.apache.flink.runtime.state.SharedStateRegistry;
import org.apache.flink.runtime.state.SharedStateRegistryFactory;
import org.apache.flink.runtime.state.memory.ByteStreamStateHandle;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FlinkRuntimeException;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.StringUtils;
import org.apache.flink.util.clock.Clock;
import org.apache.flink.util.clock.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.apache.flink.util.ExceptionUtils.findThrowable;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The checkpoint coordinator coordinates the distributed snapshots of operators and state. It
 * triggers the checkpoint by sending the messages to the relevant tasks and collects the checkpoint
 * acknowledgements. It also collects and maintains the overview of the state handles reported by
 * the tasks that acknowledge the checkpoint.
 * 检查点协调器协调操作员和状态的分布式快照。 它通过向相关任务发送消息来触发检查点并收集检查点确认。
 * 它还收集和维护确认检查点的任务报告的状态句柄的概述。
 */
public class CheckpointCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

    /** The number of recent checkpoints whose IDs are remembered.
     * 记住其 ID 的最近检查点的数量。
     * */
    private static final int NUM_GHOST_CHECKPOINT_IDS = 16;

    // ------------------------------------------------------------------------

    /** Coordinator-wide lock to safeguard the checkpoint updates.
     * 协调器范围的锁定以保护检查点更新。
     * */
    private final Object lock = new Object();

    /** The job whose checkpoint this coordinator coordinates.
     * 此协调器协调其检查点的作业。
     * */
    private final JobID job;

    /** Default checkpoint properties.
     * 默认检查点属性。
     * */
    private final CheckpointProperties checkpointProperties;

    /** The executor used for asynchronous calls, like potentially blocking I/O.
     * 用于异步调用的执行器，例如潜在的阻塞 I/O。
     * */
    private final Executor executor;

    private final CheckpointsCleaner checkpointsCleaner;

    /** The operator coordinators that need to be checkpointed.
     * 需要检查点的操作员协调员。
     * */
    private final Collection<OperatorCoordinatorCheckpointContext> coordinatorsToCheckpoint;

    /** Map from checkpoint ID to the pending checkpoint.
     * 从检查点 ID 映射到待处理检查点。
     * */
    @GuardedBy("lock")
    private final Map<Long, PendingCheckpoint> pendingCheckpoints;

    /**
     * Completed checkpoints. Implementations can be blocking. Make sure calls to methods accessing
     * this don't block the job manager actor and run asynchronously.
     * 已完成检查站。 实现可能是阻塞的。 确保对访问它的方法的调用不会阻塞作业管理器参与者并异步运行。
     */
    private final CompletedCheckpointStore completedCheckpointStore;

    /**
     * The root checkpoint state backend, which is responsible for initializing the checkpoint,
     * storing the metadata, and cleaning up the checkpoint.
     * 根检查点状态后端，负责初始化检查点、存储元数据和清理检查点。
     */
    private final CheckpointStorageCoordinatorView checkpointStorageView;

    /** A list of recent checkpoint IDs, to identify late messages (vs invalid ones).
     * 最近检查点 ID 的列表，用于识别延迟消息（相对于无效消息）。
     * */
    private final ArrayDeque<Long> recentPendingCheckpoints;

    /**
     * Checkpoint ID counter to ensure ascending IDs. In case of job manager failures, these need to
     * be ascending across job managers.
     * 检查点 ID 计数器，以确保升序 ID。 在作业管理器失败的情况下，这些需要在作业管理器中提升。
     */
    private final CheckpointIDCounter checkpointIdCounter;

    /**
     * The base checkpoint interval. Actual trigger time may be affected by the max concurrent
     * checkpoints and minimum-pause values
     * 基本检查点间隔。 实际触发时间可能会受到最大并发检查点和最小暂停值的影响
     */
    private final long baseInterval;

    /** The max time (in ms) that a checkpoint may take.
     * 检查点可能花费的最长时间（以毫秒为单位）。
     * */
    private final long checkpointTimeout;

    /**
     * The min time(in ms) to delay after a checkpoint could be triggered. Allows to enforce minimum
     * processing time between checkpoint attempts
     * 可以触发检查点后延迟的最小时间（以毫秒为单位）。 允许在检查点尝试之间强制执行最短处理时间
     */
    private final long minPauseBetweenCheckpoints;

    /**
     * The timer that handles the checkpoint timeouts and triggers periodic checkpoints. It must be
     * single-threaded. Eventually it will be replaced by main thread executor.
     * 处理检查点超时并触发定期检查点的计时器。 它必须是单线程的。 最终它将被主线程执行器取代。
     */
    private final ScheduledExecutor timer;

    /** The master checkpoint hooks executed by this checkpoint coordinator.
     * 由该检查点协调器执行的主检查点挂钩。
     * */
    private final HashMap<String, MasterTriggerRestoreHook<?>> masterHooks;

    private final boolean unalignedCheckpointsEnabled;

    private final long alignmentTimeout;

    /** Actor that receives status updates from the execution graph this coordinator works for.
     * 从该协调器工作的执行图中接收状态更新的 Actor。
     * */
    private JobStatusListener jobStatusListener;

    /** The number of consecutive failed trigger attempts.
     * 连续失败的触发尝试次数。
     * */
    private final AtomicInteger numUnsuccessfulCheckpointsTriggers = new AtomicInteger(0);

    /** A handle to the current periodic trigger, to cancel it when necessary.
     * 当前周期性触发器的句柄，在必要时取消它。
     * */
    private ScheduledFuture<?> currentPeriodicTrigger;

    /**
     * The timestamp (via {@link Clock#relativeTimeMillis()}) when the last checkpoint completed.
     * 最后一个检查点完成时的时间戳（通过 {@link Clock#relativeTimeMillis()}）。
     */
    private long lastCheckpointCompletionRelativeTime;

    /**
     * Flag whether a triggered checkpoint should immediately schedule the next checkpoint.
     * Non-volatile, because only accessed in synchronized scope
     * 标记触发的检查点是否应立即安排下一个检查点。 非易失性，因为仅在同步范围内访问
     */
    private boolean periodicScheduling;

    /** Flag marking the coordinator as shut down (not accepting any messages any more).
     * 将协调器标记为已关闭（不再接受任何消息）。
     * */
    private volatile boolean shutdown;

    /** Optional tracker for checkpoint statistics.
     * 检查点统计信息的可选跟踪器。
     * */
    @Nullable private CheckpointStatsTracker statsTracker;

    /** A factory for SharedStateRegistry objects.
     * SharedStateRegistry 对象的工厂。
     * */
    private final SharedStateRegistryFactory sharedStateRegistryFactory;

    /** Registry that tracks state which is shared across (incremental) checkpoints.
     * 跟踪在（增量）检查点之间共享的状态的注册表。
     * */
    private SharedStateRegistry sharedStateRegistry;

    private boolean isPreferCheckpointForRecovery;

    private final CheckpointFailureManager failureManager;

    private final Clock clock;

    private final boolean isExactlyOnceMode;

    /** Flag represents there is an in-flight trigger request.
     * 标志表示有一个正在进行的触发请求。
     * */
    private boolean isTriggering = false;

    private final CheckpointRequestDecider requestDecider;

    private final CheckpointPlanCalculator checkpointPlanCalculator;

    private final ExecutionAttemptMappingProvider attemptMappingProvider;

    // --------------------------------------------------------------------------------------------

    public CheckpointCoordinator(
            JobID job,
            CheckpointCoordinatorConfiguration chkConfig,
            Collection<OperatorCoordinatorCheckpointContext> coordinatorsToCheckpoint,
            CheckpointIDCounter checkpointIDCounter,
            CompletedCheckpointStore completedCheckpointStore,
            CheckpointStorage checkpointStorage,
            Executor executor,
            CheckpointsCleaner checkpointsCleaner,
            ScheduledExecutor timer,
            SharedStateRegistryFactory sharedStateRegistryFactory,
            CheckpointFailureManager failureManager,
            CheckpointPlanCalculator checkpointPlanCalculator,
            ExecutionAttemptMappingProvider attemptMappingProvider) {

        this(
                job,
                chkConfig,
                coordinatorsToCheckpoint,
                checkpointIDCounter,
                completedCheckpointStore,
                checkpointStorage,
                executor,
                checkpointsCleaner,
                timer,
                sharedStateRegistryFactory,
                failureManager,
                checkpointPlanCalculator,
                attemptMappingProvider,
                SystemClock.getInstance());
    }

    @VisibleForTesting
    public CheckpointCoordinator(
            JobID job,
            CheckpointCoordinatorConfiguration chkConfig,
            Collection<OperatorCoordinatorCheckpointContext> coordinatorsToCheckpoint,
            CheckpointIDCounter checkpointIDCounter,
            CompletedCheckpointStore completedCheckpointStore,
            CheckpointStorage checkpointStorage,
            Executor executor,
            CheckpointsCleaner checkpointsCleaner,
            ScheduledExecutor timer,
            SharedStateRegistryFactory sharedStateRegistryFactory,
            CheckpointFailureManager failureManager,
            CheckpointPlanCalculator checkpointPlanCalculator,
            ExecutionAttemptMappingProvider attemptMappingProvider,
            Clock clock) {

        // sanity checks
        checkNotNull(checkpointStorage);

        // max "in between duration" can be one year - this is to prevent numeric overflows
        long minPauseBetweenCheckpoints = chkConfig.getMinPauseBetweenCheckpoints();
        if (minPauseBetweenCheckpoints > 365L * 24 * 60 * 60 * 1_000) {
            minPauseBetweenCheckpoints = 365L * 24 * 60 * 60 * 1_000;
        }

        // it does not make sense to schedule checkpoints more often then the desired
        // time between checkpoints
        long baseInterval = chkConfig.getCheckpointInterval();
        if (baseInterval < minPauseBetweenCheckpoints) {
            baseInterval = minPauseBetweenCheckpoints;
        }

        this.job = checkNotNull(job);
        this.baseInterval = baseInterval;
        this.checkpointTimeout = chkConfig.getCheckpointTimeout();
        this.minPauseBetweenCheckpoints = minPauseBetweenCheckpoints;
        this.coordinatorsToCheckpoint =
                Collections.unmodifiableCollection(coordinatorsToCheckpoint);
        this.pendingCheckpoints = new LinkedHashMap<>();
        this.checkpointIdCounter = checkNotNull(checkpointIDCounter);
        this.completedCheckpointStore = checkNotNull(completedCheckpointStore);
        this.executor = checkNotNull(executor);
        this.checkpointsCleaner = checkNotNull(checkpointsCleaner);
        this.sharedStateRegistryFactory = checkNotNull(sharedStateRegistryFactory);
        this.sharedStateRegistry = sharedStateRegistryFactory.create(executor);
        this.isPreferCheckpointForRecovery = chkConfig.isPreferCheckpointForRecovery();
        this.failureManager = checkNotNull(failureManager);
        this.checkpointPlanCalculator = checkNotNull(checkpointPlanCalculator);
        this.attemptMappingProvider = checkNotNull(attemptMappingProvider);
        this.clock = checkNotNull(clock);
        this.isExactlyOnceMode = chkConfig.isExactlyOnce();
        this.unalignedCheckpointsEnabled = chkConfig.isUnalignedCheckpointsEnabled();
        this.alignmentTimeout = chkConfig.getAlignmentTimeout();

        this.recentPendingCheckpoints = new ArrayDeque<>(NUM_GHOST_CHECKPOINT_IDS);
        this.masterHooks = new HashMap<>();

        this.timer = timer;

        this.checkpointProperties =
                CheckpointProperties.forCheckpoint(chkConfig.getCheckpointRetentionPolicy());

        try {
            this.checkpointStorageView = checkpointStorage.createCheckpointStorage(job);
            checkpointStorageView.initializeBaseLocations();
        } catch (IOException e) {
            throw new FlinkRuntimeException(
                    "Failed to create checkpoint storage at checkpoint coordinator side.", e);
        }

        try {
            // Make sure the checkpoint ID enumerator is running. Possibly
            // issues a blocking call to ZooKeeper.
            checkpointIDCounter.start();
        } catch (Throwable t) {
            throw new RuntimeException(
                    "Failed to start checkpoint ID counter: " + t.getMessage(), t);
        }
        this.requestDecider =
                new CheckpointRequestDecider(
                        chkConfig.getMaxConcurrentCheckpoints(),
                        this::rescheduleTrigger,
                        this.clock,
                        this.minPauseBetweenCheckpoints,
                        this.pendingCheckpoints::size,
                        this.checkpointsCleaner::getNumberOfCheckpointsToClean);
    }

    // --------------------------------------------------------------------------------------------
    //  Configuration
    // --------------------------------------------------------------------------------------------

    /**
     * Adds the given master hook to the checkpoint coordinator. This method does nothing, if the
     * checkpoint coordinator already contained a hook with the same ID (as defined via {@link
     * MasterTriggerRestoreHook#getIdentifier()}).
     * 将给定的主钩子添加到检查点协调器。 如果检查点协调器已经包含具有相同 ID 的钩子
     * （通过 {@link MasterTriggerRestoreHook#getIdentifier()} 定义），则此方法不执行任何操作。
     *
     * @param hook The hook to add.
     * @return True, if the hook was added, false if the checkpoint coordinator already contained a
     *     hook with the same ID.
     */
    public boolean addMasterHook(MasterTriggerRestoreHook<?> hook) {
        checkNotNull(hook);

        final String id = hook.getIdentifier();
        checkArgument(!StringUtils.isNullOrWhitespaceOnly(id), "The hook has a null or empty id");

        synchronized (lock) {
            if (!masterHooks.containsKey(id)) {
                masterHooks.put(id, hook);
                return true;
            } else {
                return false;
            }
        }
    }

    /** Gets the number of currently register master hooks. */
    public int getNumberOfRegisteredMasterHooks() {
        synchronized (lock) {
            return masterHooks.size();
        }
    }

    /**
     * Sets the checkpoint stats tracker.
     *
     * @param statsTracker The checkpoint stats tracker.
     */
    public void setCheckpointStatsTracker(@Nullable CheckpointStatsTracker statsTracker) {
        this.statsTracker = statsTracker;
    }

    // --------------------------------------------------------------------------------------------
    //  Clean shutdown
    // --------------------------------------------------------------------------------------------

    /**
     * Shuts down the checkpoint coordinator.
     *
     * <p>After this method has been called, the coordinator does not accept and further messages
     * and cannot trigger any further checkpoints.
     */
    public void shutdown() throws Exception {
        synchronized (lock) {
            if (!shutdown) {
                shutdown = true;
                LOG.info("Stopping checkpoint coordinator for job {}.", job);

                periodicScheduling = false;

                // shut down the hooks
                MasterHooks.close(masterHooks.values(), LOG);
                masterHooks.clear();

                final CheckpointException reason =
                        new CheckpointException(
                                CheckpointFailureReason.CHECKPOINT_COORDINATOR_SHUTDOWN);
                // clear queued requests and in-flight checkpoints
                abortPendingAndQueuedCheckpoints(reason);
            }
        }
    }

    public boolean isShutdown() {
        return shutdown;
    }

    // --------------------------------------------------------------------------------------------
    //  Triggering Checkpoints and Savepoints
    // --------------------------------------------------------------------------------------------

    /**
     * Triggers a savepoint with the given savepoint directory as a target.
     *
     * @param targetLocation Target location for the savepoint, optional. If null, the state
     *     backend's configured default will be used.
     * @return A future to the completed checkpoint
     * @throws IllegalStateException If no savepoint directory has been specified and no default
     *     savepoint directory has been configured
     */
    public CompletableFuture<CompletedCheckpoint> triggerSavepoint(
            @Nullable final String targetLocation) {
        final CheckpointProperties properties =
                CheckpointProperties.forSavepoint(!unalignedCheckpointsEnabled);
        return triggerSavepointInternal(properties, targetLocation);
    }

    /**
     * Triggers a synchronous savepoint with the given savepoint directory as a target.
     *
     * @param terminate flag indicating if the job should terminate or just suspend
     * @param targetLocation Target location for the savepoint, optional. If null, the state
     *     backend's configured default will be used.
     * @return A future to the completed checkpoint
     * @throws IllegalStateException If no savepoint directory has been specified and no default
     *     savepoint directory has been configured
     */
    public CompletableFuture<CompletedCheckpoint> triggerSynchronousSavepoint(
            final boolean terminate, @Nullable final String targetLocation) {

        final CheckpointProperties properties =
                CheckpointProperties.forSyncSavepoint(!unalignedCheckpointsEnabled, terminate);

        return triggerSavepointInternal(properties, targetLocation);
    }

    private CompletableFuture<CompletedCheckpoint> triggerSavepointInternal(
            final CheckpointProperties checkpointProperties,
            @Nullable final String targetLocation) {

        checkNotNull(checkpointProperties);

        // TODO, call triggerCheckpoint directly after removing timer thread
        // for now, execute the trigger in timer thread to avoid competition
        final CompletableFuture<CompletedCheckpoint> resultFuture = new CompletableFuture<>();
        timer.execute(
                () ->
                        triggerCheckpoint(checkpointProperties, targetLocation, false)
                                .whenComplete(
                                        (completedCheckpoint, throwable) -> {
                                            if (throwable == null) {
                                                resultFuture.complete(completedCheckpoint);
                                            } else {
                                                resultFuture.completeExceptionally(throwable);
                                            }
                                        }));
        return resultFuture;
    }

    /**
     * Triggers a new standard checkpoint and uses the given timestamp as the checkpoint timestamp.
     * The return value is a future. It completes when the checkpoint triggered finishes or an error
     * occurred.
     * 触发新的标准检查点并将给定的时间戳用作检查点时间戳。 返回值是一个未来。
     * 它在触发的检查点完成或发生错误时完成。
     *
     * @param isPeriodic Flag indicating whether this triggered checkpoint is periodic. If this flag
     *     is true, but the periodic scheduler is disabled, the checkpoint will be declined.
     * @return a future to the completed checkpoint.
     */
    public CompletableFuture<CompletedCheckpoint> triggerCheckpoint(boolean isPeriodic) {
        return triggerCheckpoint(checkpointProperties, null, isPeriodic);
    }

    @VisibleForTesting
    public CompletableFuture<CompletedCheckpoint> triggerCheckpoint(
            CheckpointProperties props,
            @Nullable String externalSavepointLocation,
            boolean isPeriodic) {

        if (props.getCheckpointType().getPostCheckpointAction() == PostCheckpointAction.TERMINATE
                && !(props.isSynchronous() && props.isSavepoint())) {
            return FutureUtils.completedExceptionally(
                    new IllegalArgumentException(
                            "Only synchronous savepoints are allowed to advance the watermark to MAX."));
        }

        CheckpointTriggerRequest request =
                new CheckpointTriggerRequest(props, externalSavepointLocation, isPeriodic);
        chooseRequestToExecute(request).ifPresent(this::startTriggeringCheckpoint);
        return request.onCompletionPromise;
    }

    private void startTriggeringCheckpoint(CheckpointTriggerRequest request) {
        try {
            synchronized (lock) {
                preCheckGlobalState(request.isPeriodic);
            }

            // we will actually trigger this checkpoint!
            Preconditions.checkState(!isTriggering);
            isTriggering = true;

            final long timestamp = System.currentTimeMillis();

            CompletableFuture<CheckpointPlan> checkpointPlanFuture =
                    checkpointPlanCalculator.calculateCheckpointPlan();

            final CompletableFuture<PendingCheckpoint> pendingCheckpointCompletableFuture =
                    checkpointPlanFuture
                            .thenApplyAsync(
                                    plan -> {
                                        try {
                                            CheckpointIdAndStorageLocation
                                                    checkpointIdAndStorageLocation =
                                                            initializeCheckpoint(
                                                                    request.props,
                                                                    request.externalSavepointLocation);
                                            return new Tuple2<>(
                                                    plan, checkpointIdAndStorageLocation);
                                        } catch (Throwable e) {
                                            throw new CompletionException(e);
                                        }
                                    },
                                    executor)
                            .thenApplyAsync(
                                    (checkpointInfo) ->
                                            createPendingCheckpoint(
                                                    timestamp,
                                                    request.props,
                                                    checkpointInfo.f0,
                                                    request.isPeriodic,
                                                    checkpointInfo.f1.checkpointId,
                                                    checkpointInfo.f1.checkpointStorageLocation,
                                                    request.getOnCompletionFuture()),
                                    timer);

            final CompletableFuture<?> coordinatorCheckpointsComplete =
                    pendingCheckpointCompletableFuture.thenComposeAsync(
                            (pendingCheckpoint) ->
                                    OperatorCoordinatorCheckpoints
                                            .triggerAndAcknowledgeAllCoordinatorCheckpointsWithCompletion(
                                                    coordinatorsToCheckpoint,
                                                    pendingCheckpoint,
                                                    timer),
                            timer);

            // We have to take the snapshot of the master hooks after the coordinator checkpoints
            // has completed.
            // This is to ensure the tasks are checkpointed after the OperatorCoordinators in case
            // ExternallyInducedSource is used.
            final CompletableFuture<?> masterStatesComplete =
                    coordinatorCheckpointsComplete.thenComposeAsync(
                            ignored -> {
                                // If the code reaches here, the pending checkpoint is guaranteed to
                                // be not null.
                                // We use FutureUtils.getWithoutException() to make compiler happy
                                // with checked
                                // exceptions in the signature.
                                PendingCheckpoint checkpoint =
                                        FutureUtils.getWithoutException(
                                                pendingCheckpointCompletableFuture);
                                return snapshotMasterState(checkpoint);
                            },
                            timer);

            FutureUtils.assertNoException(
                    CompletableFuture.allOf(masterStatesComplete, coordinatorCheckpointsComplete)
                            .handleAsync(
                                    (ignored, throwable) -> {
                                        final PendingCheckpoint checkpoint =
                                                FutureUtils.getWithoutException(
                                                        pendingCheckpointCompletableFuture);

                                        Preconditions.checkState(
                                                checkpoint != null || throwable != null,
                                                "Either the pending checkpoint needs to be created or an error must have been occurred.");

                                        if (throwable != null) {
                                            // the initialization might not be finished yet
                                            if (checkpoint == null) {
                                                onTriggerFailure(request, throwable);
                                            } else {
                                                onTriggerFailure(checkpoint, throwable);
                                            }
                                        } else {
                                            if (checkpoint.isDisposed()) {
                                                onTriggerFailure(
                                                        checkpoint,
                                                        new CheckpointException(
                                                                CheckpointFailureReason
                                                                        .TRIGGER_CHECKPOINT_FAILURE,
                                                                checkpoint.getFailureCause()));
                                            } else {
                                                // no exception, no discarding, everything is OK
                                                final long checkpointId =
                                                        checkpoint.getCheckpointId();
                                                snapshotTaskState(
                                                        timestamp,
                                                        checkpointId,
                                                        checkpoint.getCheckpointStorageLocation(),
                                                        request.props,
                                                        checkpoint
                                                                .getCheckpointPlan()
                                                                .getTasksToTrigger());

                                                coordinatorsToCheckpoint.forEach(
                                                        (ctx) ->
                                                                ctx.afterSourceBarrierInjection(
                                                                        checkpointId));
                                                // It is possible that the tasks has finished
                                                // checkpointing at this point.
                                                // So we need to complete this pending checkpoint.
                                                if (!maybeCompleteCheckpoint(checkpoint)) {
                                                    return null;
                                                }
                                                onTriggerSuccess();
                                            }
                                        }
                                        return null;
                                    },
                                    timer)
                            .exceptionally(
                                    error -> {
                                        if (!isShutdown()) {
                                            throw new CompletionException(error);
                                        } else if (findThrowable(
                                                        error, RejectedExecutionException.class)
                                                .isPresent()) {
                                            LOG.debug("Execution rejected during shutdown");
                                        } else {
                                            LOG.warn("Error encountered during shutdown", error);
                                        }
                                        return null;
                                    }));
        } catch (Throwable throwable) {
            onTriggerFailure(request, throwable);
        }
    }

    /**
     * Initialize the checkpoint trigger asynchronously. It will expected to be executed in io
     * thread due to it might be time-consuming.
     * 异步初始化检查点触发器。 由于它可能很耗时，因此预计它将在 io 线程中执行。
     *
     * @param props checkpoint properties
     * @param externalSavepointLocation the external savepoint location, it might be null
     * @return the initialized result, checkpoint id and checkpoint location
     */
    private CheckpointIdAndStorageLocation initializeCheckpoint(
            CheckpointProperties props, @Nullable String externalSavepointLocation)
            throws Exception {

        // this must happen outside the coordinator-wide lock, because it
        // communicates
        // with external services (in HA mode) and may block for a while.
        // 这必须发生在协调器范围的锁之外，因为它与外部服务通信（在 HA 模式下）并且可能会阻塞一段时间。
        long checkpointID = checkpointIdCounter.getAndIncrement();

        CheckpointStorageLocation checkpointStorageLocation =
                props.isSavepoint()
                        ? checkpointStorageView.initializeLocationForSavepoint(
                                checkpointID, externalSavepointLocation)
                        : checkpointStorageView.initializeLocationForCheckpoint(checkpointID);

        return new CheckpointIdAndStorageLocation(checkpointID, checkpointStorageLocation);
    }

    private PendingCheckpoint createPendingCheckpoint(
            long timestamp,
            CheckpointProperties props,
            CheckpointPlan checkpointPlan,
            boolean isPeriodic,
            long checkpointID,
            CheckpointStorageLocation checkpointStorageLocation,
            CompletableFuture<CompletedCheckpoint> onCompletionPromise) {

        synchronized (lock) {
            try {
                // since we haven't created the PendingCheckpoint yet, we need to check the
                // global state here.
                // 由于我们还没有创建 PendingCheckpoint，我们需要在这里检查全局状态。
                preCheckGlobalState(isPeriodic);
            } catch (Throwable t) {
                throw new CompletionException(t);
            }
        }

        final PendingCheckpoint checkpoint =
                new PendingCheckpoint(
                        job,
                        checkpointID,
                        timestamp,
                        checkpointPlan,
                        OperatorInfo.getIds(coordinatorsToCheckpoint),
                        masterHooks.keySet(),
                        props,
                        checkpointStorageLocation,
                        onCompletionPromise);

        trackPendingCheckpointStats(checkpoint);

        synchronized (lock) {
            pendingCheckpoints.put(checkpointID, checkpoint);

            ScheduledFuture<?> cancellerHandle =
                    timer.schedule(
                            new CheckpointCanceller(checkpoint),
                            checkpointTimeout,
                            TimeUnit.MILLISECONDS);

            if (!checkpoint.setCancellerHandle(cancellerHandle)) {
                // checkpoint is already disposed!
                cancellerHandle.cancel(false);
            }
        }

        LOG.info(
                "Triggering checkpoint {} (type={}) @ {} for job {}.",
                checkpointID,
                checkpoint.getProps().getCheckpointType(),
                timestamp,
                job);
        return checkpoint;
    }

    /**
     * Snapshot master hook states asynchronously.
     * 快照主挂钩状态异步。
     *
     * @param checkpoint the pending checkpoint
     * @return the future represents master hook states are finished or not
     */
    private CompletableFuture<Void> snapshotMasterState(PendingCheckpoint checkpoint) {
        if (masterHooks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        final long checkpointID = checkpoint.getCheckpointId();
        final long timestamp = checkpoint.getCheckpointTimestamp();

        final CompletableFuture<Void> masterStateCompletableFuture = new CompletableFuture<>();
        for (MasterTriggerRestoreHook<?> masterHook : masterHooks.values()) {
            MasterHooks.triggerHook(masterHook, checkpointID, timestamp, executor)
                    .whenCompleteAsync(
                            (masterState, throwable) -> {
                                try {
                                    synchronized (lock) {
                                        if (masterStateCompletableFuture.isDone()) {
                                            return;
                                        }
                                        if (checkpoint.isDisposed()) {
                                            throw new IllegalStateException(
                                                    "Checkpoint "
                                                            + checkpointID
                                                            + " has been discarded");
                                        }
                                        if (throwable == null) {
                                            checkpoint.acknowledgeMasterState(
                                                    masterHook.getIdentifier(), masterState);
                                            if (checkpoint.areMasterStatesFullyAcknowledged()) {
                                                masterStateCompletableFuture.complete(null);
                                            }
                                        } else {
                                            masterStateCompletableFuture.completeExceptionally(
                                                    throwable);
                                        }
                                    }
                                } catch (Throwable t) {
                                    masterStateCompletableFuture.completeExceptionally(t);
                                }
                            },
                            timer);
        }
        return masterStateCompletableFuture;
    }

    /**
     * Snapshot task state.
     *
     * @param timestamp the timestamp of this checkpoint reques
     * @param checkpointID the checkpoint id
     * @param checkpointStorageLocation the checkpoint location
     * @param props the checkpoint properties
     * @param tasksToTrigger the executions which should be triggered
     */
    private void snapshotTaskState(
            long timestamp,
            long checkpointID,
            CheckpointStorageLocation checkpointStorageLocation,
            CheckpointProperties props,
            List<Execution> tasksToTrigger) {

        final CheckpointOptions checkpointOptions =
                CheckpointOptions.forConfig(
                        props.getCheckpointType(),
                        checkpointStorageLocation.getLocationReference(),
                        isExactlyOnceMode,
                        unalignedCheckpointsEnabled,
                        alignmentTimeout);

        // send the messages to the tasks that trigger their checkpoint
        for (Execution execution : tasksToTrigger) {
            if (props.isSynchronous()) {
                execution.triggerSynchronousSavepoint(checkpointID, timestamp, checkpointOptions);
            } else {
                execution.triggerCheckpoint(checkpointID, timestamp, checkpointOptions);
            }
        }
    }

    /** Trigger request is successful. NOTE, it must be invoked if trigger request is successful.
     * 触发请求成功。 注意，如果触发请求成功，则必须调用它。
     * */
    private void onTriggerSuccess() {
        isTriggering = false;
        numUnsuccessfulCheckpointsTriggers.set(0);
        executeQueuedRequest();
    }

    /**
     * The trigger request is failed prematurely without a proper initialization. There is no
     * resource to release, but the completion promise needs to fail manually here.
     * 触发请求在没有正确初始化的情况下过早失败。 没有资源可以释放，但是完成promise需要在这里手动失败。
     *
     * @param onCompletionPromise the completion promise of the checkpoint/savepoint
     * @param throwable the reason of trigger failure
     */
    private void onTriggerFailure(
            CheckpointTriggerRequest onCompletionPromise, Throwable throwable) {
        final CheckpointException checkpointException =
                getCheckpointException(
                        CheckpointFailureReason.TRIGGER_CHECKPOINT_FAILURE, throwable);
        onCompletionPromise.completeExceptionally(checkpointException);
        onTriggerFailure((PendingCheckpoint) null, checkpointException);
    }

    /**
     * The trigger request is failed. NOTE, it must be invoked if trigger request is failed.
     * 触发请求失败。 注意，如果触发请求失败，则必须调用它。
     *
     * @param checkpoint the pending checkpoint which is failed. It could be null if it's failed
     *     prematurely without a proper initialization.
     * @param throwable the reason of trigger failure
     */
    private void onTriggerFailure(@Nullable PendingCheckpoint checkpoint, Throwable throwable) {
        // beautify the stack trace a bit
        throwable = ExceptionUtils.stripCompletionException(throwable);

        try {
            coordinatorsToCheckpoint.forEach(
                    OperatorCoordinatorCheckpointContext::abortCurrentTriggering);

            if (checkpoint != null && !checkpoint.isDisposed()) {
                int numUnsuccessful = numUnsuccessfulCheckpointsTriggers.incrementAndGet();
                LOG.warn(
                        "Failed to trigger checkpoint {} for job {}. ({} consecutive failed attempts so far)",
                        checkpoint.getCheckpointId(),
                        job,
                        numUnsuccessful,
                        throwable);
                final CheckpointException cause =
                        getCheckpointException(
                                CheckpointFailureReason.TRIGGER_CHECKPOINT_FAILURE, throwable);
                synchronized (lock) {
                    abortPendingCheckpoint(checkpoint, cause);
                }
            } else {
                LOG.info(
                        "Failed to trigger checkpoint for job {} since {}",
                        job,
                        throwable.getMessage());
            }
        } finally {
            isTriggering = false;
            executeQueuedRequest();
        }
    }

    private void executeQueuedRequest() {
        chooseQueuedRequestToExecute().ifPresent(this::startTriggeringCheckpoint);
    }

    private Optional<CheckpointTriggerRequest> chooseQueuedRequestToExecute() {
        synchronized (lock) {
            return requestDecider.chooseQueuedRequestToExecute(
                    isTriggering, lastCheckpointCompletionRelativeTime);
        }
    }

    private Optional<CheckpointTriggerRequest> chooseRequestToExecute(
            CheckpointTriggerRequest request) {
        synchronized (lock) {
            return requestDecider.chooseRequestToExecute(
                    request, isTriggering, lastCheckpointCompletionRelativeTime);
        }
    }

    // Returns true if the checkpoint is successfully completed, false otherwise.
    // 如果检查点成功完成，则返回 true，否则返回 false。
    private boolean maybeCompleteCheckpoint(PendingCheckpoint checkpoint) {
        synchronized (lock) {
            if (checkpoint.isFullyAcknowledged()) {
                try {
                    // we need to check inside the lock for being shutdown as well,
                    // otherwise we get races and invalid error log messages.
                    if (shutdown) {
                        return false;
                    }
                    completePendingCheckpoint(checkpoint);
                } catch (CheckpointException ce) {
                    onTriggerFailure(checkpoint, ce);
                    return false;
                }
            }
        }
        return true;
    }

    // --------------------------------------------------------------------------------------------
    //  Handling checkpoints and messages
    // --------------------------------------------------------------------------------------------

    /**
     * Receives a {@link DeclineCheckpoint} message for a pending checkpoint.
     * 接收待处理检查点的 {@link DeclineCheckpoint} 消息。
     *
     * @param message Checkpoint decline from the task manager
     * @param taskManagerLocationInfo The location info of the decline checkpoint message's sender
     */
    public void receiveDeclineMessage(DeclineCheckpoint message, String taskManagerLocationInfo) {
        if (shutdown || message == null) {
            return;
        }

        if (!job.equals(message.getJob())) {
            throw new IllegalArgumentException(
                    "Received DeclineCheckpoint message for job "
                            + message.getJob()
                            + " from "
                            + taskManagerLocationInfo
                            + " while this coordinator handles job "
                            + job);
        }

        final long checkpointId = message.getCheckpointId();
        final CheckpointException checkpointException =
                message.getSerializedCheckpointException().unwrap();
        final String reason = checkpointException.getMessage();

        PendingCheckpoint checkpoint;

        synchronized (lock) {
            // we need to check inside the lock for being shutdown as well, otherwise we
            // get races and invalid error log messages
            if (shutdown) {
                return;
            }

            checkpoint = pendingCheckpoints.get(checkpointId);

            if (checkpoint != null) {
                Preconditions.checkState(
                        !checkpoint.isDisposed(),
                        "Received message for discarded but non-removed checkpoint "
                                + checkpointId);
                LOG.info(
                        "Decline checkpoint {} by task {} of job {} at {}.",
                        checkpointId,
                        message.getTaskExecutionId(),
                        job,
                        taskManagerLocationInfo,
                        checkpointException.getCause());
                abortPendingCheckpoint(
                        checkpoint, checkpointException, message.getTaskExecutionId());
            } else if (LOG.isDebugEnabled()) {
                if (recentPendingCheckpoints.contains(checkpointId)) {
                    // message is for an unknown checkpoint, or comes too late (checkpoint disposed)
                    LOG.debug(
                            "Received another decline message for now expired checkpoint attempt {} from task {} of job {} at {} : {}",
                            checkpointId,
                            message.getTaskExecutionId(),
                            job,
                            taskManagerLocationInfo,
                            reason);
                } else {
                    // message is for an unknown checkpoint. might be so old that we don't even
                    // remember it any more
                    LOG.debug(
                            "Received decline message for unknown (too old?) checkpoint attempt {} from task {} of job {} at {} : {}",
                            checkpointId,
                            message.getTaskExecutionId(),
                            job,
                            taskManagerLocationInfo,
                            reason);
                }
            }
        }
    }

    /**
     * Receives an AcknowledgeCheckpoint message and returns whether the message was associated with
     * a pending checkpoint.
     * 接收 AcknowledgeCheckpoint 消息并返回该消息是否与挂起的检查点相关联。
     *
     * @param message Checkpoint ack from the task manager
     * @param taskManagerLocationInfo The location of the acknowledge checkpoint message's sender
     * @return Flag indicating whether the ack'd checkpoint was associated with a pending
     *     checkpoint.
     * @throws CheckpointException If the checkpoint cannot be added to the completed checkpoint
     *     store.
     */
    public boolean receiveAcknowledgeMessage(
            AcknowledgeCheckpoint message, String taskManagerLocationInfo)
            throws CheckpointException {
        if (shutdown || message == null) {
            return false;
        }

        if (!job.equals(message.getJob())) {
            LOG.error(
                    "Received wrong AcknowledgeCheckpoint message for job {} from {} : {}",
                    job,
                    taskManagerLocationInfo,
                    message);
            return false;
        }

        final long checkpointId = message.getCheckpointId();

        synchronized (lock) {
            // we need to check inside the lock for being shutdown as well, otherwise we
            // get races and invalid error log messages
            if (shutdown) {
                return false;
            }

            final PendingCheckpoint checkpoint = pendingCheckpoints.get(checkpointId);

            if (checkpoint != null && !checkpoint.isDisposed()) {

                switch (checkpoint.acknowledgeTask(
                        message.getTaskExecutionId(),
                        message.getSubtaskState(),
                        message.getCheckpointMetrics(),
                        getStatsCallback(checkpoint))) {
                    case SUCCESS:
                        LOG.debug(
                                "Received acknowledge message for checkpoint {} from task {} of job {} at {}.",
                                checkpointId,
                                message.getTaskExecutionId(),
                                message.getJob(),
                                taskManagerLocationInfo);

                        if (checkpoint.isFullyAcknowledged()) {
                            completePendingCheckpoint(checkpoint);
                        }
                        break;
                    case DUPLICATE:
                        LOG.debug(
                                "Received a duplicate acknowledge message for checkpoint {}, task {}, job {}, location {}.",
                                message.getCheckpointId(),
                                message.getTaskExecutionId(),
                                message.getJob(),
                                taskManagerLocationInfo);
                        break;
                    case UNKNOWN:
                        LOG.warn(
                                "Could not acknowledge the checkpoint {} for task {} of job {} at {}, "
                                        + "because the task's execution attempt id was unknown. Discarding "
                                        + "the state handle to avoid lingering state.",
                                message.getCheckpointId(),
                                message.getTaskExecutionId(),
                                message.getJob(),
                                taskManagerLocationInfo);

                        discardSubtaskState(
                                message.getJob(),
                                message.getTaskExecutionId(),
                                message.getCheckpointId(),
                                message.getSubtaskState());

                        break;
                    case DISCARDED:
                        LOG.warn(
                                "Could not acknowledge the checkpoint {} for task {} of job {} at {}, "
                                        + "because the pending checkpoint had been discarded. Discarding the "
                                        + "state handle tp avoid lingering state.",
                                message.getCheckpointId(),
                                message.getTaskExecutionId(),
                                message.getJob(),
                                taskManagerLocationInfo);

                        discardSubtaskState(
                                message.getJob(),
                                message.getTaskExecutionId(),
                                message.getCheckpointId(),
                                message.getSubtaskState());
                }

                return true;
            } else if (checkpoint != null) {
                // this should not happen
                throw new IllegalStateException(
                        "Received message for discarded but non-removed checkpoint "
                                + checkpointId);
            } else {
                reportStats(
                        message.getCheckpointId(),
                        message.getTaskExecutionId(),
                        message.getCheckpointMetrics());
                boolean wasPendingCheckpoint;

                // message is for an unknown checkpoint, or comes too late (checkpoint disposed)
                if (recentPendingCheckpoints.contains(checkpointId)) {
                    wasPendingCheckpoint = true;
                    LOG.warn(
                            "Received late message for now expired checkpoint attempt {} from task "
                                    + "{} of job {} at {}.",
                            checkpointId,
                            message.getTaskExecutionId(),
                            message.getJob(),
                            taskManagerLocationInfo);
                } else {
                    LOG.debug(
                            "Received message for an unknown checkpoint {} from task {} of job {} at {}.",
                            checkpointId,
                            message.getTaskExecutionId(),
                            message.getJob(),
                            taskManagerLocationInfo);
                    wasPendingCheckpoint = false;
                }

                // try to discard the state so that we don't have lingering state lying around
                discardSubtaskState(
                        message.getJob(),
                        message.getTaskExecutionId(),
                        message.getCheckpointId(),
                        message.getSubtaskState());

                return wasPendingCheckpoint;
            }
        }
    }

    /**
     * Try to complete the given pending checkpoint.
     * 尝试完成给定的待处理检查点。
     *
     * <p>Important: This method should only be called in the checkpoint lock scope.
     * 重要提示：此方法只能在检查点锁定范围内调用。
     *
     * @param pendingCheckpoint to complete
     * @throws CheckpointException if the completion failed
     */
    private void completePendingCheckpoint(PendingCheckpoint pendingCheckpoint)
            throws CheckpointException {
        final long checkpointId = pendingCheckpoint.getCheckpointId();
        final CompletedCheckpoint completedCheckpoint;

        // As a first step to complete the checkpoint, we register its state with the registry
        Map<OperatorID, OperatorState> operatorStates = pendingCheckpoint.getOperatorStates();
        sharedStateRegistry.registerAll(operatorStates.values());

        try {
            try {
                completedCheckpoint =
                        pendingCheckpoint.finalizeCheckpoint(
                                checkpointsCleaner,
                                this::scheduleTriggerRequest,
                                executor,
                                getStatsCallback(pendingCheckpoint));

                failureManager.handleCheckpointSuccess(pendingCheckpoint.getCheckpointId());
            } catch (Exception e1) {
                // abort the current pending checkpoint if we fails to finalize the pending
                // checkpoint.
                if (!pendingCheckpoint.isDisposed()) {
                    abortPendingCheckpoint(
                            pendingCheckpoint,
                            new CheckpointException(
                                    CheckpointFailureReason.FINALIZE_CHECKPOINT_FAILURE, e1));
                }

                throw new CheckpointException(
                        "Could not finalize the pending checkpoint " + checkpointId + '.',
                        CheckpointFailureReason.FINALIZE_CHECKPOINT_FAILURE,
                        e1);
            }

            // the pending checkpoint must be discarded after the finalization
            Preconditions.checkState(pendingCheckpoint.isDisposed() && completedCheckpoint != null);

            try {
                completedCheckpointStore.addCheckpoint(
                        completedCheckpoint, checkpointsCleaner, this::scheduleTriggerRequest);
            } catch (Exception exception) {
                if (exception instanceof PossibleInconsistentStateException) {
                    LOG.warn(
                            "An error occurred while writing checkpoint {} to the underlying metadata store. Flink was not able to determine whether the metadata was successfully persisted. The corresponding state located at '{}' won't be discarded and needs to be cleaned up manually.",
                            completedCheckpoint.getCheckpointID(),
                            completedCheckpoint.getExternalPointer());
                } else {
                    // we failed to store the completed checkpoint. Let's clean up
                    checkpointsCleaner.cleanCheckpointOnFailedStoring(
                            completedCheckpoint, executor);
                }

                sendAbortedMessages(
                        pendingCheckpoint.getCheckpointPlan().getTasksToCommitTo(),
                        checkpointId,
                        pendingCheckpoint.getCheckpointTimestamp());
                throw new CheckpointException(
                        "Could not complete the pending checkpoint " + checkpointId + '.',
                        CheckpointFailureReason.FINALIZE_CHECKPOINT_FAILURE,
                        exception);
            }
        } finally {
            pendingCheckpoints.remove(checkpointId);
            scheduleTriggerRequest();
        }

        rememberRecentCheckpointId(checkpointId);

        // drop those pending checkpoints that are at prior to the completed one
        dropSubsumedCheckpoints(checkpointId);

        // record the time when this was completed, to calculate
        // the 'min delay between checkpoints'
        lastCheckpointCompletionRelativeTime = clock.relativeTimeMillis();

        LOG.info(
                "Completed checkpoint {} for job {} ({} bytes in {} ms).",
                checkpointId,
                job,
                completedCheckpoint.getStateSize(),
                completedCheckpoint.getDuration());

        if (LOG.isDebugEnabled()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Checkpoint state: ");
            for (OperatorState state : completedCheckpoint.getOperatorStates().values()) {
                builder.append(state);
                builder.append(", ");
            }
            // Remove last two chars ", "
            builder.setLength(builder.length() - 2);

            LOG.debug(builder.toString());
        }

        // send the "notify complete" call to all vertices, coordinators, etc.
        sendAcknowledgeMessages(
                pendingCheckpoint.getCheckpointPlan().getTasksToCommitTo(),
                checkpointId,
                completedCheckpoint.getTimestamp());
    }

    void scheduleTriggerRequest() {
        synchronized (lock) {
            if (isShutdown()) {
                LOG.debug(
                        "Skip scheduling trigger request because the CheckpointCoordinator is shut down");
            } else {
                timer.execute(this::executeQueuedRequest);
            }
        }
    }

    private void sendAcknowledgeMessages(
            List<ExecutionVertex> tasksToCommit, long checkpointId, long timestamp) {
        // commit tasks
        for (ExecutionVertex ev : tasksToCommit) {
            Execution ee = ev.getCurrentExecutionAttempt();
            if (ee != null) {
                ee.notifyCheckpointComplete(checkpointId, timestamp);
            }
        }

        // commit coordinators
        for (OperatorCoordinatorCheckpointContext coordinatorContext : coordinatorsToCheckpoint) {
            coordinatorContext.notifyCheckpointComplete(checkpointId);
        }
    }

    private void sendAbortedMessages(
            List<ExecutionVertex> tasksToAbort, long checkpointId, long timeStamp) {
        // send notification of aborted checkpoints asynchronously.
        executor.execute(
                () -> {
                    // send the "abort checkpoint" messages to necessary vertices.
                    for (ExecutionVertex ev : tasksToAbort) {
                        Execution ee = ev.getCurrentExecutionAttempt();
                        if (ee != null) {
                            ee.notifyCheckpointAborted(checkpointId, timeStamp);
                        }
                    }
                });

        // commit coordinators
        for (OperatorCoordinatorCheckpointContext coordinatorContext : coordinatorsToCheckpoint) {
            coordinatorContext.notifyCheckpointAborted(checkpointId);
        }
    }

    /**
     * Fails all pending checkpoints which have not been acknowledged by the given execution attempt
     * id.
     * 使给定执行尝试 ID 未确认的所有待处理检查点失败。
     *
     * @param executionAttemptId for which to discard unacknowledged pending checkpoints
     * @param cause of the failure
     */
    public void failUnacknowledgedPendingCheckpointsFor(
            ExecutionAttemptID executionAttemptId, Throwable cause) {
        synchronized (lock) {
            abortPendingCheckpoints(
                    checkpoint -> !checkpoint.isAcknowledgedBy(executionAttemptId),
                    new CheckpointException(CheckpointFailureReason.TASK_FAILURE, cause));
        }
    }

    private void rememberRecentCheckpointId(long id) {
        if (recentPendingCheckpoints.size() >= NUM_GHOST_CHECKPOINT_IDS) {
            recentPendingCheckpoints.removeFirst();
        }
        recentPendingCheckpoints.addLast(id);
    }

    private void dropSubsumedCheckpoints(long checkpointId) {
        abortPendingCheckpoints(
                checkpoint ->
                        checkpoint.getCheckpointId() < checkpointId && checkpoint.canBeSubsumed(),
                new CheckpointException(CheckpointFailureReason.CHECKPOINT_SUBSUMED));
    }

    // --------------------------------------------------------------------------------------------
    //  Checkpoint State Restoring
    // --------------------------------------------------------------------------------------------

    /**
     * Restores the latest checkpointed state.
     * 恢复最新的检查点状态。
     *
     * @param tasks Map of job vertices to restore. State for these vertices is restored via {@link
     *     Execution#setInitialState(JobManagerTaskRestore)}.
     * @param errorIfNoCheckpoint Fail if no completed checkpoint is available to restore from.
     * @param allowNonRestoredState Allow checkpoint state that cannot be mapped to any job vertex
     *     in tasks.
     * @return <code>true</code> if state was restored, <code>false</code> otherwise.
     * @throws IllegalStateException If the CheckpointCoordinator is shut down.
     * @throws IllegalStateException If no completed checkpoint is available and the <code>
     *     failIfNoCheckpoint</code> flag has been set.
     * @throws IllegalStateException If the checkpoint contains state that cannot be mapped to any
     *     job vertex in <code>tasks</code> and the <code>allowNonRestoredState</code> flag has not
     *     been set.
     * @throws IllegalStateException If the max parallelism changed for an operator that restores
     *     state from this checkpoint.
     * @throws IllegalStateException If the parallelism changed for an operator that restores
     *     <i>non-partitioned</i> state from this checkpoint.
     */
    @Deprecated
    public boolean restoreLatestCheckpointedState(
            Map<JobVertexID, ExecutionJobVertex> tasks,
            boolean errorIfNoCheckpoint,
            boolean allowNonRestoredState)
            throws Exception {

        final OptionalLong restoredCheckpointId =
                restoreLatestCheckpointedStateInternal(
                        new HashSet<>(tasks.values()),
                        OperatorCoordinatorRestoreBehavior.RESTORE_OR_RESET,
                        errorIfNoCheckpoint,
                        allowNonRestoredState);

        return restoredCheckpointId.isPresent();
    }

    /**
     * Restores the latest checkpointed state to a set of subtasks. This method represents a "local"
     * or "regional" failover and does restore states to coordinators. Note that a regional failover
     * might still include all tasks.
     * 将最新的检查点状态恢复到一组子任务。 此方法表示“本地”或“区域”故障转移，并将状态还原到协调器。
     * 请注意，区域故障转移可能仍包括所有任务。
     *
     * @param tasks Set of job vertices to restore. State for these vertices is restored via {@link
     *     Execution#setInitialState(JobManagerTaskRestore)}.
     * @return An {@code OptionalLong} with the checkpoint ID, if state was restored, an empty
     *     {@code OptionalLong} otherwise.
     * @throws IllegalStateException If the CheckpointCoordinator is shut down.
     * @throws IllegalStateException If no completed checkpoint is available and the <code>
     *     failIfNoCheckpoint</code> flag has been set.
     * @throws IllegalStateException If the checkpoint contains state that cannot be mapped to any
     *     job vertex in <code>tasks</code> and the <code>allowNonRestoredState</code> flag has not
     *     been set.
     * @throws IllegalStateException If the max parallelism changed for an operator that restores
     *     state from this checkpoint.
     * @throws IllegalStateException If the parallelism changed for an operator that restores
     *     <i>non-partitioned</i> state from this checkpoint.
     */
    public OptionalLong restoreLatestCheckpointedStateToSubtasks(
            final Set<ExecutionJobVertex> tasks) throws Exception {
        // when restoring subtasks only we accept potentially unmatched state for the
        // following reasons
        //   - the set frequently does not include all Job Vertices (only the ones that are part
        //     of the restarted region), meaning there will be unmatched state by design.
        //   - because what we might end up restoring from an original savepoint with unmatched
        //     state, if there is was no checkpoint yet.
        return restoreLatestCheckpointedStateInternal(
                tasks,
                OperatorCoordinatorRestoreBehavior
                        .SKIP, // local/regional recovery does not reset coordinators
                false, // recovery might come before first successful checkpoint
                true); // see explanation above
    }

    /**
     * Restores the latest checkpointed state to all tasks and all coordinators. This method
     * represents a "global restore"-style operation where all stateful tasks and coordinators from
     * the given set of Job Vertices are restored. are restored to their latest checkpointed state.
     * 将最新的检查点状态恢复到所有任务和所有协调器。 此方法表示“全局恢复”式操作，
     * 其中来自给定作业顶点集的所有有状态任务和协调器都将被恢复。 恢复到最新的检查点状态。
     *
     * @param tasks Set of job vertices to restore. State for these vertices is restored via {@link
     *     Execution#setInitialState(JobManagerTaskRestore)}.
     * @param allowNonRestoredState Allow checkpoint state that cannot be mapped to any job vertex
     *     in tasks.
     * @return <code>true</code> if state was restored, <code>false</code> otherwise.
     * @throws IllegalStateException If the CheckpointCoordinator is shut down.
     * @throws IllegalStateException If no completed checkpoint is available and the <code>
     *     failIfNoCheckpoint</code> flag has been set.
     * @throws IllegalStateException If the checkpoint contains state that cannot be mapped to any
     *     job vertex in <code>tasks</code> and the <code>allowNonRestoredState</code> flag has not
     *     been set.
     * @throws IllegalStateException If the max parallelism changed for an operator that restores
     *     state from this checkpoint.
     * @throws IllegalStateException If the parallelism changed for an operator that restores
     *     <i>non-partitioned</i> state from this checkpoint.
     */
    public boolean restoreLatestCheckpointedStateToAll(
            final Set<ExecutionJobVertex> tasks, final boolean allowNonRestoredState)
            throws Exception {

        final OptionalLong restoredCheckpointId =
                restoreLatestCheckpointedStateInternal(
                        tasks,
                        OperatorCoordinatorRestoreBehavior
                                .RESTORE_OR_RESET, // global recovery restores coordinators, or
                        // resets them to empty
                        false, // recovery might come before first successful checkpoint
                        allowNonRestoredState);

        return restoredCheckpointId.isPresent();
    }

    /**
     * Restores the latest checkpointed at the beginning of the job execution. If there is a
     * checkpoint, this method acts like a "global restore"-style operation where all stateful tasks
     * and coordinators from the given set of Job Vertices are restored.
     * 恢复作业执行开始时的最新检查点。 如果存在检查点，则此方法的作用类似于“全局还原”式操作，
     * 其中来自给定作业顶点集的所有有状态任务和协调器都将被还原。
     *
     * @param tasks Set of job vertices to restore. State for these vertices is restored via {@link
     *     Execution#setInitialState(JobManagerTaskRestore)}.
     * @return True, if a checkpoint was found and its state was restored, false otherwise.
     */
    public boolean restoreInitialCheckpointIfPresent(final Set<ExecutionJobVertex> tasks)
            throws Exception {
        final OptionalLong restoredCheckpointId =
                restoreLatestCheckpointedStateInternal(
                        tasks,
                        OperatorCoordinatorRestoreBehavior.RESTORE_IF_CHECKPOINT_PRESENT,
                        false, // initial checkpoints exist only on JobManager failover. ok if not
                        // present.
                        false); // JobManager failover means JobGraphs match exactly.

        return restoredCheckpointId.isPresent();
    }

    /**
     * Performs the actual restore operation to the given tasks.
     * 对给定任务执行实际还原操作。
     *
     * <p>This method returns the restored checkpoint ID (as an optional) or an empty optional, if
     * no checkpoint was restored.
     * 如果没有恢复检查点，则此方法返回恢复的检查点 ID（作为可选项）或空的可选项。
     */
    private OptionalLong restoreLatestCheckpointedStateInternal(
            final Set<ExecutionJobVertex> tasks,
            final OperatorCoordinatorRestoreBehavior operatorCoordinatorRestoreBehavior,
            final boolean errorIfNoCheckpoint,
            final boolean allowNonRestoredState)
            throws Exception {

        synchronized (lock) {
            if (shutdown) {
                throw new IllegalStateException("CheckpointCoordinator is shut down");
            }

            // We create a new shared state registry object, so that all pending async disposal
            // requests from previous
            // runs will go against the old object (were they can do no harm).
            // This must happen under the checkpoint lock.
            sharedStateRegistry.close();
            sharedStateRegistry = sharedStateRegistryFactory.create(executor);

            // Recover the checkpoints, TODO this could be done only when there is a new leader, not
            // on each recovery
            completedCheckpointStore.recover();

            // Now, we re-register all (shared) states from the checkpoint store with the new
            // registry
            for (CompletedCheckpoint completedCheckpoint :
                    completedCheckpointStore.getAllCheckpoints()) {
                completedCheckpoint.registerSharedStatesAfterRestored(sharedStateRegistry);
            }

            LOG.debug(
                    "Status of the shared state registry of job {} after restore: {}.",
                    job,
                    sharedStateRegistry);

            // Restore from the latest checkpoint
            CompletedCheckpoint latest =
                    completedCheckpointStore.getLatestCheckpoint(isPreferCheckpointForRecovery);

            if (latest == null) {
                LOG.info("No checkpoint found during restore.");

                if (errorIfNoCheckpoint) {
                    throw new IllegalStateException("No completed checkpoint available");
                }

                LOG.debug("Resetting the master hooks.");
                MasterHooks.reset(masterHooks.values(), LOG);

                if (operatorCoordinatorRestoreBehavior
                        == OperatorCoordinatorRestoreBehavior.RESTORE_OR_RESET) {
                    // we let the JobManager-side components know that there was a recovery,
                    // even if there was no checkpoint to recover from, yet
                    LOG.info("Resetting the Operator Coordinators to an empty state.");
                    restoreStateToCoordinators(
                            OperatorCoordinator.NO_CHECKPOINT, Collections.emptyMap());
                }

                return OptionalLong.empty();
            }

            LOG.info("Restoring job {} from {}.", job, latest);

            // re-assign the task states
            final Map<OperatorID, OperatorState> operatorStates = latest.getOperatorStates();

            StateAssignmentOperation stateAssignmentOperation =
                    new StateAssignmentOperation(
                            latest.getCheckpointID(), tasks, operatorStates, allowNonRestoredState);

            stateAssignmentOperation.assignStates();

            // call master hooks for restore. we currently call them also on "regional restore"
            // because
            // there is no other failure notification mechanism in the master hooks
            // ultimately these should get removed anyways in favor of the operator coordinators

            MasterHooks.restoreMasterHooks(
                    masterHooks,
                    latest.getMasterHookStates(),
                    latest.getCheckpointID(),
                    allowNonRestoredState,
                    LOG);

            if (operatorCoordinatorRestoreBehavior != OperatorCoordinatorRestoreBehavior.SKIP) {
                restoreStateToCoordinators(latest.getCheckpointID(), operatorStates);
            }

            // update metrics

            if (statsTracker != null) {
                long restoreTimestamp = System.currentTimeMillis();
                RestoredCheckpointStats restored =
                        new RestoredCheckpointStats(
                                latest.getCheckpointID(),
                                latest.getProperties(),
                                restoreTimestamp,
                                latest.getExternalPointer());

                statsTracker.reportRestoredCheckpoint(restored);
            }

            return OptionalLong.of(latest.getCheckpointID());
        }
    }

    /**
     * Restore the state with given savepoint.
     * 使用给定的保存点恢复状态。
     *
     * @param savepointPointer The pointer to the savepoint.
     * @param allowNonRestored True if allowing checkpoint state that cannot be mapped to any job
     *     vertex in tasks.
     * @param tasks Map of job vertices to restore. State for these vertices is restored via {@link
     *     Execution#setInitialState(JobManagerTaskRestore)}.
     * @param userClassLoader The class loader to resolve serialized classes in legacy savepoint
     *     versions.
     */
    public boolean restoreSavepoint(
            String savepointPointer,
            boolean allowNonRestored,
            Map<JobVertexID, ExecutionJobVertex> tasks,
            ClassLoader userClassLoader)
            throws Exception {

        Preconditions.checkNotNull(savepointPointer, "The savepoint path cannot be null.");

        LOG.info(
                "Starting job {} from savepoint {} ({})",
                job,
                savepointPointer,
                (allowNonRestored ? "allowing non restored state" : ""));

        final CompletedCheckpointStorageLocation checkpointLocation =
                checkpointStorageView.resolveCheckpoint(savepointPointer);

        // Load the savepoint as a checkpoint into the system
        CompletedCheckpoint savepoint =
                Checkpoints.loadAndValidateCheckpoint(
                        job, tasks, checkpointLocation, userClassLoader, allowNonRestored);

        completedCheckpointStore.addCheckpoint(
                savepoint, checkpointsCleaner, this::scheduleTriggerRequest);

        // Reset the checkpoint ID counter
        long nextCheckpointId = savepoint.getCheckpointID() + 1;
        checkpointIdCounter.setCount(nextCheckpointId);

        LOG.info("Reset the checkpoint ID of job {} to {}.", job, nextCheckpointId);

        final OptionalLong restoredCheckpointId =
                restoreLatestCheckpointedStateInternal(
                        new HashSet<>(tasks.values()),
                        OperatorCoordinatorRestoreBehavior.RESTORE_IF_CHECKPOINT_PRESENT,
                        true,
                        allowNonRestored);

        return restoredCheckpointId.isPresent();
    }

    // ------------------------------------------------------------------------
    //  Accessors
    // ------------------------------------------------------------------------

    public int getNumberOfPendingCheckpoints() {
        synchronized (lock) {
            return this.pendingCheckpoints.size();
        }
    }

    public int getNumberOfRetainedSuccessfulCheckpoints() {
        synchronized (lock) {
            return completedCheckpointStore.getNumberOfRetainedCheckpoints();
        }
    }

    public Map<Long, PendingCheckpoint> getPendingCheckpoints() {
        synchronized (lock) {
            return new HashMap<>(this.pendingCheckpoints);
        }
    }

    public List<CompletedCheckpoint> getSuccessfulCheckpoints() throws Exception {
        synchronized (lock) {
            return completedCheckpointStore.getAllCheckpoints();
        }
    }

    public CheckpointStorageCoordinatorView getCheckpointStorage() {
        return checkpointStorageView;
    }

    public CompletedCheckpointStore getCheckpointStore() {
        return completedCheckpointStore;
    }

    public long getCheckpointTimeout() {
        return checkpointTimeout;
    }

    /** @deprecated use {@link #getNumQueuedRequests()} */
    @Deprecated
    @VisibleForTesting
    PriorityQueue<CheckpointTriggerRequest> getTriggerRequestQueue() {
        synchronized (lock) {
            return requestDecider.getTriggerRequestQueue();
        }
    }

    public boolean isTriggering() {
        return isTriggering;
    }

    @VisibleForTesting
    boolean isCurrentPeriodicTriggerAvailable() {
        return currentPeriodicTrigger != null;
    }

    /**
     * Returns whether periodic checkpointing has been configured.
     * 返回是否已配置定期检查点。
     *
     * @return <code>true</code> if periodic checkpoints have been configured.
     */
    public boolean isPeriodicCheckpointingConfigured() {
        return baseInterval != Long.MAX_VALUE;
    }

    // --------------------------------------------------------------------------------------------
    //  Periodic scheduling of checkpoints
    // --------------------------------------------------------------------------------------------

    public void startCheckpointScheduler() {
        synchronized (lock) {
            if (shutdown) {
                throw new IllegalArgumentException("Checkpoint coordinator is shut down");
            }
            Preconditions.checkState(
                    isPeriodicCheckpointingConfigured(),
                    "Can not start checkpoint scheduler, if no periodic checkpointing is configured");

            // make sure all prior timers are cancelled
            stopCheckpointScheduler();

            periodicScheduling = true;
            currentPeriodicTrigger = scheduleTriggerWithDelay(getRandomInitDelay());
        }
    }

    public void stopCheckpointScheduler() {
        synchronized (lock) {
            periodicScheduling = false;

            cancelPeriodicTrigger();

            final CheckpointException reason =
                    new CheckpointException(CheckpointFailureReason.CHECKPOINT_COORDINATOR_SUSPEND);
            abortPendingAndQueuedCheckpoints(reason);

            numUnsuccessfulCheckpointsTriggers.set(0);
        }
    }

    public boolean isPeriodicCheckpointingStarted() {
        return periodicScheduling;
    }

    /**
     * Aborts all the pending checkpoints due to en exception.
     * 由于 en 异常而中止所有挂起的检查点。
     *
     * @param exception The exception.
     */
    public void abortPendingCheckpoints(CheckpointException exception) {
        synchronized (lock) {
            abortPendingCheckpoints(ignored -> true, exception);
        }
    }

    private void abortPendingCheckpoints(
            Predicate<PendingCheckpoint> checkpointToFailPredicate, CheckpointException exception) {

        assert Thread.holdsLock(lock);

        final PendingCheckpoint[] pendingCheckpointsToFail =
                pendingCheckpoints.values().stream()
                        .filter(checkpointToFailPredicate)
                        .toArray(PendingCheckpoint[]::new);

        // do not traverse pendingCheckpoints directly, because it might be changed during
        // traversing
        for (PendingCheckpoint pendingCheckpoint : pendingCheckpointsToFail) {
            abortPendingCheckpoint(pendingCheckpoint, exception);
        }
    }

    private void rescheduleTrigger(long tillNextMillis) {
        cancelPeriodicTrigger();
        currentPeriodicTrigger = scheduleTriggerWithDelay(tillNextMillis);
    }

    private void cancelPeriodicTrigger() {
        if (currentPeriodicTrigger != null) {
            currentPeriodicTrigger.cancel(false);
            currentPeriodicTrigger = null;
        }
    }

    private long getRandomInitDelay() {
        return ThreadLocalRandom.current().nextLong(minPauseBetweenCheckpoints, baseInterval + 1L);
    }

    private ScheduledFuture<?> scheduleTriggerWithDelay(long initDelay) {
        return timer.scheduleAtFixedRate(
                new ScheduledTrigger(), initDelay, baseInterval, TimeUnit.MILLISECONDS);
    }

    private void restoreStateToCoordinators(
            final long checkpointId, final Map<OperatorID, OperatorState> operatorStates)
            throws Exception {

        for (OperatorCoordinatorCheckpointContext coordContext : coordinatorsToCheckpoint) {
            final OperatorState state = operatorStates.get(coordContext.operatorId());
            final ByteStreamStateHandle coordinatorState =
                    state == null ? null : state.getCoordinatorState();
            final byte[] bytes = coordinatorState == null ? null : coordinatorState.getData();
            coordContext.resetToCheckpoint(checkpointId, bytes);
        }
    }

    // ------------------------------------------------------------------------
    //  job status listener that schedules / cancels periodic checkpoints
    // ------------------------------------------------------------------------

    public JobStatusListener createActivatorDeactivator() {
        synchronized (lock) {
            if (shutdown) {
                throw new IllegalArgumentException("Checkpoint coordinator is shut down");
            }

            if (jobStatusListener == null) {
                jobStatusListener = new CheckpointCoordinatorDeActivator(this);
            }

            return jobStatusListener;
        }
    }

    int getNumQueuedRequests() {
        synchronized (lock) {
            return requestDecider.getNumQueuedRequests();
        }
    }

    public void reportStats(long id, ExecutionAttemptID attemptId, CheckpointMetrics metrics)
            throws CheckpointException {
        if (statsTracker != null) {
            attemptMappingProvider
                    .getVertex(attemptId)
                    .ifPresent(ev -> statsTracker.reportIncompleteStats(id, ev, metrics));
        }
    }

    // ------------------------------------------------------------------------

    private final class ScheduledTrigger implements Runnable {

        @Override
        public void run() {
            try {
                triggerCheckpoint(true);
            } catch (Exception e) {
                LOG.error("Exception while triggering checkpoint for job {}.", job, e);
            }
        }
    }

    /**
     * Discards the given state object asynchronously belonging to the given job, execution attempt
     * id and checkpoint id.
     * 异步丢弃属于给定作业、执行尝试 id 和检查点 id 的给定状态对象。
     *
     * @param jobId identifying the job to which the state object belongs
     * @param executionAttemptID identifying the task to which the state object belongs
     * @param checkpointId of the state object
     * @param subtaskState to discard asynchronously
     */
    private void discardSubtaskState(
            final JobID jobId,
            final ExecutionAttemptID executionAttemptID,
            final long checkpointId,
            final TaskStateSnapshot subtaskState) {

        if (subtaskState != null) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {

                            try {
                                subtaskState.discardState();
                            } catch (Throwable t2) {
                                LOG.warn(
                                        "Could not properly discard state object of checkpoint {} "
                                                + "belonging to task {} of job {}.",
                                        checkpointId,
                                        executionAttemptID,
                                        jobId,
                                        t2);
                            }
                        }
                    });
        }
    }

    private void abortPendingCheckpoint(
            PendingCheckpoint pendingCheckpoint, CheckpointException exception) {

        abortPendingCheckpoint(pendingCheckpoint, exception, null);
    }

    private void abortPendingCheckpoint(
            PendingCheckpoint pendingCheckpoint,
            CheckpointException exception,
            @Nullable final ExecutionAttemptID executionAttemptID) {

        assert (Thread.holdsLock(lock));

        if (!pendingCheckpoint.isDisposed()) {
            try {
                // release resource here
                pendingCheckpoint.abort(
                        exception.getCheckpointFailureReason(),
                        exception.getCause(),
                        checkpointsCleaner,
                        this::scheduleTriggerRequest,
                        executor,
                        getStatsCallback(pendingCheckpoint));

                if (pendingCheckpoint.getProps().isSavepoint()
                        && pendingCheckpoint.getProps().isSynchronous()) {
                    failureManager.handleSynchronousSavepointFailure(exception);
                } else if (executionAttemptID != null) {
                    failureManager.handleTaskLevelCheckpointException(
                            exception, pendingCheckpoint.getCheckpointId(), executionAttemptID);
                } else {
                    failureManager.handleJobLevelCheckpointException(
                            exception, pendingCheckpoint.getCheckpointId());
                }
            } finally {
                sendAbortedMessages(
                        pendingCheckpoint.getCheckpointPlan().getTasksToCommitTo(),
                        pendingCheckpoint.getCheckpointId(),
                        pendingCheckpoint.getCheckpointTimestamp());
                pendingCheckpoints.remove(pendingCheckpoint.getCheckpointId());
                rememberRecentCheckpointId(pendingCheckpoint.getCheckpointId());
                scheduleTriggerRequest();
            }
        }
    }

    private void preCheckGlobalState(boolean isPeriodic) throws CheckpointException {
        // abort if the coordinator has been shutdown in the meantime
        if (shutdown) {
            throw new CheckpointException(CheckpointFailureReason.CHECKPOINT_COORDINATOR_SHUTDOWN);
        }

        // Don't allow periodic checkpoint if scheduling has been disabled
        if (isPeriodic && !periodicScheduling) {
            throw new CheckpointException(CheckpointFailureReason.PERIODIC_SCHEDULER_SHUTDOWN);
        }
    }

    private void abortPendingAndQueuedCheckpoints(CheckpointException exception) {
        assert (Thread.holdsLock(lock));
        requestDecider.abortAll(exception);
        abortPendingCheckpoints(exception);
    }

    /**
     * The canceller of checkpoint. The checkpoint might be cancelled if it doesn't finish in a
     * configured period.
     * 检查点的取消者。 如果检查点未在配置的时间段内完成，则可能会取消检查点。
     */
    private class CheckpointCanceller implements Runnable {

        private final PendingCheckpoint pendingCheckpoint;

        private CheckpointCanceller(PendingCheckpoint pendingCheckpoint) {
            this.pendingCheckpoint = checkNotNull(pendingCheckpoint);
        }

        @Override
        public void run() {
            synchronized (lock) {
                // only do the work if the checkpoint is not discarded anyways
                // note that checkpoint completion discards the pending checkpoint object
                // 仅当检查点没有被丢弃时才执行工作
                // 注意检查点完成会丢弃待处理的检查点对象
                if (!pendingCheckpoint.isDisposed()) {
                    LOG.info(
                            "Checkpoint {} of job {} expired before completing.",
                            pendingCheckpoint.getCheckpointId(),
                            job);

                    abortPendingCheckpoint(
                            pendingCheckpoint,
                            new CheckpointException(CheckpointFailureReason.CHECKPOINT_EXPIRED));
                }
            }
        }
    }

    private static CheckpointException getCheckpointException(
            CheckpointFailureReason defaultReason, Throwable throwable) {

        final Optional<CheckpointException> checkpointExceptionOptional =
                findThrowable(throwable, CheckpointException.class);
        return checkpointExceptionOptional.orElseGet(
                () -> new CheckpointException(defaultReason, throwable));
    }

    private static class CheckpointIdAndStorageLocation {
        private final long checkpointId;
        private final CheckpointStorageLocation checkpointStorageLocation;

        CheckpointIdAndStorageLocation(
                long checkpointId, CheckpointStorageLocation checkpointStorageLocation) {

            this.checkpointId = checkpointId;
            this.checkpointStorageLocation = checkNotNull(checkpointStorageLocation);
        }
    }

    static class CheckpointTriggerRequest {
        final long timestamp;
        final CheckpointProperties props;
        final @Nullable String externalSavepointLocation;
        final boolean isPeriodic;
        private final CompletableFuture<CompletedCheckpoint> onCompletionPromise =
                new CompletableFuture<>();

        CheckpointTriggerRequest(
                CheckpointProperties props,
                @Nullable String externalSavepointLocation,
                boolean isPeriodic) {

            this.timestamp = System.currentTimeMillis();
            this.props = checkNotNull(props);
            this.externalSavepointLocation = externalSavepointLocation;
            this.isPeriodic = isPeriodic;
        }

        CompletableFuture<CompletedCheckpoint> getOnCompletionFuture() {
            return onCompletionPromise;
        }

        public void completeExceptionally(CheckpointException exception) {
            onCompletionPromise.completeExceptionally(exception);
        }

        public boolean isForce() {
            return props.forceCheckpoint();
        }
    }

    private enum OperatorCoordinatorRestoreBehavior {

        /** Coordinators are always restored. If there is no checkpoint, they are restored empty. */
        RESTORE_OR_RESET,

        /** Coordinators are restored if there was a checkpoint. */
        RESTORE_IF_CHECKPOINT_PRESENT,

        /** Coordinators are not restored during this checkpoint restore. */
        SKIP;
    }

    private void trackPendingCheckpointStats(PendingCheckpoint checkpoint) {
        if (statsTracker == null) {
            return;
        }
        Map<JobVertexID, Integer> vertices =
                Stream.concat(
                                checkpoint.getCheckpointPlan().getTasksToWaitFor().stream(),
                                checkpoint.getCheckpointPlan().getFinishedTasks().stream())
                        .map(Execution::getVertex)
                        .map(ExecutionVertex::getJobVertex)
                        .distinct()
                        .collect(
                                toMap(
                                        ExecutionJobVertex::getJobVertexId,
                                        ExecutionJobVertex::getParallelism));

        PendingCheckpointStats pendingCheckpointStats =
                statsTracker.reportPendingCheckpoint(
                        checkpoint.getCheckpointID(),
                        checkpoint.getCheckpointTimestamp(),
                        checkpoint.getProps(),
                        vertices);

        reportFinishedTasks(
                pendingCheckpointStats, checkpoint.getCheckpointPlan().getFinishedTasks());
    }

    private void reportFinishedTasks(
            PendingCheckpointStats pendingCheckpointStats, List<Execution> finishedTasks) {
        long now = System.currentTimeMillis();
        finishedTasks.forEach(
                execution ->
                        pendingCheckpointStats.reportSubtaskStats(
                                execution.getVertex().getJobvertexId(),
                                new SubtaskStateStats(execution.getParallelSubtaskIndex(), now)));
    }

    @Nullable
    private PendingCheckpointStats getStatsCallback(PendingCheckpoint pendingCheckpoint) {
        return statsTracker == null
                ? null
                : statsTracker.getPendingCheckpointStats(pendingCheckpoint.getCheckpointID());
    }
}
