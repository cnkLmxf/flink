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

package org.apache.flink.core.fs;

import org.apache.flink.annotation.Public;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream to a file that is created via a {@link FileSystem}. This class extends the base
 * {@link java.io.OutputStream} with some additional important methods.
 * 通过 {@link FileSystem} 创建的文件的输出流。 这个类用一些额外的重要方法扩展了基础 {@link java.io.OutputStream}。
 *
 * <h2>Data Persistence Guarantees</h2>
 * 数据持久性保证
 *
 * <p>These streams are used to persistently store data, both for results of streaming applications
 * and for fault tolerance and recovery. It is therefore crucial that the persistence semantics of
 * these streams are well defined.
 * 这些流用于持久存储数据，既用于流应用程序的结果，也用于容错和恢复。 因此，明确定义这些流的持久性语义至关重要。
 *
 * <p>Please refer to the class-level docs of {@link FileSystem} for the definition of data
 * persistence via Flink's FileSystem abstraction and the {@code FSDataOutputStream}.
 * 通过 Flink 的 FileSystem 抽象和 {@code FSDataOutputStream} 定义数据持久性请参考 {@link FileSystem} 的类级文档。
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Implementations of the {@code FSDataOutputStream} are generally not assumed to be thread safe.
 * Instances of {@code FSDataOutputStream} should not be passed between threads, because there are
 * no guarantees about the order of visibility of operations across threads.
 * 通常不假定 {@code FSDataOutputStream} 的实现是线程安全的。
 * {@code FSDataOutputStream} 的实例不应在线程之间传递，因为无法保证跨线程操作的可见性顺序。
 *
 * @see FileSystem
 * @see FSDataInputStream
 */
@Public
public abstract class FSDataOutputStream extends OutputStream {

    /**
     * Gets the position of the stream (non-negative), defined as the number of bytes from the
     * beginning of the file to the current writing position. The position corresponds to the
     * zero-based index of the next byte that will be written.
     * 获取流的位置（非负数），定义为从文件开头到当前写入位置的字节数。 该位置对应于将要写入的下一个字节的从零开始的索引。
     *
     * <p>This method must report accurately report the current position of the stream. Various
     * components of the high-availability and recovery logic rely on the accurate
     * 此方法必须准确地报告流的当前位置。 高可用性和恢复逻辑的各个组件依赖于准确的
     *
     * @return The current position in the stream, defined as the number of bytes from the beginning
     *     of the file to the current writing position.
     * @throws IOException Thrown if an I/O error occurs while obtaining the position from the
     *     stream implementation.
     */
    public abstract long getPos() throws IOException;

    /**
     * Flushes the stream, writing any data currently buffered in stream implementation to the
     * proper output stream. After this method has been called, the stream implementation must not
     * hold onto any buffered data any more.
     * 刷新流，将当前在流实现中缓冲的任何数据写入正确的输出流。 调用此方法后，流实现不得再保留任何缓冲数据。
     *
     * <p>A completed flush does not mean that the data is necessarily persistent. Data persistence
     * can is only assumed after calls to {@link #close()} or {@link #sync()}.
     * 完成的刷新并不意味着数据一定是持久的。 只有在调用 {@link #close()} 或 {@link #sync()} 后才能假定数据持久性。
     *
     * <p>Implementation note: This overrides the method defined in {@link OutputStream} as abstract
     * to force implementations of the {@code FSDataOutputStream} to implement this method directly.
     * 实现说明：这会覆盖 {@link OutputStream} 中定义为抽象的方法，以强制 {@code FSDataOutputStream} 的实现直接实现此方法。
     *
     * @throws IOException Thrown if an I/O error occurs while flushing the stream.
     */
    public abstract void flush() throws IOException;

    /**
     * Flushes the data all the way to the persistent non-volatile storage (for example disks). The
     * method behaves similar to the <i>fsync</i> function, forcing all data to be persistent on the
     * devices.
     * 将数据一直刷新到持久性非易失性存储（例如磁盘）。 该方法的行为类似于 <i>fsync</i> 函数，强制所有数据在设备上持久化。
     *
     * @throws IOException Thrown if an I/O error occurs
     */
    public abstract void sync() throws IOException;

    /**
     * Closes the output stream. After this method returns, the implementation must guarantee that
     * all data written to the stream is persistent/visible, as defined in the {@link FileSystem
     * class-level docs}.
     * 关闭输出流。 在此方法返回后，实现必须保证写入流的所有数据都是持久的/可见的，
     * 如 {@link FileSystem class-level docs} 中所定义。
     *
     * <p>The above implies that the method must block until persistence can be guaranteed. For
     * example for distributed replicated file systems, the method must block until the replication
     * quorum has been reached. If the calling thread is interrupted in the process, it must fail
     * with an {@code IOException} to indicate that persistence cannot be guaranteed.
     * 以上暗示该方法必须阻塞，直到可以保证持久性。
     * 例如，对于分布式复制文件系统，该方法必须阻塞，直到达到复制法定人数。
     * 如果调用线程在进程中被中断，它必须失败并返回 {@code IOException} 以表明无法保证持久性。
     *
     * <p>If this method throws an exception, the data in the stream cannot be assumed to be
     * persistent.
     * 如果此方法抛出异常，则不能假定流中的数据是持久的。
     *
     * <p>Implementation note: This overrides the method defined in {@link OutputStream} as abstract
     * to force implementations of the {@code FSDataOutputStream} to implement this method directly.
     * 实现说明：这会覆盖 {@link OutputStream} 中定义为抽象的方法，以强制 {@code FSDataOutputStream} 的实现直接实现此方法。
     *
     * @throws IOException Thrown, if an error occurred while closing the stream or guaranteeing
     *     that the data is persistent.
     */
    public abstract void close() throws IOException;
}
