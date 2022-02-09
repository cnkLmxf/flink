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

package org.apache.flink.api.common.operators.base;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.aggregators.AggregatorRegistry;
import org.apache.flink.api.common.functions.AbstractRichFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.operators.BinaryOperatorInformation;
import org.apache.flink.api.common.operators.DualInputOperator;
import org.apache.flink.api.common.operators.IterationOperator;
import org.apache.flink.api.common.operators.Operator;
import org.apache.flink.api.common.operators.OperatorInformation;
import org.apache.flink.api.common.operators.util.UserCodeClassWrapper;
import org.apache.flink.api.common.operators.util.UserCodeWrapper;
import org.apache.flink.util.Visitor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A DeltaIteration is similar to a {@link BulkIterationBase}, but maintains state across the
 * individual iteration steps. The state is called the <i>solution set</i>, can be obtained via
 * {@link #getSolutionSet()}, and be accessed by joining (or CoGrouping) with it. The solution set
 * is updated by producing a delta for it, which is merged into the solution set at the end of each
 * iteration step.
 * DeltaIteration 类似于 {@link BulkIterationBase}，但在各个迭代步骤中保持状态。
 * 该状态称为<i>solution set</i>，可以通过{@link #getSolutionSet()} 获得，并通过加入（或CoGrouping）来访问。
 * 解决方案集通过为其生成一个增量来更新，该增量在每个迭代步骤结束时合并到解决方案集中。
 *
 * <p>The delta iteration must be closed by setting a delta for the solution set ({@link
 * #setSolutionSetDelta(org.apache.flink.api.common.operators.Operator)}) and the new workset (the
 * data set that will be fed back, {@link
 * #setNextWorkset(org.apache.flink.api.common.operators.Operator)}). The DeltaIteration itself
 * represents the result after the iteration has terminated. Delta iterations terminate when the
 * feed back data set (the workset) is empty. In addition, a maximum number of steps is given as a
 * fall back termination guard.
 * 增量迭代必须通过为解决方案集（{@link #setSolutionSetDelta(org.apache.flink.api.common.operators.Operator)}）
 * 和新工作集（将被反馈的数据集）设置一个增量来关闭 , {@link #setNextWorkset(org.apache.flink.api.common.operators.Operator)})。
 * DeltaIteration 本身代表迭代终止后的结果。 当反馈数据集（工作集）为空时，增量迭代终止。 此外，最大步数作为后备终止保护给出。
 *
 * <p>Elements in the solution set are uniquely identified by a key. When merging the solution set
 * delta, contained elements with the same key are replaced.
 * 解决方案集中的元素由键唯一标识。 合并解决方案集 delta 时，将替换具有相同键的包含元素。
 *
 * <p>This class is a subclass of {@code DualInputOperator}. The solution set is considered the
 * first input, the workset is considered the second input.
 * 此类是 {@code DualInputOperator} 的子类。 解决方案集被视为第一个输入，工作集被视为第二个输入。
 */
@Internal
public class DeltaIterationBase<ST, WT> extends DualInputOperator<ST, WT, ST, AbstractRichFunction>
        implements IterationOperator {

    private final Operator<ST> solutionSetPlaceholder;

    private final Operator<WT> worksetPlaceholder;

    private Operator<ST> solutionSetDelta;

    private Operator<WT> nextWorkset;

    /** The positions of the keys in the solution tuple.
     * 解决方案元组中键的位置。
     * */
    private final int[] solutionSetKeyFields;

    /** The maximum number of iterations. Possibly used only as a safeguard.
     * 最大迭代次数。 可能仅用作保护措施。
     * */
    private int maxNumberOfIterations = -1;

    private final AggregatorRegistry aggregators = new AggregatorRegistry();

    private boolean solutionSetUnManaged;

    // --------------------------------------------------------------------------------------------

    public DeltaIterationBase(BinaryOperatorInformation<ST, WT, ST> operatorInfo, int keyPosition) {
        this(operatorInfo, new int[] {keyPosition});
    }

    public DeltaIterationBase(
            BinaryOperatorInformation<ST, WT, ST> operatorInfo, int[] keyPositions) {
        this(operatorInfo, keyPositions, "<Unnamed Delta Iteration>");
    }

    public DeltaIterationBase(
            BinaryOperatorInformation<ST, WT, ST> operatorInfo, int keyPosition, String name) {
        this(operatorInfo, new int[] {keyPosition}, name);
    }

    public DeltaIterationBase(
            BinaryOperatorInformation<ST, WT, ST> operatorInfo, int[] keyPositions, String name) {
        super(
                new UserCodeClassWrapper<AbstractRichFunction>(AbstractRichFunction.class),
                operatorInfo,
                name);
        this.solutionSetKeyFields = keyPositions;
        solutionSetPlaceholder =
                new SolutionSetPlaceHolder<ST>(
                        this, new OperatorInformation<ST>(operatorInfo.getFirstInputType()));
        worksetPlaceholder =
                new WorksetPlaceHolder<WT>(
                        this, new OperatorInformation<WT>(operatorInfo.getSecondInputType()));
    }

    // --------------------------------------------------------------------------------------------

    public int[] getSolutionSetKeyFields() {
        return this.solutionSetKeyFields;
    }

    public void setMaximumNumberOfIterations(int maxIterations) {
        this.maxNumberOfIterations = maxIterations;
    }

    public int getMaximumNumberOfIterations() {
        return this.maxNumberOfIterations;
    }

    @Override
    public AggregatorRegistry getAggregators() {
        return this.aggregators;
    }

    // --------------------------------------------------------------------------------------------
    // Getting / Setting of the step function input place-holders
    // --------------------------------------------------------------------------------------------

    /**
     * Gets the contract that represents the solution set for the step function.
     * 获取表示阶跃函数的解决方案集的协定。
     *
     * @return The solution set for the step function.
     */
    public Operator<ST> getSolutionSet() {
        return this.solutionSetPlaceholder;
    }

    /**
     * Gets the contract that represents the workset for the step function.
     * 获取表示 step 函数的工作集的协定。
     *
     * @return The workset for the step function.
     */
    public Operator<WT> getWorkset() {
        return this.worksetPlaceholder;
    }

    /**
     * Sets the contract of the step function that represents the next workset. This contract is
     * considered one of the two sinks of the step function (the other one being the solution set
     * delta).
     * 设置代表下一个工作集的阶跃函数的协定。 该合约被认为是阶跃函数的两个接收器之一（另一个是解决方案集增量）。
     *
     * @param result The contract representing the next workset.
     */
    public void setNextWorkset(Operator<WT> result) {
        this.nextWorkset = result;
    }

    /**
     * Gets the contract that has been set as the next workset.
     * 获取已设置为下一个工作集的合同。
     *
     * @return The contract that has been set as the next workset.
     */
    public Operator<WT> getNextWorkset() {
        return this.nextWorkset;
    }

    /**
     * Sets the contract of the step function that represents the solution set delta. This contract
     * is considered one of the two sinks of the step function (the other one being the next
     * workset).
     * 设置表示解集增量的阶跃函数的合同。 该合约被认为是阶跃函数的两个接收器之一（另一个是下一个工作集）。
     *
     * @param delta The contract representing the solution set delta.
     */
    public void setSolutionSetDelta(Operator<ST> delta) {
        this.solutionSetDelta = delta;
    }

    /**
     * Gets the contract that has been set as the solution set delta.
     * 获取已设置为解决方案集增量的合同。
     *
     * @return The contract that has been set as the solution set delta.
     */
    public Operator<ST> getSolutionSetDelta() {
        return this.solutionSetDelta;
    }

    // --------------------------------------------------------------------------------------------
    // Getting / Setting the Inputs
    // --------------------------------------------------------------------------------------------

    /**
     * Returns the initial solution set input, or null, if none is set.
     * 返回初始解集输入，如果未设置，则返回 null。
     *
     * @return The iteration's initial solution set input.
     */
    public Operator<ST> getInitialSolutionSet() {
        return getFirstInput();
    }

    /**
     * Returns the initial workset input, or null, if none is set.
     * 返回初始工作集输入，如果未设置，则返回 null。
     *
     * @return The iteration's workset input.
     */
    public Operator<WT> getInitialWorkset() {
        return getSecondInput();
    }

    /**
     * Sets the given input as the initial solution set.
     * 将给定的输入设置为初始解集。
     *
     * @param input The contract to set the initial solution set.
     */
    public void setInitialSolutionSet(Operator<ST> input) {
        setFirstInput(input);
    }

    /**
     * Sets the given input as the initial workset.
     * 将给定的输入设置为初始工作集。
     *
     * @param input The contract to set as the initial workset.
     */
    public void setInitialWorkset(Operator<WT> input) {
        setSecondInput(input);
    }

    /**
     * DeltaIteration meta operator cannot have broadcast inputs.
     * DeltaIteration 元运算符不能有广播输入。
     *
     * @return An empty map.
     */
    public Map<String, Operator<?>> getBroadcastInputs() {
        return Collections.emptyMap();
    }

    /**
     * The DeltaIteration meta operator cannot have broadcast inputs. This method always throws an
     * exception.
     * DeltaIteration 元运算符不能有广播输入。 此方法总是抛出异常。
     *
     * @param name Ignored.
     * @param root Ignored.
     */
    public void setBroadcastVariable(String name, Operator<?> root) {
        throw new UnsupportedOperationException(
                "The DeltaIteration meta operator cannot have broadcast inputs.");
    }

    /**
     * The DeltaIteration meta operator cannot have broadcast inputs. This method always throws an
     * exception.
     * DeltaIteration 元运算符不能有广播输入。 此方法总是抛出异常。
     *
     * @param inputs Ignored
     */
    public <X> void setBroadcastVariables(Map<String, Operator<X>> inputs) {
        throw new UnsupportedOperationException(
                "The DeltaIteration meta operator cannot have broadcast inputs.");
    }

    /**
     * Sets whether to keep the solution set in managed memory (safe against heap exhaustion) or
     * unmanaged memory (objects on heap).
     * 设置是否将解决方案集保留在托管内存（防止堆耗尽）或非托管内存（堆上的对象）中。
     *
     * @param solutionSetUnManaged True to keep the solution set in unmanaged memory, false to keep
     *     it in managed memory.
     * @see #isSolutionSetUnManaged()
     */
    public void setSolutionSetUnManaged(boolean solutionSetUnManaged) {
        this.solutionSetUnManaged = solutionSetUnManaged;
    }

    /**
     * gets whether the solution set is in managed or unmanaged memory.
     * 获取解决方案集是在托管内存还是非托管内存中。
     *
     * @return True, if the solution set is in unmanaged memory (object heap), false if in managed
     *     memory.
     * @see #setSolutionSetUnManaged(boolean)
     */
    public boolean isSolutionSetUnManaged() {
        return solutionSetUnManaged;
    }

    // --------------------------------------------------------------------------------------------
    // Place-holder Operators
    // --------------------------------------------------------------------------------------------

    /**
     * Specialized operator to use as a recognizable place-holder for the working set input to the
     * step function.
     * 专用运算符用作可识别的占位符，用于输入到步进函数的工作集。
     */
    public static class WorksetPlaceHolder<WT> extends Operator<WT> {

        private final DeltaIterationBase<?, WT> containingIteration;

        public WorksetPlaceHolder(
                DeltaIterationBase<?, WT> container, OperatorInformation<WT> operatorInfo) {
            super(operatorInfo, "Workset");
            this.containingIteration = container;
        }

        public DeltaIterationBase<?, WT> getContainingWorksetIteration() {
            return this.containingIteration;
        }

        @Override
        public void accept(Visitor<Operator<?>> visitor) {
            visitor.preVisit(this);
            visitor.postVisit(this);
        }

        @Override
        public UserCodeWrapper<?> getUserCodeWrapper() {
            return null;
        }
    }

    /**
     * Specialized operator to use as a recognizable place-holder for the solution set input to the
     * step function.
     * 专用运算符用作可识别的占位符，用于输入到阶跃函数的解决方案集。
     */
    public static class SolutionSetPlaceHolder<ST> extends Operator<ST> {

        protected final DeltaIterationBase<ST, ?> containingIteration;

        public SolutionSetPlaceHolder(
                DeltaIterationBase<ST, ?> container, OperatorInformation<ST> operatorInfo) {
            super(operatorInfo, "Solution Set");
            this.containingIteration = container;
        }

        public DeltaIterationBase<ST, ?> getContainingWorksetIteration() {
            return this.containingIteration;
        }

        @Override
        public void accept(Visitor<Operator<?>> visitor) {
            visitor.preVisit(this);
            visitor.postVisit(this);
        }

        @Override
        public UserCodeWrapper<?> getUserCodeWrapper() {
            return null;
        }
    }

    @Override
    protected List<ST> executeOnCollections(
            List<ST> inputData1,
            List<WT> inputData2,
            RuntimeContext runtimeContext,
            ExecutionConfig executionConfig) {
        throw new UnsupportedOperationException();
    }
}
