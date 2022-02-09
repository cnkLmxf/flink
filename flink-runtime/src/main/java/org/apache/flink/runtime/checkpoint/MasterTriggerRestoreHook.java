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

import org.apache.flink.core.io.SimpleVersionedSerializer;

import javax.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The interface for hooks that can be called by the checkpoint coordinator when triggering or
 * restoring a checkpoint. Such a hook is useful for example when preparing external systems for
 * taking or restoring checkpoints.
 * 检查点协调器在触发或恢复检查点时可以调用的钩子接口。 例如，当准备外部系统以获取或恢复检查点时，这样的钩子很有用。
 *
 * <p>The {@link #triggerCheckpoint(long, long, Executor)} method (called when triggering a
 * checkpoint) can return a result (via a future) that will be stored as part of the checkpoint
 * metadata. When restoring a checkpoint, that stored result will be given to the {@link
 * #restoreCheckpoint(long, Object)} method. The hook's {@link #getIdentifier() identifier} is used
 * to map data to hook in the presence of multiple hooks, and when resuming a savepoint that was
 * potentially created by a different job. The identifier has a similar role as for example the
 * operator UID in the streaming API.
 * {@link #triggerCheckpoint(long, long, Executor)} 方法（在触发检查点时调用）可以返回一个结果（通过未来），
 * 该结果将作为检查点元数据的一部分存储。 恢复检查点时，
 * 存储的结果将提供给 {@link #restoreCheckpoint(long, Object)} 方法。
 * 钩子的 {@link #getIdentifier() 标识符} 用于在存在多个钩子时将数据映射到钩子，
 * 以及在恢复可能由不同作业创建的保存点时。 标识符具有与流 API 中的操作员 UID 类似的作用。
 *
 * <p>It is possible that a job fails (and is subsequently restarted) before any checkpoints were
 * successful. In that situation, the checkpoint coordination calls {@link #reset()} to give the
 * hook an opportunity to, for example, reset an external system to initial conditions.
 * 在任何检查点成功之前，作业可能会失败（并随后重新启动）。
 * 在这种情况下，检查点协调调用 {@link #reset()} 为钩子提供机会，例如，将外部系统重置为初始条件。
 *
 * <p>The MasterTriggerRestoreHook is defined when creating the streaming dataflow graph. It is
 * attached to the job graph, which gets sent to the cluster for execution. To avoid having to make
 * the hook itself serializable, these hooks are attached to the job graph via a {@link
 * MasterTriggerRestoreHook.Factory}.
 * MasterTriggerRestoreHook 在创建流式数据流图时定义。 它附加到作业图，该作业图被发送到集群执行。
 * 为了避免让钩子本身可序列化，这些钩子通过 {@link MasterTriggerRestoreHook.Factory} 附加到作业图。
 *
 * @param <T> The type of the data produced by the hook and stored as part of the checkpoint
 *     metadata. If the hook never stores any data, this can be typed to {@code Void}.
 */
public interface MasterTriggerRestoreHook<T> {

    /**
     * Gets the identifier of this hook. The identifier is used to identify a specific hook in the
     * presence of multiple hooks and to give it the correct checkpointed data upon checkpoint
     * restoration.
     * 获取此钩子的标识符。 标识符用于在存在多个钩子时识别特定钩子，并在检查点恢复时为其提供正确的检查点数据。
     *
     * <p>The identifier should be unique between different hooks of a job, but
     * deterministic/constant so that upon resuming a savepoint, the hook will get the correct data.
     * For example, if the hook calls into another storage system and persists namespace/schema
     * specific information, then the name of the storage system, together with the namespace/schema
     * name could be an appropriate identifier.
     * 标识符在作业的不同挂钩之间应该是唯一的，但具有确定性/恒定性，以便在恢复保存点时，挂钩将获得正确的数据。
     * 例如，如果钩子调用另一个存储系统并保存命名空间/模式特定信息，那么存储系统的名称以及命名空间/模式名称可以是适当的标识符。
     *
     * <p>When multiple hooks of the same name are created and attached to a job graph, only the
     * first one is actually used. This can be exploited to deduplicate hooks that would do the same
     * thing.
     * 当创建多个同名的钩子并将其附加到作业图时，实际上只使用第一个。 这可以被利用来删除会做同样事情的钩子。
     *
     * @return The identifier of the hook.
     */
    String getIdentifier();

    /**
     * This method is called by the checkpoint coordinator to reset the hook when execution is
     * restarted in the absence of any checkpoint state.
     * 在没有任何检查点状态的情况下重新启动执行时，检查点协调器调用此方法以重置挂钩。
     *
     * @throws Exception Exceptions encountered when calling the hook will cause execution to fail.
     */
    default void reset() throws Exception {}

    /**
     * Tear-down method for the hook.
     *
     * @throws Exception Exceptions encountered when calling close will be logged.
     */
    default void close() throws Exception {}

    /**
     * This method is called by the checkpoint coordinator prior when triggering a checkpoint, prior
     * to sending the "trigger checkpoint" messages to the source tasks.
     * 此方法由检查点协调器在触发检查点之前调用，在将“触发检查点”消息发送到源任务之前。
     *
     * <p>If the hook implementation wants to store data as part of the checkpoint, it may return
     * that data via a future, otherwise it should return null. The data is stored as part of the
     * checkpoint metadata under the hooks identifier (see {@link #getIdentifier()}).
     * 如果钩子实现想要将数据存储为检查点的一部分，它可能会通过未来返回该数据，否则它应该返回 null。
     * 数据作为检查点元数据的一部分存储在钩子标识符下（请参阅 {@link #getIdentifier()}）。
     *
     * <p>If the action by this hook needs to be executed synchronously, then this method should
     * directly execute the action synchronously. The returned future (if any) would typically be a
     * completed future.
     * 如果这个钩子的动作需要同步执行，那么这个方法应该直接同步执行动作。
     * 返回的未来（如果有的话）通常是一个完整的未来。
     *
     * <p>If the action should be executed asynchronously and only needs to complete before the
     * checkpoint is considered completed, then the method may use the given executor to execute the
     * actual action and would signal its completion by completing the future. For hooks that do not
     * need to store data, the future would be completed with null.
     * 如果动作应该异步执行并且只需要在检查点被认为完成之前完成，
     * 那么该方法可以使用给定的执行器来执行实际的动作，并通过完成未来来表示其完成。
     * 对于不需要存储数据的钩子，future 将用 null 完成。
     *
     * <p>Please note that this method should be non-blocking. Any heavy operation like IO operation
     * should be executed asynchronously with given executor.
     * 请注意，此方法应该是非阻塞的。 任何像 IO 操作这样的繁重操作都应该与给定的执行器异步执行。
     *
     * @param checkpointId The ID (logical timestamp, monotonously increasing) of the checkpoint
     * @param timestamp The wall clock timestamp when the checkpoint was triggered, for info/logging
     *     purposes.
     * @param executor The executor for asynchronous actions
     * @return Optionally, a future that signals when the hook has completed and that contains data
     *     to be stored with the checkpoint.
     * @throws Exception Exceptions encountered when calling the hook will cause the checkpoint to
     *     abort.
     */
    @Nullable
    CompletableFuture<T> triggerCheckpoint(long checkpointId, long timestamp, Executor executor)
            throws Exception;

    /**
     * This method is called by the checkpoint coordinator prior to restoring the state of a
     * checkpoint. If the checkpoint did store data from this hook, that data will be passed to this
     * method.
     * 在恢复检查点的状态之前，检查点协调器会调用此方法。 如果检查点确实存储了来自此挂钩的数据，则该数据将传递给此方法。
     *
     * @param checkpointId The ID (logical timestamp) of the restored checkpoint
     * @param checkpointData The data originally stored in the checkpoint by this hook, possibly
     *     null.
     * @throws Exception Exceptions thrown while restoring the checkpoint will cause the restore
     *     operation to fail and to possibly fall back to another checkpoint.
     */
    void restoreCheckpoint(long checkpointId, @Nullable T checkpointData) throws Exception;

    /**
     * Creates a the serializer to (de)serializes the data stored by this hook. The serializer
     * serializes the result of the Future returned by the {@link #triggerCheckpoint(long, long,
     * Executor)} method, and deserializes the data stored in the checkpoint into the object passed
     * to the {@link #restoreCheckpoint(long, Object)} method.
     * 创建一个序列化程序以（反）序列化此挂钩存储的数据。
     * 序列化器将{@link #triggerCheckpoint(long, long, Executor)}方法返回的Future的结果序列化，
     * 将checkpoint中存储的数据反序列化为传递给{@link #restoreCheckpoint(long, Object)的对象 } 方法。
     *
     * <p>If the hook never returns any data to be stored, then this method may return null as the
     * serializer.
     * 如果钩子从不返回任何要存储的数据，则此方法可能会返回 null 作为序列化程序。
     *
     * @return The serializer to (de)serializes the data stored by this hook
     */
    @Nullable
    SimpleVersionedSerializer<T> createCheckpointDataSerializer();

    // ------------------------------------------------------------------------
    //  factory
    // ------------------------------------------------------------------------

    /**
     * A factory to instantiate a {@code MasterTriggerRestoreHook}.
     * 实例化 {@code MasterTriggerRestoreHook} 的工厂。
     *
     * <p>The hooks are defined when creating the streaming dataflow graph and are attached to the
     * job graph, which gets sent to the cluster for execution. To avoid having to make the hook
     * implementation serializable, a serializable hook factory is actually attached to the job
     * graph instead of the hook implementation itself.
     * 这些钩子是在创建流式数据流图时定义的，并附加到作业图，该作业图被发送到集群执行。
     * 为了避免必须使钩子实现可序列化，实际上将可序列化的钩子工厂附加到作业图而不是钩子实现本身。
     */
    interface Factory extends java.io.Serializable {

        /** Instantiates the {@code MasterTriggerRestoreHook}. */
        <V> MasterTriggerRestoreHook<V> create();
    }
}
