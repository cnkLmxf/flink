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

import org.apache.flink.annotation.Public;
import org.apache.flink.api.common.io.statistics.BaseStatistics;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.InputSplit;
import org.apache.flink.core.io.InputSplitAssigner;
import org.apache.flink.core.io.InputSplitSource;

import java.io.IOException;
import java.io.Serializable;

/**
 * The base interface for data sources that produces records.
 * 生成记录的数据源的基本接口。
 *
 * <p>The input format handles the following:
 * 输入格式处理以下内容：
 *<ul>
 *     <li>它描述了如何将输入拆分为可以并行处理的拆分。
 *     <li>它描述了如何从输入拆分中读取记录。
 *     <li>它描述了如何从输入中收集基本统计信息。
 * </ul>
 * <ul>
 *   <li>It describes how the input is split into splits that can be processed in parallel.
 *   <li>It describes how to read records from the input split.
 *   <li>It describes how to gather basic statistics from the input.
 * </ul>
 *
 * <p>The life cycle of an input format is the following:
 * 输入格式的生命周期如下：
 *<ol>
 *     <li>实例化后（无参数），配置一个{@link Configuration}对象。 如果格式将文件描述为输入，则从配置中读取基本字段，例如文件路径。
 *     <li>可选：编译器调用它以生成有关输入的基本统计信息。
 *     <li>调用它来创建输入拆分。
 *     <li>每个并行输入任务都会创建一个实例，对其进行配置并为特定拆分打开它。
 *     <li>从输入中读取所有记录
 *     <li>输入格式关闭
 * </ol>
 * <ol>
 *   <li>After being instantiated (parameterless), it is configured with a {@link Configuration}
 *       object. Basic fields are read from the configuration, such as a file path, if the format
 *       describes files as input.
 *   <li>Optionally: It is called by the compiler to produce basic statistics about the input.
 *   <li>It is called to create the input splits.
 *   <li>Each parallel input task creates an instance, configures it and opens it for a specific
 *       split.
 *   <li>All records are read from the input
 *   <li>The input format is closed
 * </ol>
 *
 * <p>IMPORTANT NOTE: Input formats must be written such that an instance can be opened again after
 * it was closed. That is due to the fact that the input format is used for potentially multiple
 * splits. After a split is done, the format's close function is invoked and, if another split is
 * available, the open function is invoked afterwards for the next split.
 * 重要提示：必须编写输入格式，以便实例在关闭后可以再次打开。
 * 这是因为输入格式可能用于多个拆分。 拆分完成后，将调用格式的关闭函数，如果另一个拆分可用，则随后为下一次拆分调用打开函数。
 *
 * @see InputSplit
 * @see BaseStatistics
 * @param <OT> The type of the produced records.
 * @param <T> The type of input split.
 */
@Public
public interface InputFormat<OT, T extends InputSplit> extends InputSplitSource<T>, Serializable {

    /**
     * Configures this input format. Since input formats are instantiated generically and hence
     * parameterless, this method is the place where the input formats set their basic fields based
     * on configuration values.
     * 配置此输入格式。 由于输入格式是通用实例化的，因此是无参数的，因此此方法是输入格式根据配置值设置其基本字段的地方。
     *
     * <p>This method is always called first on a newly instantiated input format.
     * 此方法总是在新实例化的输入格式上首先调用。
     *
     * @param parameters The configuration with all parameters (note: not the Flink config but the
     *     TaskConfig).
     */
    void configure(Configuration parameters);

    /**
     * Gets the basic statistics from the input described by this format. If the input format does
     * not know how to create those statistics, it may return null. This method optionally gets a
     * cached version of the statistics. The input format may examine them and decide whether it
     * directly returns them without spending effort to re-gather the statistics.
     * 从此格式描述的输入中获取基本统计信息。 如果输入格式不知道如何创建这些统计信息，它可能会返回 null。
     * 此方法可选择获取统计信息的缓存版本。 输入格式可以检查它们并决定是否直接返回它们，而无需花费精力重新收集统计信息。
     *
     * <p>When this method is called, the input format is guaranteed to be configured.
     * 调用该方法时，保证配置了输入格式。
     *
     * @param cachedStatistics The statistics that were cached. May be null.
     * @return The base statistics for the input, or null, if not available.
     */
    BaseStatistics getStatistics(BaseStatistics cachedStatistics) throws IOException;

    // --------------------------------------------------------------------------------------------

    @Override
    T[] createInputSplits(int minNumSplits) throws IOException;

    @Override
    InputSplitAssigner getInputSplitAssigner(T[] inputSplits);

    // --------------------------------------------------------------------------------------------

    /**
     * Opens a parallel instance of the input format to work on a split.
     * 打开输入格式的并行实例以处理拆分。
     *
     * <p>When this method is called, the input format it guaranteed to be configured.
     * 调用此方法时，它保证配置的输入格式。
     *
     * @param split The split to be opened.
     * @throws IOException Thrown, if the spit could not be opened due to an I/O problem.
     */
    void open(T split) throws IOException;

    /**
     * Method used to check if the end of the input is reached.
     * 用于检查输入是否到达末尾的方法。
     *
     * <p>When this method is called, the input format it guaranteed to be opened.
     * 当调用此方法时，它保证打开的输入格式。
     *
     * @return True if the end is reached, otherwise false.
     * @throws IOException Thrown, if an I/O error occurred.
     */
    boolean reachedEnd() throws IOException;

    /**
     * Reads the next record from the input.
     * 从输入中读取下一条记录。
     *
     * <p>When this method is called, the input format it guaranteed to be opened.
     * 当调用此方法时，它保证打开的输入格式。
     *
     * @param reuse Object that may be reused.
     * @return Read record.
     * @throws IOException Thrown, if an I/O error occurred.
     */
    OT nextRecord(OT reuse) throws IOException;

    /**
     * Method that marks the end of the life-cycle of an input split. Should be used to close
     * channels and streams and release resources. After this method returns without an error, the
     * input is assumed to be correctly read.
     * 标记输入拆分生命周期结束的方法。 应该用于关闭通道和流并释放资源。 在此方法无错误返回后，假定已正确读取输入。
     *
     * <p>When this method is called, the input format it guaranteed to be opened.
     * 当调用此方法时，它保证打开的输入格式。
     *
     * @throws IOException Thrown, if the input could not be closed properly.
     */
    void close() throws IOException;
}
