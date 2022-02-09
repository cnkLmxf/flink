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

package org.apache.flink.runtime.checkpoint.metadata;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper to access {@link MetadataSerializer}s for specific format versions.
 * 帮助程序访问特定格式版本的 {@link MetadataSerializer}。
 *
 * <p>The serializer for a specific version can be obtained via {@link #getSerializer(int)}.
 * 可以通过 {@link #getSerializer(int)} 获取特定版本的序列化程序。
 */
public class MetadataSerializers {

    private static final Map<Integer, MetadataSerializer> SERIALIZERS = new HashMap<>(3);

    static {
        registerSerializer(MetadataV1Serializer.INSTANCE);
        registerSerializer(MetadataV2Serializer.INSTANCE);
        registerSerializer(MetadataV3Serializer.INSTANCE);
    }

    private static void registerSerializer(MetadataSerializer serializer) {
        SERIALIZERS.put(serializer.getVersion(), serializer);
    }

    /**
     * Returns the {@link MetadataSerializer} for the given savepoint version.
     * 返回给定保存点版本的 {@link MetadataSerializer}。
     *
     * @param version Savepoint version to get serializer for
     * @return Savepoint for the given version
     * @throws IllegalArgumentException If unknown savepoint version
     */
    public static MetadataSerializer getSerializer(int version) {
        MetadataSerializer serializer = SERIALIZERS.get(version);
        if (serializer != null) {
            return serializer;
        } else {
            throw new IllegalArgumentException(
                    "Unrecognized checkpoint version number: " + version);
        }
    }

    // ------------------------------------------------------------------------

    /** Utility method class, not meant to be instantiated.
     * 实用方法类，不打算实例化。
     * */
    private MetadataSerializers() {}
}
