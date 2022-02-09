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

import org.apache.flink.api.common.typeutils.TypeComparator;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.runtime.jobgraph.tasks.AbstractInvokable;
import org.apache.flink.runtime.memory.MemoryManager;
import org.apache.flink.util.MutableObjectIterator;
import org.apache.flink.util.WrappingRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The {@link ExternalSorter} is a full fledged sorter. It implements a multi-way merge sort.
 * Internally, the logic is factored into two or three threads (read, sort, spill) which communicate
 * through a set of blocking queues, forming a closed loop. Memory is allocated using the {@link
 * MemoryManager} interface. Thus the component will not exceed the provided memory limits.
 * {@link ExternalSorter} 是一个成熟的分拣机。 它实现了多路归并排序。
 * 在内部，逻辑被分解为两个或三个线程（读取、排序、溢出），它们通过一组阻塞队列进行通信，形成一个闭环。
 * 使用 {@link MemoryManager} 接口分配内存。 因此，组件不会超过提供的内存限制。
 */
public class ExternalSorter<E> implements Sorter<E> {

    // ------------------------------------------------------------------------
    //                              Constants
    // ------------------------------------------------------------------------

    /** Logging. */
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSorter.class);

    // ------------------------------------------------------------------------
    //                                  Threads
    // ------------------------------------------------------------------------

    /** The thread that reads the input channels into buffers and passes them on to the merger. */
    private final StageRunner readThread;

    /** The thread that merges the buffer handed from the reading thread. */
    private final StageRunner sortThread;

    /** The thread that handles spilling to secondary storage. */
    private final StageRunner spillThread;

    // ------------------------------------------------------------------------
    //                                   Memory
    // ------------------------------------------------------------------------

    /**
     * The memory segments used first for sorting and later for reading/pre-fetching during the
     * external merge.
     * 内存段首先用于排序，然后在外部合并期间用于读取/预取。
     */
    private final List<MemorySegment> sortReadMemory;

    /** The memory segments used to stage data to be written.
     * 用于暂存要写入的数据的内存段。
     * */
    private final List<MemorySegment> writeMemory;

    /** The memory manager through which memory is allocated and released.
     * 分配和释放内存的内存管理器。
     * */
    private final MemoryManager memoryManager;

    // ------------------------------------------------------------------------
    //                            Miscellaneous Fields
    // ------------------------------------------------------------------------

    /**
     * The handler for large records, that do not go though the in-memory sorter as a whole, but
     * directly go to disk.
     */
    private final LargeRecordHandler<E> largeRecordHandler;

    /** Collection of all currently open channels, to be closed and deleted during cleanup. */
    private final SpillChannelManager spillChannelManager;

    private final CircularQueues<E> queues;

    /** Flag indicating that the sorter was closed. */
    private volatile boolean closed;

    private final Collection<InMemorySorter<E>> inMemorySorters;

    ExternalSorter(
            @Nullable StageRunner readThread,
            StageRunner sortThread,
            StageRunner spillThread,
            List<MemorySegment> sortReadMemory,
            List<MemorySegment> writeMemory,
            MemoryManager memoryManager,
            @Nullable LargeRecordHandler<E> largeRecordHandler,
            SpillChannelManager spillChannelManager,
            Collection<InMemorySorter<E>> inMemorySorters,
            CircularQueues<E> queues) {
        this.readThread = readThread;
        this.sortThread = checkNotNull(sortThread);
        this.spillThread = checkNotNull(spillThread);
        this.sortReadMemory = checkNotNull(sortReadMemory);
        this.writeMemory = checkNotNull(writeMemory);
        this.memoryManager = checkNotNull(memoryManager);
        this.largeRecordHandler = largeRecordHandler;
        this.spillChannelManager = checkNotNull(spillChannelManager);
        this.inMemorySorters = checkNotNull(inMemorySorters);
        this.queues = checkNotNull(queues);
        this.queues
                .getIteratorFuture()
                .whenComplete(
                        // close the sorter if an error occurred
                        (iterator, throwable) -> {
                            if (throwable != null) {
                                ExternalSorter.this.close();
                            }
                        });
        startThreads();
    }

    /** Starts all the threads that are used by this sort-merger. */
    private void startThreads() {
        if (this.readThread != null) {
            this.readThread.start();
        }
        this.sortThread.start();
        this.spillThread.start();
    }

    /**
     * Shuts down all the threads initiated by this sort/merger. Also releases all previously
     * allocated memory, if it has not yet been released by the threads, and closes and deletes all
     * channels (removing the temporary files).
     * 关闭由此排序/合并启动的所有线程。 如果尚未被线程释放，还会释放所有先前分配的内存，并关闭并删除所有通道（删除临时文件）。
     *
     * <p>The threads are set to exit directly, but depending on their operation, it may take a
     * while to actually happen. The sorting thread will for example not finish before the current
     * batch is sorted. This method attempts to wait for the working thread to exit. If it is
     * however interrupted, the method exits immediately and is not guaranteed how long the threads
     * continue to exist and occupy resources afterwards.
     * 线程被设置为直接退出，但根据它们的操作，实际发生可能需要一段时间。
     * 例如，排序线程将不会在当前批次被排序之前完成。 此方法尝试等待工作线程退出。
     * 但是，如果它被中断，则该方法立即退出，并且不保证线程继续存在多长时间并随后占用资源。
     *
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() {
        // check if the sorter has been closed before
        synchronized (this) {
            if (this.closed) {
                return;
            }

            // mark as closed
            this.closed = true;
        }

        // from here on, the code is in a try block, because even though errors might be thrown in
        // this block,
        // we need to make sure that all the memory is released.
        try {
            // stop all the threads
            if (this.readThread != null) {
                closeThread(this.readThread, "reader");
            }
            closeThread(this.sortThread, "sorter");
            closeThread(this.spillThread, "spilling");
        } finally {

            // now that we closed all the threads, close the queue and disable any further
            // writing/reading
            this.queues.close();

            // Dispose all in memory sorter in order to clear memory references
            for (InMemorySorter<?> inMemorySorter : inMemorySorters) {
                inMemorySorter.dispose();
            }

            // RELEASE ALL MEMORY. If the threads and channels are still running, this should cause
            // exceptions, because their memory segments are freed
            try {
                if (!this.writeMemory.isEmpty()) {
                    this.memoryManager.release(this.writeMemory);
                }
                this.writeMemory.clear();
            } catch (Throwable ignored) {
            }

            try {
                if (!this.sortReadMemory.isEmpty()) {
                    this.memoryManager.release(this.sortReadMemory);
                }
                this.sortReadMemory.clear();
            } catch (Throwable ignored) {
            }

            this.spillChannelManager.close();

            try {
                if (this.largeRecordHandler != null) {
                    this.largeRecordHandler.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void closeThread(StageRunner thread, String threadName) {
        try {
            thread.close();
        } catch (InterruptedException ie) {
            LOG.debug(
                    String.format(
                            "Closing of %s was interrupted. "
                                    + "The %s thread may still be working.",
                            threadName, threadName),
                    ie);
        } catch (Throwable t) {
            LOG.error(
                    String.format("Error shutting down %s thread: %s", threadName, t.getMessage()),
                    t);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------------
    //                           Result Iterator
    // ------------------------------------------------------------------------

    @Override
    public MutableObjectIterator<E> getIterator() throws InterruptedException {
        try {
            return queues.getIteratorFuture()
                    .exceptionally(
                            exception -> {
                                throw new RuntimeException(
                                        "Error obtaining the sorted input: "
                                                + exception.getMessage(),
                                        exception);
                            })
                    .get();
        } catch (ExecutionException e) {
            close();
            throw new WrappingRuntimeException(e);
        }
    }

    /** Creates a builder for the {@link ExternalSorter}. */
    public static <E> ExternalSorterBuilder<E> newBuilder(
            MemoryManager memoryManager,
            AbstractInvokable parentTask,
            TypeSerializer<E> serializer,
            TypeComparator<E> comparator) {
        return new ExternalSorterBuilder<>(
                checkNotNull(memoryManager),
                checkNotNull(parentTask),
                checkNotNull(serializer),
                checkNotNull(comparator));
    }
}
