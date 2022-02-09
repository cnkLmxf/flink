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

package org.apache.flink.runtime.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Extension for the {@link Executor} interface which is enriched by method for scheduling tasks in
 * the future.
 * {@link Executor} 接口的扩展，通过将来调度任务的方法来丰富。
 */
public interface ScheduledExecutor extends Executor {

    /**
     * Executes the given command after the given delay.
     * 在给定延迟后执行给定命令。
     *
     * @param command the task to execute in the future
     * @param delay the time from now to delay the execution
     * @param unit the time unit of the delay parameter
     * @return a ScheduledFuture representing the completion of the scheduled task
     */
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    /**
     * Executes the given callable after the given delay. The result of the callable is returned as
     * a {@link ScheduledFuture}.
     * 在给定的延迟后执行给定的可调用对象。 可调用的结果以 {@link ScheduledFuture} 的形式返回。
     *
     * @param callable the callable to execute
     * @param delay the time from now to delay the execution
     * @param unit the time unit of the delay parameter
     * @param <V> result type of the callable
     * @return a ScheduledFuture which holds the future value of the given callable
     */
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * Executes the given command periodically. The first execution is started after the {@code
     * initialDelay}, the second execution is started after {@code initialDelay + period}, the third
     * after {@code initialDelay + 2*period} and so on. The task is executed until either an
     * execution fails, or the returned {@link ScheduledFuture} is cancelled.
     * 定期执行给定的命令。 第一次执行在 {@code initialDelay} 之后开始，
     * 第二次执行在 {@code initialDelay + period} 之后开始，
     * 第三次在 {@code initialDelay + 2*period} 之后开始，依此类推。
     * 任务一直执行，直到执行失败，或者返回的 {@link ScheduledFuture} 被取消。
     *
     * @param command the task to be executed periodically
     * @param initialDelay the time from now until the first execution is triggered
     * @param period the time after which the next execution is triggered
     * @param unit the time unit of the delay and period parameter
     * @return a ScheduledFuture representing the periodic task. This future never completes unless
     *     an execution of the given task fails or if the future is cancelled
     */
    ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * Executed the given command repeatedly with the given delay between the end of an execution
     * and the start of the next execution. The task is executed repeatedly until either an
     * exception occurs or if the returned {@link ScheduledFuture} is cancelled.
     * 在执行结束和下一次执行开始之间以给定的延迟重复执行给定的命令。
     * 该任务会重复执行，直到发生异常或返回的 {@link ScheduledFuture} 被取消。
     *
     * @param command the task to execute repeatedly
     * @param initialDelay the time from now until the first execution is triggered
     * @param delay the time between the end of the current and the start of the next execution
     * @param unit the time unit of the initial delay and the delay parameter
     * @return a ScheduledFuture representing the repeatedly executed task. This future never
     *     completes unless the execution of the given task fails or if the future is cancelled
     */
    ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command, long initialDelay, long delay, TimeUnit unit);
}
