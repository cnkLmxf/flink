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

package org.apache.flink.api.common.functions;

import org.apache.flink.annotation.Public;

/**
 * The base interface for all user-defined functions.
 * 所有用户定义函数的基本接口。
 *
 * <p>This interface is empty in order to allow extending interfaces to be SAM (single abstract
 * method) interfaces that can be implemented via Java 8 lambdas.
 * 该接口是空的，以便允许将接口扩展为可以通过 Java 8 lambdas 实现的 SAM（单一抽象方法）接口。
 */
@Public
public interface Function extends java.io.Serializable {}
