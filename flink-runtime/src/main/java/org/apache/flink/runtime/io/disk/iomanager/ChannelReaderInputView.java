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
import org.apache.flink.runtime.memory.AbstractPagedInputView;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A {@link org.apache.flink.core.memory.DataInputView} that is backed by a {@link
 * BlockChannelReader}, making it effectively a data input stream. The view reads it data in blocks
 * from the underlying channel. The view can only read data that has been written by a {@link
 * ChannelWriterOutputView}, due to block formatting.
 * 由 {@link BlockChannelReader} 支持的 {@link org.apache.flink.core.memory.DataInputView}，
 * 使其成为有效的数据输入流。 视图从底层通道以块的形式读取它的数据。
 * 由于块格式，视图只能读取由 {@link ChannelWriterOutputView} 写入的数据。
 */
public class ChannelReaderInputView extends AbstractChannelReaderInputView {

    protected final BlockChannelReader<MemorySegment>
            reader; // the block reader that reads memory segments // 读取内存段的块读取器

    protected int numRequestsRemaining; // the number of block requests remaining// 剩余的块请求数

    private final int numSegments; // the number of memory segment the view works with// 视图使用的内存段数

    private final ArrayList<MemorySegment> freeMem; // memory gathered once the work is done// 工作完成后收集的内存

    // 指示视图是否已经在最后一个块中的标志
    private boolean inLastBlock; // flag indicating whether the view is already in the last block

    private boolean closed; // flag indicating whether the reader is closed// 指示阅读器是否关闭的标志

    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new channel reader that reads from the given channel until the last block (as
     * marked by a {@link ChannelWriterOutputView}) is found.
     * 创建一个从给定通道读取的新通道读取器，直到找到最后一个块（由 {@link ChannelWriterOutputView} 标记）。
     *
     * @param reader The reader that reads the data from disk back into memory.
     * @param memory A list of memory segments that the reader uses for reading the data in. If the
     *     list contains more than one segment, the reader will asynchronously pre-fetch blocks
     *     ahead.
     * @param waitForFirstBlock A flag indicating weather this constructor call should block until
     *     the first block has returned from the asynchronous I/O reader.
     * @throws IOException Thrown, if the read requests for the first blocks fail to be served by
     *     the reader.
     */
    public ChannelReaderInputView(
            BlockChannelReader<MemorySegment> reader,
            List<MemorySegment> memory,
            boolean waitForFirstBlock)
            throws IOException {
        this(reader, memory, -1, waitForFirstBlock);
    }

    /**
     * Creates a new channel reader that reads from the given channel, expecting a specified number
     * of blocks in the channel.
     * 创建一个从给定通道读取的新通道读取器，期望通道中有指定数量的块。
     *
     * <p>WARNING: The reader will lock if the number of blocks given here is actually lower than
     * the actual number of blocks in the channel.
     * 警告：如果此处给出的块数实际上低于通道中的实际块数，阅读器将锁定。
     *
     * @param reader The reader that reads the data from disk back into memory.
     * @param memory A list of memory segments that the reader uses for reading the data in. If the
     *     list contains more than one segment, the reader will asynchronously pre-fetch blocks
     *     ahead.
     * @param numBlocks The number of blocks this channel will read. If this value is given, the
     *     reader avoids issuing pre-fetch requests for blocks beyond the channel size.
     * @param waitForFirstBlock A flag indicating weather this constructor call should block until
     *     the first block has returned from the asynchronous I/O reader.
     * @throws IOException Thrown, if the read requests for the first blocks fail to be served by
     *     the reader.
     */
    public ChannelReaderInputView(
            BlockChannelReader<MemorySegment> reader,
            List<MemorySegment> memory,
            int numBlocks,
            boolean waitForFirstBlock)
            throws IOException {
        this(reader, memory, numBlocks, ChannelWriterOutputView.HEADER_LENGTH, waitForFirstBlock);
    }

    /**
     * Non public constructor to allow subclasses to use this input view with different headers.
     * 非公共构造函数允许子类使用具有不同标题的输入视图。
     *
     * <p>WARNING: The reader will lock if the number of blocks given here is actually lower than
     * the actual number of blocks in the channel.
     * 警告：如果此处给出的块数实际上低于通道中的实际块数，阅读器将锁定。
     *
     * @param reader The reader that reads the data from disk back into memory.
     * @param memory A list of memory segments that the reader uses for reading the data in. If the
     *     list contains more than one segment, the reader will asynchronously pre-fetch blocks
     *     ahead.
     * @param numBlocks The number of blocks this channel will read. If this value is given, the
     *     reader avoids issuing pre-fetch requests for blocks beyond the channel size.
     * @param headerLen The length of the header assumed at the beginning of the block. Note that
     *     the {@link #nextSegment(org.apache.flink.core.memory.MemorySegment)} method assumes the
     *     default header length, so any subclass changing the header length should override that
     *     methods as well.
     * @param waitForFirstBlock A flag indicating weather this constructor call should block until
     *     the first block has returned from the asynchronous I/O reader.
     * @throws IOException
     */
    ChannelReaderInputView(
            BlockChannelReader<MemorySegment> reader,
            List<MemorySegment> memory,
            int numBlocks,
            int headerLen,
            boolean waitForFirstBlock)
            throws IOException {
        super(headerLen);

        if (reader == null || memory == null) {
            throw new NullPointerException();
        }
        if (memory.isEmpty()) {
            throw new IllegalArgumentException("Empty list of memory segments given.");
        }
        if (numBlocks < 1 && numBlocks != -1) {
            throw new IllegalArgumentException(
                    "The number of blocks must be a positive number, or -1, if unknown.");
        }

        this.reader = reader;
        this.numRequestsRemaining = numBlocks;
        this.numSegments = memory.size();
        this.freeMem = new ArrayList<MemorySegment>(this.numSegments);

        for (int i = 0; i < memory.size(); i++) {
            sendReadRequest(memory.get(i));
        }

        if (waitForFirstBlock) {
            advance();
        }
    }

