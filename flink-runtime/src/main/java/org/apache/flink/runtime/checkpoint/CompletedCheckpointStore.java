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
import org.apache.flink.api.common.JobStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ListIterator;

/** A bounded LIFO-queue of {@link CompletedCheckpoint} instances.
 * {@link CompletedCheckpoint} 实例的有界 LIFO 队列。
 * */
public interface CompletedCheckpointStore {

    Logger LOG = LoggerFactory.getLogger(CompletedCheckpointStore.class);

    /**
     * Recover available {@link CompletedCheckpoint} instances.
     * 恢复可用的 {@link CompletedCheckpoint} 实例。
     *
     * <p>After a call to this method, {@link #getLatestCheckpoint(boolean)} returns the latest
     * available checkpoint.
     * 调用此方法后，{@link #getLatestCheckpoint(boolean)} 返回最新的可用检查点。
     */
    void recover() throws Exception;

    /**
     * Adds a {@link CompletedCheckpoint} instance to the list of completed checkpoints.
     * 将 {@link CompletedCheckpoint} 实例添加到已完成的检查点列表中。
     *
     * <p>Only a bounded number of checkpoints is kept. When exceeding the maximum number of
     * retained checkpoints, the oldest one will be discarded.
     * 只保留有限数量的检查点。 当超过保留检查点的最大数量时，最旧的将被丢弃。
     */
    void addCheckpoint(
            CompletedCheckpoint checkpoint,
            CheckpointsCleaner checkpointsCleaner,
            Runnable postCleanup)
            throws Exception;

    /**
     * Returns the latest {@link CompletedCheckpoint} instance or <code>null</code> if none was
     * added.
     * 如果没有添加，则返回最新的 {@link CompletedCheckpoint} 实例或 <code>null</code>。
     */
    default CompletedCheckpoint getLatestCheckpoint(boolean isPreferCheckpointForRecovery)
            throws Exception {
        List<CompletedCheckpoint> allCheckpoints = getAllCheckpoints();
        if (allCheckpoints.isEmpty()) {
            return null;
        }

        CompletedCheckpoint lastCompleted = allCheckpoints.get(allCheckpoints.size() - 1);

        if (isPreferCheckpointForRecovery
                && allCheckpoints.size() > 1
                && lastCompleted.getProperties().isSavepoint()) {
            ListIterator<CompletedCheckpoint> listIterator =
                    allCheckpoints.listIterator(allCheckpoints.size() - 1);
            while (listIterator.hasPrevious()) {
                CompletedCheckpoint prev = listIterator.previous();
                if (!prev.getProperties().isSavepoint()) {
                    LOG.info(
                            "Found a completed checkpoint ({}) before the latest savepoint, will use it to recover!",
                            prev);
                    return prev;
                }
            }
            LOG.info("Did not find earlier checkpoint, using latest savepoint to recover.");
        }

        return lastCompleted;
    }

    /**
     * Shuts down the store.
     *
     * <p>The job status is forwarded and used to decide whether state should actually be discarded
     * or kept.
     * 作业状态被转发并用于决定是否应该实际丢弃或保留状态。
     *
     * @param jobStatus Job state on shut down
     * @param checkpointsCleaner that will cleanup copmpleted checkpoints if needed
     */
    void shutdown(JobStatus jobStatus, CheckpointsCleaner checkpointsCleaner) throws Exception;

    /**
     * Returns all {@link CompletedCheckpoint} instances.
     *
     * <p>Returns an empty list if no checkpoint has been added yet.
     */
    List<CompletedCheckpoint> getAllCheckpoints() throws Exception;

    /** Returns the current number of retained checkpoints. */
    int getNumberOfRetainedCheckpoints();

    /** Returns the max number of retained checkpoints. */
    int getMaxNumberOfRetainedCheckpoints();

    /**
     * This method returns whether the completed checkpoint store requires checkpoints to be
     * externalized. Externalized checkpoints have their meta data persisted, which the checkpoint
     * store can exploit (for example by simply pointing the persisted metadata).
     * 此方法返回已完成的检查点存储是否需要外部化检查点。
     * 外部化检查点的元数据被持久化，检查点存储可以利用这些数据（例如，通过简单地指向持久化的元数据）。
     *
     * @return True, if the store requires that checkpoints are externalized before being added,
     *     false if the store stores the metadata itself.
     */
    boolean requiresExternalizedCheckpoints();

    @VisibleForTesting
    static CompletedCheckpointStore storeFor(
            Runnable postCleanupAction, CompletedCheckpoint... checkpoints) throws Exception {
        StandaloneCompletedCheckpointStore store =
                new StandaloneCompletedCheckpointStore(checkpoints.length);
        CheckpointsCleaner checkpointsCleaner = new CheckpointsCleaner();
        for (final CompletedCheckpoint checkpoint : checkpoints) {
            store.addCheckpoint(checkpoint, checkpointsCleaner, postCleanupAction);
        }
        return store;
    }
}
