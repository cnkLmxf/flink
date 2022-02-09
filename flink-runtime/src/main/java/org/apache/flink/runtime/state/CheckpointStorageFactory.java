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

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.configuration.IllegalConfigurationException;
import org.apache.flink.configuration.ReadableConfig;

/**
 * A factory to create a specific {@link CheckpointStorage}. The storage creation gets a
 * configuration object that can be used to read further config values.
 * 创建特定 {@link CheckpointStorage} 的工厂。 存储创建获得一个配置对象，可用于读取进一步的配置值。
 *
 * <p>The checkpoint storage factory is typically specified in the configuration to produce a
 * configured storage backend.
 * 检查点存储工厂通常在配置中指定以生成配置的存储后端。
 *
 * @param <T> The type of the checkpoint storage created.
 */
@PublicEvolving
public interface CheckpointStorageFactory<T extends CheckpointStorage> {

    /**
     * Creates the checkpoint storage, optionally using the given configuration.
     * 创建检查点存储，可选择使用给定的配置。
     *
     * @param config The Flink configuration (loaded by the TaskManager).
     * @param classLoader The clsas loader that should be used to load the checkpoint storage.
     * @return The created checkpoint storage.
     * @throws IllegalConfigurationException If the configuration misses critical values, or
     *     specifies invalid values
     */
    T createFromConfig(ReadableConfig config, ClassLoader classLoader)
            throws IllegalConfigurationException;
}
