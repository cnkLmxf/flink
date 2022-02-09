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

package org.apache.flink.runtime.operators.coordination;

import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.messages.Acknowledge;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

/**
 * A coordinator for runtime operators. The OperatorCoordinator runs on the master, associated with
 * the job vertex of the operator. It communicates with operators via sending operator events.
 * 运行时运算符的协调器。 OperatorCoordinator 在 master 上运行，与 operator 的作业顶点相关联。
 * 它通过发送操作员事件与操作员进行通信。
 *
 * <p>Operator coordinators are for example source and sink coordinators that discover and assign
 * work, or aggregate and commit metadata.
 * 操作员协调器是例如发现和分配工作，或聚合和提交元数据的源和接收器协调器。
 *
 * <h2>Thread Model</h2>
 *
 * <p>All coordinator methods are called by the Job Manager's main thread (mailbox thread). That
 * means that these methods must not, under any circumstances, perform blocking operations (like I/O
 * or waiting on locks or futures). That would run a high risk of bringing down the entire
 * JobManager.
 * 所有的协调器方法都由 Job Manager 的主线程（邮箱线程）调用。
 * 这意味着这些方法在任何情况下都不得执行阻塞操作（如 I/O 或等待锁或期货）。 这将冒着降低整个 JobManager 的风险。
 *
 * <p>Coordinators that involve more complex operations should hence spawn threads to handle the I/O
 * work. The methods on the {@link Context} are safe to be called from another thread than the
 * thread that calls the Coordinator's methods.
 * 因此，涉及更复杂操作的协调器应该产生线程来处理 I/O 工作。
 * {@link Context} 上的方法可以安全地从另一个线程调用，而不是调用协调器方法的线程。
 *
 * <h2>Consistency</h2>
 *
 * <p>The coordinator's view of the task execution is highly simplified, compared to the Scheduler's
 * view, but allows for consistent interaction with the operators running on the parallel subtasks.
 * In particular, the following methods are guaranteed to be called strictly in order:
 * 与调度器的视图相比，协调器的任务执行视图高度简化，但允许与运行在并行子任务上的操作员进行一致的交互。
 * 特别是，保证严格按顺序调用以下方法：
 *<ol>
 *   <li>{@link #subtaskReady(int, SubtaskGateway)}：一旦您可以向子任务发送事件，就会调用它。
 *   提供的网关绑定到该特定任务。这是与操作员子任务交互的开始。
 *   <li>{@link #subtaskFailed(int, Throwable)}：一旦子任务执行失败或被取消，就会为每个子任务调用。
 *   此时，与子任务的交互应该停止。
 *   <li>{@link #subtaskReset(int, long)} 或 {@link #resetToCheckpoint(long, byte[])}：
 *   一旦调度器确定要恢复哪个检查点，这些方法就会通知协调器。前一种方法在区域故障/恢复（影响可能的子任务子集）的情况下调用，
 *   后一种方法在全局故障/恢复的情况下调用。此方法应该用于确定要恢复的操作，因为它会告诉您回退到哪个检查点。
 *   自恢复检查点以来，协调器实现需要恢复与相关任务的交互。
 *   <li>{@link #subtaskReady(int, SubtaskGateway)}：一旦恢复的任务准备就绪，再次调用。
 *   这比 {@link #subtaskReset(int, long)} 晚，因为在这些方法之间，任务被调度和部署。
 * </ol>
 * <ol>
 *   <li>{@link #subtaskReady(int, SubtaskGateway)}: Called once you can send events to the subtask.
 *       The provided gateway is bound to that specific task. This is the start of interaction with
 *       the operator subtasks.
 *   <li>{@link #subtaskFailed(int, Throwable)}: Called for each subtask as soon as the subtask
 *       execution failed or was cancelled. At this point, interaction with the subtask should stop.
 *   <li>{@link #subtaskReset(int, long)} or {@link #resetToCheckpoint(long, byte[])}: Once the
 *       scheduler determined which checkpoint to restore, these methods notify the coordinator of
 *       that. The former method is called in case of a regional failure/recovery (affecting
 *       possible a subset of subtasks), the later method in case of a global failure/recovery. This
 *       method should be used to determine which actions to recover, because it tells you which
 *       checkpoint to fall back to. The coordinator implementation needs to recover the
 *       interactions with the relevant tasks since the checkpoint that is restored.
 *   <li>{@link #subtaskReady(int, SubtaskGateway)}: Called again, once the recovered tasks are
 *       ready to go. This is later than {@link #subtaskReset(int, long)}, because between those
 *       methods, the task are scheduled and deployed.
 * </ol>
 */
public interface OperatorCoordinator extends CheckpointListener, AutoCloseable {

