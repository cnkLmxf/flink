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

package org.apache.flink.core.io;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;
import java.util.Arrays;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Simple serialization / deserialization methods for the {@link SimpleVersionedSerializer}.
 * {@link SimpleVersionedSerializer} 的简单序列化/反序列化方法。
 * */
@PublicEvolving
public class SimpleVersionedSerialization {

    /**
     * Serializes the version and datum into a stream.
     * 将版本和数据序列化为流。
     *
     * <p>Data serialized via this method can be deserialized via {@link
     * #readVersionAndDeSerialize(SimpleVersionedSerializer, DataInputView)}.
     * 通过此方法序列化的数据可以通过 {@link #readVersionAndDeSerialize(SimpleVersionedSerializer, DataInputView)} 进行反序列化。
     *
     * <p>The first four bytes will be occupied by the version, as returned by {@link
     * SimpleVersionedSerializer#getVersion()}. The remaining bytes will be the serialized datum, as
     * produced by {@link SimpleVersionedSerializer#serialize(Object)}, plus its length. The
     * resulting array will hence be eight bytes larger than the serialized datum.
     * 前四个字节将被版本占用，由 {@link SimpleVersionedSerializer#getVersion()} 返回。
     * 剩余的字节将是由 {@link SimpleVersionedSerializer#serialize(Object)} 生成的序列化数据，加上它的长度。
     * 因此，结果数组将比序列化数据大 8 个字节。
     *
     * @param serializer The serializer to serialize the datum with.
     * @param datum The datum to serialize.
     * @param out The stream to serialize to.
     */
    public static <T> void writeVersionAndSerialize(
            SimpleVersionedSerializer<T> serializer, T datum, DataOutputView out)
            throws IOException {
        checkNotNull(serializer, "serializer");
        checkNotNull(datum, "datum");
        checkNotNull(out, "out");

        final byte[] data = serializer.serialize(datum);

        out.writeInt(serializer.getVersion());
        out.writeInt(data.length);
        out.write(data);
    }

    /**
     * Deserializes the version and datum from a stream.
     * 反序列化流中的版本和数据。
     *
     * <p>This method deserializes data serialized via {@link
     * #writeVersionAndSerialize(SimpleVersionedSerializer, Object, DataOutputView)}.
     * 此方法反序列化通过 {@link #writeVersionAndSerialize(SimpleVersionedSerializer, Object, DataOutputView)} 序列化的数据。
     *
     * <p>The first four bytes will be interpreted as the version. The next four bytes will be
     * interpreted as the length of the datum bytes, then length-many bytes will be read. Finally,
     * the datum is deserialized via the {@link SimpleVersionedSerializer#deserialize(int, byte[])}
     * method.
     * 前四个字节将被解释为版本。 接下来的四个字节将被解释为数据字节的长度，然后将读取 length-many 个字节。
     * 最后，通过 {@link SimpleVersionedSerializer#deserialize(int, byte[])} 方法对数据进行反序列化。
     *
     * @param serializer The serializer to serialize the datum with.
     * @param in The stream to deserialize from.
     */
    public static <T> T readVersionAndDeSerialize(
            SimpleVersionedSerializer<T> serializer, DataInputView in) throws IOException {
        checkNotNull(serializer, "serializer");
        checkNotNull(in, "in");

        final int version = in.readInt();
        final int length = in.readInt();
        final byte[] data = new byte[length];
        in.readFully(data);

        return serializer.deserialize(version, data);
    }

