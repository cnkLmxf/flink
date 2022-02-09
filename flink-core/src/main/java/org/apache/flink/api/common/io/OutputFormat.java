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
import org.apache.flink.configuration.Configuration;

import java.io.IOException;
import java.io.Serializable;

/**
 * The base interface for outputs that consumes records. The output format describes how to store
 * the final records, for example in a file.
 * 使用记录的输出的基本接口。 输出格式描述了如何存储最终记录，例如在文件中。
 *
 * <p>The life cycle of an output format is the following:
 * 输出格式的生命周期如下：
 *<ol>
 *     <li>configure() 被调用一次。 该方法可用于从实例化时附加的参数（配置）实现初始化。
 *     <li>每个并行输出任务都会创建一个实例，对其进行配置并打开它。
 *     <li>其并行实例的所有记录都交给输出格式。
 *     <li>输出格式关闭
 *</ol>
 * <ol>
 *   <li>configure() is invoked a single time. The method can be used to implement initialization
 *       from the parameters (configuration) that may be attached upon instantiation.
 *   <li>Each parallel output task creates an instance, configures it and opens it.
 *   <li>All records of its parallel instance are handed to the output format.
 *   <li>The output format is closed
 * </ol>
 *
 * @param <IT> The type of the consumed records.
 */
@Public
public interface OutputFormat<IT> extends Serializable {

    /**
     * Configures this output format. Since output formats are instantiated generically and hence
     * parameterless, this method is the place where the output formats set their basic fields based
     * on configuration values.
     * 配置此输出格式。 由于输出格式是通用实例化的，因此是无参数的，因此此方法是输出格式根据配置值设置其基本字段的地方。
     *
     * <p>This method is always called first on a newly instantiated output format.
     * 在新实例化的输出格式上总是首先调用此方法。
     *
     * @param parameters The configuration with all parameters.
     */
    void configure(Configuration parameters);

    /**
     * Opens a parallel instance of the output format to store the result of its parallel instance.
     * 打开输出格式的并行实例以存储其并行实例的结果。
     *
     * <p>When this method is called, the output format it guaranteed to be configured.
     * 调用此方法时，它保证配置的输出格式。
     *
     * @param taskNumber The number of the parallel instance.
     * @param numTasks The number of parallel tasks.
     * @throws IOException Thrown, if the output could not be opened due to an I/O problem.
     */
    void open(int taskNumber, int numTasks) throws IOException;

    /**
     * Adds a record to the output.
     * 在输出中添加一条记录。
     *
     * <p>When this method is called, the output format it guaranteed to be opened.
     * 调用此方法时，它保证打开的输出格式。
     *
     * @param record The records to add to the output.
     * @throws IOException Thrown, if the records could not be added to to an I/O problem.
     */
    void writeRecord(IT record) throws IOException;

    /**
     * Method that marks the end of the life-cycle of parallel output instance. Should be used to
     * close channels and streams and release resources. After this method returns without an error,
     * the output is assumed to be correct.
     * 标记并行输出实例生命周期结束的方法。 应该用于关闭通道和流并释放资源。 在此方法无错误返回后，假定输出正确。
     *
     * <p>When this method is called, the output format it guaranteed to be opened.
     * 调用此方法时，它保证打开的输出格式。
     *
     * @throws IOException Thrown, if the input could not be closed properly.
     */
    void close() throws IOException;
}
