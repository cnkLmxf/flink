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

package org.apache.flink.api.common.serialization;

import org.apache.flink.annotation.PublicEvolving;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * A simple {@link Encoder} that uses {@code toString()} on the input elements and writes them to
 * the output bucket file separated by newline.
 * 一个简单的 {@link Encoder}，它在输入元素上使用 {@code toString()} 并将它们写入由换行符分隔的输出存储桶文件。
 *
 * @param <IN> The type of the elements that are being written by the sink.
 */
@PublicEvolving
public class SimpleStringEncoder<IN> implements Encoder<IN> {

    private static final long serialVersionUID = -6865107843734614452L;

    private String charsetName;

    private transient Charset charset;

    /**
     * Creates a new {@code StringWriter} that uses {@code "UTF-8"} charset to convert strings to
     * bytes.
     * 创建一个新的 {@code StringWriter} ，它使用 {@code "UTF-8"} 字符集将字符串转换为字节。
     */
    public SimpleStringEncoder() {
        this("UTF-8");
    }

    /**
     * Creates a new {@code StringWriter} that uses the given charset to convert strings to bytes.
     *创建一个使用给定字符集将字符串转换为字节的新 {@code StringWriter}。
     *
     * @param charsetName Name of the charset to be used, must be valid input for {@code
     *     Charset.forName(charsetName)}
     */
    public SimpleStringEncoder(String charsetName) {
        this.charsetName = charsetName;
    }

    @Override
    public void encode(IN element, OutputStream stream) throws IOException {
        if (charset == null) {
            charset = Charset.forName(charsetName);
        }

        stream.write(element.toString().getBytes(charset));
        stream.write('\n');
    }
}
