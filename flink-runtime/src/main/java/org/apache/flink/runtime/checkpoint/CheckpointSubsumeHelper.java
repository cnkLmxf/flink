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

package org.apache.flink.runtime.checkpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

/**
 * Encapsulates the logic to subsume older checkpoints by {@link CompletedCheckpointStore checkpoint
 * stores}. In general, checkpoints should be subsumed whenever state.checkpoints.num-retained is
 * exceeded.
 * 通过 {@link CompletedCheckpointStore checkpoint stores} 封装包含旧检查点的逻辑。
 * 一般来说，只要 state.checkpoints.num-retained 被超过，就应该包含检查点。
 *
 * <p>Additional considerations:
 *<ul>
 *     <li>保存点必须存储在同一队列中以防止重复（@see <a href="https://issues.apache.org/jira/browse/FLINK-10354">FLINK-10354</a>）。
 *    <li>为防止队列无限增长，保存点也与检查点一起计入 num-retained
 *   <li>保存点的实际状态不应在包含时被丢弃。
 *    <li>应至少保留一个（最近的）检查点（不是保存点）。 否则，后续增量检查点可能会引用丢弃状态
 *    （@see <a href="https://issues.apache.org/jira/browse/FLINK-21351">FLINK-21351</a>）。
 *    <li>除非使用保存点停止作业，否则以后不会创建检查点。
 *   </ul>
 * <ul>
 *   <li>Savepoints must be stored in the same queue to prevent duplicates (@see <a
 *       href="https://issues.apache.org/jira/browse/FLINK-10354">FLINK-10354</a>).
 *   <li>To prevent unlimited queue growth, savepoints are also counted in num-retained together
 *       with checkpoints
 *   <li>Savepoints actual state should NOT be discarded when they are subsumed.
 *   <li>At least one (most recent) checkpoint (not savepoint) should be kept. Otherwise, subsequent
 *       incremental checkpoints may refer to a discarded state (@see <a
 *       href="https://issues.apache.org/jira/browse/FLINK-21351">FLINK-21351</a>).
 *   <li>Except when the job is stopped with savepoint when no future checkpoints will be made.
 * </ul>
 */
class CheckpointSubsumeHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CheckpointSubsumeHelper.class);

    public static void subsume(
            Deque<CompletedCheckpoint> checkpoints, int numRetain, SubsumeAction subsumeAction)
            throws Exception {
        if (checkpoints.isEmpty() || checkpoints.size() <= numRetain) {
            return;
        }
        CompletedCheckpoint latest = checkpoints.peekLast();
        Optional<CompletedCheckpoint> latestNotSavepoint = getLatestNotSavepoint(checkpoints);
        Iterator<CompletedCheckpoint> iterator = checkpoints.iterator();
        while (checkpoints.size() > numRetain && iterator.hasNext()) {
            CompletedCheckpoint next = iterator.next();
            if (canSubsume(next, latest, latestNotSavepoint)) {
                iterator.remove();
                try {
                    subsumeAction.subsume(next);
                } catch (Exception e) {
                    LOG.warn("Fail to subsume the old checkpoint.", e);
                }
            }
            // Don't break out from the loop to subsume intermediate savepoints
        }
    }

    private static Optional<CompletedCheckpoint> getLatestNotSavepoint(
            Deque<CompletedCheckpoint> completed) {
        Iterator<CompletedCheckpoint> descendingIterator = completed.descendingIterator();
        while (descendingIterator.hasNext()) {
            CompletedCheckpoint next = descendingIterator.next();
            if (!next.getProperties().isSavepoint()) {
                return Optional.of(next);
            }
        }
        return Optional.empty();
    }

    private static boolean canSubsume(
            CompletedCheckpoint next,
            CompletedCheckpoint latest,
            Optional<CompletedCheckpoint> latestNonSavepoint) {
        if (next == latest) {
            return false;
        } else if (next.getProperties().isSavepoint()) {
            return true;
        } else if (latest.getProperties().isSynchronous()) {
            // If the job has stopped with a savepoint then it's safe to subsume because no future
            // snapshots will be taken during this run
            return true;
        } else {
            // Don't remove the latest non-savepoint lest invalidate future incremental snapshots
            return latestNonSavepoint.filter(checkpoint -> checkpoint != next).isPresent();
        }
    }

    @FunctionalInterface
    interface SubsumeAction {
        void subsume(CompletedCheckpoint checkpoint) throws Exception;
    }
}
