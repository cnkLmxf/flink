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
 * This interface provides a context in which operators can initialize by registering to managed
 * state (i.e. state that is managed by state backends) or iterating over streams of state
 * partitions written as raw state in a previous snapshot.
 * 该接口提供了一个上下文，在该上下文中，操作员可以通过注册到托管状态（即由状态后端管理的状态）
 * 或迭代在先前快照中作为原始状态写入的状态分区流来初始化。
 *
 * <p>Similar to the managed state from {@link ManagedInitializationContext} and in general, raw
 * operator state is available to all operators, while raw keyed state is only available for
 * operators after keyBy.
 * 类似于 {@link ManagedInitializationContext} 中的托管状态，
 * 一般来说，原始操作符状态可用于所有操作符，而原始键控状态仅适用于 keyBy 之后的操作符。
 *
 * <p>For the purpose of initialization, the context signals if all state is empty (new operator) or
 * if any state was restored from a previous execution of this operator.
 * 出于初始化的目的，上下文指示所有状态是否为空（新运算符）或是否从该运算符的先前执行中恢复了任何状态。
 */
@PublicEvolving
public interface StateInitializationContext extends FunctionInitializationContext {

    /**
     * Returns an iterable to obtain input streams for previously stored operator state partitions
     * that are assigned to this operator.
     * 返回一个可迭代对象，以获取分配给此运算符的先前存储的运算符状态分区的输入流。
     */
    Iterable<StatePartitionStreamProvider> getRawOperatorStateInputs();

    /**
     * Returns an iterable to obtain input streams for previously stored keyed state partitions that
     * are assigned to this operator.
     * 返回一个可迭代对象，以获取分配给此运算符的先前存储的键控状态分区的输入流。
     */
    Iterable<KeyGroupStatePartitionStreamProvider> getRawKeyedStateInputs();
}
