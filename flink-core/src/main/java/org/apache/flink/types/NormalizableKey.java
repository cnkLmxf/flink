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

package org.apache.flink.types;

import org.apache.flink.annotation.Public;
import org.apache.flink.core.memory.MemorySegment;

/**
 * The base interface for normalizable keys. Normalizable keys can create a binary representation of
 * themselves that is byte-wise comparable. The byte-wise comparison of two normalized keys proceeds
 * until all bytes are compared or two bytes at the corresponding positions are not equal. If two
 * corresponding byte values are not equal, the lower byte value indicates the lower key. If both
 * normalized keys are byte-wise identical, the actual key may have to be looked at to determine
 * which one is actually lower.
 * 可规范化键的基本接口。 可规范化的键可以创建自己的二进制表示，可以按字节进行比较。
 * 两个规范化键的逐字节比较继续进行，直到比较所有字节或对应位置的两个字节不相等。
 * 如果两个对应的字节值不相等，则较低的字节值表示较低的键。
 * 如果两个规范化的键在字节上是相同的，则可能必须查看实际的键以确定哪个实际上更低。
 *
 * <p>The latter depends on whether the normalized key covers the entire key or is just a prefix of
 * the key. A normalized key is considered a prefix, if its length is less than the maximal
 * normalized key length.
 * 后者取决于规范化的键是覆盖整个键还是只是键的前缀。 如果规范化密钥的长度小于最大规范化密钥长度，则规范化密钥被视为前缀。
 */
@Public
public interface NormalizableKey<T> extends Comparable<T>, Key<T> {

    /**
     * Gets the maximal length of normalized keys that the data type would produce to determine the
     * order of instances solely by the normalized key. A value of {@link
     * java.lang.Integer}.MAX_VALUE is interpreted as infinite.
     * 获取数据类型将生成的规范化键的最大长度，以仅通过规范化键来确定实例的顺序。
     * {@link java.lang.Integer}.MAX_VALUE 的值被解释为无限。
     *
     * <p>For example, 32 bit integers return four, while Strings (potentially unlimited in length)
     * return {@link java.lang.Integer}.MAX_VALUE.
     * 例如，32 位整数返回 4，而字符串（可能无限长）返回 {@link java.lang.Integer}.MAX_VALUE。
     *
     * @return The maximal length of normalized keys.
     */
    int getMaxNormalizedKeyLen();

    /**
     * Writes a normalized key for the given record into the target byte array, starting at the
     * specified position an writing exactly the given number of bytes. Note that the comparison of
     * the bytes is treating the bytes as unsigned bytes: {@code int byteI = bytes[i] & 0xFF;}
     * 将给定记录的规范化键写入目标字节数组，从指定位置开始精确写入给定字节数。
     * 请注意，字节的比较将字节视为无符号字节： {@code int byteI = bytes[i] & 0xFF;}
     *
     * <p>If the meaningful part of the normalized key takes less than the given number of bytes,
     * then it must be padded. Padding is typically required for variable length data types, such as
     * strings. The padding uses a special character, either {@code 0} or {@code 0xff}, depending on
     * whether shorter values are sorted to the beginning or the end.
     * 如果规范化密钥的有意义部分占用的字节数少于给定的字节数，则必须对其进行填充。
     * 可变长度数据类型（例如字符串）通常需要填充。 填充使用特殊字符，{@code 0} 或 {@code 0xff}，
     * 具体取决于较短的值是排在开头还是结尾。
     *
     * @param memory The memory segment to put the normalized key bytes into.
     * @param offset The offset in the byte array where the normalized key's bytes should start.
     * @param len The number of bytes to put.
     */
    void copyNormalizedKey(MemorySegment memory, int offset, int len);
}
