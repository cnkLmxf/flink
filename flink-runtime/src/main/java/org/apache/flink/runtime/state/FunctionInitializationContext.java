/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.state;

import org.apache.flink.annotation.PublicEvolving;

/**
 * This interface provides a context in which user functions can initialize by registering to
 * managed state (i.e. state that is managed by state backends).
 * 该接口提供了一个上下文，在该上下文中，用户函数可以通过注册到托管状态（即由状态后端管理的状态）进行初始化。
 *
 * <p>Operator state is available to all functions, while keyed state is only available for
 * functions after keyBy.
 * 操作符状态对所有函数有效，keyed 状态只对keyBy 之后的函数有效。
 *
 * <p>For the purpose of initialization, the context signals if the state is empty or was restored
 * from a previous execution.
 * 出于初始化的目的，上下文指示状态是否为空或从先前的执行中恢复。
 */
@PublicEvolving
public interface FunctionInitializationContext extends ManagedInitializationContext {}
