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

package org.apache.flink.runtime.resourcemanager;

import org.apache.flink.util.AbstractID;

import javax.annotation.Nullable;

import java.util.UUID;

/** Fencing token for the {@link ResourceManager}.
 * {@link ResourceManager} 的隔离令牌。
 * */
public class ResourceManagerId extends AbstractID {

    private static final long serialVersionUID = -6042820142662137374L;

    /** Generates a new random ResourceManagerId.
     * 生成一个新的随机 ResourceManagerId。
     * */
    private ResourceManagerId() {}

    /** Creates a ResourceManagerId that takes the bits from the given UUID.
     * 创建一个从给定 UUID 中获取位的 ResourceManagerId。
     * */
    private ResourceManagerId(UUID uuid) {
        super(uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
    }

    /** Creates a UUID with the bits from this ResourceManagerId.
     * 使用来自此 ResourceManagerId 的位创建一个 UUID。
     * */
    public UUID toUUID() {
        return new UUID(getUpperPart(), getLowerPart());
    }

    /** Generates a new random ResourceManagerId.
     * 生成一个新的随机 ResourceManagerId。
     * */
    public static ResourceManagerId generate() {
        return new ResourceManagerId();
    }

    /** Creates a ResourceManagerId that corresponds to the given UUID.
     * 创建与给定 UUID 对应的 ResourceManagerId。
     * */
    public static ResourceManagerId fromUuid(UUID uuid) {
        return new ResourceManagerId(uuid);
    }

    /**
     * If the given uuid is null, this returns null, otherwise a ResourceManagerId that corresponds
     * to the UUID, via {@link #ResourceManagerId(UUID)}.
     * 如果给定的 uuid 为 null，则返回 null，否则返回与 UUID 对应的 ResourceManagerId，
     * 通过 {@link #ResourceManagerId(UUID)}。
     */
    public static ResourceManagerId fromUuidOrNull(@Nullable UUID uuid) {
        return uuid == null ? null : new ResourceManagerId(uuid);
    }
}
