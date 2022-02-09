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

package org.apache.flink.api.common.state;

import org.apache.flink.annotation.PublicEvolving;

import java.util.Set;

/** This interface contains methods for registering operator state with a managed store.
 * 此接口包含用于向托管存储注册操作员状态的方法。
 * */
@PublicEvolving
public interface OperatorStateStore {

    /**
     * Creates (or restores) a {@link BroadcastState broadcast state}. This type of state can only
     * be created to store the state of a {@code BroadcastStream}. Each state is registered under a
     * unique name. The provided serializer is used to de/serialize the state in case of
     * checkpointing (snapshot/restore). The returned broadcast state has {@code key-value} format.
     * 创建（或恢复）一个 {@link BroadcastState 广播状态}。
     * 只能创建这种类型的状态来存储 {@code BroadcastStream} 的状态。 每个state都以唯一的名称注册。
     * 提供的序列化程序用于在检查点（快照/恢复）的情况下反/序列化状态。 返回的广播状态具有 {@code key-value} 格式。
     *
     * <p><b>CAUTION: the user has to guarantee that all task instances store the same elements in
     * this type of state.</b>
     * <b>注意：用户必须保证所有任务实例在这种状态下存储相同的元素。</b>
     *
     * <p>Each operator instance individually maintains and stores elements in the broadcast state.
     * The fact that the incoming stream is a broadcast one guarantees that all instances see all
     * the elements. Upon recovery or re-scaling, the same state is given to each of the instances.
     * To avoid hotspots, each task reads its previous partition, and if there are more tasks (scale
     * up), then the new instances read from the old instances in a round robin fashion. This is why
     * each instance has to guarantee that it stores the same elements as the rest. If not, upon
     * recovery or rescaling you may have unpredictable redistribution of the partitions, thus
     * unpredictable results.
     * 每个算子实例单独维护和存储广播状态中的元素。 传入流是广播流这一事实保证了所有实例都能看到所有元素。
     * 在恢复或重新扩展时，每个实例都被赋予相同的状态。
     * 为避免热点，每个任务读取其前一个分区，如果有更多任务（向上扩展），则新实例以循环方式从旧实例读取。
     * 这就是为什么每个实例必须保证它存储与其余实例相同的元素。
     * 否则，在恢复或重新缩放时，您可能会无法预测分区的重新分布，从而导致不可预测的结果。
     *
     * @param stateDescriptor The descriptor for this state, providing a name, a serializer for the
     *     keys and one for the values.
     * @param <K> The type of the keys in the broadcast state.
     * @param <V> The type of the values in the broadcast state.
     * @return The Broadcast State
     */
    <K, V> BroadcastState<K, V> getBroadcastState(MapStateDescriptor<K, V> stateDescriptor)
            throws Exception;

    /**
     * Creates (or restores) a list state. Each state is registered under a unique name. The
     * provided serializer is used to de/serialize the state in case of checkpointing
     * (snapshot/restore).
     * 创建（或恢复）列表状态。 每个州都以唯一的名称注册。 提供的序列化程序用于在检查点（快照/恢复）的情况下反/序列化状态。
     *
     * <p>Note the semantic differences between an operator list state and a keyed list state (see
     * {@link KeyedStateStore#getListState(ListStateDescriptor)}). Under the context of operator
     * state, the list is a collection of state items that are independent from each other and
     * eligible for redistribution across operator instances in case of changed operator
     * parallelism. In other words, these state items are the finest granularity at which non-keyed
     * state can be redistributed, and should not be correlated with each other.
     * 请注意运算符列表状态和键控列表状态之间的语义差异（请参阅 {@link KeyedStateStore#getListState(ListStateDescriptor)}）。
     * 在算子状态的上下文中，列表是状态项的集合，这些状态项彼此独立，并且在算子并行度发生变化的情况下可以在算子实例之间重新分配。
     * 换句话说，这些状态项是可以重新分配非键控状态的最细粒度，不应相互关联。
     *
     * <p>The redistribution scheme of this list state upon operator rescaling is a round-robin
     * pattern, such that the logical whole state (a concatenation of all the lists of state
     * elements previously managed by each operator before the restore) is evenly divided into as
     * many sublists as there are parallel operators.
     * 此列表状态在操作符重新缩放时的重新分配方案是一种循环模式，这样整个逻辑状态（在恢复之前由每个操作符先前管理的所有状态元素列表的串联）
     * 被均匀地划分为尽可能多的子列表 有并行操作符。
     *
     * @param stateDescriptor The descriptor for this state, providing a name and serializer.
     * @param <S> The generic type of the state
     * @return A list for all state partitions.
     */
    <S> ListState<S> getListState(ListStateDescriptor<S> stateDescriptor) throws Exception;

    /**
     * Creates (or restores) a list state. Each state is registered under a unique name. The
     * provided serializer is used to de/serialize the state in case of checkpointing
     * (snapshot/restore).
     * 创建（或恢复）列表状态。 每个state都以唯一的名称注册。 提供的序列化程序用于在检查点（快照/恢复）的情况下反/序列化状态。
     *
     * <p>Note the semantic differences between an operator list state and a keyed list state (see
     * {@link KeyedStateStore#getListState(ListStateDescriptor)}). Under the context of operator
     * state, the list is a collection of state items that are independent from each other and
     * eligible for redistribution across operator instances in case of changed operator
     * parallelism. In other words, these state items are the finest granularity at which non-keyed
     * state can be redistributed, and should not be correlated with each other.
     * 请注意运算符列表状态和键控列表状态之间的语义差异（请参阅 {@link KeyedStateStore#getListState(ListStateDescriptor)}）。
     * 在算子状态的上下文中，列表是状态项的集合，这些状态项彼此独立，并且在算子并行度发生变化的情况下可以在算子实例之间重新分配。
     * 换句话说，这些状态项是可以重新分配非键控状态的最细粒度，不应相互关联。
     *
     * <p>The redistribution scheme of this list state upon operator rescaling is a broadcast
     * pattern, such that the logical whole state (a concatenation of all the lists of state
     * elements previously managed by each operator before the restore) is restored to all parallel
     * operators so that each of them will get the union of all state items before the restore.
     * 这个列表状态在算子重新缩放时的重新分配方案是一种广播模式，
     * 这样逻辑整体状态（恢复之前每个算子先前管理的所有状态元素列表的串联）被恢复到所有并行算子，
     * 以便每个 他们将在恢复之前获得所有状态项的联合。
     *
     * @param stateDescriptor The descriptor for this state, providing a name and serializer.
     * @param <S> The generic type of the state
     * @return A list for all state partitions.
     */
    <S> ListState<S> getUnionListState(ListStateDescriptor<S> stateDescriptor) throws Exception;

    /**
     * Returns a set with the names of all currently registered states.
     * 返回一个包含所有当前注册状态名称的集合。
     *
     * @return set of names for all registered states.
     */
    Set<String> getRegisteredStateNames();

    /**
     * Returns a set with the names of all currently registered broadcast states.
     * 返回一个包含所有当前注册的广播状态名称的集合。
     *
     * @return set of names for all registered broadcast states.
     */
    Set<String> getRegisteredBroadcastStateNames();
}
