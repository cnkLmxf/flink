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

package org.apache.flink.api.common.operators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeinfo.TypeInformation;

/**
 * A class for holding information about an operator, such as input/output TypeInformation.
 * 用于保存有关运算符的信息的类，例如输入/输出 TypeInformation。
 *
 * @param <OUT> Output type of the records output by the operator described by this information
 */
@Internal
public class OperatorInformation<OUT> {
    /** Output type of the operator
     * 算子的输出类型
     * */
    protected final TypeInformation<OUT> outputType;

    /** @param outputType The output type of the operator */
    public OperatorInformation(TypeInformation<OUT> outputType) {
        this.outputType = outputType;
    }

    /** Gets the return type of the user code function.
     * 获取用户代码函数的返回类型。
     * */
    public TypeInformation<OUT> getOutputType() {
        return outputType;
    }

    @Override
    public String toString() {
        return "Operator Info; Output type: " + outputType;
    }
}
