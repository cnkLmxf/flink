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

package org.apache.flink.runtime.state;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import java.util.Optional;

/**
 * Transformer of state values which are included or skipped in the snapshot.
 * 快照中包含或跳过的状态值转换器。
 *
 * <p>This transformer can be applied to state values to decide which entries should be included
 * into the snapshot. The included entries can be optionally modified before.
 * 这个转换器可以应用于状态值来决定哪些条目应该包含在快照中。 包含的条目之前可以有选择地修改。
 *
 * <p>Unless specified differently, the transformer should be applied per entry for collection types
 * of state, like list or map.
 * 除非另有说明，否则应该为状态集合类型（如列表或映射）的每个条目应用转换器。
 *
 * @param <T> type of state
 */
@FunctionalInterface
@NotThreadSafe
public interface StateSnapshotTransformer<T> {
    /**
     * Transform or filter out state values which are included or skipped in the snapshot.
     * 转换或过滤掉快照中包含或跳过的状态值。
     *
     * @param value non-serialized form of value
     * @return value to snapshot or null which means the entry is not included
     */
    @Nullable
    T filterOrTransform(@Nullable T value);

    /**
     * Collection state specific transformer which says how to transform entries of the collection.
     * 集合状态特定转换器，说明如何转换集合的条目。
     */
    interface CollectionStateSnapshotTransformer<T> extends StateSnapshotTransformer<T> {
        enum TransformStrategy {
            /** Transform all entries. */
            TRANSFORM_ALL,

            /**
             * Skip first null entries.
             *
             * <p>While traversing collection entries, as optimisation, stops transforming if
             * encounters first non-null included entry and returns it plus the rest untouched.
             * 在遍历集合条目时，作为优化，如果遇到第一个非空包含条目，则停止转换并返回它加上其余未触及的条目。
             */
            STOP_ON_FIRST_INCLUDED
        }

        default TransformStrategy getFilterStrategy() {
            return TransformStrategy.TRANSFORM_ALL;
        }
    }

    /**
     * This factory creates state transformers depending on the form of values to transform.
     * 该工厂根据要转换的值的形式创建状态转换器。
     *
     * <p>If there is no transforming needed, the factory methods return {@code Optional.empty()}.
     * 如果不需要转换，工厂方法返回 {@code Optional.empty()}。
     */
    interface StateSnapshotTransformFactory<T> {
        StateSnapshotTransformFactory<?> NO_TRANSFORM = createNoTransform();

        @SuppressWarnings("unchecked")
        static <T> StateSnapshotTransformFactory<T> noTransform() {
            return (StateSnapshotTransformFactory<T>) NO_TRANSFORM;
        }

        static <T> StateSnapshotTransformFactory<T> createNoTransform() {
            return new StateSnapshotTransformFactory<T>() {
                @Override
                public Optional<StateSnapshotTransformer<T>> createForDeserializedState() {
                    return Optional.empty();
                }

                @Override
                public Optional<StateSnapshotTransformer<byte[]>> createForSerializedState() {
                    return Optional.empty();
                }
            };
        }

        Optional<StateSnapshotTransformer<T>> createForDeserializedState();

        Optional<StateSnapshotTransformer<byte[]>> createForSerializedState();
    }
}
