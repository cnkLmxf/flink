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
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.operators.util.UserCodeWrapper;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Visitable;

import java.util.List;

/**
 * Abstract base class for all operators. An operator is a source, sink, or it applies an operation
 * to one or more inputs, producing a result.
 * 所有运算符的抽象基类。 运算符是source、sink，或者它将操作应用于一个或多个输入，产生结果。
 *
 * @param <OUT> Output type of the records output by this operator
 */
@Internal
public abstract class Operator<OUT> implements Visitable<Operator<?>> {
    //参数化UDF的参数
    protected final Configuration parameters; // the parameters to parameterize the UDF

    protected CompilerHints compilerHints; // hints to the compiler
    // 合约实例的名称。 选修的。
    protected String name; // the name of the contract instance. optional.
    // 要使用的并行实例数
    private int parallelism =
            ExecutionConfig.PARALLELISM_DEFAULT; // the number of parallel instances to use
    // 合约实例的最小资源。
    private ResourceSpec minResources =
            ResourceSpec.DEFAULT; // the minimum resource of the contract instance.
    // 合约实例的首选资源。
    private ResourceSpec preferredResources =
            ResourceSpec.DEFAULT; // the preferred resource of the contract instance.

    /** The return type of the user function. */
    protected final OperatorInformation<OUT> operatorInfo;

    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new contract with the given name. The parameters are empty by default and the
     * compiler hints are not set.
     * 创建具有给定名称的新合同。 默认情况下参数为空，并且未设置编译器提示。
     *
     * @param name The name that is used to describe the contract.
     */
    protected Operator(OperatorInformation<OUT> operatorInfo, String name) {
        this.name = (name == null) ? "(null)" : name;
        this.parameters = new Configuration();
        this.compilerHints = new CompilerHints();
        this.operatorInfo = operatorInfo;
    }

    /** Gets the information about the operators input/output types.
     * 获取有关运算符输入/输出类型的信息。
     * */
    public OperatorInformation<OUT> getOperatorInfo() {
        return operatorInfo;
    }

    /**
     * Gets the name of the contract instance. The name is only used to describe the contract
     * instance in logging output and graphical representations.
     * 获取合约实例的名称。 该名称仅用于在日志输出和图形表示中描述合约实例。
     *
     * @return The contract instance's name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the contract instance. The name is only used to describe the contract
     * instance in logging output and graphical representations.
     * 设置合约实例的名称。 该名称仅用于在日志输出和图形表示中描述合约实例。
     *
     * @param name The operator's name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the compiler hints for this contract instance. In the compiler hints, different fields
     * may be set (for example the selectivity) that will be evaluated by the pact compiler when
     * generating plan alternatives.
     * 获取此合约实例的编译器提示。 在编译器提示中，可以设置不同的字段（例如选择性），
     * 这些字段将由协议编译器在生成计划备选方案时进行评估。
     *
     * @return The compiler hints object.
     */
    public CompilerHints getCompilerHints() {
        return this.compilerHints;
    }

    /**
     * Gets the stub parameters of this contract. The stub parameters are a map that maps string
     * keys to string or integer values. The map is accessible by the user code at runtime.
     * Parameters that the user code needs to access at runtime to configure its behavior are
     * typically stored in that configuration object.
     * 获取此合约的存根参数。 存根参数是将字符串键映射到字符串或整数值的映射。 该地图可由用户代码在运行时访问。
     * 用户代码需要在运行时访问以配置其行为的参数通常存储在该配置对象中。
     *
     * @return The configuration containing the stub parameters.
     */
    public Configuration getParameters() {
        return this.parameters;
    }

