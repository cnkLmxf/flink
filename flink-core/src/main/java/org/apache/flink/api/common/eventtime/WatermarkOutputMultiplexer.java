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

package org.apache.flink.api.common.eventtime;

import org.apache.flink.annotation.Internal;
import org.apache.flink.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkState;

/**
 * A {@link WatermarkOutputMultiplexer} combines the watermark (and idleness) updates of multiple
 * partitions/shards/splits into one combined watermark update and forwards it to an underlying
 * {@link WatermarkOutput}.
 * {@link WatermarkOutputMultiplexer} 将多个分区/分片/拆分的水印（和空闲）更新组合成一个组合水印更新，
 * 并将其转发到底层 {@link WatermarkOutput}。
 *
 * <p>A multiplexed output can either be immediate or deferred. Watermark updates on an immediate
 * output will potentially directly affect the combined watermark state, which will be forwarded to
 * the underlying output immediately. Watermark updates on a deferred output will only update an
 * internal state but not directly update the combined watermark state. Only when {@link
 * #onPeriodicEmit()} is called will the deferred updates be combined and forwarded to the
 * underlying output.
 * 多路复用输出可以是立即的，也可以是延迟的。 即时输出上的水印更新可能会直接影响组合水印状态，该状态将立即转发到底层输出。
 * 延迟输出上的水印更新只会更新内部状态，而不会直接更新组合水印状态。
 * 只有当 {@link #onPeriodicEmit()} 被调用时，延迟更新才会被合并并转发到底层输出。
 *
 * <p>For registering a new multiplexed output, you must first call {@link
 * #registerNewOutput(String)} and then call {@link #getImmediateOutput(String)} or {@link
 * #getDeferredOutput(String)} with the output ID you get from that. You can get both an immediate
 * and deferred output for a given output ID, you can also call the getters multiple times.
 * 要注册一个新的多路复用输出，您必须首先调用 {@link #registerNewOutput(String)}，
 * 然后使用您的输出 ID 调用 {@link #getImmediateOutput(String)} 或
 * {@link #getDeferredOutput(String)} 从中得到。 您可以获得给定输出 ID 的立即输出和延迟输出，
 * 也可以多次调用 getter。
 *
 * <p><b>WARNING:</b>This class is not thread safe.
 * <b>警告：</b>此类不是线程安全的。
 */
@Internal
public class WatermarkOutputMultiplexer {

    /**
     * The {@link WatermarkOutput} that we use to emit our multiplexed watermark updates. We assume
     * that outside code holds a coordinating lock so we don't lock in this class when accessing
     * this {@link WatermarkOutput}.
     * 我们用来发出多路复用水印更新的 {@link WatermarkOutput}。
     * 我们假设外部代码持有一个协调锁，所以我们在访问这个 {@link WatermarkOutput} 时不会锁定这个类。
     */
    private final WatermarkOutput underlyingOutput;

    /** The combined watermark over the per-output watermarks.
     * 每个输出水印上的组合水印。
     * */
    private long combinedWatermark = Long.MIN_VALUE;

    /**
     * Map view, to allow finding them when requesting the {@link WatermarkOutput} for a given id.
     * Map视图，允许在请求给定 ID 的 {@link WatermarkOutput} 时找到它们。
     */
    private final Map<String, OutputState> watermarkPerOutputId;

    /** List of all watermark outputs, for efficient access.
     * 所有水印输出的列表，以便高效访问。
     * */
    private final List<OutputState> watermarkOutputs;

    /**
     * Creates a new {@link WatermarkOutputMultiplexer} that emits combined updates to the given
     * {@link WatermarkOutput}.
     * 创建一个新的 {@link WatermarkOutputMultiplexer}，它向给定的 {@link WatermarkOutput} 发出组合更新。
     */
    public WatermarkOutputMultiplexer(WatermarkOutput underlyingOutput) {
        this.underlyingOutput = underlyingOutput;
        this.watermarkPerOutputId = new HashMap<>();
        this.watermarkOutputs = new ArrayList<>();
    }

    /**
     * Registers a new multiplexed output, which creates internal states for that output and returns
     * an output ID that can be used to get a deferred or immediate {@link WatermarkOutput} for that
     * output.
     * 注册一个新的多路复用输出，它为该输出创建内部状态并返回一个输出 ID，
     * 该 ID 可用于为该输出获取延迟或立即 {@link WatermarkOutput}。
     */
    public void registerNewOutput(String id) {
        final OutputState outputState = new OutputState();

        final OutputState previouslyRegistered = watermarkPerOutputId.putIfAbsent(id, outputState);
        checkState(previouslyRegistered == null, "Already contains an output for ID %s", id);

        watermarkOutputs.add(outputState);
    }

