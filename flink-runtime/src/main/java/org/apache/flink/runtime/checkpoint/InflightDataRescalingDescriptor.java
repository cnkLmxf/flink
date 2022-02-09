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

package org.apache.flink.runtime.checkpoint;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Captures ambiguous mappings of old channels to new channels for a particular gate or partition.
 * 捕获特定门或分区的旧通道到新通道的模糊映射。
 *
 * @see InflightDataGateOrPartitionRescalingDescriptor
 */
public class InflightDataRescalingDescriptor implements Serializable {

    public static final InflightDataRescalingDescriptor NO_RESCALE = new NoRescalingDescriptor();

    private static final long serialVersionUID = -3396674344669796295L;

    /** Set when several operator instances are merged into one.
     * 当多个运算符实例合并为一个时设置。
     * */
    private final InflightDataGateOrPartitionRescalingDescriptor[] gateOrPartitionDescriptors;

    public InflightDataRescalingDescriptor(
            InflightDataGateOrPartitionRescalingDescriptor[] gateOrPartitionDescriptors) {
        this.gateOrPartitionDescriptors = checkNotNull(gateOrPartitionDescriptors);
    }

    public int[] getOldSubtaskIndexes(int gateOrPartitionIndex) {
        return gateOrPartitionDescriptors[gateOrPartitionIndex].oldSubtaskIndexes;
    }

    public RescaleMappings getChannelMapping(int gateOrPartitionIndex) {
        return gateOrPartitionDescriptors[gateOrPartitionIndex].rescaledChannelsMappings;
    }

