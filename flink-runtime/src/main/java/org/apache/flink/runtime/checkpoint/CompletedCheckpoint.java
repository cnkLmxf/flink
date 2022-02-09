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

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.state.CompletedCheckpointStorageLocation;
import org.apache.flink.runtime.state.SharedStateRegistry;
import org.apache.flink.runtime.state.StateUtil;
import org.apache.flink.runtime.state.StreamStateHandle;
import org.apache.flink.util.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A CompletedCheckpoint describes a checkpoint after all required tasks acknowledged it (with their
 * state) and that is considered successful. The CompletedCheckpoint class contains all the metadata
 * of the checkpoint, i.e., checkpoint ID, timestamps, and the handles to all states that are part
 * of the checkpoint.
 * CompletedCheckpoint 描述了在所有必需的任务（及其状态）确认并被认为成功之后的检查点。
 * CompletedCheckpoint 类包含检查点的所有元数据，即检查点 ID、时间戳和作为检查点一部分的所有状态的句柄。
 *
 * <h2>Size the CompletedCheckpoint Instances</h2>
 * 调整 CompletedCheckpoint 实例的大小
 *
 * <p>In most cases, the CompletedCheckpoint objects are very small, because the handles to the
 * checkpoint states are only pointers (such as file paths). However, the some state backend
 * implementations may choose to store some payload data directly with the metadata (for example to
 * avoid many small files). If those thresholds are increased to large values, the memory
 * consumption of the CompletedCheckpoint objects can be significant.
 * 在大多数情况下，CompletedCheckpoint 对象非常小，因为检查点状态的句柄只是指针（例如文件路径）。
 * 但是，某些状态后端实现可能会选择将一些有效负载数据直接与元数据一起存储（例如，以避免许多小文件）。
 * 如果这些阈值增加到较大的值，则 CompletedCheckpoint 对象的内存消耗可能会很大。
 *
 * <h2>Metadata Persistence</h2>
 * 元数据持久性
 *
 * <p>The metadata of the CompletedCheckpoint is also persisted in an external storage system.
 * Checkpoints have an external pointer, which points to the metadata. For example when storing a
 * checkpoint in a file system, that pointer is the file path to the checkpoint's folder or the
 * metadata file. For a state backend that stores metadata in database tables, the pointer could be
 * the table name and row key. The pointer is encoded as a String.
 * CompletedCheckpoint 的元数据也保存在外部存储系统中。 检查点有一个指向元数据的外部指针。
 * 例如，当在文件系统中存储检查点时，该指针是检查点文件夹或元数据文件的文件路径。
 * 对于在数据库表中存储元数据的状态后端，指针可以是表名和行键。 指针被编码为字符串。
 */
public class CompletedCheckpoint implements Serializable, Checkpoint {

    private static final Logger LOG = LoggerFactory.getLogger(CompletedCheckpoint.class);

    private static final long serialVersionUID = -8360248179615702014L;

    // ------------------------------------------------------------------------

    /** The ID of the job that the checkpoint belongs to. */
    private final JobID job;

    /** The ID (logical timestamp) of the checkpoint. */
    private final long checkpointID;

    /** The timestamp when the checkpoint was triggered. */
    private final long timestamp;

    /** The duration of the checkpoint (completion timestamp - trigger timestamp). */
    private final long duration;

    /** States of the different operator groups belonging to this checkpoint.
     * 属于此检查点的不同opertor groups的状态。
     * */
    private final Map<OperatorID, OperatorState> operatorStates;

    /** Properties for this checkpoint. */
    private final CheckpointProperties props;

    /** States that were created by a hook on the master (in the checkpoint coordinator).
     * 由 master 上的钩子创建的状态（在检查点协调器中）。
     * */
    private final Collection<MasterState> masterHookStates;

    /** The location where the checkpoint is stored. */
    private final CompletedCheckpointStorageLocation storageLocation;

    /** The state handle to the externalized meta data.
     * 外部化元数据的状态句柄。
     * */
    private final StreamStateHandle metadataHandle;

    /** External pointer to the completed checkpoint (for example file path).
     * 指向已完成检查点的外部指针（例如文件路径）。
     * */
    private final String externalPointer;

    /** Optional stats tracker callback for discard.
     * 丢弃的可选统计跟踪器回调。
     * */
    @Nullable private transient volatile CompletedCheckpointStats.DiscardCallback discardCallback;

    // ------------------------------------------------------------------------

    public CompletedCheckpoint(
            JobID job,
            long checkpointID,
            long timestamp,
            long completionTimestamp,
            Map<OperatorID, OperatorState> operatorStates,
            @Nullable Collection<MasterState> masterHookStates,
            CheckpointProperties props,
            CompletedCheckpointStorageLocation storageLocation) {

        checkArgument(checkpointID >= 0);
        checkArgument(timestamp >= 0);
        checkArgument(completionTimestamp >= 0);

        this.job = checkNotNull(job);
        this.checkpointID = checkpointID;
        this.timestamp = timestamp;
        this.duration = completionTimestamp - timestamp;

        // we create copies here, to make sure we have no shared mutable
        // data structure with the "outside world"
        // 我们在这里创建副本，以确保我们没有与“外部世界”共享的可变数据结构
        this.operatorStates = new HashMap<>(checkNotNull(operatorStates));
        this.masterHookStates =
                masterHookStates == null || masterHookStates.isEmpty()
                        ? Collections.emptyList()
                        : new ArrayList<>(masterHookStates);

        this.props = checkNotNull(props);
        this.storageLocation = checkNotNull(storageLocation);
        this.metadataHandle = storageLocation.getMetadataHandle();
        this.externalPointer = storageLocation.getExternalPointer();
    }

