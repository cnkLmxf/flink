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

package org.apache.flink.runtime.entrypoint.parser;

import org.apache.flink.runtime.entrypoint.FlinkParseException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import javax.annotation.Nonnull;

/**
 * Parser result factory used by the {@link CommandLineParser}.
 * {@link CommandLineParser} 使用的解析器结果工厂。
 *
 * @param <T> type of the parsed result
 */
public interface ParserResultFactory<T> {

    /**
     * Returns all relevant {@link Options} for parsing the command line arguments.
     * 返回所有相关的 {@link Options} 用于解析命令行参数。
     *
     * @return Options to use for the parsing
     */
    Options getOptions();

    /**
     * Create the result of the command line argument parsing.
     * 创建命令行参数解析的结果。
     *
     * @param commandLine to extract the options from
     * @return Result of the parsing
     * @throws FlinkParseException Thrown on failures while parsing command line arguments
     */
    T createResult(@Nonnull CommandLine commandLine) throws FlinkParseException;
}