    public boolean isAmbiguous(int gateOrPartitionIndex, int oldSubtaskIndex) {
        return gateOrPartitionDescriptors[gateOrPartitionIndex].ambiguousSubtaskIndexes.contains(
                oldSubtaskIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InflightDataRescalingDescriptor that = (InflightDataRescalingDescriptor) o;
        return Arrays.equals(gateOrPartitionDescriptors, that.gateOrPartitionDescriptors);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(gateOrPartitionDescriptors);
    }

    @Override
    public String toString() {
        return "InflightDataRescalingDescriptor{"
                + "gateOrPartitionDescriptors="
                + Arrays.toString(gateOrPartitionDescriptors)
                + '}';
    }

    /**
     * Captures ambiguous mappings of old channels to new channels.
     * 捕获旧通道到新通道的模糊映射。
     *
     * <p>For inputs, this mapping implies the following:
     * 对于输入，此映射意味着以下内容：
     * <li>
     *     <ul>
     *      {@link #oldSubtaskIndexes} 在此任务重新调整可能导致不同的密钥组时设置。 上游任务有一个相应的 {@link #rescaledChannelsMappings}，它通过虚拟通道发送数据，同时在 VirtualChannelSelector 中指定通道索引。 这个子任务然后在虚拟子任务索引上解复用。
     *   </ul>
     *   <ul>
     *    {@link #rescaledChannelsMappings} 在上游任务缩减时设置。 上游任务有一个对应的 {@link #oldSubtaskIndexes}，它通过虚拟通道发送数据，同时在 VirtualChannelSelector 中指定子任务索引。 然后这个子任务在通道索引上解复用。
     *   </ul>
     *   </li>
     * <li>
     *
     *     <ul>
     *       {@link #oldSubtaskIndexes} is set when there is a rescale on this task potentially
     *       leading to different key groups. Upstream task has a corresponding {@link
     *       #rescaledChannelsMappings} where it sends data over virtual channel while specifying
     *       the channel index in the VirtualChannelSelector. This subtask then demultiplexes over
     *       the virtual subtask index.
     * </ul>
     *
     * <ul>
     *   {@link #rescaledChannelsMappings} is set when there is a downscale of the upstream task.
     *   Upstream task has a corresponding {@link #oldSubtaskIndexes} where it sends data over
     *   virtual channel while specifying the subtask index in the VirtualChannelSelector. This
     *   subtask then demultiplexes over channel indexes.
     * </ul>
     *
     * <p>For outputs, it's vice-versa. The information must be kept in sync but they are used in
     * opposite ways for multiplexing/demultiplexing.
     * 对于输出，反之亦然。 信息必须保持同步，但它们以相反的方式用于复用/解复用。
     *
     * <p>Note that in the common rescaling case both information is set and need to be
     * simultaneously used. If the input subtask subsumes the state of 3 old subtasks and a channel
     * corresponds to 2 old channels, then there are 6 virtual channels to be demultiplexed.
     * 请注意，在常见的重新缩放情况下，两个信息都已设置并且需要同时使用。
     * 如果输入子任务包含 3 个旧子任务的状态，一个通道对应 2 个旧通道，则有 6 个虚拟通道需要解复用。
     */
    public static class InflightDataGateOrPartitionRescalingDescriptor implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Set when several operator instances are merged into one.
         * 当多个运算符实例合并为一个时设置。
         * */
        private final int[] oldSubtaskIndexes;

        /**
         * Set when channels are merged because the connected operator has been rescaled for each
         * gate/partition.
         * 在合并通道时设置，因为已为每个门/分区重新调整了连接的运算符。
         */
        private final RescaleMappings rescaledChannelsMappings;

        /** All channels where upstream duplicates data (only valid for downstream mappings).
         * 上游复制数据的所有通道（仅对下游映射有效）。
         * */
        private final Set<Integer> ambiguousSubtaskIndexes;

        private final MappingType mappingType;

        /** Type of mapping which should be used for this in-flight data.
         * 应用于此飞行中数据的映射类型。
         * */
        public enum MappingType {
            IDENTITY,
            RESCALING
        }

        public InflightDataGateOrPartitionRescalingDescriptor(
                int[] oldSubtaskIndexes,
                RescaleMappings rescaledChannelsMappings,
                Set<Integer> ambiguousSubtaskIndexes,
                MappingType mappingType) {
            this.oldSubtaskIndexes = oldSubtaskIndexes;
            this.rescaledChannelsMappings = rescaledChannelsMappings;
            this.ambiguousSubtaskIndexes = ambiguousSubtaskIndexes;
            this.mappingType = mappingType;
        }

        public boolean isIdentity() {
            return mappingType == MappingType.IDENTITY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InflightDataGateOrPartitionRescalingDescriptor that =
                    (InflightDataGateOrPartitionRescalingDescriptor) o;
            return Arrays.equals(oldSubtaskIndexes, that.oldSubtaskIndexes)
                    && Objects.equals(rescaledChannelsMappings, that.rescaledChannelsMappings)
                    && Objects.equals(ambiguousSubtaskIndexes, that.ambiguousSubtaskIndexes)
                    && mappingType == that.mappingType;
        }

        @Override
        public int hashCode() {
            int result =
                    Objects.hash(rescaledChannelsMappings, ambiguousSubtaskIndexes, mappingType);
            result = 31 * result + Arrays.hashCode(oldSubtaskIndexes);
            return result;
        }

        @Override
        public String toString() {
            return "InflightDataGateOrPartitionRescalingDescriptor{"
                    + "oldSubtaskIndexes="
                    + Arrays.toString(oldSubtaskIndexes)
                    + ", rescaledChannelsMappings="
                    + rescaledChannelsMappings
                    + ", ambiguousSubtaskIndexes="
                    + ambiguousSubtaskIndexes
                    + ", mappingType="
                    + mappingType
                    + '}';
        }
    }

    private static class NoRescalingDescriptor extends InflightDataRescalingDescriptor {
        private static final long serialVersionUID = 1L;

        public NoRescalingDescriptor() {
            super(new InflightDataGateOrPartitionRescalingDescriptor[0]);
        }

        @Override
        public int[] getOldSubtaskIndexes(int gateOrPartitionIndex) {
            return new int[0];
        }

        @Override
        public RescaleMappings getChannelMapping(int gateOrPartitionIndex) {
            return RescaleMappings.SYMMETRIC_IDENTITY;
        }

        @Override
        public boolean isAmbiguous(int gateOrPartitionIndex, int oldSubtaskIndex) {
            return false;
        }

        private Object readResolve() throws ObjectStreamException {
            return NO_RESCALE;
        }
    }
}
