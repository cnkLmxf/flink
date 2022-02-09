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

package org.apache.flink.runtime.io.network.api.writer;

import org.apache.flink.annotation.Internal;
import org.apache.flink.runtime.checkpoint.RescaleMappings;
import org.apache.flink.runtime.state.KeyGroupRange;
import org.apache.flink.runtime.state.KeyGroupRangeAssignment;
import org.apache.flink.runtime.util.IntArrayList;

import java.util.stream.IntStream;

/**
 * The {@code SubtaskStateMapper} narrows down the subtasks that need to be read during rescaling to
 * recover from a particular subtask when in-flight data has been stored in the checkpoint.
 * {@code SubtaskStateMapper} 缩小了在重新缩放期间需要读取的子任务，以便在检查点中存储飞行数据时从特定子任务中恢复。
 *
 * <p>Mappings of old subtasks to new subtasks may be unique or non-unique. A unique assignment
 * means that a particular old subtask is only assigned to exactly one new subtask. Non-unique
 * assignments require filtering downstream. That means that the receiver side has to cross-verify
 * for a deserialized record if it truly belongs to the new subtask or not. Most {@code
 * SubtaskStateMapper} will only produce unique assignments and are thus optimal. Some rescaler,
 * such as {@link #RANGE}, create a mixture of unique and non-unique mappings, where downstream
 * tasks need to filter on some mapped subtasks.
 * 旧子任务到新子任务的映射可能是唯一的或非唯一的。 唯一分配意味着特定的旧子任务仅分配给一个新子任务。
 * 非唯一分配需要过滤下游。 这意味着接收方必须交叉验证反序列化记录是否真的属于新的子任务。
 * 大多数 {@code SubtaskStateMapper} 只会产生唯一的分配，因此是最优的。
 * 一些重新缩放器，例如 {@link #RANGE}，创建了唯一和非唯一映射的混合，其中下游任务需要过滤一些映射的子任务。
 */
@Internal
public enum SubtaskStateMapper {

    /**
     * Extra state is redistributed to other subtasks without any specific guarantee (only that up-
     * and downstream are matched).
     * 额外的状态在没有任何特定保证的情况下重新分配给其他子任务（只有上游和下游匹配）。
     */
    ARBITRARY {
        @Override
        public int[] getOldSubtasks(
                int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks) {
            // The current implementation uses round robin but that may be changed later.
            return ROUND_ROBIN.getOldSubtasks(
                    newSubtaskIndex, oldNumberOfSubtasks, newNumberOfSubtasks);
        }
    },

    /** Restores extra subtasks to the first subtask.
     * 将额外的子任务恢复到第一个子任务。
     * */
    FIRST {
        @Override
        public int[] getOldSubtasks(
                int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks) {
            return newSubtaskIndex == 0 ? IntStream.range(0, oldNumberOfSubtasks).toArray() : EMPTY;
        }
    },

    /**
     * Replicates the state to all subtasks. This rescaling causes a huge overhead and completely
     * relies on filtering the data downstream.
     * 将状态复制到所有子任务。 这种重新调整会导致巨大的开销，并且完全依赖于过滤下游的数据。
     *
     * <p>This strategy should only be used as a fallback.
     * 此策略应仅用作后备。
     */
    FULL {
        @Override
        public int[] getOldSubtasks(
                int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks) {
            return IntStream.range(0, oldNumberOfSubtasks).toArray();
        }

        @Override
        public boolean isAmbiguous() {
            return true;
        }
    },

    /**
     * Remaps old ranges to new ranges. For minor rescaling that means that new subtasks are mostly
     * assigned 2 old subtasks.
     * 将旧范围重新映射到新范围。 对于较小的重新缩放，这意味着新的子任务主要分配有 2 个旧的子任务。
     *
     * <p>Example:<br>
     * old assignment: 0 -> [0;43); 1 -> [43;87); 2 -> [87;128)<br>
     * new assignment: 0 -> [0;64]; 1 -> [64;128)<br>
     * subtask 0 recovers data from old subtask 0 + 1 and subtask 1 recovers data from old subtask 0
     * + 2
     * 子任务 0 从旧子任务 0 + 1 中恢复数据，子任务 1 从旧子任务 0 + 2 中恢复数据
     *
     * <p>For all downscale from n to [n-1 .. n/2], each new subtasks get exactly two old subtasks
     * assigned.
     * 对于从 n 到 [n-1 .. n/2] 的所有缩减，每个新子任务都分配了两个旧子任务。
     *
     * <p>For all upscale from n to [n+1 .. 2*n-1], most subtasks get two old subtasks assigned,
     * except the two outermost.
     * 对于从 n 到 [n+1 .. 2*n-1] 的所有高档，大多数子任务分配了两个旧子任务，除了最外面的两个。
     *
     * <p>Larger scale factors ({@code <n/2}, {@code >2*n}), will increase the number of old
     * subtasks accordingly. However, they will also create more unique assignment, where an old
     * subtask is exclusively assigned to a new subtask. Thus, the number of non-unique mappings is
     * upper bound by 2*n.
     * 较大的比例因子（{@code <n/2}、{@code >2*n}）将相应地增加旧子任务的数量。
     * 但是，他们还将创建更独特的分配，其中旧的子任务专门分配给新的子任务。 因此，非唯一映射的数量上限为 2*n。
     */
    RANGE {
        @Override
        public int[] getOldSubtasks(
                int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks) {
            // the actual maxParallelism cancels out
            int maxParallelism = KeyGroupRangeAssignment.UPPER_BOUND_MAX_PARALLELISM;
            final KeyGroupRange newRange =
                    KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(
                            maxParallelism, newNumberOfSubtasks, newSubtaskIndex);
            final int start =
                    KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(
                            maxParallelism, oldNumberOfSubtasks, newRange.getStartKeyGroup());
            final int end =
                    KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(
                            maxParallelism, oldNumberOfSubtasks, newRange.getEndKeyGroup());
            return IntStream.range(start, end + 1).toArray();
        }

        @Override
        public boolean isAmbiguous() {
            return true;
        }
    },