    /**
     * Sets a stub parameters in the configuration of this contract. The stub parameters are
     * accessible by the user code at runtime. Parameters that the user code needs to access at
     * runtime to configure its behavior are typically stored as stub parameters.
     * 在此合约的配置中设置存根参数。 存根参数可由用户代码在运行时访问。
     * 用户代码需要在运行时访问以配置其行为的参数通常存储为存根参数。
     *
     * @see #getParameters()
     * @param key The parameter key.
     * @param value The parameter value.
     */
    public void setParameter(String key, String value) {
        this.parameters.setString(key, value);
    }

    /**
     * Sets a stub parameters in the configuration of this contract. The stub parameters are
     * accessible by the user code at runtime. Parameters that the user code needs to access at
     * runtime to configure its behavior are typically stored as stub parameters.
     * 在此合约的配置中设置存根参数。 存根参数可由用户代码在运行时访问。
     * 用户代码需要在运行时访问以配置其行为的参数通常存储为存根参数。
     *
     * @see #getParameters()
     * @param key The parameter key.
     * @param value The parameter value.
     */
    public void setParameter(String key, int value) {
        this.parameters.setInteger(key, value);
    }

    /**
     * Sets a stub parameters in the configuration of this contract. The stub parameters are
     * accessible by the user code at runtime. Parameters that the user code needs to access at
     * runtime to configure its behavior are typically stored as stub parameters.
     * 在此合约的配置中设置存根参数。 存根参数可由用户代码在运行时访问。
     * 用户代码需要在运行时访问以配置其行为的参数通常存储为存根参数。
     *
     * @see #getParameters()
     * @param key The parameter key.
     * @param value The parameter value.
     */
    public void setParameter(String key, boolean value) {
        this.parameters.setBoolean(key, value);
    }

    /**
     * Gets the parallelism for this contract instance. The parallelism denotes how many parallel
     * instances of the user function will be spawned during the execution. If this value is {@link
     * ExecutionConfig#PARALLELISM_DEFAULT}, then the system will decide the number of parallel
     * instances by itself.
     * 获取此合约实例的并行度。 并行度表示在执行期间将产生多少个用户函数的并行实例。
     * 如果此值为 {@link ExecutionConfig#PARALLELISM_DEFAULT}，则系统将自行决定并行实例的数量。
     *
     * @return The parallelism.
     */
    public int getParallelism() {
        return this.parallelism;
    }

    /**
     * Sets the parallelism for this contract instance. The parallelism denotes how many parallel
     * instances of the user function will be spawned during the execution.
     * 设置此合约实例的并行度。 并行度表示在执行期间将产生多少个用户函数的并行实例。
     *
     * @param parallelism The number of parallel instances to spawn. Set this value to {@link
     *     ExecutionConfig#PARALLELISM_DEFAULT} to let the system decide on its own.
     */
    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    /**
     * Gets the minimum resources for this operator. The minimum resources denotes how many
     * resources will be needed at least minimum for the operator or user function during the
     * execution.
     * 获取此运算符的最少资源。 最小资源表示在执行期间操作员或用户功能至少需要多少资源。
     *
     * @return The minimum resources of this operator.
     */
    @PublicEvolving
    public ResourceSpec getMinResources() {
        return this.minResources;
    }

    /**
     * Gets the preferred resources for this contract instance. The preferred resources denote how
     * many resources will be needed in the maximum for the user function during the execution.
     * 获取此合约实例的首选资源。 优选资源表示在执行期间用户功能最多需要多少资源。
     *
     * @return The preferred resource of this operator.
     */
    @PublicEvolving
    public ResourceSpec getPreferredResources() {
        return this.preferredResources;
    }

    /**
     * Sets the minimum and preferred resources for this contract instance. The resource denotes how
     * many memories and cpu cores of the user function will be consumed during the execution.
     * 设置此合约实例的最小资源和首选资源。 资源表示在执行期间将消耗用户函数的内存和 cpu 核心数。
     *
     * @param minResources The minimum resource of this operator.
     * @param preferredResources The preferred resource of this operator.
     */
    @PublicEvolving
    public void setResources(ResourceSpec minResources, ResourceSpec preferredResources) {
        this.minResources = minResources;
        this.preferredResources = preferredResources;
    }

