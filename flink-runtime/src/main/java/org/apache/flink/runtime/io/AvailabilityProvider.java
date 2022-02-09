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

package org.apache.flink.runtime.io;

import org.apache.flink.annotation.Internal;

import java.util.concurrent.CompletableFuture;

/**
 * Interface defining couple of essential methods for listening on data availability using {@link
 * CompletableFuture}. For usage check out for example {@link PullingAsyncDataInput}.
 * 接口定义了几个使用 {@link CompletableFuture} 监听数据可用性的基本方法。
 * 有关用法，请查看 {@link PullingAsyncDataInput}。
 */
@Internal
public interface AvailabilityProvider {
    /**
     * Constant that allows to avoid volatile checks {@link CompletableFuture#isDone()}. Check
     * {@link #isAvailable()} and {@link #isApproximatelyAvailable()} for more explanation.
     * 允许避免易失性检查的常量 {@link CompletableFuture#isDone()}。
     * 检查 {@link #isAvailable()} 和 {@link #isApproximatelyAvailable()} 以获得更多解释。
     */
    CompletableFuture<?> AVAILABLE = CompletableFuture.completedFuture(null);

    /** @return a future that is completed if the respective provider is available.
     * 如果相应的提供者可用，则完成的未来。
     * */
    CompletableFuture<?> getAvailableFuture();

    /**
     * In order to best-effort avoid volatile access in {@link CompletableFuture#isDone()}, we check
     * the condition of <code>future == AVAILABLE</code> firstly for getting probable performance
     * benefits while hot looping.
     * 为了尽量避免 {@link CompletableFuture#isDone()} 中的易失性访问，
     * 我们首先检查 <code>future == AVAILABLE</code> 的条件，以便在热循环时获得可能的性能优势。
     *
     * <p>It is always safe to use this method in performance nonsensitive scenarios to get the
     * precise state.
     * 在性能不敏感的场景中使用这种方法来获得精确的状态总是安全的。
     *
     * @return true if this instance is available for further processing.
     */
    default boolean isAvailable() {
        CompletableFuture<?> future = getAvailableFuture();
        return future == AVAILABLE || future.isDone();
    }

    /**
     * Checks whether this instance is available only via constant {@link #AVAILABLE} to avoid
     * performance concern caused by volatile access in {@link CompletableFuture#isDone()}. So it is
     * mainly used in the performance sensitive scenarios which do not always need the precise
     * state.
     * 检查此实例是否仅通过常量 {@link #AVAILABLE} 可用，以避免由 {@link CompletableFuture#isDone()}
     * 中的易失性访问引起的性能问题。 所以它主要用于性能敏感的场景，并不总是需要精确的状态。
     *
     * <p>This method is still safe to get the precise state if {@link #getAvailableFuture()} was
     * touched via (.get(), .wait(), .isDone(), ...) before, which also has a "happen-before"
     * relationship with this call.
     * 如果之前通过 (.get(), .wait(), .isDone(), ...) 触摸了 {@link #getAvailableFuture()}，
     * 此方法仍然可以安全地获得精确状态，这也有一个“发生” -before”与此调用的关系。
     *
     * @return true if this instance is available for further processing.
     */
    default boolean isApproximatelyAvailable() {
        return getAvailableFuture() == AVAILABLE;
    }

    static CompletableFuture<?> and(CompletableFuture<?> first, CompletableFuture<?> second) {
        if (first == AVAILABLE && second == AVAILABLE) {
            return AVAILABLE;
        } else if (first == AVAILABLE) {
            return second;
        } else if (second == AVAILABLE) {
            return first;
        } else {
            return CompletableFuture.allOf(first, second);
        }
    }

    static CompletableFuture<?> or(CompletableFuture<?> first, CompletableFuture<?> second) {
        if (first == AVAILABLE || second == AVAILABLE) {
            return AVAILABLE;
        }
        return CompletableFuture.anyOf(first, second);
    }

    /**
     * A availability implementation for providing the helpful functions of resetting the
     * available/unavailable states.
     * 一种可用性实现，用于提供重置可用/不可用状态的有用功能。
     */
    final class AvailabilityHelper implements AvailabilityProvider {

        private CompletableFuture<?> availableFuture = new CompletableFuture<>();

        public CompletableFuture<?> and(CompletableFuture<?> other) {
            return AvailabilityProvider.and(availableFuture, other);
        }

        public CompletableFuture<?> and(AvailabilityProvider other) {
            return and(other.getAvailableFuture());
        }

        public CompletableFuture<?> or(CompletableFuture<?> other) {
            return AvailabilityProvider.or(availableFuture, other);
        }

        public CompletableFuture<?> or(AvailabilityProvider other) {
            return or(other.getAvailableFuture());
        }

        /** Judges to reset the current available state as unavailable.
         * 判断将当前可用状态重置为不可用。
         * */
        public void resetUnavailable() {
            if (isAvailable()) {
                availableFuture = new CompletableFuture<>();
            }
        }

        /** Resets the constant completed {@link #AVAILABLE} as the current state.
         * 将已完成的常量 {@link #AVAILABLE} 重置为当前状态。
         * */
        public void resetAvailable() {
            availableFuture = AVAILABLE;
        }

        /**
         * Returns the previously not completed future and resets the constant completed {@link
         * #AVAILABLE} as the current state.
         * 返回先前未完成的未来并将常量完成 {@link #AVAILABLE} 重置为当前状态。
         */
        public CompletableFuture<?> getUnavailableToResetAvailable() {
            CompletableFuture<?> toNotify = availableFuture;
            availableFuture = AVAILABLE;
            return toNotify;
        }

        /**
         * Creates a new uncompleted future as the current state and returns the previous
         * uncompleted one.
         * 创建一个新的未完成的未来作为当前状态并返回前一个未完成的未来。
         */
        public CompletableFuture<?> getUnavailableToResetUnavailable() {
            CompletableFuture<?> toNotify = availableFuture;
            availableFuture = new CompletableFuture<>();
            return toNotify;
        }

        /** @return a future that is completed if the respective provider is available.
         * 如果相应的提供者可用，则完成的future。
         * */
        @Override
        public CompletableFuture<?> getAvailableFuture() {
            return availableFuture;
        }

        @Override
        public String toString() {
            if (availableFuture == AVAILABLE) {
                return "AVAILABLE";
            }
            return availableFuture.toString();
        }
    }
}