    /**
     * Redistributes subtask state in a round robin fashion. Returns a mapping of {@code newIndex ->
     * oldIndexes}. The mapping is accessed by using {@code Bitset oldIndexes =
     * mapping.get(newIndex)}.
     * 以循环方式重新分配子任务状态。 返回 {@code newIndex -> oldIndexes} 的映射。
     * 使用 {@code Bitset oldIndexes = mapping.get(newIndex)} 访问映射。
     *
     * <p>For {@code oldParallelism < newParallelism}, that mapping is trivial. For example if
     * oldParallelism = 6 and newParallelism = 10.
     * 对于 {@code oldParallelism < newParallelism}，该映射是微不足道的。
     * 例如，如果 oldParallelism = 6 和 newParallelism = 10。
     *
     * <table>
     *     <thead><td>New index</td><td>Old indexes</td></thead>
     *     <tr><td>0</td><td>0</td></tr>
     *     <tr><td>1</td><td>1</td></tr>
     *     <tr><td span="2" align="center">...</td></tr>
     *     <tr><td>5</td><td>5</td></tr>
     *     <tr><td>6</td><td></td></tr>
     *     <tr><td span="2" align="center">...</td></tr>
     *     <tr><td>9</td><td></td></tr>
     * </table>
     *
     * <p>For {@code oldParallelism > newParallelism}, new indexes get multiple assignments by
     * wrapping around assignments in a round-robin fashion. For example if oldParallelism = 10 and
     * newParallelism = 4.
     * 对于 {@code oldParallelism > newParallelism}，新索引通过以循环方式环绕分配来获得多个分配。
     * 例如，如果 oldParallelism = 10 和 newParallelism = 4。
     *
     * <table>
     *     <thead><td>New index</td><td>Old indexes</td></thead>
     *     <tr><td>0</td><td>0, 4, 8</td></tr>
     *     <tr><td>1</td><td>1, 5, 9</td></tr>
     *     <tr><td>2</td><td>2, 6</td></tr>
     *     <tr><td>3</td><td>3, 7</td></tr>
     * </table>
     */
    ROUND_ROBIN {
        @Override
        public int[] getOldSubtasks(
                int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks) {
            final IntArrayList subtasks =
                    new IntArrayList(oldNumberOfSubtasks / newNumberOfSubtasks + 1);
            for (int subtask = newSubtaskIndex;
                    subtask < oldNumberOfSubtasks;
                    subtask += newNumberOfSubtasks) {
                subtasks.add(subtask);
            }
            return subtasks.toArray();
        }
    },

    UNSUPPORTED {
        @Override
        public int[] getOldSubtasks(
                int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks) {
            throw new UnsupportedOperationException(
                    "Cannot rescale the given pointwise partitioner.\n"
                            + "Did you change the partitioner to forward or rescale?\n"
                            + "It may also help to add an explicit shuffle().");
        }
    };

    private static final int[] EMPTY = new int[0];

    /**
     * Returns all old subtask indexes that need to be read to restore all buffers for the given new
     * subtask index on rescale.
     * 返回需要读取的所有旧子任务索引，以在重新缩放时恢复给定新子任务索引的所有缓冲区。
     */
    public abstract int[] getOldSubtasks(
            int newSubtaskIndex, int oldNumberOfSubtasks, int newNumberOfSubtasks);

    /** Returns a mapping new subtask index to all old subtask indexes. */
    public RescaleMappings getNewToOldSubtasksMapping(int oldParallelism, int newParallelism) {
        return RescaleMappings.of(
                IntStream.range(0, newParallelism)
                        .mapToObj(
                                channelIndex ->
                                        getOldSubtasks(
                                                channelIndex, oldParallelism, newParallelism)),
                oldParallelism);
    }

    /**
     * Returns true iff this mapper can potentially lead to ambiguous mappings where the different
     * new subtasks map to the same old subtask. The assumption is that such replicated data needs
     * to be filtered.
     * 如果此映射器可能导致不明确的映射，其中不同的新子任务映射到相同的旧子任务，则返回 true。 假设需要过滤此类复制的数据。
     */
    public boolean isAmbiguous() {
        return false;
    }
}
