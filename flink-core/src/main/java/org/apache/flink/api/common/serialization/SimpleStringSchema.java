/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.serialization;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Very simple serialization schema for strings.
 * 非常简单的字符串序列化模式。
 *
 * <p>By default, the serializer uses "UTF-8" for string/byte conversion.
 * 默认情况下，序列化程序使用“UTF-8”进行字符串/字节转换。
 */
@PublicEvolving
public class SimpleStringSchema
        implements DeserializationSchema<String>, SerializationSchema<String> {

    private static final long serialVersionUID = 1L;

    /**
     * The charset to use to convert between strings and bytes. The field is transient because we
     * serialize a different delegate object instead
     * 用于在字符串和字节之间进行转换的字符集。 该字段是瞬态的，因为我们改为序列化不同的委托对象
     */
    private transient Charset charset;

    /** Creates a new SimpleStringSchema that uses "UTF-8" as the encoding.
     * 创建一个使用“UTF-8”作为编码的新 SimpleStringSchema。
     * */
    public SimpleStringSchema() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * Creates a new SimpleStringSchema that uses the given charset to convert between strings and
     * bytes.
     * 创建一个新的 SimpleStringSchema，它使用给定的字符集在字符串和字节之间进行转换。
     *
     * @param charset The charset to use to convert between strings and bytes.
     */
    public SimpleStringSchema(Charset charset) {
        this.charset = checkNotNull(charset);
    }

    /**
     * Gets the charset used by this schema for serialization.
     * 获取此架构用于序列化的字符集。
     *
     * @return The charset used by this schema for serialization.
     */
    public Charset getCharset() {
        return charset;
    }

    // ------------------------------------------------------------------------
    //  Kafka Serialization
    // ------------------------------------------------------------------------

    @Override
    public String deserialize(byte[] message) {
        return new String(message, charset);
    }

    @Override
    public boolean isEndOfStream(String nextElement) {
        return false;
    }

    @Override
    public byte[] serialize(String element) {
        return element.getBytes(charset);
    }

    @Override
    public TypeInformation<String> getProducedType() {
        return BasicTypeInfo.STRING_TYPE_INFO;
    }

    // ------------------------------------------------------------------------
    //  Java Serialization
    // ------------------------------------------------------------------------

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(charset.name());
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        String charsetName = in.readUTF();
        this.charset = Charset.forName(charsetName);
    }
}
