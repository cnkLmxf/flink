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

package org.apache.flink.runtime.jobmanager.scheduler;

/**
 * Defines the location preference constraint.
 * 定义位置偏好约束。
 *
 * <p>Currently, we support that all input locations have to be taken into consideration and only
 * those which are known at scheduling time. Note that if all input locations are considered, then
 * the scheduling operation can potentially take a while until all inputs have locations assigned.
 * 目前，我们支持必须考虑所有输入位置，并且只考虑那些在调度时已知的位置。
 * 请注意，如果考虑了所有输入位置，那么调度操作可能需要一段时间，直到所有输入都分配了位置。
 */
public enum LocationPreferenceConstraint {
    // 等待所有输入分配一个位置
    ALL, // wait for all inputs to have a location assigned
    // 只考虑那些已经分配了位置的输入
    ANY // only consider those inputs who already have a location assigned
}
