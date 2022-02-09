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

package org.apache.flink.runtime.io.network.partition.consumer;

import org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter;
import org.apache.flink.runtime.checkpoint.channel.InputChannelInfo;
import org.apache.flink.runtime.event.TaskEvent;
import org.apache.flink.runtime.io.PullingAsyncDataInput;
import org.apache.flink.runtime.io.network.partition.ChannelStateHolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * An input gate consumes one or more partitions of a single produced intermediate result.
 * 输入门消耗单个生成的中间结果的一个或多个分区。
 *
 * <p>Each intermediate result is partitioned over its producing parallel subtasks; each of these
 * partitions is furthermore partitioned into one or more subpartitions.
 * 每个中间结果在其产生的并行子任务上进行分区； 此外，这些分区中的每一个都被划分为一个或多个子分区。
 *
 * <p>As an example, consider a map-reduce program, where the map operator produces data and the
 * reduce operator consumes the produced data.
 * 例如，考虑一个 map-reduce 程序，其中 map 运算符生成数据，reduce 运算符使用生成的数据。
 *
 * <pre>{@code
 * +-----+              +---------------------+              +--------+
 * | Map | = produce => | Intermediate Result | <= consume = | Reduce |
 * +-----+              +---------------------+              +--------+
 * }</pre>
 *
 * <p>When deploying such a program in parallel, the intermediate result will be partitioned over
 * its producing parallel subtasks; each of these partitions is furthermore partitioned into one or
 * more subpartitions.
 * 当并行部署这样的程序时，中间结果将被划分到其产生的并行子任务上； 此外，这些分区中的每一个都被划分为一个或多个子分区。
 *
 * <pre>{@code
 *                            Intermediate result
 *               +-----------------------------------------+
 *               |                      +----------------+ |              +-----------------------+
 * +-------+     | +-------------+  +=> | Subpartition 1 | | <=======+=== | Input Gate | Reduce 1 |
 * | Map 1 | ==> | | Partition 1 | =|   +----------------+ |         |    +-----------------------+
 * +-------+     | +-------------+  +=> | Subpartition 2 | | <==+    |
 *               |                      +----------------+ |    |    | Subpartition request
 *               |                                         |    |    |
 *               |                      +----------------+ |    |    |
 * +-------+     | +-------------+  +=> | Subpartition 1 | | <==+====+
 * | Map 2 | ==> | | Partition 2 | =|   +----------------+ |    |         +-----------------------+
 * +-------+     | +-------------+  +=> | Subpartition 2 | | <==+======== | Input Gate | Reduce 2 |
 *               |                      +----------------+ |              +-----------------------+
 *               +-----------------------------------------+
 * }</pre>
 *
 * <p>In the above example, two map subtasks produce the intermediate result in parallel, resulting
 * in two partitions (Partition 1 and 2). Each of these partitions is further partitioned into two
 * subpartitions -- one for each parallel reduce subtask. As shown in the Figure, each reduce task
 * will have an input gate attached to it. This will provide its input, which will consist of one
 * subpartition from each partition of the intermediate result.
 * 在上面的示例中，两个 map 子任务并行产生中间结果，从而产生两个分区（分区 1 和 2）。
 * 这些分区中的每一个都被进一步划分为两个子分区——一个用于每个并行 reduce 子任务。
 * 如图所示，每个reduce 任务都将附加一个输入门。 这将提供其输入，该输入将由来自中间结果的每个分区的一个子分区组成。
 */