    /**
     * Serializes the version and datum into a byte array. The first four bytes will be occupied by
     * the version (as returned by {@link SimpleVersionedSerializer#getVersion()}), written in
     * <i>big-endian</i> encoding. The remaining bytes will be the serialized datum, as produced by
     * {@link SimpleVersionedSerializer#serialize(Object)}. The resulting array will hence be four
     * bytes larger than the serialized datum.
     * 将版本和数据序列化为字节数组。 前四个字节将被版本占用（由 {@link SimpleVersionedSerializer#getVersion()} 返回），
     * 以 <i>big-endian</i> 编码编写。 其余字节将是序列化数据，由 {@link SimpleVersionedSerializer#serialize(Object)} 生成。
     * 因此，结果数组将比序列化数据大四个字节。
     *
     * <p>Data serialized via this method can be deserialized via {@link
     * #readVersionAndDeSerialize(SimpleVersionedSerializer, byte[])}.
     * 通过此方法序列化的数据可以通过 {@link #readVersionAndDeSerialize(SimpleVersionedSerializer, byte[])} 进行反序列化。
     *
     * @param serializer The serializer to serialize the datum with.
     * @param datum The datum to serialize.
     * @return A byte array containing the serialized version and serialized datum.
     * @throws IOException Exceptions from the {@link SimpleVersionedSerializer#serialize(Object)}
     *     method are forwarded.
     */
    public static <T> byte[] writeVersionAndSerialize(
            SimpleVersionedSerializer<T> serializer, T datum) throws IOException {
        checkNotNull(serializer, "serializer");
        checkNotNull(datum, "datum");

        final byte[] data = serializer.serialize(datum);
        final byte[] versionAndData = new byte[data.length + 8];

        final int version = serializer.getVersion();
        versionAndData[0] = (byte) (version >> 24);
        versionAndData[1] = (byte) (version >> 16);
        versionAndData[2] = (byte) (version >> 8);
        versionAndData[3] = (byte) version;

        final int length = data.length;
        versionAndData[4] = (byte) (length >> 24);
        versionAndData[5] = (byte) (length >> 16);
        versionAndData[6] = (byte) (length >> 8);
        versionAndData[7] = (byte) length;

        // move the data to the array
        System.arraycopy(data, 0, versionAndData, 8, data.length);

        return versionAndData;
    }

    /**
     * Deserializes the version and datum from a byte array. The first four bytes will be read as
     * the version, in <i>big-endian</i> encoding. The remaining bytes will be passed to the
     * serializer for deserialization, via {@link SimpleVersionedSerializer#deserialize(int,
     * byte[])}.
     * 从字节数组中反序列化版本和数据。 前四个字节将作为版本读取，采用 <i>big-endian</i> 编码。
     * 剩余的字节将通过 {@link SimpleVersionedSerializer#deserialize(int, byte[])} 传递给序列化器进行反序列化。
     *
     * @param serializer The serializer to deserialize the datum with.
     * @param bytes The bytes to deserialize from.
     * @return The deserialized datum.
     * @throws IOException Exceptions from the {@link SimpleVersionedSerializer#deserialize(int,
     *     byte[])} method are forwarded.
     */
    public static <T> T readVersionAndDeSerialize(
            SimpleVersionedSerializer<T> serializer, byte[] bytes) throws IOException {
        checkNotNull(serializer, "serializer");
        checkNotNull(bytes, "bytes");
        checkArgument(bytes.length >= 8, "byte array below minimum length (8 bytes)");

        final byte[] dataOnly = Arrays.copyOfRange(bytes, 8, bytes.length);
        final int version =
                ((bytes[0] & 0xff) << 24)
                        | ((bytes[1] & 0xff) << 16)
                        | ((bytes[2] & 0xff) << 8)
                        | (bytes[3] & 0xff);

        final int length =
                ((bytes[4] & 0xff) << 24)
                        | ((bytes[5] & 0xff) << 16)
                        | ((bytes[6] & 0xff) << 8)
                        | (bytes[7] & 0xff);

        if (length == dataOnly.length) {
            return serializer.deserialize(version, dataOnly);
        } else {
            throw new IOException(
                    "Corrupt data, conflicting lengths. Length fields: "
                            + length
                            + ", data: "
                            + dataOnly.length);
        }
    }

    // ------------------------------------------------------------------------

    /** Utility class, not meant to be instantiated. */
    private SimpleVersionedSerialization() {}
}
