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
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.operators.util.UserCodeWrapper;
import org.apache.flink.util.Visitor;

import java.util.List;

/**
 * Abstract operator superclass for all operators that have two inputs, like "Join", "CoGroup", or
 * "Cross".
 * 具有两个输入的所有运算符的抽象运算符超类，例如“Join”、“CoGroup”或“Cross”。
 *
 * @param <IN1> First input type of the user function
 * @param <IN2> Second input type of the user function
 * @param <OUT> Output type of the user function
 * @param <FT> Type of the user function
 */
@Internal
public abstract class DualInputOperator<IN1, IN2, OUT, FT extends Function>
        extends AbstractUdfOperator<OUT, FT> {

    /** The operator producing the first input.
     * 产生第一个输入的操作员。
     * */
    protected Operator<IN1> input1;

    /** The operator producing the second input. */
    protected Operator<IN2> input2;

    /** The positions of the keys in the tuples of the first input.
     * 第一个输入的元组中键的位置。
     * */
    private final int[] keyFields1;

    /** The positions of the keys in the tuples of the second input. */
    private final int[] keyFields2;

    /** Semantic properties of the associated function.
     * 相关函数的语义属性。
     * */
    private DualInputSemanticProperties semanticProperties = new DualInputSemanticProperties();

    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new abstract dual-input Pact with the given name wrapping the given user function.
     * 使用给定名称包装给定用户函数创建一个新的抽象双输入 Pact。
     *
     * @param stub The class containing the user function.
     * @param name The given name for the operator, used in plans, logs and progress messages.
     */
    protected DualInputOperator(
            UserCodeWrapper<FT> stub,
            BinaryOperatorInformation<IN1, IN2, OUT> operatorInfo,
            String name) {
        super(stub, operatorInfo, name);
        this.keyFields1 = this.keyFields2 = new int[0];
    }

    /**
     * Creates a new abstract dual-input operator with the given name wrapping the given user
     * function. This constructor is specialized only for operator that require no keys for their
     * processing.
     * 使用给定名称包装给定用户函数创建一个新的抽象双输入运算符。 此构造函数仅适用于不需要键进行处理的运算符。
     *
     * @param stub The object containing the user function.
     * @param keyPositions1 The positions of the fields in the first input that act as keys.
     * @param keyPositions2 The positions of the fields in the second input that act as keys.
     * @param name The given name for the operator, used in plans, logs and progress messages.
     */
    protected DualInputOperator(
            UserCodeWrapper<FT> stub,
            BinaryOperatorInformation<IN1, IN2, OUT> operatorInfo,
            int[] keyPositions1,
            int[] keyPositions2,
            String name) {
        super(stub, operatorInfo, name);
        this.keyFields1 = keyPositions1;
        this.keyFields2 = keyPositions2;
    }

    // --------------------------------------------------------------------------------------------

    /** Gets the information about the operators input/output types.
     * 获取有关运算符输入/输出类型的信息。
     * */
    @Override
    @SuppressWarnings("unchecked")
    public BinaryOperatorInformation<IN1, IN2, OUT> getOperatorInfo() {
        return (BinaryOperatorInformation<IN1, IN2, OUT>) this.operatorInfo;
    }

    /**
     * Returns the first input, or null, if none is set.
     * 返回第一个输入，如果没有设置，则返回 null。
     *
     * @return The contract's first input.
     */
    public Operator<IN1> getFirstInput() {
        return this.input1;
    }

    /**
     * Returns the second input, or null, if none is set.
     * 如果未设置，则返回第二个输入或 null。
     *
     * @return The contract's second input.
     */
    public Operator<IN2> getSecondInput() {
        return this.input2;
    }

    /** Clears this operator's first input.
     * 清除此运算符的第一个输入。
     * */
    public void clearFirstInput() {
        this.input1 = null;
    }

    /** Clears this operator's second input. */
    public void clearSecondInput() {
        this.input2 = null;
    }

    /**
     * Clears all previous connections and connects the first input to the task wrapped in this
     * contract
     * 清除所有先前的连接并将第一个输入连接到包装在此合同中的任务
     *
     * @param input The contract that is connected as the first input.
     */
    public void setFirstInput(Operator<IN1> input) {
        this.input1 = input;
    }

    /**
     * Clears all previous connections and connects the second input to the task wrapped in this
     * contract
     * 清除所有先前的连接并将第二个输入连接到包含在此合同中的任务
     *
     * @param input The contract that is connected as the second input.
     */
    public void setSecondInput(Operator<IN2> input) {
        this.input2 = input;
    }

    /**
     * Sets the first input to the union of the given operators.
     * 将第一个输入设置为给定运算符的并集。
     *
     * @param inputs The operator(s) that form the first input.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    public void setFirstInput(Operator<IN1>... inputs) {
        this.input1 = Operator.createUnionCascade(inputs);
    }

    /**
     * Sets the second input to the union of the given operators.
     * 将第二个输入设置为给定运算符的并集。
     *
     * @param inputs The operator(s) that form the second input.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    public void setSecondInput(Operator<IN2>... inputs) {
        this.input2 = Operator.createUnionCascade(inputs);
    }

    /**
     * Sets the first input to the union of the given operators.
     * 将第一个输入设置为给定运算符的并集。
     *
     * @param inputs The operator(s) that form the first inputs.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    public void setFirstInputs(List<Operator<IN1>> inputs) {
        this.input1 = Operator.createUnionCascade(inputs);
    }

    /**
     * Sets the second input to the union of the given operators.
     * 将第二个输入设置为给定运算符的并集。
     *
     * @param inputs The operator(s) that form the second inputs.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    public void setSecondInputs(List<Operator<IN2>> inputs) {
        this.input2 = Operator.createUnionCascade(inputs);
    }

    /**
     * Add to the first input the union of the given operators.
     * 将给定运算符的并集添加到第一个输入。
     *
     * @param input The operator(s) to be unioned with the first input.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    public void addFirstInput(Operator<IN1>... input) {
        this.input1 = Operator.createUnionCascade(this.input1, input);
    }

    /**
     * Add to the second input the union of the given operators.
     * 将给定运算符的并集添加到第二个输入。
     *
     * @param input The operator(s) to be unioned with the second input.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    public void addSecondInput(Operator<IN2>... input) {
        this.input2 = Operator.createUnionCascade(this.input2, input);
    }

    /**
     * Add to the first input the union of the given operators.
     * 将给定运算符的并集添加到第一个输入。
     *
     * @param inputs The operator(s) to be unioned with the first input.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public void addFirstInputs(List<Operator<IN1>> inputs) {
        this.input1 =
                Operator.createUnionCascade(
                        this.input1, inputs.toArray(new Operator[inputs.size()]));
    }

    /**
     * Add to the second input the union of the given operators.
     * 将给定运算符的并集添加到第二个输入。
     *
     * @param inputs The operator(s) to be unioned with the second input.
     * @deprecated This method will be removed in future versions. Use the {@link Union} operator
     *     instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public void addSecondInputs(List<Operator<IN2>> inputs) {
        this.input2 =
                Operator.createUnionCascade(
                        this.input2, inputs.toArray(new Operator[inputs.size()]));
    }

    // --------------------------------------------------------------------------------------------

    public DualInputSemanticProperties getSemanticProperties() {
        return this.semanticProperties;
    }

    public void setSemanticProperties(DualInputSemanticProperties semanticProperties) {
        this.semanticProperties = semanticProperties;
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public final int getNumberOfInputs() {
        return 2;
    }

    @Override
    public int[] getKeyColumns(int inputNum) {
        if (inputNum == 0) {
            return this.keyFields1;
        } else if (inputNum == 1) {
            return this.keyFields2;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public void accept(Visitor<Operator<?>> visitor) {
        boolean descend = visitor.preVisit(this);
        if (descend) {
            this.input1.accept(visitor);
            this.input2.accept(visitor);
            for (Operator<?> c : this.broadcastInputs.values()) {
                c.accept(visitor);
            }
            visitor.postVisit(this);
        }
    }

    // --------------------------------------------------------------------------------------------

    protected abstract List<OUT> executeOnCollections(
            List<IN1> inputData1,
            List<IN2> inputData2,
            RuntimeContext runtimeContext,
            ExecutionConfig executionConfig)
            throws Exception;
}