    /**
     * Gets the user code wrapper. In the case of a pact, that object will be the stub with the user
     * function, in the case of an input or output format, it will be the format object.
     * 获取用户代码包装器。 在协议的情况下，该对象将是具有用户功能的存根，在输入或输出格式的情况下，它将是格式对象。
     *
     * @return The class with the user code.
     */
    public UserCodeWrapper<?> getUserCodeWrapper() {
        return null;
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Takes a list of operators and creates a cascade of unions of this inputs, if needed. If not
     * needed (there was only one operator in the list), then that operator is returned.
     * 如果需要，获取运算符列表并创建此输入的级联联合。 如果不需要（列表中只有一个运算符），则返回该运算符。
     *
     * @param operators The operators.
     * @return The single operator or a cascade of unions of the operators.
     */
    @SuppressWarnings("unchecked")
    public static <T> Operator<T> createUnionCascade(List<? extends Operator<T>> operators) {
        return createUnionCascade(
                (Operator<T>[]) operators.toArray(new Operator[operators.size()]));
    }

    /**
     * Takes a list of operators and creates a cascade of unions of this inputs, if needed. If not
     * needed (there was only one operator in the list), then that operator is returned.
     * 如果需要，获取运算符列表并创建此输入的级联联合。 如果不需要（列表中只有一个运算符），则返回该运算符。
     *
     * @param operators The operators.
     * @return The single operator or a cascade of unions of the operators.
     */
    public static <T> Operator<T> createUnionCascade(Operator<T>... operators) {
        return createUnionCascade(null, operators);
    }

    /**
     * Takes a single Operator and a list of operators and creates a cascade of unions of this
     * inputs, if needed. If not needed there was only one operator as input, then this operator is
     * returned.
     * 采用单个运算符和运算符列表，并在需要时创建此输入的级联联合。 如果不需要只有一个运算符作为输入，则返回此运算符。
     *
     * @param input1 The first input operator.
     * @param input2 The other input operators.
     * @return The single operator or a cascade of unions of the operators.
     */
    public static <T> Operator<T> createUnionCascade(Operator<T> input1, Operator<T>... input2) {
        // return cases where we don't need a union
        if (input2 == null || input2.length == 0) {
            return input1;
        } else if (input2.length == 1 && input1 == null) {
            return input2[0];
        }

        TypeInformation<T> type = null;
        if (input1 != null) {
            type = input1.getOperatorInfo().getOutputType();
        } else if (input2.length > 0 && input2[0] != null) {
            type = input2[0].getOperatorInfo().getOutputType();
        } else {
            throw new IllegalArgumentException("Could not determine type information from inputs.");
        }

        // Otherwise construct union cascade
        Union<T> lastUnion =
                new Union<T>(new BinaryOperatorInformation<T, T, T>(type, type, type), "<unknown>");

        int i;
        if (input2[0] == null) {
            throw new IllegalArgumentException("The input may not contain null elements.");
        }
        lastUnion.setFirstInput(input2[0]);

        if (input1 != null) {
            lastUnion.setSecondInput(input1);
            i = 1;
        } else {
            if (input2[1] == null) {
                throw new IllegalArgumentException("The input may not contain null elements.");
            }
            lastUnion.setSecondInput(input2[1]);
            i = 2;
        }
        for (; i < input2.length; i++) {
            Union<T> tmpUnion =
                    new Union<T>(
                            new BinaryOperatorInformation<T, T, T>(type, type, type), "<unknown>");
            tmpUnion.setSecondInput(lastUnion);
            if (input2[i] == null) {
                throw new IllegalArgumentException("The input may not contain null elements.");
            }
            tmpUnion.setFirstInput(input2[i]);
            lastUnion = tmpUnion;
        }
        return lastUnion;
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return getClass().getSimpleName() + " - " + getName();
    }
}
