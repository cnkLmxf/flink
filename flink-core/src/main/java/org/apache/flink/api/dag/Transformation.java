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

package org.apache.flink.api.dag;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.functions.InvalidTypesException;
import org.apache.flink.api.common.operators.ResourceSpec;
import org.apache.flink.api.common.operators.util.OperatorValidationUtils;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.MissingTypeInfo;
import org.apache.flink.core.memory.ManagedMemoryUseCase;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A {@code Transformation} represents the operation that creates a DataStream. Every DataStream has
 * an underlying {@code Transformation} that is the origin of said DataStream.
 * {@code Transformation} 表示创建 DataStream 的操作。
 * 每个 DataStream 都有一个底层 {@code Transformation}，它是所述 DataStream 的起源。
 *
 * <p>API operations such as DataStream#map create a tree of {@code Transformation}s underneath.
 * When the stream program is to be executed this graph is translated to a StreamGraph using
 * StreamGraphGenerator.
 * 诸如 DataStream#map 之类的 API 操作会在下面创建一个 {@code Transformation} 树。
 * 当要执行流程序时，该图使用 StreamGraphGenerator 转换为 StreamGraph。
 *
 * <p>A {@code Transformation} does not necessarily correspond to a physical operation at runtime.
 * Some operations are only logical concepts. Examples of this are union, split/select data stream,
 * partitioning.
 * {@code Transformation} 不一定对应于运行时的物理操作。 有些操作只是逻辑概念。 这方面的示例是 union, split/select data stream、partitioning。
 *
 * <p>The following graph of {@code Transformations}:
 *
 * <pre>{@code
 *   Source              Source
 *      +                   +
 *      |                   |
 *      v                   v
 *  Rebalance          HashPartition
 *      +                   +
 *      |                   |
 *      |                   |
 *      +------>Union<------+
 *                +
 *                |
 *                v
 *              Split
 *                +
 *                |
 *                v
 *             Select
 *                +
 *                v
 *               Map
 *                +
 *                |
 *                v
 *              Sink
 * }</pre>
 *
 * <p>Would result in this graph of operations at runtime:
 * 将在运行时生成此操作图：
 *
 * <pre>{@code
 * Source              Source
 *   +                   +
 *   |                   |
 *   |                   |
 *   +------->Map<-------+
 *             +
 *             |
 *             v
 *            Sink
 * }</pre>
 *
 * <p>The information about partitioning, union, split/select end up being encoded in the edges that
 * connect the sources to the map operation.
 * 有关分区、联合、拆分/选择的信息最终被编码在将源连接到映射操作的边中。
 *
 * @param <T> The type of the elements that result from this {@code Transformation}
 */
@Internal
public abstract class Transformation<T> {

    // Has to be equal to StreamGraphGenerator.UPPER_BOUND_MAX_PARALLELISM
    public static final int UPPER_BOUND_MAX_PARALLELISM = 1 << 15;

    // This is used to assign a unique ID to every Transformation
    protected static Integer idCounter = 0;

    public static int getNewNodeId() {
        idCounter++;
        return idCounter;
    }

    protected final int id;

    protected String name;

    protected TypeInformation<T> outputType;
    // This is used to handle MissingTypeInfo. As long as the outputType has not been queried
    // it can still be changed using setOutputType(). Afterwards an exception is thrown when
    // trying to change the output type.
    // 这用于处理 MissingTypeInfo。 只要 outputType 没有被查询，它仍然可以使用 setOutputType() 进行更改。
    // 之后尝试更改输出类型时会引发异常。
    protected boolean typeUsed;

    private int parallelism;

    /**
     * The maximum parallelism for this stream transformation. It defines the upper limit for
     * dynamic scaling and the number of key groups used for partitioned state.
     */
    private int maxParallelism = -1;

    /**
     * The minimum resources for this stream transformation. It defines the lower limit for dynamic
     * resources resize in future plan.
     */
    private ResourceSpec minResources = ResourceSpec.DEFAULT;

    /**
     * The preferred resources for this stream transformation. It defines the upper limit for
     * dynamic resource resize in future plan.
     */
    private ResourceSpec preferredResources = ResourceSpec.DEFAULT;