    public boolean unregisterOutput(String id) {
        final OutputState output = watermarkPerOutputId.remove(id);
        if (output != null) {
            watermarkOutputs.remove(output);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns an immediate {@link WatermarkOutput} for the given output ID.
     * 返回给定输出 ID 的即时 {@link WatermarkOutput}。
     *
     * <p>>See {@link WatermarkOutputMultiplexer} for a description of immediate and deferred
     * outputs.
     */
    public WatermarkOutput getImmediateOutput(String outputId) {
        final OutputState outputState = watermarkPerOutputId.get(outputId);
        Preconditions.checkArgument(
                outputState != null, "no output registered under id %s", outputId);
        return new ImmediateOutput(outputState);
    }

    /**
     * Returns a deferred {@link WatermarkOutput} for the given output ID.
     * 为给定的输出 ID 返回延迟的 {@link WatermarkOutput}。
     *
     * <p>>See {@link WatermarkOutputMultiplexer} for a description of immediate and deferred
     * outputs.
     */
    public WatermarkOutput getDeferredOutput(String outputId) {
        final OutputState outputState = watermarkPerOutputId.get(outputId);
        Preconditions.checkArgument(
                outputState != null, "no output registered under id %s", outputId);
        return new DeferredOutput(outputState);
    }

    /**
     * Tells the {@link WatermarkOutputMultiplexer} to combine all outstanding deferred watermark
     * updates and possibly emit a new update to the underlying {@link WatermarkOutput}.
     * 告诉 {@link WatermarkOutputMultiplexer} 组合所有未完成的延迟水印更新，并可能向底层 {@link WatermarkOutput} 发出新更新。
     */
    public void onPeriodicEmit() {
        updateCombinedWatermark();
    }

    /**
     * Checks whether we need to update the combined watermark. Should be called when a newly
     * emitted per-output watermark is higher than the max so far or if we need to combined the
     * deferred per-output updates.
     * 检查我们是否需要更新组合水印。
     * 当新发出的每个输出水印高于迄今为止的最大值或者我们需要合并延迟的每个输出更新时，应该调用。
     */
    private void updateCombinedWatermark() {
        long minimumOverAllOutputs = Long.MAX_VALUE;

        boolean hasOutputs = false;
        boolean allIdle = true;
        for (OutputState outputState : watermarkOutputs) {
            if (!outputState.isIdle()) {
                minimumOverAllOutputs = Math.min(minimumOverAllOutputs, outputState.getWatermark());
                allIdle = false;
            }
            hasOutputs = true;
        }

        // if we don't have any outputs minimumOverAllOutputs is not valid, it's still
        // at its initial Long.MAX_VALUE state and we must not emit that
        // 如果我们没有任何输出 minimumOverAllOutputs 无效，它仍然处于初始 Long.MAX_VALUE 状态，我们不能发出那个
        if (!hasOutputs) {
            return;
        }

        if (allIdle) {
            underlyingOutput.markIdle();
        } else if (minimumOverAllOutputs > combinedWatermark) {
            combinedWatermark = minimumOverAllOutputs;
            underlyingOutput.emitWatermark(new Watermark(minimumOverAllOutputs));
        }
    }

    /** Per-output watermark state.
     * 每个输出水印状态。
     * */
    private static class OutputState {
        private long watermark = Long.MIN_VALUE;
        private boolean idle = false;

        /**
         * Returns the current watermark timestamp. This will throw {@link IllegalStateException} if
         * the output is currently idle.
         * 返回当前水印时间戳。 如果输出当前空闲，这将抛出 {@link IllegalStateException}。
         */
        public long getWatermark() {
            checkState(!idle, "Output is idle.");
            return watermark;
        }

        /**
         * Returns true if the watermark was advanced, that is if the new watermark is larger than
         * the previous one.
         * 如果水印是高级的，即如果新水印大于前一个，则返回 true。
         *
         * <p>Setting a watermark will clear the idleness flag.
         * 设置水印将清除空闲标志。
         */
        public boolean setWatermark(long watermark) {
            this.idle = false;
            final boolean updated = watermark > this.watermark;
            this.watermark = Math.max(watermark, this.watermark);
            return updated;
        }

        public boolean isIdle() {
            return idle;
        }

        public void setIdle(boolean idle) {
            this.idle = idle;
        }
    }

    /**
     * Updating the state of an immediate output can possible lead to a combined watermark update to
     * the underlying {@link WatermarkOutput}.
     * 更新即时输出的状态可能会导致对底层 {@link WatermarkOutput} 的组合水印更新。
     */
    private class ImmediateOutput implements WatermarkOutput {

        private final OutputState state;

        public ImmediateOutput(OutputState state) {
            this.state = state;
        }

        @Override
        public void emitWatermark(Watermark watermark) {
            long timestamp = watermark.getTimestamp();
            boolean wasUpdated = state.setWatermark(timestamp);

            // if it's higher than the max watermark so far we might have to update the
            // combined watermark
            // 如果到目前为止它高于最大水印，我们可能需要更新组合水印
            if (wasUpdated && timestamp > combinedWatermark) {
                updateCombinedWatermark();
            }
        }

        @Override
        public void markIdle() {
            state.setIdle(true);

            // this can always lead to an advancing watermark. We don't know if this output
            // was holding back the watermark or not.
            // 这总是会导致水印前进。 我们不知道这个输出是否阻止了水印。
            updateCombinedWatermark();
        }
    }

    /**
     * Updating the state of a deferred output will never lead to a combined watermark update. Only
     * when {@link WatermarkOutputMultiplexer#onPeriodicEmit()} is called will the deferred updates
     * be combined into a potential combined update of the underlying {@link WatermarkOutput}.
     * 更新延迟输出的状态永远不会导致组合水印更新。
     * 只有当 {@link WatermarkOutputMultiplexer#onPeriodicEmit()} 被调用时，
     * 延迟更新才会被组合成底层 {@link WatermarkOutput} 的潜在组合更新。
     */
    private static class DeferredOutput implements WatermarkOutput {

        private final OutputState state;

        public DeferredOutput(OutputState state) {
            this.state = state;
        }

        @Override
        public void emitWatermark(Watermark watermark) {
            state.setWatermark(watermark.getTimestamp());
        }

        @Override
        public void markIdle() {
            state.setIdle(true);
        }
    }
}
