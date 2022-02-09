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

package org.apache.flink.runtime.types;

import com.twitter.chill.IKryoRegistrar;
import com.twitter.chill.java.ArraysAsListSerializer;
import com.twitter.chill.java.BitSetSerializer;
import com.twitter.chill.java.InetSocketAddressSerializer;
import com.twitter.chill.java.IterableRegistrar;
import com.twitter.chill.java.LocaleSerializer;
import com.twitter.chill.java.RegexSerializer;
import com.twitter.chill.java.SimpleDateFormatSerializer;
import com.twitter.chill.java.SqlDateSerializer;
import com.twitter.chill.java.SqlTimeSerializer;
import com.twitter.chill.java.TimestampSerializer;
import com.twitter.chill.java.URISerializer;
import com.twitter.chill.java.UUIDSerializer;

/*
This code is copied as is from Twitter Chill 0.7.4 because we need to user a newer chill version
but want to ensure that the serializers that are registered by default stay the same.
这段代码是从 Twitter Chill 0.7.4 复制而来的，因为我们需要使用更新的 chill 版本
但要确保默认注册的序列化程序保持不变。

The only changes to the code are those that are required to make it compile and pass checkstyle
checks in our code base.
对代码的唯一更改是使其在我们的代码库中编译和通过检查样式检查所需的更改。
 */

/** Creates a registrar for all the serializers in the chill.java package.
 * 为 chill.java 包中的所有序列化程序创建一个注册器。
 * */
public class FlinkChillPackageRegistrar {

    public static IKryoRegistrar all() {
        return new IterableRegistrar(
                ArraysAsListSerializer.registrar(),
                BitSetSerializer.registrar(),
                PriorityQueueSerializer.registrar(),
                RegexSerializer.registrar(),
                SqlDateSerializer.registrar(),
                SqlTimeSerializer.registrar(),
                TimestampSerializer.registrar(),
                URISerializer.registrar(),
                InetSocketAddressSerializer.registrar(),
                UUIDSerializer.registrar(),
                LocaleSerializer.registrar(),
                SimpleDateFormatSerializer.registrar());
    }
}
