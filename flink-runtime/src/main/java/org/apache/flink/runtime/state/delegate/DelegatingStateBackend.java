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

package org.apache.flink.runtime.state.delegate;

import org.apache.flink.annotation.Internal;
import org.apache.flink.runtime.state.StateBackend;

/**
 * An interface to delegate state backend.
 * 委托状态后端的接口。
 *
 * <p>As its name, it should include a state backend to delegate.
 * 正如它的名字一样，它应该包括一个状态后端来委托。
 */
@Internal
public interface DelegatingStateBackend extends StateBackend {
    StateBackend getDelegatedStateBackend();
}