public abstract class InputGate
        implements PullingAsyncDataInput<BufferOrEvent>, AutoCloseable, ChannelStateHolder {

    protected final AvailabilityHelper availabilityHelper = new AvailabilityHelper();

    protected final AvailabilityHelper priorityAvailabilityHelper = new AvailabilityHelper();

    @Override
    public void setChannelStateWriter(ChannelStateWriter channelStateWriter) {
        for (int index = 0, numChannels = getNumberOfInputChannels();
                index < numChannels;
                index++) {
            final InputChannel channel = getChannel(index);
            if (channel instanceof ChannelStateHolder) {
                ((ChannelStateHolder) channel).setChannelStateWriter(channelStateWriter);
            }
        }
    }

    public abstract int getNumberOfInputChannels();

    public abstract boolean isFinished();

    /**
     * Blocking call waiting for next {@link BufferOrEvent}.
     * 阻塞呼叫等待下一个 {@link BufferOrEvent}。
     *
     * <p>Note: It should be guaranteed that the previous returned buffer has been recycled before
     * getting next one.
     * 注意：要保证上一个返回的缓冲区在得到下一个之前已经被回收。
     *
     * @return {@code Optional.empty()} if {@link #isFinished()} returns true.
     */
    public abstract Optional<BufferOrEvent> getNext() throws IOException, InterruptedException;

    /**
     * Poll the {@link BufferOrEvent}.
     *
     * <p>Note: It should be guaranteed that the previous returned buffer has been recycled before
     * polling next one.
     * 注意：要保证上一个返回的缓冲区在得到下一个之前已经被回收。
     *
     * @return {@code Optional.empty()} if there is no data to return or if {@link #isFinished()}
     *     returns true.
     */
    public abstract Optional<BufferOrEvent> pollNext() throws IOException, InterruptedException;

    public abstract void sendTaskEvent(TaskEvent event) throws IOException;

    /**
     * @return a future that is completed if there are more records available. If there are more
     *     records available immediately, {@link #AVAILABLE} should be returned. Previously returned
     *     not completed futures should become completed once there are more records available.
     *     如果有更多可用记录，则完成的未来。 如果立即有更多可用记录，则应返回 {@link #AVAILABLE}。
     *     一旦有更多可用记录，先前返回的未完成期货应该会完成。
     */
    @Override
    public CompletableFuture<?> getAvailableFuture() {
        return availabilityHelper.getAvailableFuture();
    }

    public abstract void resumeConsumption(InputChannelInfo channelInfo) throws IOException;

    /** Returns the channel of this gate.
     * 返回此门的通道。
     * */
    public abstract InputChannel getChannel(int channelIndex);

    /** Returns the channel infos of this gate.
     * 返回此门的通道信息。
     * */
    public List<InputChannelInfo> getChannelInfos() {
        return IntStream.range(0, getNumberOfInputChannels())
                .mapToObj(index -> getChannel(index).getChannelInfo())
                .collect(Collectors.toList());
    }

    /**
     * Notifies when a priority event has been enqueued. If this future is queried from task thread,
     * it is guaranteed that a priority event is available and retrieved through {@link #getNext()}.
     * 当优先级事件已入队时通知。 如果从任务线程中查询此未来，则保证优先级事件可用并通过 {@link #getNext()} 检索。
     */
    public CompletableFuture<?> getPriorityEventAvailableFuture() {
        return priorityAvailabilityHelper.getAvailableFuture();
    }

    /** Simple pojo for INPUT, DATA and moreAvailable.
     * 用于 INPUT、DATA 等的简单 pojo 可用。
     * */
    protected static class InputWithData<INPUT, DATA> {
        protected final INPUT input;
        protected final DATA data;
        protected final boolean moreAvailable;
        protected final boolean morePriorityEvents;

        InputWithData(INPUT input, DATA data, boolean moreAvailable, boolean morePriorityEvents) {
            this.input = checkNotNull(input);
            this.data = checkNotNull(data);
            this.moreAvailable = moreAvailable;
            this.morePriorityEvents = morePriorityEvents;
        }

        @Override
        public String toString() {
            return "InputWithData{"
                    + "input="
                    + input
                    + ", data="
                    + data
                    + ", moreAvailable="
                    + moreAvailable
                    + ", morePriorityEvents="
                    + morePriorityEvents
                    + '}';
        }
    }

    /** Setup gate, potentially heavy-weight, blocking operation comparing to just creation.
     * 与仅创建相比，设置门，可能是重量级的阻塞操作。
     * */
    public abstract void setup() throws IOException;

    public abstract void requestPartitions() throws IOException;

    public abstract CompletableFuture<Void> getStateConsumedFuture();

    public abstract void finishReadRecoveredState() throws IOException;
}
