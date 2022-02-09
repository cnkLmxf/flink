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

package org.apache.flink.runtime.jobgraph.tasks;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.checkpoint.CheckpointMetaData;
import org.apache.flink.runtime.checkpoint.CheckpointMetricsBuilder;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.execution.Environment;
import org.apache.flink.runtime.io.network.partition.consumer.InputGate;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.flink.util.FlinkException;
import org.apache.flink.util.SerializedValue;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * This is the abstract base class for every task that can be executed by a TaskManager. Concrete
 * tasks extend this class, for example the streaming and batch tasks.
 * 这是 TaskManager 可以执行的每个任务的抽象基类。 具体任务扩展了这个类，例如流和批处理任务。
 *
 * <p>The TaskManager invokes the {@link #invoke()} method when executing a task. All operations of
 * the task happen in this method (setting up input output stream readers and writers as well as the
 * task's core operation).
 * TaskManager 在执行任务时调用 {@link #invoke()} 方法。
 * 任务的所有操作都发生在这个方法中（设置输入输出流的读写器以及任务的核心操作）。
 *
 * <p>All classes that extend must offer a constructor {@code MyTask(Environment,
 * TaskStateSnapshot)}. Tasks that are always stateless can, for convenience, also only implement
 * the constructor {@code MyTask(Environment)}.
 * 所有扩展的类都必须提供构造函数 {@code MyTask(Environment, TaskStateSnapshot)}。
 * 为方便起见，总是无状态的任务也可以只实现构造函数 {@code MyTask(Environment)}。
 *
 * <p><i>Developer note: While constructors cannot be enforced at compile time, we did not yet
 * venture on the endeavor of introducing factories (it is only an internal API after all, and with
 * Java 8, one can use {@code Class::new} almost like a factory lambda.</i>
 * <i>开发者说明：虽然构造函数不能在编译时强制执行，但我们还没有冒险引入工厂（毕竟它只是一个内部 API，
 * 在 Java 8 中，可以使用 {@code Class:: new} 几乎就像一个工厂 lambda。</i>
 *
 * <p><b>NOTE:</b> There is no constructor that accepts and initial task state snapshot and stores
 * it in a variable. That is on purpose, because the AbstractInvokable itself does not need the
 * state snapshot (only subclasses such as StreamTask do need the state) and we do not want to store
 * a reference indefinitely, thus preventing cleanup of the initial state structure by the Garbage
 * Collector.
 * <b>注意：</b> 没有构造函数可以接受初始任务状态快照并将其存储在变量中。
 * 这是故意的，因为 AbstractInvokable 本身不需要状态快照（只有 StreamTask 等子类才需要状态）
 * 并且我们不想无限期地存储引用，从而防止垃圾收集器清理初始状态结构。
 *
 * <p>Any subclass that supports recoverable state and participates in checkpointing needs to
 * override {@link #triggerCheckpointAsync(CheckpointMetaData, CheckpointOptions, boolean)}, {@link
 * #triggerCheckpointOnBarrier(CheckpointMetaData, CheckpointOptions, CheckpointMetricsBuilder)},
 * {@link #abortCheckpointOnBarrier(long, Throwable)} and {@link
 * #notifyCheckpointCompleteAsync(long)}.
 * 任何支持可恢复状态并参与检查点的子类都需要重写
 * {@link #triggerCheckpointAsync(CheckpointMetaData, CheckpointOptions, boolean)},
 * {@link #triggerCheckpointOnBarrier(CheckpointMetaData, CheckpointOptions, CheckpointMetricsBuilder)},
 * {@link #abortCheckpointOnBarrier(long, Throwable )} 和 {@link #notifyCheckpointCompleteAsync(long)}。
 */
public abstract class AbstractInvokable {

    /** The environment assigned to this invokable.
     * 分配给此可调用对象的环境。
     * */
    private final Environment environment;

    /** Flag whether cancellation should interrupt the executing thread.
     * 标记取消是否应该中断正在执行的线程。
     * */
    private volatile boolean shouldInterruptOnCancel = true;

    /**
     * Create an Invokable task and set its environment.
     * 创建一个 Invokable 任务并设置它的环境。
     *
     * @param environment The environment assigned to this invokable.
     */
    public AbstractInvokable(Environment environment) {
        this.environment = checkNotNull(environment);
    }

    // ------------------------------------------------------------------------
    //  Core methods
    // ------------------------------------------------------------------------

    /**
     * Starts the execution.
     * 开始执行。
     *
     * <p>Must be overwritten by the concrete task implementation. This method is called by the task
     * manager when the actual execution of the task starts.
     * 必须被具体的任务实现覆盖。 此方法在任务实际执行开始时由任务管理器调用。
     *
     * <p>All resources should be cleaned up when the method returns. Make sure to guard the code
     * with <code>try-finally</code> blocks where necessary.
     * 方法返回时应清理所有资源。 确保在必要时使用 <code>try-finally</code> 块保护代码。
     *
     * @throws Exception Tasks may forward their exceptions for the TaskManager to handle through
     *     failure/recovery.
     */
    public abstract void invoke() throws Exception;

    /**
     * This method is called when a task is canceled either as a result of a user abort or an
     * execution failure. It can be overwritten to respond to shut down the user code properly.
     * 当由于用户中止或执行失败而取消任务时调用此方法。 它可以被覆盖以响应正确关闭用户代码。
     *
     * @throws Exception thrown if any exception occurs during the execution of the user code
     * @return a future that is completed when this {@link AbstractInvokable} is fully terminated.
     *     Note that it may never complete if the invokable is stuck.
     */
    public Future<Void> cancel() throws Exception {
        // The default implementation does nothing.
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Sets whether the thread that executes the {@link #invoke()} method should be interrupted
     * during cancellation. This method sets the flag for both the initial interrupt, as well as for
     * the repeated interrupt. Setting the interruption to false at some point during the
     * cancellation procedure is a way to stop further interrupts from happening.
     * 设置执行 {@link #invoke()} 方法的线程是否应在取消期间中断。
     * 此方法为初始中断和重复中断设置标志。 在取消过程中的某个时刻将中断设置为 false 是一种阻止进一步中断发生的方法。
     */
    public void setShouldInterruptOnCancel(boolean shouldInterruptOnCancel) {
        this.shouldInterruptOnCancel = shouldInterruptOnCancel;
    }

    /**
     * Checks whether the task should be interrupted during cancellation. This method is check both
     * for the initial interrupt, as well as for the repeated interrupt. Setting the interruption to
     * false via {@link #setShouldInterruptOnCancel(boolean)} is a way to stop further interrupts
     * from happening.
     * 检查取消期间是否应中断任务。 这种方法既检查初始中断，也检查重复中断。
     * 通过 {@link #setShouldInterruptOnCancel(boolean)} 将中断设置为 false 是一种阻止进一步中断发生的方法。
     */
    public boolean shouldInterruptOnCancel() {
        return shouldInterruptOnCancel;
    }

    // ------------------------------------------------------------------------
    //  Access to Environment and Configuration
    // ------------------------------------------------------------------------

    /**
     * Returns the environment of this task.
     *
     * @return The environment of this task.
     */
    public final Environment getEnvironment() {
        return this.environment;
    }

    /**
     * Returns the user code class loader of this invokable.
     *
     * @return user code class loader of this invokable.
     */
    public final ClassLoader getUserCodeClassLoader() {
        return getEnvironment().getUserCodeClassLoader().asClassLoader();
    }

    /**
     * Returns the current number of subtasks the respective task is split into.
     *
     * @return the current number of subtasks the respective task is split into
     */
    public int getCurrentNumberOfSubtasks() {
        return this.environment.getTaskInfo().getNumberOfParallelSubtasks();
    }

    /**
     * Returns the index of this subtask in the subtask group.
     *
     * @return the index of this subtask in the subtask group
     */
    public int getIndexInSubtaskGroup() {
        return this.environment.getTaskInfo().getIndexOfThisSubtask();
    }

    /**
     * Returns the task configuration object which was attached to the original {@link
     * org.apache.flink.runtime.jobgraph.JobVertex}.
     * 返回附加到原始 {@link org.apache.flink.runtime.jobgraph.JobVertex} 的任务配置对象。
     *
     * @return the task configuration object which was attached to the original {@link
     *     org.apache.flink.runtime.jobgraph.JobVertex}
     */
    public final Configuration getTaskConfiguration() {
        return this.environment.getTaskConfiguration();
    }

    /**
     * Returns the job configuration object which was attached to the original {@link
     * org.apache.flink.runtime.jobgraph.JobGraph}.
     * 返回附加到原始 {@link org.apache.flink.runtime.jobgraph.JobGraph} 的作业配置对象。
     *
     * @return the job configuration object which was attached to the original {@link
     *     org.apache.flink.runtime.jobgraph.JobGraph}
     */
    public Configuration getJobConfiguration() {
        return this.environment.getJobConfiguration();
    }

    /** Returns the global ExecutionConfig. */
    public ExecutionConfig getExecutionConfig() {
        return this.environment.getExecutionConfig();
    }

    // ------------------------------------------------------------------------
    //  Checkpointing Methods
    // ------------------------------------------------------------------------

    /**
     * This method is called to trigger a checkpoint, asynchronously by the checkpoint coordinator.
     * 检查点协调器异步调用此方法来触发检查点。
     *
     * <p>This method is called for tasks that start the checkpoints by injecting the initial
     * barriers, i.e., the source tasks. In contrast, checkpoints on downstream operators, which are
     * the result of receiving checkpoint barriers, invoke the {@link
     * #triggerCheckpointOnBarrier(CheckpointMetaData, CheckpointOptions, CheckpointMetricsBuilder)}
     * method.
     * 通过注入初始屏障（即源任务）启动检查点的任务调用此方法。 相反，下游算子上的检查点是接收检查点屏障的结果，
     * 调用 {@link #triggerCheckpointOnBarrier(CheckpointMetaData, CheckpointOptions, CheckpointMetricsBuilder)} 方法。
     *
     * @param checkpointMetaData Meta data for about this checkpoint
     * @param checkpointOptions Options for performing this checkpoint
     * @return future with value of {@code false} if the checkpoint was not carried out, {@code
     *     true} otherwise
     */
    public Future<Boolean> triggerCheckpointAsync(
            CheckpointMetaData checkpointMetaData, CheckpointOptions checkpointOptions) {
        throw new UnsupportedOperationException(
                String.format(
                        "triggerCheckpointAsync not supported by %s", this.getClass().getName()));
    }

    /**
     * This method is called when a checkpoint is triggered as a result of receiving checkpoint
     * barriers on all input streams.
     * 当由于在所有输入流上接收到检查点屏障而触发检查点时，将调用此方法。
     *
     * @param checkpointMetaData Meta data for about this checkpoint
     * @param checkpointOptions Options for performing this checkpoint
     * @param checkpointMetrics Metrics about this checkpoint
     * @throws Exception Exceptions thrown as the result of triggering a checkpoint are forwarded.
     */
    public void triggerCheckpointOnBarrier(
            CheckpointMetaData checkpointMetaData,
            CheckpointOptions checkpointOptions,
            CheckpointMetricsBuilder checkpointMetrics)
            throws IOException {
        throw new UnsupportedOperationException(
                String.format(
                        "triggerCheckpointOnBarrier not supported by %s",
                        this.getClass().getName()));
    }

    /**
     * Aborts a checkpoint as the result of receiving possibly some checkpoint barriers, but at
     * least one {@link org.apache.flink.runtime.io.network.api.CancelCheckpointMarker}.
     * 由于可能收到一些检查点障碍，
     * 但至少有一个 {@link org.apache.flink.runtime.io.network.api.CancelCheckpointMarker}，因此中止检查点。
     *
     * <p>This requires implementing tasks to forward a {@link
     * org.apache.flink.runtime.io.network.api.CancelCheckpointMarker} to their outputs.
     * 这需要实现将 {@link org.apache.flink.runtime.io.network.api.CancelCheckpointMarker} 转发到其输出的任务。
     *
     * @param checkpointId The ID of the checkpoint to be aborted.
     * @param cause The reason why the checkpoint was aborted during alignment
     */
    public void abortCheckpointOnBarrier(long checkpointId, CheckpointException cause)
            throws IOException {
        throw new UnsupportedOperationException(
                String.format(
                        "abortCheckpointOnBarrier not supported by %s", this.getClass().getName()));
    }

    /**
     * Invoked when a checkpoint has been completed, i.e., when the checkpoint coordinator has
     * received the notification from all participating tasks.
     * 当检查点完成时调用，即当检查点协调器收到所有参与任务的通知时。
     *
     * @param checkpointId The ID of the checkpoint that is complete.
     * @return future that completes when the notification has been processed by the task.
     */
    public Future<Void> notifyCheckpointCompleteAsync(long checkpointId) {
        throw new UnsupportedOperationException(
                String.format(
                        "notifyCheckpointCompleteAsync not supported by %s",
                        this.getClass().getName()));
    }

    /**
     * Invoked when a checkpoint has been aborted, i.e., when the checkpoint coordinator has
     * received a decline message from one task and try to abort the targeted checkpoint by
     * notification.
     * 当检查点被中止时调用，即当检查点协调器收到来自一项任务的拒绝消息并尝试通过通知中止目标检查点时。
     *
     * @param checkpointId The ID of the checkpoint that is aborted.
     * @return future that completes when the notification has been processed by the task.
     */
    public Future<Void> notifyCheckpointAbortAsync(long checkpointId) {
        throw new UnsupportedOperationException(
                String.format(
                        "notifyCheckpointAbortAsync not supported by %s",
                        this.getClass().getName()));
    }

    public void dispatchOperatorEvent(OperatorID operator, SerializedValue<OperatorEvent> event)
            throws FlinkException {
        throw new UnsupportedOperationException(
                "dispatchOperatorEvent not supported by " + getClass().getName());
    }

    /**
     * This method can be called before {@link #invoke()} to restore an invokable object for the
     * last valid state, if it has it.
     * 可以在 {@link #invoke()} 之前调用此方法来恢复最后一个有效状态的可调用对象（如果有的话）。
     *
     * <p>Every implementation determinate what should be restored by itself. (nothing happens by
     * default).
     * 每个实现都决定了自己应该恢复什么。 （默认情况下不会发生任何事情）。
     *
     * @throws Exception Tasks may forward their exceptions for the TaskManager to handle through
     *     failure/recovery.
     */
    public void restore() throws Exception {}

    /**
     * @return true if blocking input such as {@link InputGate#getNext()} is used (as opposed to
     *     {@link InputGate#pollNext()}. To be removed together with the DataSet API.
     */
    public boolean isUsingNonBlockingInput() {
        return false;
    }
}