    public void waitForFirstBlock() throws IOException {
        if (getCurrentSegment() == null) {
            advance();
        }
    }

    public boolean isClosed() {
        return this.closed;
    }

    /**
     * Closes this InputView, closing the underlying reader and returning all memory segments.
     * 关闭此 InputView，关闭底层阅读器并返回所有内存段。
     *
     * @return A list containing all memory segments originally supplied to this view.
     * @throws IOException Thrown, if the underlying reader could not be properly closed.
     */
    @Override
    public List<MemorySegment> close() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed.");
        }
        this.closed = true;

        // re-collect all memory segments
        ArrayList<MemorySegment> list = this.freeMem;
        final MemorySegment current = getCurrentSegment();
        if (current != null) {
            list.add(current);
        }
        clear();

        // close the writer and gather all segments
        final LinkedBlockingQueue<MemorySegment> queue = this.reader.getReturnQueue();
        this.reader.close();

        while (list.size() < this.numSegments) {
            final MemorySegment m = queue.poll();
            if (m == null) {
                // we get null if the queue is empty. that should not be the case if the reader was
                // properly closed.
                throw new RuntimeException("Bug in ChannelReaderInputView: MemorySegments lost.");
            }
            list.add(m);
        }
        return list;
    }

    @Override
    public FileIOChannel getChannel() {
        return reader;
    }

    // --------------------------------------------------------------------------------------------
    //                                        Utilities
    // --------------------------------------------------------------------------------------------

    /**
     * Gets the next segment from the asynchronous block reader. If more requests are to be issued,
     * the method first sends a new request with the current memory segment. If no more requests are
     * pending, the method adds the segment to the readers return queue, which thereby effectively
     * collects all memory segments. Secondly, the method fetches the next non-consumed segment
     * returned by the reader. If no further segments are available, this method thrown an {@link
     * EOFException}.
     * 从异步块读取器获取下一段。 如果要发出更多请求，该方法首先使用当前内存段发送一个新请求。
     * 如果没有更多的请求待处理，则该方法将该段添加到读取器返回队列，从而有效地收集所有内存段。
     * 其次，该方法获取读取器返回的下一个未使用的段。 如果没有更多可用段，则此方法抛出 {@link EOFException}。
     *
     * @param current The memory segment used for the next request.
     * @return The memory segment to read from next.
     * @throws EOFException Thrown, if no further segments are available.
     * @throws IOException Thrown, if an I/O error occurred while reading
     * @see AbstractPagedInputView#nextSegment(org.apache.flink.core.memory.MemorySegment)
     */
    @Override
    protected MemorySegment nextSegment(MemorySegment current) throws IOException {
        // check if we are at our end
        if (this.inLastBlock) {
            throw new EOFException();
        }

        // send a request first. if we have only a single segment, this same segment will be the one
        // obtained in
        // the next lines
        if (current != null) {
            sendReadRequest(current);
        }

        // get the next segment
        final MemorySegment seg = this.reader.getNextReturnedBlock();

        // check the header
        if (seg.getShort(0) != ChannelWriterOutputView.HEADER_MAGIC_NUMBER) {
            throw new IOException(
                    "The current block does not belong to a ChannelWriterOutputView / "
                            + "ChannelReaderInputView: Wrong magic number.");
        }
        if ((seg.getShort(ChannelWriterOutputView.HEADER_FLAGS_OFFSET)
                        & ChannelWriterOutputView.FLAG_LAST_BLOCK)
                != 0) {
            // last block
            this.numRequestsRemaining = 0;
            this.inLastBlock = true;
        }

        return seg;
    }

    @Override
    protected int getLimitForSegment(MemorySegment segment) {
        return segment.getInt(ChannelWriterOutputView.HEAD_BLOCK_LENGTH_OFFSET);
    }

    /**
     * Sends a new read requests, if further requests remain. Otherwise, this method adds the
     * segment directly to the readers return queue.
     * 如果还有其他请求，则发送新的读取请求。 否则，此方法直接将段添加到读者返回队列。
     *
     * @param seg The segment to use for the read request.
     * @throws IOException Thrown, if the reader is in error.
     */
    protected void sendReadRequest(MemorySegment seg) throws IOException {
        if (this.numRequestsRemaining != 0) {
            this.reader.readBlock(seg);
            if (this.numRequestsRemaining != -1) {
                this.numRequestsRemaining--;
            }
        } else {
            // directly add it to the end of the return queue
            this.freeMem.add(seg);
        }
    }
}