    /**
     * The checkpoint ID passed to the restore methods when no completed checkpoint exists, yet. It
     * indicates that the restore is to the "initial state" of the coordinator or the failed
     * subtask.
     * 当不存在已完成的检查点时，传递给恢复方法的检查点 ID。 它表示恢复到协调器或失败子任务的“初始状态”。
     */
    long NO_CHECKPOINT = -1L;

    // ------------------------------------------------------------------------

    /**
     * Starts the coordinator. This method is called once at the beginning, before any other
     * methods.
     * 启动协调器。 此方法在开始时调用一次，在任何其他方法之前。
     *
     * @throws Exception Any exception thrown from this method causes a full job failure.
     */
    void start() throws Exception;

    /**
     * This method is called when the coordinator is disposed. This method should release currently
     * held resources. Exceptions in this method do not cause the job to fail.
     * 当协调器被释放时调用此方法。 此方法应释放当前持有的资源。 此方法中的异常不会导致作业失败。
     */
    @Override
    void close() throws Exception;

    // ------------------------------------------------------------------------

    /**
     * Hands an OperatorEvent coming from a parallel Operator instances (one of the parallel
     * subtasks).
     * 处理来自并行 Operator 实例（并行子任务之一）的 OperatorEvent。
     *
     * @throws Exception Any exception thrown by this method results in a full job failure and
     *     recovery.
     */
    void handleEventFromOperator(int subtask, OperatorEvent event) throws Exception;

    // ------------------------------------------------------------------------

    /**
     * Takes a checkpoint of the coordinator. The checkpoint is identified by the given ID.
     * 获取协调器的检查点。 检查点由给定的 ID 标识。
     *
     * <p>To confirm the checkpoint and store state in it, the given {@code CompletableFuture} must
     * be completed with the state. To abort or dis-confirm the checkpoint, the given {@code
     * CompletableFuture} must be completed exceptionally. In any case, the given {@code
     * CompletableFuture} must be completed in some way, otherwise the checkpoint will not progress.
     * 要确认检查点并在其中存储状态，给定的 {@code CompletableFuture} 必须与状态一起完成。
     * 要中止或取消确认检查点，给定的 {@code CompletableFuture} 必须异常完成。
     * 无论如何，给定的 {@code CompletableFuture} 必须以某种方式完成，否则检查点将无法进行。
     *
     * <h3>Exactly-once Semantics</h3>
     *
     * <p>The semantics are defined as follows:
     * 语义定义如下：
     *<ul>
     *     <li>检查点未来完成的时间点被认为是协调器检查点发生的时间点。
     *     <li>OperatorCoordinator 实现必须有一种严格排序事件发送和检查点未来完成的方式
     *     （例如，同一个线程执行两个操作，或者两个操作都由互斥锁保护）。
     *     <li>在检查点未来完成之前发送的每个事件都会在检查点之前考虑。
     *     <li>在检查点 future 完成后发送的每个事件都被认为是在检查点之后。
     * </ul>
     * <ul>
     *   <li>The point in time when the checkpoint future is completed is considered the point in
     *       time when the coordinator's checkpoint takes place.
     *   <li>The OperatorCoordinator implementation must have a way of strictly ordering the sending
     *       of events and the completion of the checkpoint future (for example the same thread does
     *       both actions, or both actions are guarded by a mutex).
     *   <li>Every event sent before the checkpoint future is completed is considered before the
     *       checkpoint.
     *   <li>Every event sent after the checkpoint future is completed is considered to be after the
     *       checkpoint.
     * </ul>
     *
     * @throws Exception Any exception thrown by this method results in a full job failure and
     *     recovery.
     */
    void checkpointCoordinator(long checkpointId, CompletableFuture<byte[]> resultFuture)
            throws Exception;

    /**
     * We override the method here to remove the checked exception. Please check the Java docs of
     * {@link CheckpointListener#notifyCheckpointComplete(long)} for more detail semantic of the
     * method.
     * 我们在此处重写该方法以删除已检查的异常。
     * 请查看 {@link CheckpointListener#notifyCheckpointComplete(long)} 的 Java 文档以获取该方法的更多详细语义。
     */
    @Override
    void notifyCheckpointComplete(long checkpointId);

    /**
     * We override the method here to remove the checked exception. Please check the Java docs of
     * {@link CheckpointListener#notifyCheckpointAborted(long)} for more detail semantic of the
     * method.
     * 我们在此处重写该方法以删除已检查的异常。
     * 请查看 {@link CheckpointListener#notifyCheckpointAborted(long)} 的 Java 文档以了解该方法的更多详细语义。
     */
    @Override
    default void notifyCheckpointAborted(long checkpointId) {}

