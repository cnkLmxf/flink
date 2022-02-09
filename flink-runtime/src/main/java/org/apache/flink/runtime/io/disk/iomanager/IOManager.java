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

package org.apache.flink.runtime.io.disk.iomanager;

import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.runtime.io.disk.FileChannelManager;
import org.apache.flink.runtime.io.disk.FileChannelManagerImpl;
import org.apache.flink.runtime.io.disk.iomanager.FileIOChannel.Enumerator;
import org.apache.flink.runtime.io.disk.iomanager.FileIOChannel.ID;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.util.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/** The facade for the provided I/O manager services.
 * 提供的 I/O 管理器服务的外观。
 * */
public abstract class IOManager implements AutoCloseable {
    protected static final Logger LOG = LoggerFactory.getLogger(IOManager.class);

    private static final String DIR_NAME_PREFIX = "io";

    private final FileChannelManager fileChannelManager;

    // -------------------------------------------------------------------------
    //               Constructors / Destructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a new IOManager.
     *
     * @param tempDirs The basic directories for files underlying anonymous channels.
     */
    protected IOManager(String[] tempDirs) {
        this.fileChannelManager =
                new FileChannelManagerImpl(Preconditions.checkNotNull(tempDirs), DIR_NAME_PREFIX);
    }

    /** Removes all temporary files. */
    @Override
    public void close() throws Exception {
        fileChannelManager.close();
    }

    // ------------------------------------------------------------------------
    //                          Channel Instantiations
    // ------------------------------------------------------------------------

    /**
     * Creates a new {@link ID} in one of the temp directories. Multiple invocations of this method
     * spread the channels evenly across the different directories.
     * 在其中一个临时目录中创建一个新的 {@link ID}。 此方法的多次调用将通道均匀地分布在不同的目录中。
     *
     * @return A channel to a temporary directory.
     */
    public ID createChannel() {
        return fileChannelManager.createChannel();
    }

    /**
     * Creates a new {@link Enumerator}, spreading the channels in a round-robin fashion across the
     * temporary file directories.
     * 创建一个新的 {@link Enumerator}，以循环方式在临时文件目录中传播通道。
     *
     * @return An enumerator for channels.
     */
    public Enumerator createChannelEnumerator() {
        return fileChannelManager.createChannelEnumerator();
    }

    /**
     * Deletes the file underlying the given channel. If the channel is still open, this call may
     * fail.
     * 删除给定通道下的文件。 如果通道仍然打开，则此调用可能会失败。
     *
     * @param channel The channel to be deleted.
     */
    public static void deleteChannel(ID channel) {
        if (channel != null) {
            if (channel.getPathFile().exists() && !channel.getPathFile().delete()) {
                LOG.warn("IOManager failed to delete temporary file {}", channel.getPath());
            }
        }
    }

    /**
     * Gets the directories that the I/O manager spills to.
     * 获取 I/O 管理器溢出到的目录。
     *
     * @return The directories that the I/O manager spills to.
     */
    public File[] getSpillingDirectories() {
        return fileChannelManager.getPaths();
    }

