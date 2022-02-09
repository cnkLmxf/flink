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
import org.apache.flink.core.io.InputStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The interface for a source reader which is responsible for reading the records from the source
 * splits assigned by {@link SplitEnumerator}.
 * 源阅读器的接口，负责从 {@link SplitEnumerator} 分配的源拆分中读取记录。
 *
 * @param <T> The type of the record emitted by this source reader.
 * @param <SplitT> The type of the the source splits.
 */
@PublicEvolving
public interface SourceReader<T, SplitT extends SourceSplit>
        extends AutoCloseable, CheckpointListener {

    /** Start the reader. */
    void start();

    /**
     * Poll the next available record into the {@link SourceOutput}.
     * 将下一条可用记录轮询到 {@link SourceOutput}。
     *
     * <p>The implementation must make sure this method is non-blocking.
     * 实现必须确保此方法是非阻塞的。
     *
     * <p>Although the implementation can emit multiple records into the given SourceOutput, it is
     * recommended not doing so. Instead, emit one record into the SourceOutput and return a {@link
     * InputStatus#MORE_AVAILABLE} to let the caller thread know there are more records available.
     * 尽管实现可以将多条记录发送到给定的 SourceOutput，但建议不要这样做。
     * 相反，将一条记录发送到 SourceOutput 并返回 {@link InputStatus#MORE_AVAILABLE} 以让调用者线程知道还有更多可用记录。
     *
     * @return The InputStatus of the SourceReader after the method invocation.
     */
    InputStatus pollNext(ReaderOutput<T> output) throws Exception;

    /**
     * Checkpoint on the state of the source.
     * 源状态的检查点。
     *
     * @return the state of the source.
     */
    List<SplitT> snapshotState(long checkpointId);

    /**
     * Returns a future that signals that data is available from the reader.
     * 返回一个未来，表明数据可以从阅读器获得。
     *
     * <p>Once the future completes, the runtime will keep calling the {@link
     * #pollNext(ReaderOutput)} method until that methods returns a status other than {@link
     * InputStatus#MORE_AVAILABLE}. After that the, the runtime will again call this method to
     * obtain the next future. Once that completes, it will again call {@link
     * #pollNext(ReaderOutput)} and so on.
     * 一旦 future 完成，运行时将继续调用 {@link #pollNext(ReaderOutput)} 方法，
     * 直到该方法返回 {@link InputStatus#MORE_AVAILABLE} 以外的状态。
     * 之后，runtime会再次调用这个方法来获取下一个future。 一旦完成，它将再次调用 {@link #pollNext(ReaderOutput)} 等等。
     *
     * <p>The contract is the following: If the reader has data available, then all futures
     * previously returned by this method must eventually complete. Otherwise the source might stall
     * indefinitely.
     * 合约如下：如果读取器有可用数据，则此方法之前返回的所有期货最终都必须完成。 否则，源可能会无限期停止。
     *
     * <p>It is not a problem to have occasional "false positives", meaning to complete a future
     * even if no data is available. However, one should not use an "always complete" future in
     * cases no data is available, because that will result in busy waiting loops calling {@code
     * pollNext(...)} even though no data is available.
     * 偶尔出现“误报”不是问题，这意味着即使没有可用的数据也可以完成未来。
     * 但是，在没有可用数据的情况下，不应使用“始终完整”的未来，因为即使没有可用数据，
     * 这也会导致调用 {@code pollNext(...)} 的繁忙等待循环。
     *
     * @return a future that will be completed once there is a record available to poll.
     */
    CompletableFuture<Void> isAvailable();

    /**
     * Adds a list of splits for this reader to read. This method is called when the enumerator
     * assigns a split via {@link SplitEnumeratorContext#assignSplit(SourceSplit, int)} or {@link
     * SplitEnumeratorContext#assignSplits(SplitsAssignment)}.
     * 添加供此读者阅读的拆分列表。 当枚举器通过 {@link SplitEnumeratorContext#assignSplit(SourceSplit, int)}
     * 或 {@link SplitEnumeratorContext#assignSplits(SplitsAssignment)} 分配拆分时，将调用此方法。
     *
     * @param splits The splits assigned by the split enumerator.
     */
    void addSplits(List<SplitT> splits);

    /**
     * This method is called when the reader is notified that it will not receive any further
     * splits.
     * 当通知读者它将不会收到任何进一步的拆分时，将调用此方法。
     *
     * <p>It is triggered when the enumerator calls {@link
     * SplitEnumeratorContext#signalNoMoreSplits(int)} with the reader's parallel subtask.
     */
    void notifyNoMoreSplits();

    /**
     * Handle a custom source event sent by the {@link SplitEnumerator}. This method is called when
     * the enumerator sends an event via {@link SplitEnumeratorContext#sendEventToSourceReader(int,
     * SourceEvent)}.
     * 处理由 {@link SplitEnumerator} 发送的自定义源事件。
     * 当枚举器通过 {@link SplitEnumeratorContext#sendEventToSourceReader(int, SourceEvent)} 发送事件时调用此方法。
     *
     * <p>This method has a default implementation that does nothing, because most sources do not
     * require any custom events.
     * 此方法有一个默认实现，它什么都不做，因为大多数源不需要任何自定义事件。
     *
     * @param sourceEvent the event sent by the {@link SplitEnumerator}.
     */
    default void handleSourceEvents(SourceEvent sourceEvent) {}

    /**
     * We have an empty default implementation here because most source readers do not have to
     * implement the method.
     * 我们这里有一个空的默认实现，因为大多数源代码阅读器不必实现该方法。
     *
     * @see CheckpointListener#notifyCheckpointComplete(long)
     */
    @Override
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}
}