    /**
     * Resets the coordinator to the given checkpoint. When this method is called, the coordinator
     * can discard all other in-flight working state. All subtasks will also have been reset to the
     * same checkpoint.
     * 将协调器重置为给定的检查点。 当调用此方法时，协调器可以丢弃所有其他的飞行中的工作状态。
     * 所有子任务也将被重置到相同的检查点。
     *
     * <p>This method is called in the case of a <i>global failover</i> of the system, which means a
     * failover of the coordinator (JobManager). This method is not invoked on a <i>partial
     * failover</i>; partial failovers call the {@link #subtaskReset(int, long)} method for the
     * involved subtasks.
     * 在系统<i>全局故障转移</i>的情况下调用该方法，这意味着协调器（JobManager）的故障转移。
     * <i>部分故障转移</i>时不会调用此方法； 部分故障转移为涉及的子任务调用 {@link #subtaskReset(int, long)} 方法。
     *
     * <p>This method is expected to behave synchronously with respect to other method calls and
     * calls to {@code Context} methods. For example, Events being sent by the Coordinator after
     * this method returns are assumed to take place after the checkpoint that was restored.
     * 该方法应该与其他方法调用和对 {@code Context} 方法的调用同步。
     * 例如，Coordinator 在此方法返回后发送的事件假定发生在恢复的检查点之后。
     *
     * <p>This method is called with a null state argument in the following situations:
     * 在以下情况下，使用 null 状态参数调用此方法：
     *<ul>
     *     <li>正在恢复，但尚未完成检查点。
     *     <li>已从已完成的检查点/保存点恢复，但它不包含协调器的状态。
     * </ul>
     * <ul>
     *   <li>There is a recovery and there was no completed checkpoint yet.
     *   <li>There is a recovery from a completed checkpoint/savepoint but it contained no state for
     *       the coordinator.
     * </ul>
     *
     * <p>In both cases, the coordinator should reset to an empty (new) state.
     * 在这两种情况下，协调器都应重置为空（新）状态。
     *
     * <h2>Restoring implicitly notifies of Checkpoint Completion</h2>
     * 隐式还原检查点完成通知
     *
     * <p>Restoring to a checkpoint is a way of confirming that the checkpoint is complete. It is
     * safe to commit side-effects that are predicated on checkpoint completion after this call.
     * 恢复到检查点是确认检查点已完成的一种方式。 在此调用之后提交基于检查点完成的副作用是安全的。
     *
     * <p>Even if no call to {@link #notifyCheckpointComplete(long)} happened, the checkpoint can
     * still be complete (for example when a system failure happened directly after committing the
     * checkpoint, before calling the {@link #notifyCheckpointComplete(long)} method).
     * 即使没有发生对 {@link #notifyCheckpointComplete(long)} 的调用，
     * 检查点仍然可以完成（例如，在提交检查点后直接发生系统故障时，在调用 {@link #notifyCheckpointComplete(long)} 方法之前） .
     */
    void resetToCheckpoint(long checkpointId, @Nullable byte[] checkpointData) throws Exception;

    // ------------------------------------------------------------------------

    /**
     * Called when one of the subtasks of the task running the coordinated operator goes through a
     * failover (failure / recovery cycle).
     * 当运行协调操作员的任务的其中一个子任务经历故障转移（故障/恢复周期）时调用。
     *
     * <p>This method is called every time there is a failover of a subtasks, regardless of whether
     * there it is a partial failover or a global failover.
     * 每次发生子任务故障转移时都会调用此方法，无论是部分故障转移还是全局故障转移。
     */
    void subtaskFailed(int subtask, @Nullable Throwable reason);

    /**
     * Called if a task is recovered as part of a <i>partial failover</i>, meaning a failover
     * handled by the scheduler's failover strategy (by default recovering a pipelined region). The
     * method is invoked for each subtask involved in that partial failover.
     * 如果任务作为<i>部分故障转移</i>的一部分被恢复，
     * 则调用，这意味着由调度程序的故障转移策略处理的故障转移（默认情况下恢复流水线区域）。
     * 对该部分故障转移中涉及的每个子任务调用该方法。
     *
     * <p>In contrast to this method, the {@link #resetToCheckpoint(long, byte[])} method is called
     * in the case of a global failover, which is the case when the coordinator (JobManager) is
     * recovered.
     * 与此方法相比，{@link #resetToCheckpoint(long, byte[])} 方法在全局故障转移的情况下被调用，
     * 这是协调器（JobManager）恢复时的情况。
     */
    void subtaskReset(int subtask, long checkpointId);

