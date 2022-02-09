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

package org.apache.flink.core.io;

import org.apache.flink.annotation.PublicEvolving;

/**
 * This interface is implemented by classes that provide a version number. Versions numbers can be
 * used to differentiate between evolving classes.
 * 该接口由提供版本号的类实现。 版本号可用于区分不断发展的类。
 */
@PublicEvolving
public interface Versioned {

    /**
     * Returns the version number of the object. Versions numbers can be used to differentiate
     * evolving classes.
     * 返回对象的版本号。 版本号可用于区分不断发展的类。
     */
    int getVersion();
}