    /**
     * Each entry in this map represents a operator scope use case that this transformation needs
     * managed memory for. The keys indicate the use cases, while the values are the
     * use-case-specific weights for this transformation. Managed memory reserved for a use case
     * will be shared by all the declaring transformations within a slot according to this weight.
     * 此映射中的每个条目表示此转换需要托管内存的操作员范围用例。
     * 键表示用例，而值是此转换的特定于用例的权重。 根据此权重，为用例保留的托管内存将由插槽内的所有声明转换共享。
     */
    private final Map<ManagedMemoryUseCase, Integer> managedMemoryOperatorScopeUseCaseWeights =
            new HashMap<>();

    /** Slot scope use cases that this transformation needs managed memory for.
     * 此转换需要托管内存的插槽范围用例。
     * */
    private final Set<ManagedMemoryUseCase> managedMemorySlotScopeUseCases = new HashSet<>();

    /**
     * User-specified ID for this transformation. This is used to assign the same operator ID across
     * job restarts. There is also the automatically generated {@link #id}, which is assigned from a
     * static counter. That field is independent from this.
     * 此转换的用户指定 ID。 这用于在作业重新启动时分配相同的操作员 ID。
     * 还有自动生成的 {@link #id}，它是从静态计数器分配的。 该领域与此无关。
     */
    private String uid;

    private String userProvidedNodeHash;

    protected long bufferTimeout = -1;

    private String slotSharingGroup;

    @Nullable private String coLocationGroupKey;

    /**
     * Creates a new {@code Transformation} with the given name, output type and parallelism.
     * 使用给定的名称、输出类型和并行度创建一个新的 {@code Transformation}。
     *
     * @param name The name of the {@code Transformation}, this will be shown in Visualizations and
     *     the Log
     * @param outputType The output type of this {@code Transformation}
     * @param parallelism The parallelism of this {@code Transformation}
     */
    public Transformation(String name, TypeInformation<T> outputType, int parallelism) {
        this.id = getNewNodeId();
        this.name = Preconditions.checkNotNull(name);
        this.outputType = outputType;
        this.parallelism = parallelism;
        this.slotSharingGroup = null;
    }

    /** Returns the unique ID of this {@code Transformation}. */
    public int getId() {
        return id;
    }

    /** Changes the name of this {@code Transformation}. */
    public void setName(String name) {
        this.name = name;
    }

    /** Returns the name of this {@code Transformation}. */
    public String getName() {
        return name;
    }

    /** Returns the parallelism of this {@code Transformation}. */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Sets the parallelism of this {@code Transformation}.
     *
     * @param parallelism The new parallelism to set on this {@code Transformation}.
     */
    public void setParallelism(int parallelism) {
        OperatorValidationUtils.validateParallelism(parallelism);
        this.parallelism = parallelism;
    }

    /**
     * Gets the maximum parallelism for this stream transformation.
     *
     * @return Maximum parallelism of this transformation.
     */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Sets the maximum parallelism for this stream transformation.
     *
     * @param maxParallelism Maximum parallelism for this stream transformation.
     */
    public void setMaxParallelism(int maxParallelism) {
        OperatorValidationUtils.validateMaxParallelism(maxParallelism, UPPER_BOUND_MAX_PARALLELISM);
        this.maxParallelism = maxParallelism;
    }

    /**
     * Sets the minimum and preferred resources for this stream transformation.
     *
     * @param minResources The minimum resource of this transformation.
     * @param preferredResources The preferred resource of this transformation.
     */
    public void setResources(ResourceSpec minResources, ResourceSpec preferredResources) {
        OperatorValidationUtils.validateMinAndPreferredResources(minResources, preferredResources);
        this.minResources = checkNotNull(minResources);
        this.preferredResources = checkNotNull(preferredResources);
    }

    /**
     * Gets the minimum resource of this stream transformation.
     *
     * @return The minimum resource of this transformation.
     */
    public ResourceSpec getMinResources() {
        return minResources;
    }

    /**
     * Gets the preferred resource of this stream transformation.
     *
     * @return The preferred resource of this transformation.
     */
    public ResourceSpec getPreferredResources() {
        return preferredResources;
    }

