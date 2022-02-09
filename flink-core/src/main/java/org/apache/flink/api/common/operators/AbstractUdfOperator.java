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
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.operators.util.UserCodeWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract superclass for all contracts that represent actual operators.
 * 代表实际运营商的所有合约的抽象超类。
 *
 * @param <FT> Type of the user function
 */
@Internal
public abstract class AbstractUdfOperator<OUT, FT extends Function> extends Operator<OUT> {

    /** The object or class containing the user function.
     * 包含用户函数的对象或类。
     * */
    protected final UserCodeWrapper<FT> userFunction;

    /** The extra inputs which parameterize the user function.
     * 参数化用户功能的额外输入。
     * */
    protected final Map<String, Operator<?>> broadcastInputs = new HashMap<>();

    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new abstract operator with the given name wrapping the given user function.
     * 用给定的名称创建一个新的抽象运算符，包装给定的用户函数。
     *
     * @param function The wrapper object containing the user function.
     * @param name The given name for the operator, used in plans, logs and progress messages.
     */
    protected AbstractUdfOperator(
            UserCodeWrapper<FT> function, OperatorInformation<OUT> operatorInfo, String name) {
        super(operatorInfo, name);
        this.userFunction = function;
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Gets the function that is held by this operator. The function is the actual implementation of
     * the user code.
     * 获取此运算符持有的函数。 该函数是用户代码的实际实现。
     *
     * <p>This throws an exception if the pact does not contain an object but a class for the user
     * code.
     * 如果协议不包含对象而是包含用户代码的类，则会引发异常。
     *
     * @return The object with the user function for this operator.
     * @see org.apache.flink.api.common.operators.Operator#getUserCodeWrapper()
     */
    @Override
    public UserCodeWrapper<FT> getUserCodeWrapper() {
        return userFunction;
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Returns the input, or null, if none is set.
     * 如果未设置，则返回输入或 null。
     *
     * @return The broadcast input root operator.
     */
    public Map<String, Operator<?>> getBroadcastInputs() {
        return this.broadcastInputs;
    }

    /**
     * Binds the result produced by a plan rooted at {@code root} to a variable used by the UDF
     * wrapped in this operator.
     * 将由以 {@code root} 为根的计划生成的结果绑定到包装在此运算符中的 UDF 使用的变量。
     *
     * @param root The root of the plan producing this input.
     */
    public void setBroadcastVariable(String name, Operator<?> root) {
        if (name == null) {
            throw new IllegalArgumentException("The broadcast input name may not be null.");
        }
        if (root == null) {
            throw new IllegalArgumentException(
                    "The broadcast input root operator may not be null.");
        }

        this.broadcastInputs.put(name, root);
    }

    /**
     * Clears all previous broadcast inputs and binds the given inputs as broadcast variables of
     * this operator.
     * 清除所有先前的广播输入并将给定的输入绑定为此运算符的广播变量。
     *
     * @param inputs The {@code<name, root>} pairs to be set as broadcast inputs.
     */
    public <T> void setBroadcastVariables(Map<String, Operator<T>> inputs) {
        this.broadcastInputs.clear();
        this.broadcastInputs.putAll(inputs);
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Gets the number of inputs for this operator.
     * 获取此运算符的输入数。
     *
     * @return The number of inputs for this operator.
     */
    public abstract int getNumberOfInputs();

    /**
     * Gets the column numbers of the key fields in the input records for the given input.
     * 获取给定输入的输入记录中的关键字段的列号。
     *
     * @return The column numbers of the key fields.
     */
    public abstract int[] getKeyColumns(int inputNum);

    // --------------------------------------------------------------------------------------------

    /**
     * Generic utility function that wraps a single class object into an array of that class type.
     * 将单个类对象包装到该类类型的数组中的通用实用程序函数。
     *
     * @param <U> The type of the classes.
     * @param clazz The class object to be wrapped.
     * @return An array wrapping the class object.
     */
    protected static <U> Class<U>[] asArray(Class<U> clazz) {
        @SuppressWarnings("unchecked")
        Class<U>[] array = new Class[] {clazz};
        return array;
    }

    /**
     * Generic utility function that returns an empty class array.
     * 返回空类数组的通用实用程序函数。
     *
     * @param <U> The type of the classes.
     * @return An empty array of type <tt>Class&lt;U&gt;</tt>.
     */
    protected static <U> Class<U>[] emptyClassArray() {
        @SuppressWarnings("unchecked")
        Class<U>[] array = new Class[0];
        return array;
    }
}
