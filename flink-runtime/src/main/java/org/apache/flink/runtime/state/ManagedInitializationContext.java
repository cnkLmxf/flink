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

import org.apache.flink.api.common.state.KeyedStateStore;
import org.apache.flink.api.common.state.OperatorStateStore;

/**
 * This interface provides a context in which operators can initialize by registering to managed
 * state (i.e. state that is managed by state backends).
 * 该接口提供了一个上下文，在该上下文中，操作员可以通过注册到托管状态（即由状态后端管理的状态）进行初始化。
 *
 * <p>Operator state is available to all operators, while keyed state is only available for
 * operators after keyBy.
 * 算子状态对所有算子都可用，keyed状态只对keyBy之后的算子可用。
 *
 * <p>For the purpose of initialization, the context signals if the state is empty (new operator) or
 * was restored from a previous execution of this operator.
 * 出于初始化的目的，上下文指示状态是否为空（新运算符）或从该运算符的先前执行中恢复。
 */
public interface ManagedInitializationContext {

    /**
     * Returns true, if state was restored from the snapshot of a previous execution. This returns
     * always false for stateless tasks.
     * 如果状态是从上一次执行的快照中恢复的，则返回 true。 对于无状态任务，这始终返回 false。
     */
    boolean isRestored();

    /** Returns an interface that allows for registering operator state with the backend.
     * 返回一个允许向后端注册操作员状态的接口。
     * */
    OperatorStateStore getOperatorStateStore();

    /** Returns an interface that allows for registering keyed state with the backend.
     * 返回一个允许向后端注册键控状态的接口。
     * */
    KeyedStateStore getKeyedStateStore();
}
