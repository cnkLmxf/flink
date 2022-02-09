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

import org.apache.flink.annotation.Internal;
import org.apache.flink.configuration.IllegalConfigurationException;
import org.apache.flink.configuration.ReadableConfig;

/** An interface for state backends that pick up additional parameters from a configuration.
 * 从配置中获取额外参数的状态后端接口。
 * */
@Internal
public interface ConfigurableStateBackend {

    /**
     * Creates a variant of the state backend that applies additional configuration parameters.
     * 创建应用其他配置参数的状态后端的变体。
     *
     * <p>Settings that were directly done on the original state backend object in the application
     * program typically have precedence over setting picked up from the configuration.
     * 直接在应用程序中的原始状态后端对象上完成的设置通常优先于从配置中提取的设置。
     *
     * <p>If no configuration is applied, or if the method directly applies configuration values to
     * the (mutable) state backend object, this method may return the original state backend object.
     * Otherwise it typically returns a modified copy.
     * 如果没有应用配置，或者如果该方法直接将配置值应用于（可变）状态后端对象，则此方法可能返回原始状态后端对象。
     * 否则，它通常会返回修改后的副本。
     *
     * @param config The configuration to pick the values from.
     * @param classLoader The class loader that should be used to load the state backend.
     * @return A reconfigured state backend.
     * @throws IllegalConfigurationException Thrown if the configuration contained invalid entries.
     */
    StateBackend configure(ReadableConfig config, ClassLoader classLoader)
            throws IllegalConfigurationException;
}
