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

package org.apache.flink.runtime.operators.sort;

import javax.annotation.Nullable;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Base class for all working threads in this sort-merger. The specific threads for reading,
 * sorting, spilling, merging, etc... extend this subclass.
 * 此排序合并中所有工作线程的基类。 用于读取、排序、溢出、合并等的特定线程......扩展了这个子类。
 *
 * <p>The threads are designed to terminate themselves when the task they are set up to do is
 * completed. Further more, they terminate immediately when the <code>shutdown()</code> method is
 * called.
 * 线程被设计为在它们被设置为完成的任务完成时自行终止。 此外，当调用 <code>shutdown()</code> 方法时，它们会立即终止。
 */
abstract class ThreadBase<E> extends Thread
        implements Thread.UncaughtExceptionHandler, StageRunner {

    /** The queue of empty buffer that can be used for reading;
     * 可用于读取的空缓冲区队列；
     * */
    protected final StageMessageDispatcher<E> dispatcher;

    /** The exception handler for any problems.
     * 任何问题的异常处理程序。
     * */
    private final ExceptionHandler<IOException> exceptionHandler;

    /** The flag marking this thread as alive.
     * 将该线程标记为活动的标志。
     * */
    private volatile boolean alive;

    /**
     * Creates a new thread.
     *
     * @param exceptionHandler The exception handler to call for all exceptions.
     * @param name The name of the thread.
     * @param queues The queues used to pass buffers between the threads.
     */
    protected ThreadBase(
            @Nullable ExceptionHandler<IOException> exceptionHandler,
            String name,
            StageMessageDispatcher<E> queues) {
        // thread setup
        super(checkNotNull(name));
        this.setDaemon(true);

        // exception handling
        this.exceptionHandler = exceptionHandler;
        this.setUncaughtExceptionHandler(this);

        this.dispatcher = checkNotNull(queues);
        this.alive = true;
    }

    /** Implements exception handling and delegates to go().
     * 实现异常处理并委托给 go()。
     * */
    public void run() {
        try {
            go();
        } catch (Throwable t) {
            internalHandleException(
                    new IOException(
                            "Thread '"
                                    + getName()
                                    + "' terminated due to an exception: "
                                    + t.getMessage(),
                            t));
        }
    }

    /**
     * Equivalent to the run() method.
     * 等效于 run() 方法。
     *
     * @throws IOException Exceptions that prohibit correct completion of the work may be thrown by
     *     the thread.
     */
    protected abstract void go() throws IOException, InterruptedException;

    /**
     * Checks whether this thread is still alive.
     * 检查此线程是否还活着。
     *
     * @return true, if the thread is alive, false otherwise.
     */
    protected boolean isRunning() {
        return this.alive;
    }

    @Override
    public void close() throws InterruptedException {
        this.alive = false;
        this.interrupt();
        this.join();
    }

    /**
     * Internally handles an exception and makes sure that this method returns without a problem.
     * 在内部处理异常并确保此方法返回没有问题。
     *
     * @param ioex The exception to handle.
     */
    protected final void internalHandleException(IOException ioex) {
        if (!isRunning()) {
            // discard any exception that occurs when after the thread is killed.
            return;
        }
        if (this.exceptionHandler != null) {
            try {
                this.exceptionHandler.handleException(ioex);
            } catch (Throwable ignored) {
            }
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        internalHandleException(
                new IOException(
                        "Thread '"
                                + t.getName()
                                + "' terminated due to an uncaught exception: "
                                + e.getMessage(),
                        e));
    }
}
