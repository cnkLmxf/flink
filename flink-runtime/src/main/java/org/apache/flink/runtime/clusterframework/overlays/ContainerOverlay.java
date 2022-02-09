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

package org.apache.flink.runtime.clusterframework.overlays;

import org.apache.flink.runtime.clusterframework.ContainerSpecification;

import java.io.IOException;

/**
 * A container overlay to produce a container specification.
 * 用于生成容器规范的容器覆盖。
 *
 * <p>An overlay applies configuration elements, environment variables, system properties, and
 * artifacts to a container specification.
 * 覆盖将配置元素、环境变量、系统属性和工件应用于容器规范。
 */
public interface ContainerOverlay {

    /** Configure the given container specification.
     * 配置给定的容器规范。
     * */
    void configure(ContainerSpecification containerSpecification) throws IOException;
}
