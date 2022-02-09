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

package org.apache.flink.api.common.io;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.io.InputSplit;

import java.io.IOException;
import java.io.Serializable;

/**
 * An interface that describes {@link InputFormat}s that allow checkpointing/restoring their state.
 * 描述允许检查点/恢复其状态的 {@link InputFormat} 的接口。
 *
 * @param <S> The type of input split.
 * @param <T> The type of the channel state to be checkpointed / included in the snapshot.
 */
@PublicEvolving
public interface CheckpointableInputFormat<S extends InputSplit, T extends Serializable> {

    /**
     * Returns the split currently being read, along with its current state. This will be used to
     * restore the state of the reading channel when recovering from a task failure. In the case of
     * a simple text file, the state can correspond to the last read offset in the split.
     * 返回当前正在读取的拆分及其当前状态。 这将用于在从任务失败中恢复时恢复读取通道的状态。
     * 在简单文本文件的情况下，状态可以对应于拆分中的最后读取偏移量。
     *
     * @return The state of the channel.
     * @throws IOException Thrown if the creation of the state object failed.
     */
    T getCurrentState() throws IOException;

    /**
     * Restores the state of a parallel instance reading from an {@link InputFormat}. This is
     * necessary when recovering from a task failure. When this method is called, the input format
     * it guaranteed to be configured.
     * 恢复从 {@link InputFormat} 读取的并行实例的状态。 从任务失败中恢复时，这是必要的。 调用此方法时，它保证配置的输入格式。
     *
     * <p><b>NOTE: </b> The caller has to make sure that the provided split is the one to whom the
     * state belongs.
     * <b>注意：</b> 调用者必须确保提供的拆分是状态所属的拆分。
     *
     * @param split The split to be opened.
     * @param state The state from which to start from. This can contain the offset, but also other
     *     data, depending on the input format.
     */
    void reopen(S split, T state) throws IOException;
}
