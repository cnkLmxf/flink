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

import java.io.Serializable;

/**
 * Base of all handles that represent checkpointed state in some form. The object may hold the
 * (small) state directly, or contain a file path (state is in the file), or contain the metadata to
 * access the state stored in some external database.
 * 以某种形式表示检查点状态的所有句柄的基础。
 * 对象可能直接持有（小）状态，或包含文件路径（状态在文件中），或包含元数据以访问存储在某些外部数据库中的状态。
 *
 * <p>State objects define how to {@link #discardState() discard state} and how to access the {@link
 * #getStateSize() size of the state}.
 * 状态对象定义了如何{@link #discardState() 丢弃状态}以及如何访问状态的{@link #getStateSize() 大小}。
 *
 * <p>State Objects are transported via RPC between <i>JobManager</i> and <i>TaskManager</i> and
 * must be {@link java.io.Serializable serializable} to support that.
 * 状态对象通过 <i>JobManager</i> 和 <i>TaskManager</i> 之间的 RPC 传输，
 * 并且必须是 {@link java.io.Serializable serializable} 以支持这一点。
 *
 * <p>Some State Objects are stored in the checkpoint/savepoint metadata. For long-term
 * compatibility, they are not stored via {@link java.io.Serializable Java Serialization}, but
 * through custom serializers.
 * 一些状态对象存储在检查点/保存点元数据中。
 * 为了长期兼容性，它们不是通过 {@link java.io.Serializable Java Serialization} 存储，而是通过自定义序列化器存储。
 */
public interface StateObject extends Serializable {

    /**
     * Discards the state referred to and solemnly owned by this handle, to free up resources in the
     * persistent storage. This method is called when the state represented by this object will not
     * be used any more.
     * 丢弃此句柄引用和庄严拥有的状态，以释放持久存储中的资源。 当不再使用此对象表示的状态时，将调用此方法。
     */
    void discardState() throws Exception;

    /**
     * Returns the size of the state in bytes. If the size is not known, this method should return
     * {@code 0}.
     * 以字节为单位返回状态的大小。 如果大小未知，则此方法应返回 {@code 0}。
     *
     * <p>The values produced by this method are only used for informational purposes and for
     * metrics/monitoring. If this method returns wrong values, the checkpoints and recovery will
     * still behave correctly. However, efficiency may be impacted (wrong space pre-allocation) and
     * functionality that depends on metrics (like monitoring) will be impacted.
     * 此方法产生的值仅用于提供信息和度量/监控。 如果此方法返回错误值，检查点和恢复仍将正确运行。
     * 但是，效率可能会受到影响（错误的空间预分配），并且依赖于指标（如监控）的功能也会受到影响。
     *
     * <p>Note for implementors: This method should not perform any I/O operations while obtaining
     * the state size (hence it does not declare throwing an {@code IOException}). Instead, the
     * state size should be stored in the state object, or should be computable from the state
     * stored in this object. The reason is that this method is called frequently by several parts
     * of the checkpointing and issuing I/O requests from this method accumulates a heavy I/O load
     * on the storage system at higher scale.
     * 实现者注意：此方法在获取状态大小时不应执行任何 I/O 操作（因此它不会声明抛出 {@code IOException}）。
     * 相反，状态大小应该存储在状态对象中，或者应该可以根据存储在该对象中的状态进行计算。
     * 原因是检查点的几个部分会频繁调用此方法，并且从该方法发出 I/O 请求会在更高规模的存储系统上累积繁重的 I/O 负载。
     *
     * @return Size of the state in bytes.
     */
    long getStateSize();
}
