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

package org.apache.flink.api.connector.source;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.state.CheckpointListener;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * A interface of a split enumerator responsible for the followings: 1. discover the splits for the
 * {@link SourceReader} to read. 2. assign the splits to the source reader.
 * 拆分枚举器接口负责以下工作： 1. 发现{@link SourceReader} 读取的拆分。 2. 将拆分分配给源阅读器。
 */
@PublicEvolving
public interface SplitEnumerator<SplitT extends SourceSplit, CheckpointT>
        extends AutoCloseable, CheckpointListener {

    /**
     * Start the split enumerator.
     *
     * <p>The default behavior does nothing.
     */
    void start();

    /**
     * Handles the request for a split. This method is called when the reader with the given subtask
     * id calls the {@link SourceReaderContext#sendSplitRequest()} method.
     * 处理拆分请求。 当具有给定子任务 ID 的阅读器调用 {@link SourceReaderContext#sendSplitRequest()} 方法时，将调用此方法。
     *
     * @param subtaskId the subtask id of the source reader who sent the source event.
     * @param requesterHostname Optional, the hostname where the requesting task is running. This
     *     can be used to make split assignments locality-aware.
     */
    void handleSplitRequest(int subtaskId, @Nullable String requesterHostname);

    /**
     * Add a split back to the split enumerator. It will only happen when a {@link SourceReader}
     * fails and there are splits assigned to it after the last successful checkpoint.
     * 将拆分添加回拆分枚举器。 它只会在 {@link SourceReader} 失败并且在最后一个成功的检查点之后分配给它的拆分时才会发生。
     *
     * @param splits The split to add back to the enumerator for reassignment.
     * @param subtaskId The id of the subtask to which the returned splits belong.
     */
    void addSplitsBack(List<SplitT> splits, int subtaskId);

    /**
     * Add a new source reader with the given subtask ID.
     * 添加具有给定子任务 ID 的新源阅读器。
     *
     * @param subtaskId the subtask ID of the new source reader.
     */
    void addReader(int subtaskId);

    /**
     * Creates a snapshot of the state of this split enumerator, to be stored in a checkpoint.
     * 创建此拆分枚举器状态的快照，以存储在检查点中。
     *
     * <p>The snapshot should contain the latest state of the enumerator: It should assume that all
     * operations that happened before the snapshot have successfully completed. For example all
     * splits assigned to readers via {@link SplitEnumeratorContext#assignSplit(SourceSplit, int)}
     * and {@link SplitEnumeratorContext#assignSplits(SplitsAssignment)}) don't need to be included
     * in the snapshot anymore.
     * 快照应该包含枚举器的最新状态：它应该假设在快照之前发生的所有操作都已成功完成。
     * 例如，通过 {@link SplitEnumeratorContext#assignSplit(SourceSplit, int)}
     * 和 {@link SplitEnumeratorContext#assignSplits(SplitsAssignment)}) 分配给读者的所有拆分不再需要包含在快照中。
     *
     * <p>This method takes the ID of the checkpoint for which the state is snapshotted. Most
     * implementations should be able to ignore this parameter, because for the contents of the
     * snapshot, it doesn't matter for which checkpoint it gets created. This parameter can be
     * interesting for source connectors with external systems where those systems are themselves
     * aware of checkpoints; for example in cases where the enumerator notifies that system about a
     * specific checkpoint being triggered.
     * 此方法获取状态被快照的检查点的 ID。 大多数实现应该能够忽略此参数，因为对于快照的内容，它创建的检查点无关紧要。
     * 这个参数对于带有外部系统的源连接器可能很有趣，这些系统本身知道检查点；
     * 例如，在枚举器通知该系统有关触发特定检查点的情况下。
     *
     * @param checkpointId The ID of the checkpoint for which the snapshot is created.
     * @return an object containing the state of the split enumerator.
     * @throws Exception when the snapshot cannot be taken.
     */
    CheckpointT snapshotState(long checkpointId) throws Exception;

    /**
     * Called to close the enumerator, in case it holds on to any resources, like threads or network
     * connections.
     * 调用以关闭枚举器，以防它占用任何资源，如线程或网络连接。
     */
    @Override
    void close() throws IOException;

    /**
     * We have an empty default implementation here because most source readers do not have to
     * implement the method.
     * 我们这里有一个空的默认实现，因为大多数源代码阅读器不必实现该方法。
     *
     * @see CheckpointListener#notifyCheckpointComplete(long)
     */
    @Override
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}

    /**
     * Handles a custom source event from the source reader.
     * 处理来自源阅读器的自定义源事件。
     *
     * <p>This method has a default implementation that does nothing, because it is only required to
     * be implemented by some sources, which have a custom event protocol between reader and
     * enumerator. The common events for reader registration and split requests are not dispatched
     * to this method, but rather invoke the {@link #addReader(int)} and {@link
     * #handleSplitRequest(int, String)} methods.
     * 此方法有一个默认实现，它什么都不做，因为它只需要由某些源实现，这些源在读取器和枚举器之间具有自定义事件协议。
     * 读者注册和拆分请求的常见事件不会分派到此方法，而是调用 {@link #addReader(int)} 和 {@link #handleSplitRequest(int, String)} 方法。
     *
     * @param subtaskId the subtask id of the source reader who sent the source event.
     * @param sourceEvent the source event from the source reader.
     */
    default void handleSourceEvent(int subtaskId, SourceEvent sourceEvent) {}
}