    /**
     * Declares that this transformation contains certain operator scope managed memory use case.
     * 声明此转换包含某些运算符范围管理的内存用例。
     *
     * @param managedMemoryUseCase The use case that this transformation declares needing managed
     *     memory for.
     * @param weight Use-case-specific weights for this transformation. Used for sharing managed
     *     memory across transformations for OPERATOR scope use cases.
     * @return The previous weight, if exist.
     */
    public Optional<Integer> declareManagedMemoryUseCaseAtOperatorScope(
            ManagedMemoryUseCase managedMemoryUseCase, int weight) {
        Preconditions.checkNotNull(managedMemoryUseCase);
        Preconditions.checkArgument(
                managedMemoryUseCase.scope == ManagedMemoryUseCase.Scope.OPERATOR,
                "Use case is not operator scope.");
        Preconditions.checkArgument(
                weight > 0, "Weights for operator scope use cases must be greater than 0.");

        return Optional.ofNullable(
                managedMemoryOperatorScopeUseCaseWeights.put(managedMemoryUseCase, weight));
    }

    /**
     * Declares that this transformation contains certain slot scope managed memory use case.
     *
     * @param managedMemoryUseCase The use case that this transformation declares needing managed
     *     memory for.
     */
    public void declareManagedMemoryUseCaseAtSlotScope(ManagedMemoryUseCase managedMemoryUseCase) {
        Preconditions.checkNotNull(managedMemoryUseCase);
        Preconditions.checkArgument(managedMemoryUseCase.scope == ManagedMemoryUseCase.Scope.SLOT);

        managedMemorySlotScopeUseCases.add(managedMemoryUseCase);
    }

    protected void updateManagedMemoryStateBackendUseCase(boolean hasStateBackend) {
        if (hasStateBackend) {
            managedMemorySlotScopeUseCases.add(ManagedMemoryUseCase.STATE_BACKEND);
        } else {
            managedMemorySlotScopeUseCases.remove(ManagedMemoryUseCase.STATE_BACKEND);
        }
    }

    /**
     * Get operator scope use cases that this transformation needs managed memory for, and the
     * use-case-specific weights for this transformation. The weights are used for sharing managed
     * memory across transformations for the use cases.
     */
    public Map<ManagedMemoryUseCase, Integer> getManagedMemoryOperatorScopeUseCaseWeights() {
        return Collections.unmodifiableMap(managedMemoryOperatorScopeUseCaseWeights);
    }

    /** Get slot scope use cases that this transformation needs managed memory for. */
    public Set<ManagedMemoryUseCase> getManagedMemorySlotScopeUseCases() {
        return Collections.unmodifiableSet(managedMemorySlotScopeUseCases);
    }

    /**
     * Sets an user provided hash for this operator. This will be used AS IS the create the
     * JobVertexID.
     * 为此运算符设置用户提供的哈希。 这将按原样创建 JobVertexID。
     *
     * <p>The user provided hash is an alternative to the generated hashes, that is considered when
     * identifying an operator through the default hash mechanics fails (e.g. because of changes
     * between Flink versions).
     * 用户提供的散列是生成散列的替代方案，当通过默认散列机制识别操作员失败时（例如，由于 Flink 版本之间的更改），会考虑该散列。
     *
     * <p><strong>Important</strong>: this should be used as a workaround or for trouble shooting.
     * The provided hash needs to be unique per transformation and job. Otherwise, job submission
     * will fail. Furthermore, you cannot assign user-specified hash to intermediate nodes in an
     * operator chain and trying so will let your job fail.
     * <strong>重要</strong>：这应该用作解决方法或排除故障。
     * 提供的散列需要在每个转换和作业中都是唯一的。 否则，作业提交将失败。
     * 此外，您不能将用户指定的哈希分配给运算符链中的中间节点，并且尝试这样做会使您的工作失败。
     *
     * <p>A use case for this is in migration between Flink versions or changing the jobs in a way
     * that changes the automatically generated hashes. In this case, providing the previous hashes
     * directly through this method (e.g. obtained from old logs) can help to reestablish a lost
     * mapping from states to their target operator.
     * 一个用例是在 Flink 版本之间迁移或以更改自动生成的哈希的方式更改作业。
     * 在这种情况下，通过这种方法直接提供以前的哈希（例如从旧日志中获得）可以帮助重新建立从状态到目标操作符的丢失映射。
     *
     * @param uidHash The user provided hash for this operator. This will become the JobVertexID,
     *     which is shown in the logs and web ui.
     */
    public void setUidHash(String uidHash) {

        Preconditions.checkNotNull(uidHash);
        Preconditions.checkArgument(
                uidHash.matches("^[0-9A-Fa-f]{32}$"),
                "Node hash must be a 32 character String that describes a hex code. Found: "
                        + uidHash);

        this.userProvidedNodeHash = uidHash;
    }

