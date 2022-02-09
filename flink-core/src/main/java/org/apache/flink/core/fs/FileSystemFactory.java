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

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.plugin.Plugin;

import java.io.IOException;
import java.net.URI;

/**
 * A factory to create file systems.

 创建文件系统的工厂。 *
 * <p>The factory is typically configured via {@link #configure(Configuration)} before creating file
 * systems via {@link #create(URI)}.
 * 在通过 {@link #create(URI)} 创建文件系统之前，工厂通常通过 {@link #configure(Configuration)} 进行配置。
 */
@PublicEvolving
public interface FileSystemFactory extends Plugin {

    /** Gets the scheme of the file system created by this factory.
     * 获取此工厂创建的文件系统的方案。
     * */
    String getScheme();

    /**
     * Creates a new file system for the given file system URI. The URI describes the type of file
     * system (via its scheme) and optionally the authority (for example the host) of the file
     * system.
     * 为给定的文件系统 URI 创建一个新的文件系统。 URI 描述文件系统的类型（通过其方案）和文件系统的可选权限（例如主机）。
     *
     * @param fsUri The URI that describes the file system.
     * @return A new instance of the specified file system.
     * @throws IOException Thrown if the file system could not be instantiated.
     */
    FileSystem create(URI fsUri) throws IOException;
}