    // ------------------------------------------------------------------------
    //  Properties
    // ------------------------------------------------------------------------

    public JobID getJobId() {
        return job;
    }

    @Override
    public long getCheckpointID() {
        return checkpointID;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public CheckpointProperties getProperties() {
        return props;
    }

    public Map<OperatorID, OperatorState> getOperatorStates() {
        return operatorStates;
    }

    public Collection<MasterState> getMasterHookStates() {
        return Collections.unmodifiableCollection(masterHookStates);
    }

    public StreamStateHandle getMetadataHandle() {
        return metadataHandle;
    }

    public String getExternalPointer() {
        return externalPointer;
    }

    public long getStateSize() {
        long result = 0L;

        for (OperatorState operatorState : operatorStates.values()) {
            result += operatorState.getStateSize();
        }

        return result;
    }

    // ------------------------------------------------------------------------
    //  Shared State
    // ------------------------------------------------------------------------

    /**
     * Register all shared states in the given registry. This is method is called before the
     * checkpoint is added into the store.
     * 在给定的注册表中注册所有共享状态。 这是在将检查点添加到存储之前调用的方法。
     *
     * @param sharedStateRegistry The registry where shared states are registered
     */
    public void registerSharedStatesAfterRestored(SharedStateRegistry sharedStateRegistry) {
        sharedStateRegistry.registerAll(operatorStates.values());
    }

    // ------------------------------------------------------------------------
    //  Discard and Dispose
    // ------------------------------------------------------------------------

    public void discardOnFailedStoring() throws Exception {
        discard();
    }

    public boolean discardOnSubsume() throws Exception {
        if (shouldBeDiscardedOnSubsume()) {
            discard();
            return true;
        }

        return false;
    }

    public boolean discardOnShutdown(JobStatus jobStatus) throws Exception {

        if (shouldBeDiscardedOnShutdown(jobStatus)) {

            discard();
            return true;
        } else {
            LOG.info("Checkpoint with ID {} at '{}' not discarded.", checkpointID, externalPointer);
            return false;
        }
    }

    @Override
    public void discard() throws Exception {
        LOG.trace("Executing discard procedure for {}.", this);

        try {
            // collect exceptions and continue cleanup
            Exception exception = null;

            // drop the metadata
            try {
                metadataHandle.discardState();
            } catch (Exception e) {
                exception = e;
            }

            // discard private state objects
            try {
                StateUtil.bestEffortDiscardAllStateObjects(operatorStates.values());
            } catch (Exception e) {
                exception = ExceptionUtils.firstOrSuppressed(e, exception);
            }

            // discard location as a whole
            try {
                storageLocation.disposeStorageLocation();
            } catch (Exception e) {
                exception = ExceptionUtils.firstOrSuppressed(e, exception);
            }

            if (exception != null) {
                throw exception;
            }
        } finally {
            operatorStates.clear();

            // to be null-pointer safe, copy reference to stack
            CompletedCheckpointStats.DiscardCallback discardCallback = this.discardCallback;
            if (discardCallback != null) {
                discardCallback.notifyDiscardedCheckpoint();
            }
        }
    }

    public boolean shouldBeDiscardedOnSubsume() {
        return props.discardOnSubsumed();
    }

    public boolean shouldBeDiscardedOnShutdown(JobStatus jobStatus) {
        return jobStatus == JobStatus.FINISHED && props.discardOnJobFinished()
                || jobStatus == JobStatus.CANCELED && props.discardOnJobCancelled()
                || jobStatus == JobStatus.FAILED && props.discardOnJobFailed()
                || jobStatus == JobStatus.SUSPENDED && props.discardOnJobSuspended();
    }

    // ------------------------------------------------------------------------
    //  Miscellaneous
    // ------------------------------------------------------------------------

    public static boolean checkpointsMatch(
            Collection<CompletedCheckpoint> first, Collection<CompletedCheckpoint> second) {
        if (first.size() != second.size()) {
            return false;
        }

        List<Tuple2<Long, JobID>> firstInterestingFields = new ArrayList<>(first.size());

        for (CompletedCheckpoint checkpoint : first) {
            firstInterestingFields.add(
                    new Tuple2<>(checkpoint.getCheckpointID(), checkpoint.getJobId()));
        }

        List<Tuple2<Long, JobID>> secondInterestingFields = new ArrayList<>(second.size());

        for (CompletedCheckpoint checkpoint : second) {
            secondInterestingFields.add(
                    new Tuple2<>(checkpoint.getCheckpointID(), checkpoint.getJobId()));
        }

        return firstInterestingFields.equals(secondInterestingFields);
    }

    /**
     * Sets the callback for tracking when this checkpoint is discarded.
     * 设置丢弃此检查点时的跟踪回调。
     *
     * @param discardCallback Callback to call when the checkpoint is discarded.
     */
    void setDiscardCallback(@Nullable CompletedCheckpointStats.DiscardCallback discardCallback) {
        this.discardCallback = discardCallback;
    }

    @Override
    public String toString() {
        return String.format(
                "%s %d @ %d for %s located at %s",
                props.getCheckpointType().getName(), checkpointID, timestamp, job, externalPointer);
    }
}
