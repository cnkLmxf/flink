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

package org.apache.flink.core.fs;

import org.apache.flink.annotation.Public;

/**
 * A {@code LocatedFileStatus} is a {@link FileStatus} that contains additionally the location
 * information of the file directly. The information is accessible through the {@link
 * #getBlockLocations()} ()} method.
 * {@code LocationFileStatus} 是一个 {@link FileStatus}，它另外直接包含文件的位置信息。
 * 该信息可通过 {@link #getBlockLocations()} ()} 方法访问。
 *
 * <p>This class eagerly communicates the block information (including locations) when that
 * information is readily (or cheaply) available. That way users can avoid an additional call to
 * {@link FileSystem#getFileBlockLocations(FileStatus, long, long)}, which is an additional RPC call
 * for each file.
 * 当该信息容易（或便宜）可用时，该类会急切地传达块信息（包括位置）。
 * 这样用户就可以避免额外调用 {@link FileSystem#getFileBlockLocations(FileStatus, long, long)}，
 * 这是对每个文件的额外 RPC 调用。
 */
@Public
public interface LocatedFileStatus extends FileStatus {

    /**
     * Gets the location information for the file. The location is per block, because each block may
     * live potentially at a different location.
     * 获取文件的位置信息。 位置是每个块，因为每个块可能住在不同的位置。
     *
     * <p>Files without location information typically expose one block with no host information for
     * that block.
     * 没有位置信息的文件通常会暴露一个块，而该块没有主机信息。
     */
    BlockLocation[] getBlockLocations();
}