    /**
     * Gets the user provided hash.
     *
     * @return The user provided hash.
     */
    public String getUserProvidedNodeHash() {
        return userProvidedNodeHash;
    }

    /**
     * Sets an ID for this {@link Transformation}. This is will later be hashed to a uidHash which
     * is then used to create the JobVertexID (that is shown in logs and the web ui).
     *
     * <p>The specified ID is used to assign the same operator ID across job submissions (for
     * example when starting a job from a savepoint).
     *
     * <p><strong>Important</strong>: this ID needs to be unique per transformation and job.
     * Otherwise, job submission will fail.
     *
     * @param uid The unique user-specified ID of this transformation.
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * Returns the user-specified ID of this transformation.
     *
     * @return The unique user-specified ID of this transformation.
     */
    public String getUid() {
        return uid;
    }

    /**
     * Returns the slot sharing group of this transformation.
     *
     * @see #setSlotSharingGroup(String)
     */
    public String getSlotSharingGroup() {
        return slotSharingGroup;
    }

    /**
     * Sets the slot sharing group of this transformation. Parallel instances of operations that are
     * in the same slot sharing group will be co-located in the same TaskManager slot, if possible.
     *
     * <p>Initially, an operation is in the default slot sharing group. This can be explicitly set
     * using {@code setSlotSharingGroup("default")}.
     *
     * @param slotSharingGroup The slot sharing group name.
     */
    public void setSlotSharingGroup(String slotSharingGroup) {
        this.slotSharingGroup = slotSharingGroup;
    }

    /**
     * <b>NOTE:</b> This is an internal undocumented feature for now. It is not clear whether this
     * will be supported and stable in the long term.
     * <b>注意：</b> 目前这是一个内部未记录的功能。 目前尚不清楚这是否会得到长期支持和稳定。
     *
     * <p>Sets the key that identifies the co-location group. Operators with the same co-location
     * key will have their corresponding subtasks placed into the same slot by the scheduler.
     * 设置标识协同定位组的键。 具有相同 co-location key 的操作员将由调度程序将其相应的子任务放置到同一插槽中。
     *
     * <p>Setting this to null means there is no co-location constraint.
     */
    public void setCoLocationGroupKey(@Nullable String coLocationGroupKey) {
        this.coLocationGroupKey = coLocationGroupKey;
    }

    /**
     * <b>NOTE:</b> This is an internal undocumented feature for now. It is not clear whether this
     * will be supported and stable in the long term.
     *
     * <p>Gets the key that identifies the co-location group. Operators with the same co-location
     * key will have their corresponding subtasks placed into the same slot by the scheduler.
     *
     * <p>If this is null (which is the default), it means there is no co-location constraint.
     */
    @Nullable
    public String getCoLocationGroupKey() {
        return coLocationGroupKey;
    }

    /**
     * Tries to fill in the type information. Type information can be filled in later when the
     * program uses a type hint. This method checks whether the type information has ever been
     * accessed before and does not allow modifications if the type was accessed already. This
     * ensures consistency by making sure different parts of the operation do not assume different
     * type information.
     * 尝试填写类型信息。 当程序使用类型提示时，可以稍后填写类型信息。
     * 此方法检查类型信息是否曾经被访问过，如果类型已经被访问过，则不允许修改。
     * 这通过确保操作的不同部分不假定不同的类型信息来确保一致性。
     *
     * @param outputType The type information to fill in.
     * @throws IllegalStateException Thrown, if the type information has been accessed before.
     */
    public void setOutputType(TypeInformation<T> outputType) {
        if (typeUsed) {
            throw new IllegalStateException(
                    "TypeInformation cannot be filled in for the type after it has been used. "
                            + "Please make sure that the type info hints are the first call after"
                            + " the transformation function, "
                            + "before any access to types or semantic properties, etc.");
        }
        this.outputType = outputType;
    }

