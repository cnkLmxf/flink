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

package org.apache.flink.api.common.operators.util;

import org.apache.flink.annotation.Internal;

import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * UDf operators can have either a class or an object containing the user code, this is the common
 * interface to access them.
 * UDf 操作符可以有一个包含用户代码的类或对象，这是访问它们的通用接口。
 */
@Internal
public interface UserCodeWrapper<T> extends Serializable {

    /**
     * Gets the user code object, which may be either a function or an input or output format. The
     * subclass is supposed to just return the user code object or instantiate the class.
     * 获取用户代码对象，它可以是函数，也可以是输入或输出格式。 子类应该只返回用户代码对象或实例化该类。
     *
     * @return The class with the user code.
     */
    T getUserCodeObject(Class<? super T> superClass, ClassLoader cl);

    /**
     * Gets the user code object. In the case of a pact, that object will be the stub with the user
     * function, in the case of an input or output format, it will be the format object.
     * 获取用户代码对象。 在协议的情况下，该对象将是具有用户功能的存根，在输入或输出格式的情况下，它将是格式对象。
     *
     * @return The class with the user code.
     */
    T getUserCodeObject();

    /**
     * Gets an annotation that pertains to the user code class. By default, this method will look
     * for annotations statically present on the user code class. However, inheritors may override
     * this behavior to provide annotations dynamically.
     * 获取与用户代码类有关的注释。 默认情况下，此方法将查找静态存在于用户代码类上的注释。
     * 但是，继承者可能会覆盖此行为以动态提供注释。
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @return the annotation, or null if no annotation of the requested type was found
     */
    <A extends Annotation> A getUserCodeAnnotation(Class<A> annotationClass);

    /**
     * Gets the class of the user code. If the user code is provided as a class, this class is just
     * returned. If the user code is provided as an object, {@link Object#getClass()} is called on
     * the user code object.
     * 获取用户代码的类。 如果用户代码作为类提供，则仅返回该类。
     * 如果用户代码作为对象提供，则在用户代码对象上调用 {@link Object#getClass()}。
     *
     * @return The class of the user code object.
     */
    Class<? extends T> getUserCodeClass();

    /**
     * Checks whether the wrapper already has an object, or whether it needs to instantiate it.
     * 检查包装器是否已经有一个对象，或者是否需要实例化它。
     *
     * @return True, if the wrapper has already an object, false if it has only a class.
     */
    boolean hasObject();
}
