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

package org.apache.flink.core.fs.local;

import org.apache.flink.annotation.Internal;
import org.apache.flink.core.fs.BlockLocation;
import org.apache.flink.util.StringUtils;

/**
 * Implementation of the {@link BlockLocation} interface for a local file system.
 * 本地文件系统的 {@link BlockLocation} 接口的实现。
 *
 * <p>Local files have only one block that represents the entire file. The block has no location
 * information, because it is not accessible where the files (or their block) actually reside,
 * especially in cases where the files are on a mounted file system.
 * 本地文件只有一个代表整个文件的块。
 * 该块没有位置信息，因为无法访问文件（或它们的块）实际驻留的位置，尤其是在文件位于已挂载的文件系统上的情况下。
 */
@Internal
public class LocalBlockLocation implements BlockLocation {

    private final long length;

    public LocalBlockLocation(final long length) {
        this.length = length;
    }

    @Override
    public String[] getHosts() {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public long getLength() {
        return this.length;
    }

    @Override
    public long getOffset() {
        return 0;
    }

    @Override
    public int compareTo(final BlockLocation o) {
        return 0;
    }
}
