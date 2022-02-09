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

package org.apache.flink.api.common.state;

import org.apache.flink.annotation.Public;

/**
 * This interface is typically only needed for transactional interaction with the "outside world",
 * like committing external side effects on checkpoints. An example is committing external
 * transactions once a checkpoint completes.
 * 此接口通常仅用于与“外部世界”的事务性交互，例如在检查点上提交外部副作用。 一个例子是一旦检查点完成就提交外部事务。
 *
 * <h3>Invocation Guarantees</h3>
 * 调用保证
 *
 * <p>It is NOT guaranteed that the implementation will receive a notification for each completed or
 * aborted checkpoint. While these notifications come in most cases, notifications might not happen,
 * for example, when a failure/restore happens directly after a checkpoint completed.
 * 不保证实现将收到每个已完成或中止的检查点的通知。
 * 虽然这些通知在大多数情况下都会出现，但通知可能不会发生，例如，在检查点完成后直接发生故障/恢复时。
 *
 * <p>To handle this correctly, implementation should follow the "Checkpoint Subsuming Contract"
 * described below.
 * 为了正确处理这个问题，实现应该遵循下面描述的“检查点提交合同”。
 *
 * <h3>Exceptions</h3>
 *
 * <p>The notifications from this interface come "after the fact", meaning after the checkpoint has
 * been aborted or completed. Throwing an exception will not change the completion/abortion of the
 * checkpoint.
 * 来自这个界面的通知是“事后”的，意思是在检查点被中止或完成之后。 抛出异常不会改变检查点的完成/中止。
 *
 * <p>Exceptions thrown from this method result in task- or job failure and recovery.
 * 此方法抛出的异常会导致任务或作业失败和恢复。
 *
 * <h3>Checkpoint Subsuming Contract</h3>
 * Checkpoint Subsuming 合约
 *
 * <p>Checkpoint IDs are strictly increasing. A checkpoint with higher ID always subsumes a
 * checkpoint with lower ID. For example, when checkpoint T is confirmed complete, the code can
 * assume that no checkpoints with lower ID (T-1, T-2, etc.) are pending any more. <b>No checkpoint
 * with lower ID will ever be committed after a checkpoint with a higher ID.</b>
 * 检查点 ID 正在严格增加。 具有较高 ID 的检查点始终包含具有较低 ID 的检查点。
 * 例如，当检查点 T 被确认完成时，代码可以假设没有任何具有较低 ID（T-1、T-2 等）的检查点处于待决状态。
 * <b>在具有较高 ID 的检查点之后，不会再提交具有较低 ID 的检查点。</b>
 *
 * <p>This does not necessarily mean that all of the previous checkpoints actually completed
 * successfully. It is also possible that some checkpoint timed out or was not fully acknowledged by
 * all tasks. Implementations must then behave as if that checkpoint did not happen. The recommended
 * way to do this is to let the completion of a new checkpoint (higher ID) subsume the completion of
 * all earlier checkpoints (lower ID).
 * 这并不一定意味着之前的所有检查点实际上都已成功完成。 也有可能是某些检查点超时或没有被所有任务完全确认。
 * 然后实现必须表现得好像那个检查点没有发生一样。 推荐的方法是让新检查点（较高 ID）的完成包含所有较早检查点（较低 ID）的完成。
 *
 * <p>This property is easy to achieve for cases where increasing "offsets", "watermarks", or other
 * progress indicators are communicated on checkpoint completion. A newer checkpoint will have a
 * higher "offset" (more progress) than the previous checkpoint, so it automatically subsumes the
 * previous one. Remember the "offset to commit" for a checkpoint ID and commit it when that
 * specific checkpoint (by ID) gets the notification that it is complete.
 * 对于在检查点完成时传达增加“偏移量”、“水印”或其他进度指示器的情况，此属性很容易实现。
 * 较新的检查点将比前一个检查点具有更高的“偏移”（更多进度），因此它会自动包含前一个检查点。
 * 记住检查点 ID 的“提交偏移量”，并在该特定检查点（按 ID）收到它已完成的通知时提交它。
 *
 * <p>If you need to publish some specific artifacts (like files) or acknowledge some specific IDs
 * after a checkpoint, you can follow a pattern like below.
 * 如果您需要发布一些特定的工件（如文件）或在检查点后确认一些特定的 ID，您可以遵循如下模式。
 *
 * <h3>Implementing Checkpoint Subsuming for Committing Artifacts</h3>
 *
 * <p>The following is a sample pattern how applications can publish specific artifacts on
 * checkpoint. Examples would be operators that acknowledge specific IDs or publish specific files
 * on checkpoint.
 * 以下是应用程序如何在检查点上发布特定工件的示例模式。 例如，在检查点上确认特定 ID 或发布特定文件的operators。
 *
 * <ul>
 *   <li>During processing, have two sets of artifacts.
 *      在处理过程中，有两组工件。
 *       <ol>
 *         <li>A "ready set": Artifacts that are ready to be published as part of the next
 *             checkpoint. Artifacts are added to this set as soon as they are ready to be
 *             committed. This set is "transient", it is not stored in Flink's state persisted
 *             anywhere.
 *             “ready set”：准备作为下一个检查点发布的一部分的工件。 一旦准备好提交，工件就会被添加到这个集合中。
 *             这个集合是“瞬态的”，它不会存储在 Flink 的任何地方持久化的状态。
 *         <li>A "pending set": Artifacts being committed with a checkpoint. The actual publishing
 *             happens when the checkpoint is complete. This is a map of "{@code long =>
 *             List<Artifact>}", mapping from the id of the checkpoint when the artifact was ready
 *             to the artifacts. /li>
 *             “pending set”：通过检查点提交的工件。 实际发布发生在检查点完成时。
 *             这是“{@code long => List<Artifact>}”的映射，从工件准备好时检查点的 id 映射到工件。
 *       </ol>
 *   <li>On checkpoint, add that set of artifacts from the "ready set" to the "pending set",
 *       associated with the checkpoint ID. The whole "pending set" gets stored in the checkpoint
 *       state.
 *       在检查点，将这组工件从“就绪集”添加到与检查点 ID 关联的“待定集”。 整个“待定集”存储在checkpoint state。
 *   <li>On {@code notifyCheckpointComplete()} publish all IDs/artifacts from the "pending set" up
 *       to the checkpoint with that ID. Remove these from the "pending set".
 *       在 {@code notifyCheckpointComplete()} 上发布从“待定集”到具有该 ID 的检查点的所有 ID/工件。
 *       从“待定集”中删除这些。
 *   <li/>
 * </ul>
 *
 * <p>That way, even if some checkpoints did not complete, or if the notification that they
 * completed got lost, the artifacts will be published as part of the next checkpoint that
 * completes.
 * 这样，即使某些检查点未完成，或者它们完成的通知丢失，工件也将作为下一个完成的检查点的一部分发布。
 */
