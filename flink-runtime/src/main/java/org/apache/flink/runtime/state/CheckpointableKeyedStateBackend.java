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

package org.apache.flink.runtime.state;

import org.apache.flink.api.common.state.CheckpointListener;

import javax.annotation.Nonnull;

import java.io.Closeable;

/**
 * Interface that combines both, the {@link KeyedStateBackend} interface, which encapsulates methods
 * responsible for keyed state management and the {@link Snapshotable} which tells the system how to
 * snapshot the underlying state.
 * 结合了两者的接口，{@link KeyedStateBackend} 接口封装了负责键控状态管理的方法和 {@link Snapshotable}，它告诉系统如何对底层状态进行快照。
 *
 * <p><b>NOTE:</b> State backends that need to be notified of completed checkpoints can additionally
 * implement the {@link CheckpointListener} interface.
 * <b>注意：</b> 需要通知已完成检查点的状态后端可以另外实现 {@link CheckpointListener} 接口。
 *
 * @param <K> Type of the key by which state is keyed.
 */
public interface CheckpointableKeyedStateBackend<K>
        extends KeyedStateBackend<K>, Snapshotable<SnapshotResult<KeyedStateHandle>>, Closeable {

    /** Returns the key groups which this state backend is responsible for.
     * 返回此状态后端负责的KeyGroup。
     * */
    KeyGroupRange getKeyGroupRange();

    /**
     * Returns a {@link SavepointResources} that can be used by {@link SavepointSnapshotStrategy} to
     * write out a savepoint in the common/unified format.
     * 返回一个 {@link SavepointResources}，{@link SavepointSnapshotStrategy} 可以使用它以通用/统一格式写出一个保存点。
     */
    @Nonnull
    SavepointResources<K> savepoint() throws Exception;
}
