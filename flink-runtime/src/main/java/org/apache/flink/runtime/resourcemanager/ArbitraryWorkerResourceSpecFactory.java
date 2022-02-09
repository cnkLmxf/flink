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

package org.apache.flink.runtime.resourcemanager;

import org.apache.flink.configuration.Configuration;

/**
 * Implementation of {@link WorkerResourceSpecFactory} that creates arbitrary {@link
 * WorkerResourceSpec}. Used for scenarios where the values in the default {@link
 * WorkerResourceSpec} does not matter.
 * 创建任意 {@link WorkerResourceSpec} 的 {@link WorkerResourceSpecFactory} 的实现。
 * 用于默认 {@link WorkerResourceSpec} 中的值无关紧要的场景。
 */
public class ArbitraryWorkerResourceSpecFactory extends WorkerResourceSpecFactory {

    public static final ArbitraryWorkerResourceSpecFactory INSTANCE =
            new ArbitraryWorkerResourceSpecFactory();

    private ArbitraryWorkerResourceSpecFactory() {}

    @Override
    public WorkerResourceSpec createDefaultWorkerResourceSpec(Configuration configuration) {
        return WorkerResourceSpec.ZERO;
    }
}
