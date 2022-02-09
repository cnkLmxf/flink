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

package org.apache.flink.runtime.jobgraph;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.operators.ResourceSpec;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.InputSplitSource;
import org.apache.flink.runtime.OperatorIDPair;
import org.apache.flink.runtime.io.network.partition.ResultPartitionType;
import org.apache.flink.runtime.jobgraph.tasks.AbstractInvokable;
import org.apache.flink.runtime.jobmanager.scheduler.CoLocationGroup;
import org.apache.flink.runtime.jobmanager.scheduler.CoLocationGroupImpl;
import org.apache.flink.runtime.jobmanager.scheduler.SlotSharingGroup;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.SerializedValue;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkNotNull;

/** The base class for job vertexes.
 * 作业顶点的基类。
 * */
public class JobVertex implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_NAME = "(unnamed vertex)";

    public static final int MAX_PARALLELISM_DEFAULT = -1;

    // --------------------------------------------------------------------------------------------
    // Members that define the structure / topology of the graph
    // --------------------------------------------------------------------------------------------

    /** The ID of the vertex. */
    private final JobVertexID id;

    /**
     * The IDs of all operators contained in this vertex.
     * 此顶点中包含的所有运算符的 ID。
     *
     * <p>The ID pairs are stored depth-first post-order; for the forking chain below the ID's would
     * be stored as [D, E, B, C, A]. A - B - D \ \ C E This is the same order that operators are
     * stored in the {@code StreamTask}.
     * ID pairs 以深度优先的后序存储； 对于 ID 下方的分叉链，将存储为 [D, E, B, C, A]。 A - B - D \ \ C E 这与运算符在{@code StreamTask} 中的存储顺序相同。
     */
    private final List<OperatorIDPair> operatorIDs;

    /** List of produced data sets, one per writer.
     * 生成的数据集列表，每个作者一个。
     * */
    private final ArrayList<IntermediateDataSet> results = new ArrayList<>();

    /** List of edges with incoming data. One per Reader.
     * 带有传入数据的边列表。 每个读者一个。
     * */
    private final ArrayList<JobEdge> inputs = new ArrayList<>();

    /** The list of factories for operator coordinators.
     * 操作员协调员的工厂列表。
     * */
    private final ArrayList<SerializedValue<OperatorCoordinator.Provider>> operatorCoordinators =
            new ArrayList<>();

    /** Number of subtasks to split this task into at runtime.
     * 在运行时将此任务拆分为的子任务数。
     * */
    private int parallelism = ExecutionConfig.PARALLELISM_DEFAULT;

    /** Maximum number of subtasks to split this task into a runtime.
     * 将此任务拆分为运行时的最大子任务数。
     * */
    private int maxParallelism = MAX_PARALLELISM_DEFAULT;

    /** The minimum resource of the vertex. */
    private ResourceSpec minResources = ResourceSpec.DEFAULT;

    /** The preferred resource of the vertex. */
    private ResourceSpec preferredResources = ResourceSpec.DEFAULT;

    /** Custom configuration passed to the assigned task at runtime.
     * 自定义配置在运行时传递给分配的任务。
     * */
    private Configuration configuration;

    /** The class of the invokable. */
    private String invokableClassName;

    /** Indicates of this job vertex is stoppable or not.
     * 指示此作业顶点是否可停止。
     * */
    private boolean isStoppable = false;

    /** Optionally, a source of input splits.
     * 可选地，输入源分裂。
     * */
    private InputSplitSource<?> inputSplitSource;

    /**
     * The name of the vertex. This will be shown in runtime logs and will be in the runtime
     * environment.
     * 顶点的名称。 这将显示在运行时日志中，并将在运行时环境中。
     */
    private String name;

    /**
     * Optionally, a sharing group that allows subtasks from different job vertices to run
     * concurrently in one slot.
     * 可选地，允许来自不同作业顶点的子任务在一个槽中同时运行的共享组。
     */
    @Nullable private SlotSharingGroup slotSharingGroup;

    /** The group inside which the vertex subtasks share slots.
     * 顶点子任务共享槽的组。
     * */
    @Nullable private CoLocationGroupImpl coLocationGroup;

    /**
     * Optional, the name of the operator, such as 'Flat Map' or 'Join', to be included in the JSON
     * plan.
     * 可选，要包含在 JSON 计划中的运算符的名称，例如“平面地图”或“加入”。
     */
    private String operatorName;

    /**
     * Optional, the description of the operator, like 'Hash Join', or 'Sorted Group Reduce', to be
     * included in the JSON plan.
     * 可选，运算符的描述，如“Hash Join”或“Sorted Group Reduce”，将包含在 JSON 计划中。
     */
    private String operatorDescription;

    /** Optional, pretty name of the operator, to be displayed in the JSON plan.
     * 可选的，漂亮的操作员名称，将显示在 JSON 计划中。
     * */
    private String operatorPrettyName;

    /**
     * Optional, the JSON for the optimizer properties of the operator result, to be included in the
     * JSON plan.
     * 运算符结果的优化器属性的 JSON，将包含在 JSON 计划中。
     */
    private String resultOptimizerProperties;

    // --------------------------------------------------------------------------------------------

    /**
     * Constructs a new job vertex and assigns it with the given name.
     * 构造一个新的作业顶点并为其分配给定的名称。
     *
     * @param name The name of the new job vertex.
     */
    public JobVertex(String name) {
        this(name, null);
    }

    /**
     * Constructs a new job vertex and assigns it with the given name.
     * 构造一个新的作业顶点并为其分配给定的名称。
     *
     * @param name The name of the new job vertex.
     * @param id The id of the job vertex.
     */
    public JobVertex(String name, JobVertexID id) {
        this.name = name == null ? DEFAULT_NAME : name;
        this.id = id == null ? new JobVertexID() : id;
        OperatorIDPair operatorIDPair =
                OperatorIDPair.generatedIDOnly(OperatorID.fromJobVertexID(this.id));
        this.operatorIDs = Collections.singletonList(operatorIDPair);
    }

    /**
     * Constructs a new job vertex and assigns it with the given name.
     * 构造一个新的作业顶点并为其分配给定的名称。
     *
     * @param name The name of the new job vertex.
     * @param primaryId The id of the job vertex.
     * @param operatorIDPairs The operator ID pairs of the job vertex.
     */
    public JobVertex(String name, JobVertexID primaryId, List<OperatorIDPair> operatorIDPairs) {
        this.name = name == null ? DEFAULT_NAME : name;
        this.id = primaryId == null ? new JobVertexID() : primaryId;
        this.operatorIDs = Collections.unmodifiableList(operatorIDPairs);
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Returns the ID of this job vertex.
     *
     * @return The ID of this job vertex
     */
    public JobVertexID getID() {
        return this.id;
    }

    /**
     * Returns the name of the vertex.
     *
     * @return The name of the vertex.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the vertex.
     *
     * @param name The new name.
     */
    public void setName(String name) {
        this.name = name == null ? DEFAULT_NAME : name;
    }

    /**
     * Returns the number of produced intermediate data sets.
     *
     * @return The number of produced intermediate data sets.
     */
    public int getNumberOfProducedIntermediateDataSets() {
        return this.results.size();
    }

    /**
     * Returns the number of inputs.
     *
     * @return The number of inputs.
     */
    public int getNumberOfInputs() {
        return this.inputs.size();
    }

    public List<OperatorIDPair> getOperatorIDs() {
        return operatorIDs;
    }

    /**
     * Returns the vertex's configuration object which can be used to pass custom settings to the
     * task at runtime.
     *
     * @return the vertex's configuration object
     */
    public Configuration getConfiguration() {
        if (this.configuration == null) {
            this.configuration = new Configuration();
        }
        return this.configuration;
    }

    public void setInvokableClass(Class<? extends AbstractInvokable> invokable) {
        Preconditions.checkNotNull(invokable);
        this.invokableClassName = invokable.getName();
    }

    /**
     * Returns the name of the invokable class which represents the task of this vertex.
     *
     * @return The name of the invokable class, <code>null</code> if not set.
     */
    public String getInvokableClassName() {
        return this.invokableClassName;
    }

    /**
     * Returns the invokable class which represents the task of this vertex.
     *
     * @param cl The classloader used to resolve user-defined classes
     * @return The invokable class, <code>null</code> if it is not set
     */
    public Class<? extends AbstractInvokable> getInvokableClass(ClassLoader cl) {
        if (cl == null) {
            throw new NullPointerException("The classloader must not be null.");
        }
        if (invokableClassName == null) {
            return null;
        }

        try {
            return Class.forName(invokableClassName, true, cl).asSubclass(AbstractInvokable.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("The user-code class could not be resolved.", e);
        } catch (ClassCastException e) {
            throw new RuntimeException(
                    "The user-code class is no subclass of " + AbstractInvokable.class.getName(),
                    e);
        }
    }

    /**
     * Gets the parallelism of the task.
     *
     * @return The parallelism of the task.
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Sets the parallelism for the task.
     *
     * @param parallelism The parallelism for the task.
     */
    public void setParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("The parallelism must be at least one.");
        }
        this.parallelism = parallelism;
    }

    /**
     * Gets the maximum parallelism for the task.
     *
     * @return The maximum parallelism for the task.
     */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Sets the maximum parallelism for the task.
     *
     * @param maxParallelism The maximum parallelism to be set. must be between 1 and
     *     Short.MAX_VALUE.
     */
    public void setMaxParallelism(int maxParallelism) {
        this.maxParallelism = maxParallelism;
    }

    /**
     * Gets the minimum resource for the task.
     *
     * @return The minimum resource for the task.
     */
    public ResourceSpec getMinResources() {
        return minResources;
    }

    /**
     * Gets the preferred resource for the task.
     *
     * @return The preferred resource for the task.
     */
    public ResourceSpec getPreferredResources() {
        return preferredResources;
    }

    /**
     * Sets the minimum and preferred resources for the task.
     *
     * @param minResources The minimum resource for the task.
     * @param preferredResources The preferred resource for the task.
     */
    public void setResources(ResourceSpec minResources, ResourceSpec preferredResources) {
        this.minResources = checkNotNull(minResources);
        this.preferredResources = checkNotNull(preferredResources);
    }

    public InputSplitSource<?> getInputSplitSource() {
        return inputSplitSource;
    }

    public void setInputSplitSource(InputSplitSource<?> inputSplitSource) {
        this.inputSplitSource = inputSplitSource;
    }

    public List<IntermediateDataSet> getProducedDataSets() {
        return this.results;
    }

    public List<JobEdge> getInputs() {
        return this.inputs;
    }

    public List<SerializedValue<OperatorCoordinator.Provider>> getOperatorCoordinators() {
        return Collections.unmodifiableList(operatorCoordinators);
    }

    public void addOperatorCoordinator(
            SerializedValue<OperatorCoordinator.Provider> serializedCoordinatorProvider) {
        operatorCoordinators.add(serializedCoordinatorProvider);
    }

    /**
     * Associates this vertex with a slot sharing group for scheduling. Different vertices in the
     * same slot sharing group can run one subtask each in the same slot.
     * 将此顶点与一个槽共享组关联以进行调度。 同一槽共享组中的不同顶点可以在同一槽中运行一个子任务。
     *
     * @param grp The slot sharing group to associate the vertex with.
     */
    public void setSlotSharingGroup(SlotSharingGroup grp) {
        checkNotNull(grp);

        if (this.slotSharingGroup != null) {
            this.slotSharingGroup.removeVertexFromGroup(this.getID());
        }

        grp.addVertexToGroup(this.getID());
        this.slotSharingGroup = grp;
    }

    /**
     * Gets the slot sharing group that this vertex is associated with. Different vertices in the
     * same slot sharing group can run one subtask each in the same slot.
     * 获取与此顶点关联的槽共享组。 同一槽共享组中的不同顶点可以在同一槽中运行一个子任务。
     *
     * @return The slot sharing group to associate the vertex with
     */
    public SlotSharingGroup getSlotSharingGroup() {
        if (slotSharingGroup == null) {
            // create a new slot sharing group for this vertex if it was in no other slot sharing
            // group.
            // this should only happen in testing cases at the moment because production code path
            // will
            // always set a value to it before used
            setSlotSharingGroup(new SlotSharingGroup());
        }
        return slotSharingGroup;
    }

    /**
     * Tells this vertex to strictly co locate its subtasks with the subtasks of the given vertex.
     * Strict co-location implies that the n'th subtask of this vertex will run on the same parallel
     * computing instance (TaskManager) as the n'th subtask of the given vertex.
     * 告诉该顶点将其子任务与给定顶点的子任务严格定位在一起。
     * 严格的协同定位意味着该顶点的第 n 个子任务将与给定顶点的第 n 个子任务在相同的并行计算实例 (TaskManager) 上运行。
     *
     * <p>NOTE: Co-location is only possible between vertices in a slot sharing group.
     * 注意：只能在槽共享组中的顶点之间进行共定位。
     *
     * <p>NOTE: This vertex must (transitively) depend on the vertex to be co-located with. That
     * means that the respective vertex must be a (transitive) input of this vertex.
     * 注意：这个顶点必须（传递地）依赖于要与之共处的顶点。 这意味着相应的顶点必须是该顶点的（传递）输入。
     *
     * @param strictlyCoLocatedWith The vertex whose subtasks to co-locate this vertex's subtasks
     *     with.
     * @throws IllegalArgumentException Thrown, if this vertex and the vertex to co-locate with are
     *     not in a common slot sharing group.
     * @see #setSlotSharingGroup(SlotSharingGroup)
     */
    public void setStrictlyCoLocatedWith(JobVertex strictlyCoLocatedWith) {
        if (this.slotSharingGroup == null
                || this.slotSharingGroup != strictlyCoLocatedWith.slotSharingGroup) {
            throw new IllegalArgumentException(
                    "Strict co-location requires that both vertices are in the same slot sharing group.");
        }

        CoLocationGroupImpl thisGroup = this.coLocationGroup;
        CoLocationGroupImpl otherGroup = strictlyCoLocatedWith.coLocationGroup;

        if (otherGroup == null) {
            if (thisGroup == null) {
                CoLocationGroupImpl group = new CoLocationGroupImpl(this, strictlyCoLocatedWith);
                this.coLocationGroup = group;
                strictlyCoLocatedWith.coLocationGroup = group;
            } else {
                thisGroup.addVertex(strictlyCoLocatedWith);
                strictlyCoLocatedWith.coLocationGroup = thisGroup;
            }
        } else {
            if (thisGroup == null) {
                otherGroup.addVertex(this);
                this.coLocationGroup = otherGroup;
            } else {
                // both had yet distinct groups, we need to merge them
                thisGroup.mergeInto(otherGroup);
            }
        }
    }

    @Nullable
    public CoLocationGroup getCoLocationGroup() {
        return coLocationGroup;
    }

    public void updateCoLocationGroup(CoLocationGroupImpl group) {
        this.coLocationGroup = group;
    }

    // --------------------------------------------------------------------------------------------

    public IntermediateDataSet createAndAddResultDataSet(ResultPartitionType partitionType) {
        return createAndAddResultDataSet(new IntermediateDataSetID(), partitionType);
    }

    public IntermediateDataSet createAndAddResultDataSet(
            IntermediateDataSetID id, ResultPartitionType partitionType) {

        IntermediateDataSet result = new IntermediateDataSet(id, partitionType, this);
        this.results.add(result);
        return result;
    }

    public JobEdge connectDataSetAsInput(
            IntermediateDataSet dataSet, DistributionPattern distPattern) {
        JobEdge edge = new JobEdge(dataSet, this, distPattern);
        this.inputs.add(edge);
        dataSet.addConsumer(edge);
        return edge;
    }

    public JobEdge connectNewDataSetAsInput(
            JobVertex input, DistributionPattern distPattern, ResultPartitionType partitionType) {

        IntermediateDataSet dataSet = input.createAndAddResultDataSet(partitionType);

        JobEdge edge = new JobEdge(dataSet, this, distPattern);
        this.inputs.add(edge);
        dataSet.addConsumer(edge);
        return edge;
    }

    public void connectIdInput(IntermediateDataSetID dataSetId, DistributionPattern distPattern) {
        JobEdge edge = new JobEdge(dataSetId, this, distPattern);
        this.inputs.add(edge);
    }

    // --------------------------------------------------------------------------------------------

    public boolean isInputVertex() {
        return this.inputs.isEmpty();
    }

    public boolean isStoppable() {
        return this.isStoppable;
    }

    public boolean isOutputVertex() {
        return this.results.isEmpty();
    }

    public boolean hasNoConnectedInputs() {
        for (JobEdge edge : inputs) {
            if (!edge.isIdReference()) {
                return false;
            }
        }

        return true;
    }

    // --------------------------------------------------------------------------------------------

    /**
     * A hook that can be overwritten by sub classes to implement logic that is called by the master
     * when the job starts.
     * 一个可以被子类覆盖的钩子，以实现在作业开始时由主控调用的逻辑。
     *
     * @param loader The class loader for user defined code.
     * @throws Exception The method may throw exceptions which cause the job to fail immediately.
     */
    public void initializeOnMaster(ClassLoader loader) throws Exception {}

    /**
     * A hook that can be overwritten by sub classes to implement logic that is called by the master
     * after the job completed.
     * 一个可以被子类覆盖的钩子，以实现在作业完成后由主控调用的逻辑。
     *
     * @param loader The class loader for user defined code.
     * @throws Exception The method may throw exceptions which cause the job to fail immediately.
     */
    public void finalizeOnMaster(ClassLoader loader) throws Exception {}

    // --------------------------------------------------------------------------------------------

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorDescription() {
        return operatorDescription;
    }

    public void setOperatorDescription(String operatorDescription) {
        this.operatorDescription = operatorDescription;
    }

    public void setOperatorPrettyName(String operatorPrettyName) {
        this.operatorPrettyName = operatorPrettyName;
    }

    public String getOperatorPrettyName() {
        return operatorPrettyName;
    }

    public String getResultOptimizerProperties() {
        return resultOptimizerProperties;
    }

    public void setResultOptimizerProperties(String resultOptimizerProperties) {
        this.resultOptimizerProperties = resultOptimizerProperties;
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return this.name + " (" + this.invokableClassName + ')';
    }
}
