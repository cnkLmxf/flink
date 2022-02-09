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

package org.apache.flink.util;

/**
 * Interface for classes that can be disposed, i.e. that have a dedicated lifecycle step to
 * "destroy" the object. On reason for this is for example to release native resources. From this
 * point, the interface fulfills a similar purpose as the {@link java.io.Closeable} interface, but
 * sometimes both should be represented as isolated, independent lifecycle steps.
 * 可以处理的类的接口，即具有专门的生命周期步骤来“销毁”对象。 这样做的原因是例如释放本机资源。
 * 从这一点来看，该接口实现了与 {@link java.io.Closeable} 接口类似的目的，但有时两者都应表示为孤立的、独立的生命周期步骤。
 */
public interface Disposable {

    /**
     * Disposes the object and releases all resources. After calling this method, calling any
     * methods on the object may result in undefined behavior.
     * 处置对象并释放所有资源。 调用此方法后，调用对象上的任何方法都可能导致未定义的行为。
     *
     * @throws Exception if something goes wrong during disposal.
     */
    void dispose() throws Exception;
}