    /**
     * This is called when a subtask of the Operator becomes ready to receive events, both after
     * initial startup and after task failover. The given {@code SubtaskGateway} can be used to send
     * events to the executed subtask.
     * 当 Operator 的子任务准备好接收事件时调用，无论是在初始启动之后还是在任务故障转移之后。
     * 给定的 {@code SubtaskGateway} 可用于将事件发送到已执行的子任务。
     *
     * <p>The given {@code SubtaskGateway} is bound to that specific execution attempt that became
     * ready. All events sent through the gateway target that execution attempt; if the attempt is
     * no longer running by the time the event is sent, then the events are failed.
     * 给定的 {@code SubtaskGateway} 绑定到准备就绪的特定执行尝试。
     * 通过网关发送的所有事件都以该执行尝试为目标； 如果在发送事件时尝试不再运行，则事件失败。
     */
    void subtaskReady(int subtask, SubtaskGateway gateway);

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The context gives the OperatorCoordinator access to contextual information and provides a
     * gateway to interact with other components, such as sending operator events.
     * 上下文使 OperatorCoordinator 可以访问上下文信息，并提供与其他组件交互的网关，例如发送操作员事件。
     */
    interface Context {

        /** Gets the ID of the operator to which the coordinator belongs.
         * 获取协调器所属的算子ID。
         * */
        OperatorID getOperatorId();

        /**
         * Fails the job and trigger a global failover operation.
         * 使作业失败并触发全局故障转移操作。
         *
         * <p>This operation restores the entire job to the latest complete checkpoint. This is
         * useful to recover from inconsistent situations (the view from the coordinator and its
         * subtasks as diverged), but is expensive and should be used with care.
         * 此操作将整个作业还原到最新的完整检查点。
         * 这对于从不一致的情况中恢复（来自协调器的视图及其子任务的分歧）很有用，但代价高昂，应谨慎使用。
         */
        void failJob(Throwable cause);

        /** Gets the current parallelism with which this operator is executed.
         * 获取执行此运算符的当前并行度。
         * */
        int currentParallelism();

        /**
         * Gets the classloader that contains the additional dependencies, which are not part of the
         * JVM's classpath.
         * 获取包含附加依赖项的类加载器，这些依赖项不属于 JVM 的类路径。
         */
        ClassLoader getUserCodeClassloader();
    }

    // ------------------------------------------------------------------------

    /**
     * The {@code SubtaskGateway} is the way to interact with a specific parallel instance of the
     * Operator (an Operator subtask), like sending events to the operator.
     * {@code SubtaskGateway} 是与 Operator 的特定并行实例（一个 Operator 子任务）交互的方式，例如向 Operator 发送事件。
     */
    interface SubtaskGateway {

        /**
         * Sends an event to the parallel subtask with the given subtask index.
         * 向具有给定子任务索引的并行子任务发送事件。
         *
         * <p>The returned future is completed successfully once the event has been received by the
         * target TaskManager. The future is completed exceptionally if the event cannot be sent.
         * That includes situations where the target task is not running.
         * 一旦目标 TaskManager 接收到事件，返回的 future 就成功完成。
         * 如果无法发送事件，则未来异常完成。 这包括目标任务未运行的情况。
         */
        CompletableFuture<Acknowledge> sendEvent(OperatorEvent evt);

        /**
         * Gets the execution attempt for the subtask execution attempt that this gateway
         * communicates with.
         * 获取此网关与之通信的子任务执行尝试的执行尝试。
         */
        ExecutionAttemptID getExecution();

        /**
         * Gets the subtask index of the parallel operator instance this gateway communicates with.
         * 获取此网关与之通信的并行算子实例的子任务索引。
         */
        int getSubtask();
    }

    // ------------------------------------------------------------------------

    /**
     * The provider creates an OperatorCoordinator and takes a {@link Context} to pass to the
     * OperatorCoordinator. This method is, for example, called on the job manager when the
     * scheduler and execution graph are created, to instantiate the OperatorCoordinator.
     * 提供者创建一个 OperatorCoordinator 并采用 {@link Context} 传递给 OperatorCoordinator。
     * 例如，当创建调度程序和执行图时，在作业管理器上调用此方法，以实例化 OperatorCoordinator。
     *
     * <p>The factory is {@link Serializable}, because it is attached to the JobGraph and is part of
     * the serialized job graph that is sent to the dispatcher, or stored for recovery.
     * 工厂是 {@link Serializable}，因为它附加到 JobGraph 并且是发送到调度程序或存储以供恢复的序列化作业图的一部分。
     */
    interface Provider extends Serializable {

        /** Gets the ID of the operator to which the coordinator belongs.
         * 获取协调器所属的算子ID。
         * */
        OperatorID getOperatorId();

        /** Creates the {@code OperatorCoordinator}, using the given context.
         * 使用给定的上下文创建 {@code OperatorCoordinator}。
         * */
        OperatorCoordinator create(Context context) throws Exception;
    }
}