@Public
public interface CheckpointListener {

    /**
     * Notifies the listener that the checkpoint with the given {@code checkpointId} completed and
     * was committed.
     * 通知侦听器给定 {@code checkpointId} 的检查点已完成并已提交。
     *
     * <p>These notifications are "best effort", meaning they can sometimes be skipped. To behave
     * properly, implementers need to follow the "Checkpoint Subsuming Contract". Please see the
     * {@link CheckpointListener class-level JavaDocs} for details.
     * 这些通知是“尽力而为”的，这意味着有时可以跳过它们。
     * 为了正确行事，实施者需要遵循“检查点提交合同”。 有关详细信息，请参阅 {@link CheckpointListener 类级 JavaDocs}。
     *
     * <p>Please note that checkpoints may generally overlap, so you cannot assume that the {@code
     * notifyCheckpointComplete()} call is always for the latest prior checkpoint (or snapshot) that
     * was taken on the function/operator implementing this interface. It might be for a checkpoint
     * that was triggered earlier. Implementing the "Checkpoint Subsuming Contract" (see above)
     * properly handles this situation correctly as well.
     * 请注意，检查点通常可能会重叠，
     * 因此您不能假设 {@code notifyCheckpointComplete()} 调用始终针对在实现此接口的函数/运算符上获取的最新的先前检查点（或快照）。
     * 可能是针对较早触发的检查点。 实施“Checkpoint Subsuming Contract”（见上文）也可以正确处理这种情况。
     *
     * <p>Please note that throwing exceptions from this method will not cause the completed
     * checkpoint to be revoked. Throwing exceptions will typically cause task/job failure and
     * trigger recovery.
     * 请注意，从此方法抛出异常不会导致已完成的检查点被撤销。 抛出异常通常会导致任务/作业失败并触发恢复。
     *
     * @param checkpointId The ID of the checkpoint that has been completed.
     * @throws Exception This method can propagate exceptions, which leads to a failure/recovery for
     *     the task. Not that this will NOT lead to the checkpoint being revoked.
     */
    void notifyCheckpointComplete(long checkpointId) throws Exception;

    /**
     * This method is called as a notification once a distributed checkpoint has been aborted.
     * 一旦分布式检查点被中止，此方法将作为通知被调用。
     *
     * <p><b>Important:</b> The fact that a checkpoint has been aborted does NOT mean that the data
     * and artifacts produced between the previous checkpoint and the aborted checkpoint are to be
     * discarded. The expected behavior is as if this checkpoint was never triggered in the first
     * place, and the next successful checkpoint simply covers a longer time span. See the
     * "Checkpoint Subsuming Contract" in the {@link CheckpointListener class-level JavaDocs} for
     * details.
     * <b>重要提示：</b> 检查点已中止的事实并不意味着前一个检查点和中止的检查点之间产生的数据和工件将被丢弃。
     * 预期的行为就像这个检查点从来没有被触发过一样，下一个成功的检查点只是覆盖了更长的时间跨度。
     * 有关详细信息，请参阅 {@link CheckpointListener class-level JavaDocs} 中的
     * “Checkpoint Subsuming Contract”。
     *
     * <p>These notifications are "best effort", meaning they can sometimes be skipped.
     * 这些通知是“尽力而为”的，这意味着有时可以跳过它们。
     *
     * <p>This method is very rarely necessary to implement. The "best effort" guarantee, together
     * with the fact that this method should not result in discarding any data (per the "Checkpoint
     * Subsuming Contract") means it is mainly useful for earlier cleanups of auxiliary resources.
     * One example is to pro-actively clear a local per-checkpoint state cache upon checkpoint
     * failure.
     * 这种方法很少需要implement。 “尽力而为”的保证以及此方法不应导致丢弃任何数据的事实（根据“检查点提交合同”）意味着它主要用于辅助资源的早期清理。
     * 一个例子是在检查点失败时主动清除本地每个检查点的状态缓存。
     *
     * @param checkpointId The ID of the checkpoint that has been aborted.
     * @throws Exception This method can propagate exceptions, which leads to a failure/recovery for
     *     the task or job.
     */
    default void notifyCheckpointAborted(long checkpointId) throws Exception {}
}
