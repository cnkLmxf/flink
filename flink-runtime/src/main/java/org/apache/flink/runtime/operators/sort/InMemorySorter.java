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

package org.apache.flink.runtime.operators.sort;

import org.apache.flink.runtime.io.disk.iomanager.ChannelWriterOutputView;
import org.apache.flink.util.Disposable;
import org.apache.flink.util.MutableObjectIterator;

import java.io.IOException;

/** */
public interface InMemorySorter<T> extends IndexedSortable, Disposable {

    /**
     * Resets the sort buffer back to the state where it is empty. All contained data is discarded.
     * 将排序缓冲区重置为空状态。 所有包含的数据都将被丢弃。
     */
    void reset();

    /**
     * Checks whether the buffer is empty.
     * 检查缓冲区是否为空。
     *
     * @return True, if no record is contained, false otherwise.
     */
    boolean isEmpty();

    /** Disposes the sorter. This method does not release the memory segments used by the sorter.
     * 处理分拣机。 此方法不会释放排序器使用的内存段。
     * */
    @Override
    void dispose();

    /**
     * Gets the total capacity of this sorter, in bytes.
     * 获取此排序器的总容量，以字节为单位。
     *
     * @return The sorter's total capacity.
     */
    long getCapacity();

    /**
     * Gets the number of bytes currently occupied in this sorter, records and sort index.
     * 获取当前在此排序器、记录和排序索引中占用的字节数。
     *
     * @return The number of bytes occupied.
     */
    long getOccupancy();

    /**
     * Gets the record at the given logical position.
     * 获取给定逻辑位置的记录。
     *
     * @param logicalPosition The logical position of the record.
     * @throws IOException Thrown, if an exception occurred during deserialization.
     */
    T getRecord(int logicalPosition) throws IOException;

    /**
     * Gets the record at the given logical position.
     * 获取给定逻辑位置的记录。
     *
     * @param reuse The reuse object to deserialize the record into.
     * @param logicalPosition The logical position of the record.
     * @throws IOException Thrown, if an exception occurred during deserialization.
     */
    T getRecord(T reuse, int logicalPosition) throws IOException;

    /**
     * Writes a given record to this sort buffer. The written record will be appended and take the
     * last logical position.
     * 将给定记录写入此排序缓冲区。 写入的记录将被附加并占据最后一个逻辑位置。
     *
     * @param record The record to be written.
     * @return True, if the record was successfully written, false, if the sort buffer was full.
     * @throws IOException Thrown, if an error occurred while serializing the record into the
     *     buffers.
     */
    boolean write(T record) throws IOException;

    /**
     * Gets an iterator over all records in this buffer in their logical order.
     * 按逻辑顺序获取此缓冲区中所有记录的迭代器。
     *
     * @return An iterator returning the records in their logical order.
     */
    MutableObjectIterator<T> getIterator();

    /**
     * Writes the records in this buffer in their logical order to the given output.
     * 将此缓冲区中的记录按其逻辑顺序写入给定输出。
     *
     * @param output The output view to write the records to.
     * @throws IOException Thrown, if an I/O exception occurred writing to the output view.
     */
    public void writeToOutput(ChannelWriterOutputView output) throws IOException;

    public void writeToOutput(
            ChannelWriterOutputView output, LargeRecordHandler<T> largeRecordsOutput)
            throws IOException;

    /**
     * Writes a subset of the records in this buffer in their logical order to the given output.
     * 将此缓冲区中的记录子集按逻辑顺序写入给定输出。
     *
     * @param output The output view to write the records to.
     * @param start The logical start position of the subset.
     * @param num The number of elements to write.
     * @throws IOException Thrown, if an I/O exception occurred writing to the output view.
     */
    public void writeToOutput(ChannelWriterOutputView output, int start, int num)
            throws IOException;
}
