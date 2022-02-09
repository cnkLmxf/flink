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

import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.util.ExceptionUtils;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * The event value is the connection through which operator events are sent, from coordinator to
 * operator. It can temporarily block events from going through, buffering them, and releasing them
 * later. It is used for "alignment" of operator event streams with checkpoint barrier injection,
 * similar to how the input channels are aligned during a common checkpoint.
 * 事件值是从协调器到操作员发送操作员事件的连接。 它可以暂时阻止事件通过、缓冲它们并稍后释放它们。
 * 它用于通过检查点屏障注入“对齐”操作员事件流，类似于在公共检查点期间如何对齐输入通道。
 *
 * <p>This class is NOT thread safe, but assumed to be used in a single threaded context. To guard
 * that, one can register a "main thread executor" (as used by the mailbox components like RPC
 * components) via {@link #setMainThreadExecutorForValidation(ComponentMainThreadExecutor)}.
 * 此类不是线程安全的，但假定在单线程上下文中使用。
 * 为了保护这一点，可以通过 {@link #setMainThreadExecutorForValidation(ComponentMainThreadExecutor)}
 * 注册一个“主线程执行器”（由邮箱组件如 RPC 组件使用）。
 */
final class OperatorEventValve implements EventSender {

    private static final long NO_CHECKPOINT = Long.MIN_VALUE;

    private final List<BlockedEvent> blockedEvents = new ArrayList<>();

    private long currentCheckpointId;

    private long lastCheckpointId;

    private boolean shut;

    @Nullable private ComponentMainThreadExecutor mainThreadExecutor;

    /** Constructs a new OperatorEventValve. */
    OperatorEventValve() {
        this.currentCheckpointId = NO_CHECKPOINT;
        this.lastCheckpointId = Long.MIN_VALUE;
    }

    public void setMainThreadExecutorForValidation(ComponentMainThreadExecutor mainThreadExecutor) {
        this.mainThreadExecutor = mainThreadExecutor;
    }

    // ------------------------------------------------------------------------

    public boolean isShut() {
        checkRunsInMainThread();

        return shut;
    }

    /**
     * Send the event directly, if the valve is open, and returns the original sending result
     * future.
     * 直接发送事件，如果阀门打开，则返回原始发送结果future。
     *
     * <p>If the valve is closed this buffers the event and returns an incomplete future. The future
     * is completed with the original result once the valve is opened again.
     * 如果阀门关闭，则会缓冲事件并返回不完整的未来。 一旦阀门再次打开，未来将以原始结果完成。
     *
     * <p>This method makes no assumptions and gives no guarantees from which thread the result
     * future gets completed.
     * 此方法不做任何假设，也不保证结果未来从哪个线程完成。
     */
    @Override
    public void sendEvent(
            Callable<CompletableFuture<Acknowledge>> sendAction,
            CompletableFuture<Acknowledge> result) {
        checkRunsInMainThread();

        if (shut) {
            blockedEvents.add(new BlockedEvent(sendAction, result));
        } else {
            callSendAction(sendAction, result);
        }
    }

    /**
     * Marks the valve for the next checkpoint. This remembers the checkpoint ID and will only allow
     * shutting the value for this specific checkpoint.
     * 标记下一个检查点的阀门。 这会记住检查点 ID，并且只允许关闭此特定检查点的值。
     *
     * <p>This is the valve's mechanism to detect situations where multiple coordinator checkpoints
     * would be attempted overlapping, which is currently not supported (the valve doesn't keep a
     * list of events blocked per checkpoint). It also helps to identify situations where the
     * checkpoint was aborted even before the valve was shut (by finding out that the {@code
     * currentCheckpointId} was already reset to {@code NO_CHECKPOINT}.
     * 这是 Valve 检测多个协调器检查点尝试重叠的情况的机制，目前不支持这种情况（Valve 不会保留每个检查点阻塞的事件列表）。
     * 它还有助于识别甚至在阀门关闭之前检查点中止的情况（通过发现 {@code currentCheckpointId}
     * 已经重置为 {@code NO_CHECKPOINT}。
     */
    public void markForCheckpoint(long checkpointId) {
        checkRunsInMainThread();

        if (currentCheckpointId != NO_CHECKPOINT && currentCheckpointId != checkpointId) {
            throw new IllegalStateException(
                    String.format(
                            "Cannot mark for checkpoint %d, already marked for checkpoint %d",
                            checkpointId, currentCheckpointId));
        }
        if (checkpointId > lastCheckpointId) {
            currentCheckpointId = checkpointId;
            lastCheckpointId = checkpointId;
        } else {
            throw new IllegalStateException(
                    String.format(
                            "Regressing checkpoint IDs. Previous checkpointId = %d, new checkpointId = %d",
                            lastCheckpointId, checkpointId));
        }
    }

    /**
     * Shuts the value. All events sent through this valve are blocked until the valve is re-opened.
     * If the valve is already shut, this does nothing.
     * 关闭值。 通过此阀门发送的所有事件都被阻止，直到阀门重新打开。 如果阀门已经关闭，则无任何作用。
     */
    public boolean tryShutValve(long checkpointId) {
        checkRunsInMainThread();

        if (checkpointId == currentCheckpointId) {
            shut = true;
            return true;
        }
        return false;
    }

    public void openValveAndUnmarkCheckpoint(long expectedCheckpointId) {
        checkRunsInMainThread();

        if (expectedCheckpointId != currentCheckpointId) {
            throw new IllegalStateException(
                    String.format(
                            "Valve closed for different checkpoint: closed for = %d, expected = %d",
                            currentCheckpointId, expectedCheckpointId));
        }
        openValveAndUnmarkCheckpoint();
    }

    /** Opens the value, releasing all buffered events.
     * 打开值，释放所有缓冲的事件。
     * */
    public void openValveAndUnmarkCheckpoint() {
        checkRunsInMainThread();

        currentCheckpointId = NO_CHECKPOINT;
        if (!shut) {
            return;
        }

        for (BlockedEvent blockedEvent : blockedEvents) {
            callSendAction(blockedEvent.sendAction, blockedEvent.future);
        }
        blockedEvents.clear();
        shut = false;
    }

    private void checkRunsInMainThread() {
        if (mainThreadExecutor != null) {
            mainThreadExecutor.assertRunningInMainThread();
        }
    }

    private void callSendAction(
            Callable<CompletableFuture<Acknowledge>> sendAction,
            CompletableFuture<Acknowledge> result) {
        try {
            final CompletableFuture<Acknowledge> sendResult = sendAction.call();
            FutureUtils.forward(sendResult, result);
        } catch (Throwable t) {
            ExceptionUtils.rethrowIfFatalError(t);
            result.completeExceptionally(t);
        }
    }

    // ------------------------------------------------------------------------

    private static final class BlockedEvent {

        final Callable<CompletableFuture<Acknowledge>> sendAction;
        final CompletableFuture<Acknowledge> future;

        BlockedEvent(
                Callable<CompletableFuture<Acknowledge>> sendAction,
                CompletableFuture<Acknowledge> future) {
            this.sendAction = sendAction;
            this.future = future;
        }
    }
}
