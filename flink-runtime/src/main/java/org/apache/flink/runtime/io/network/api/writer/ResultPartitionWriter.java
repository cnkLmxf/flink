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

package org.apache.flink.runtime.io.network.api.writer;

import org.apache.flink.runtime.event.AbstractEvent;
import org.apache.flink.runtime.io.AvailabilityProvider;
import org.apache.flink.runtime.io.network.partition.BufferAvailabilityListener;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.io.network.partition.ResultSubpartitionView;
import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A record-oriented runtime result writer API for producing results.
 * 用于生成结果的面向记录的运行时结果编写器 API。
 *
 * <p>If {@link ResultPartitionWriter#close()} is called before {@link
 * ResultPartitionWriter#fail(Throwable)} or {@link ResultPartitionWriter#finish()}, it abruptly
 * triggers failure and cancellation of production. In this case {@link
 * ResultPartitionWriter#fail(Throwable)} still needs to be called afterwards to fully release all
 * resources associated the the partition and propagate failure cause to the consumer if possible.
 * 如果在 {@link ResultPartitionWriter#fail(Throwable)} 或 {@link ResultPartitionWriter#finish()}
 * 之前调用 {@link ResultPartitionWriter#close()}，它会突然触发失败并取消生产。
 * 在这种情况下，仍然需要在之后调用 {@link ResultPartitionWriter#fail(Throwable)}
 * 以完全释放与分区关联的所有资源，并尽可能将失败原因传播给消费者。
 */
public interface ResultPartitionWriter extends AutoCloseable, AvailabilityProvider {

    /** Setup partition, potentially heavy-weight, blocking operation comparing to just creation.
     * 与仅创建相比，设置分区，可能是重量级的阻塞操作。
     * */
    void setup() throws IOException;

    ResultPartitionID getPartitionId();

    int getNumberOfSubpartitions();

    int getNumTargetKeyGroups();

    /** Writes the given serialized record to the target subpartition.
     * 将给定的序列化记录写入目标子分区。
     * */
    void emitRecord(ByteBuffer record, int targetSubpartition) throws IOException;

    /**
     * Writes the given serialized record to all subpartitions. One can also achieve the same effect
     * by emitting the same record to all subpartitions one by one, however, this method can have
     * better performance for the underlying implementation can do some optimizations, for example
     * coping the given serialized record only once to a shared channel which can be consumed by all
     * subpartitions.
     * 将给定的序列化记录写入所有子分区。 也可以通过将相同的记录一个一个地发送到所有子分区来达到相同的效果，
     * 但是这种方法可以具有更好的性能，因为底层实现可以做一些优化，例如只将给定的序列化记录处理一次到共享通道
     * 可以被所有子分区使用。
     */
    void broadcastRecord(ByteBuffer record) throws IOException;

    /** Writes the given {@link AbstractEvent} to all channels.
     * 将给定的 {@link AbstractEvent} 写入所有通道
     * */
    void broadcastEvent(AbstractEvent event, boolean isPriorityEvent) throws IOException;

    /** Sets the metric group for the {@link ResultPartitionWriter}.
     * 设置 {@link ResultPartitionWriter} 的指标组。
     * */
    void setMetricGroup(TaskIOMetricGroup metrics);

    /** Returns a reader for the subpartition with the given index.
     * 返回具有给定索引的子分区的读取器。
     * */
    ResultSubpartitionView createSubpartitionView(
            int index, BufferAvailabilityListener availabilityListener) throws IOException;

    /** Manually trigger the consumption of data from all subpartitions.
     * 手动触发对所有子分区数据的消费。
     * */
    void flushAll();

    /** Manually trigger the consumption of data from the given subpartitions.
     * 手动触发来自给定子分区的数据消耗。
     * */
    void flush(int subpartitionIndex);

    /**
     * Fail the production of the partition.
     * 分区的生产失败。
     *
     * <p>This method propagates non-{@code null} failure causes to consumers on a best-effort
     * basis. This call also leads to the release of all resources associated with the partition.
     * Closing of the partition is still needed afterwards if it has not been done before.
     * 此方法会尽最大努力将非 {@code null} 失败原因传播给消费者。
     * 此调用还会导致与分区关联的所有资源的释放。 如果之前没有关闭分区，那么之后仍然需要关闭分区。
     *
     * @param throwable failure cause
     */
    void fail(@Nullable Throwable throwable);

    /**
     * Successfully finish the production of the partition.
     * 成功完成隔板的制作。
     *
     * <p>Closing of partition is still needed afterwards.
     * 之后仍然需要关闭分区。
     */
    void finish() throws IOException;

    boolean isFinished();

    /**
     * Releases the partition writer which releases the produced data and no reader can consume the
     * partition any more.
     * 释放分区写入器，该写入器释放生成的数据，并且没有读取器可以再使用该分区。
     */
    void release(Throwable cause);

    boolean isReleased();

    /**
     * Closes the partition writer which releases the allocated resource, for example the buffer
     * pool.
     * 关闭释放分配资源的分区写入器，例如缓冲池。
     */
    void close() throws Exception;
}