    /**
     * Returns the output type of this {@code Transformation} as a {@link TypeInformation}. Once
     * this is used once the output type cannot be changed anymore using {@link #setOutputType}.
     *
     * @return The output type of this {@code Transformation}
     */
    public TypeInformation<T> getOutputType() {
        if (outputType instanceof MissingTypeInfo) {
            MissingTypeInfo typeInfo = (MissingTypeInfo) this.outputType;
            throw new InvalidTypesException(
                    "The return type of function '"
                            + typeInfo.getFunctionName()
                            + "' could not be determined automatically, due to type erasure. "
                            + "You can give type information hints by using the returns(...) "
                            + "method on the result of the transformation call, or by letting "
                            + "your function implement the 'ResultTypeQueryable' "
                            + "interface.",
                    typeInfo.getTypeException());
        }
        typeUsed = true;
        return this.outputType;
    }

    /**
     * Set the buffer timeout of this {@code Transformation}. The timeout defines how long data may
     * linger in a partially full buffer before being sent over the network.
     * 设置此 {@code Transformation} 的缓冲区超时。 超时定义了数据在通过网络发送之前可以在部分满的缓冲区中停留多长时间。
     *
     * <p>Lower timeouts lead to lower tail latencies, but may affect throughput. For Flink 1.5+,
     * timeouts of 1ms are feasible for jobs with high parallelism.
     * 较低的超时导致较低的尾部延迟，但可能会影响吞吐量。 对于 Flink 1.5+，1ms 的超时对于高并行度的作业是可行的。
     *
     * <p>A value of -1 means that the default buffer timeout should be used. A value of zero
     * indicates that no buffering should happen, and all records/events should be immediately sent
     * through the network, without additional buffering.
     * 值 -1 表示应使用默认缓冲区超时。 零值表示不应发生缓冲，所有记录/事件应立即通过网络发送，无需额外缓冲。
     */
    public void setBufferTimeout(long bufferTimeout) {
        checkArgument(bufferTimeout >= -1);
        this.bufferTimeout = bufferTimeout;
    }

    /**
     * Returns the buffer timeout of this {@code Transformation}.
     *
     * @see #setBufferTimeout(long)
     */
    public long getBufferTimeout() {
        return bufferTimeout;
    }

    /**
     * Returns all transitive predecessor {@code Transformation}s of this {@code Transformation}.
     * This is, for example, used when determining whether a feedback edge of an iteration actually
     * has the iteration head as a predecessor.
     * 返回此 {@code Transformation} 的所有传递前任 {@code Transformation}。
     * 例如，这在确定迭代的反馈边是否实际上具有迭代头作为前驱时使用。
     *
     * @return The list of transitive predecessors.
     */
    public abstract List<Transformation<?>> getTransitivePredecessors();

    /**
     * Returns the {@link Transformation transformations} that are the immediate predecessors of the
     * current transformation in the transformation graph.
     * 返回{@link Transformation transformations}，它们是转换图中当前转换的直接前导。
     */
    public abstract List<Transformation<?>> getInputs();

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{"
                + "id="
                + id
                + ", name='"
                + name
                + '\''
                + ", outputType="
                + outputType
                + ", parallelism="
                + parallelism
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transformation)) {
            return false;
        }

        Transformation<?> that = (Transformation<?>) o;

        if (bufferTimeout != that.bufferTimeout) {
            return false;
        }
        if (id != that.id) {
            return false;
        }
        if (parallelism != that.parallelism) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        return outputType != null ? outputType.equals(that.outputType) : that.outputType == null;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + name.hashCode();
        result = 31 * result + (outputType != null ? outputType.hashCode() : 0);
        result = 31 * result + parallelism;
        result = 31 * result + (int) (bufferTimeout ^ (bufferTimeout >>> 32));
        return result;
    }
}