    /**
     * Gets the directories that the I/O manager spills to, as path strings.
     * 获取 I/O 管理器溢出到的目录，作为路径字符串。
     *
     * @return The directories that the I/O manager spills to, as path strings.
     */
    public String[] getSpillingDirectoriesPaths() {
        File[] paths = fileChannelManager.getPaths();
        String[] strings = new String[paths.length];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = paths[i].getAbsolutePath();
        }
        return strings;
    }

    // ------------------------------------------------------------------------
    //                        Reader / Writer instantiations
    // ------------------------------------------------------------------------

    /**
     * Creates a block channel writer that writes to the given channel. The writer adds the written
     * segment to its return-queue afterwards (to allow for asynchronous implementations).
     * 创建一个写入给定通道的块通道写入器。 写入者之后将写入的段添加到其返回队列中（以允许异步实现）。
     *
     * @param channelID The descriptor for the channel to write to.
     * @return A block channel writer that writes to the given channel.
     * @throws IOException Thrown, if the channel for the writer could not be opened.
     */
    public BlockChannelWriter<MemorySegment> createBlockChannelWriter(ID channelID)
            throws IOException {
        return createBlockChannelWriter(channelID, new LinkedBlockingQueue<>());
    }

    /**
     * Creates a block channel writer that writes to the given channel. The writer adds the written
     * segment to the given queue (to allow for asynchronous implementations).
     * 创建一个写入给定通道的块通道写入器。 writer 将写入的段添加到给定的队列中（以允许异步实现）。
     *
     * @param channelID The descriptor for the channel to write to.
     * @param returnQueue The queue to put the written buffers into.
     * @return A block channel writer that writes to the given channel.
     * @throws IOException Thrown, if the channel for the writer could not be opened.
     */
    public abstract BlockChannelWriter<MemorySegment> createBlockChannelWriter(
            ID channelID, LinkedBlockingQueue<MemorySegment> returnQueue) throws IOException;

    /**
     * Creates a block channel writer that writes to the given channel. The writer calls the given
     * callback after the I/O operation has been performed (successfully or unsuccessfully), to
     * allow for asynchronous implementations.
     * 创建一个写入给定通道的块通道写入器。 编写器在执行 I/O 操作（成功或不成功）后调用给定的回调，以允许异步实现。
     *
     * @param channelID The descriptor for the channel to write to.
     * @param callback The callback to be called for
     * @return A block channel writer that writes to the given channel.
     * @throws IOException Thrown, if the channel for the writer could not be opened.
     */
    public abstract BlockChannelWriterWithCallback<MemorySegment> createBlockChannelWriter(
            ID channelID, RequestDoneCallback<MemorySegment> callback) throws IOException;

    /**
     * Creates a block channel reader that reads blocks from the given channel. The reader pushed
     * full memory segments (with the read data) to its "return queue", to allow for asynchronous
     * read implementations.
     * 创建一个从给定通道读取块的块通道读取器。 读取器将完整的内存段（带有读取数据）推送到其“返回队列”，以允许异步读取实现。
     *
     * @param channelID The descriptor for the channel to write to.
     * @return A block channel reader that reads from the given channel.
     * @throws IOException Thrown, if the channel for the reader could not be opened.
     */
    public BlockChannelReader<MemorySegment> createBlockChannelReader(ID channelID)
            throws IOException {
        return createBlockChannelReader(channelID, new LinkedBlockingQueue<>());
    }

    /**
     * Creates a block channel reader that reads blocks from the given channel. The reader pushes
     * the full segments to the given queue, to allow for asynchronous implementations.
     * 创建一个从给定通道读取块的块通道读取器。 阅读器将完整的段推送到给定的队列，以允许异步实现。
     *
     * @param channelID The descriptor for the channel to write to.
     * @param returnQueue The queue to put the full buffers into.
     * @return A block channel reader that reads from the given channel.
     * @throws IOException Thrown, if the channel for the reader could not be opened.
     */
    public abstract BlockChannelReader<MemorySegment> createBlockChannelReader(
            ID channelID, LinkedBlockingQueue<MemorySegment> returnQueue) throws IOException;

    public abstract BufferFileWriter createBufferFileWriter(ID channelID) throws IOException;

    public abstract BufferFileReader createBufferFileReader(
            ID channelID, RequestDoneCallback<Buffer> callback) throws IOException;

    public abstract BufferFileSegmentReader createBufferFileSegmentReader(
            ID channelID, RequestDoneCallback<FileSegment> callback) throws IOException;

    /**
     * Creates a block channel reader that reads all blocks from the given channel directly in one
     * bulk. The reader draws segments to read the blocks into from a supplied list, which must
     * contain as many segments as the channel has blocks. After the reader is done, the list with
     * the full segments can be obtained from the reader.
     * 创建一个块通道读取器，直接从给定通道中批量读取所有块。
     * 阅读器绘制段以从提供的列表中读取块，该列表必须包含与通道具有块一样多的段。
     * 阅读器完成后，可以从阅读器处获取包含完整段的列表。
     *
     * <p>If a channel is not to be read in one bulk, but in multiple smaller batches, a {@link
     * BlockChannelReader} should be used.
     * 如果不是要批量读取通道，而是要以多个较小的批次读取通道，则应使用 {@link BlockChannelReader}。
     *
     * @param channelID The descriptor for the channel to write to.
     * @param targetSegments The list to take the segments from into which to read the data.
     * @param numBlocks The number of blocks in the channel to read.
     * @return A block channel reader that reads from the given channel.
     * @throws IOException Thrown, if the channel for the reader could not be opened.
     */
    public abstract BulkBlockChannelReader createBulkBlockChannelReader(
            ID channelID, List<MemorySegment> targetSegments, int numBlocks) throws IOException;
}
