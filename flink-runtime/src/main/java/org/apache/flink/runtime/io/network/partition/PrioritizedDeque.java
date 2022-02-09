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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.annotation.Internal;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A deque-like data structure that supports prioritization of elements, such they will be polled
 * before any non-priority elements.
 * 支持元素优先级的类似双端队列的数据结构，这样它们将在任何非优先级元素之前被轮询。
 *
 * <p>{@implNote The current implementation deliberately does not implement the respective interface
 * to minimize the maintenance effort. Furthermore, it's optimized for handling non-priority
 * elements, such that all operations for adding priority elements are much slower than the
 * non-priority counter-parts.}
 * {@implNote 目前的实现故意不实现各自的接口，以尽量减少维护工作。
 * 此外，它针对处理非优先级元素进行了优化，因此添加优先级元素的所有操作都比非优先级对应部分慢得多。}
 *
 * <p>Note that all element tests are performed by identity.
 * 请注意，所有元素测试都是按身份执行的。
 *
 * @param <T> the element type.
 */
@Internal
public final class PrioritizedDeque<T> implements Iterable<T> {
    private final Deque<T> deque = new ArrayDeque<>();
    private int numPriorityElements;

    /**
     * Adds a priority element to this deque, such that it will be polled after all existing
     * priority elements but before any non-priority element.
     * 向此双端队列添加优先级元素，以便在所有现有优先级元素之后但在任何非优先级元素之前对其进行轮询。
     *
     * @param element the element to add
     */
    public void addPriorityElement(T element) {
        // priority elements are rather rare and short-lived, so most of there are none
        if (numPriorityElements == 0) {
            deque.addFirst(element);
        } else if (numPriorityElements == deque.size()) {
            // no non-priority elements
            deque.add(element);
        } else {
            // remove all priority elements
            final ArrayDeque<T> priorPriority = new ArrayDeque<>(numPriorityElements);
            for (int index = 0; index < numPriorityElements; index++) {
                priorPriority.addFirst(deque.poll());
            }
            deque.addFirst(element);
            // readd them before the newly added element
            for (final T priorityEvent : priorPriority) {
                deque.addFirst(priorityEvent);
            }
        }
        numPriorityElements++;
    }

    /**
     * Adds a non-priority element to this deque, which will be polled last.
     * 向此双端队列添加一个非优先级元素，该元素将最后轮询。
     *
     * @param element the element to add
     */
    public void add(T element) {
        deque.add(element);
    }

    /**
     * Convenience method for adding an element with optional priority and prior removal.
     * 添加具有可选优先级和优先删除的元素的便捷方法。
     *
     * @param element the element to add
     * @param priority flag indicating if it's a priority or non-priority element
     * @param prioritize flag that hints that the element is already in this deque, potentially as
     *     non-priority element.
     */
    public void add(T element, boolean priority, boolean prioritize) {
        if (!priority) {
            add(element);
        } else {
            if (prioritize) {
                prioritize(element);
            } else {
                addPriorityElement(element);
            }
        }
    }

    /**
     * Prioritizes an already existing element. Note that this method assumes identity.
     * 优先考虑已经存在的元素。 请注意，此方法假定身份。
     *
     * <p>{@implNote Since this method removes the element and reinserts it in a priority position
     * in general, some optimizations for special cases are used.}
     * {@implNote 由于此方法通常会删除元素并将其重新插入优先级位置，因此使用了一些针对特殊情况的优化。}
     *
     * @param element the element to prioritize.
     */
    public void prioritize(T element) {
        final Iterator<T> iterator = deque.iterator();
        // Already prioritized? Then, do not reorder elements.
        for (int i = 0; i < numPriorityElements && iterator.hasNext(); i++) {
            if (iterator.next() == element) {
                return;
            }
        }
        // If the next non-priority element is the given element, we can simply include it in the
        // priority section
        if (iterator.hasNext() && iterator.next() == element) {
            numPriorityElements++;
            return;
        }
        // Remove the given element.
        while (iterator.hasNext()) {
            if (iterator.next() == element) {
                iterator.remove();
                break;
            }
        }
        addPriorityElement(element);
    }

    /** Returns an unmodifiable collection view.
     * 返回一个不可修改的集合视图。
     * */
    public Collection<T> asUnmodifiableCollection() {
        return Collections.unmodifiableCollection(deque);
    }

    /**
     * Find first element matching the {@link Predicate}, remove it from the {@link
     * PrioritizedDeque} and return it.
     * 找到与 {@link Predicate} 匹配的第一个元素，将其从 {@link PrioritizedDeque} 中删除并返回。
     *
     * @return removed element
     */
    public T getAndRemove(Predicate<T> preCondition) {
        Iterator<T> iterator = deque.iterator();
        for (int i = 0; iterator.hasNext(); i++) {
            T next = iterator.next();
            if (preCondition.test(next)) {
                if (i < numPriorityElements) {
                    numPriorityElements--;
                }
                iterator.remove();
                return next;
            }
        }
        throw new NoSuchElementException();
    }

    /**
     * Polls the first priority element or non-priority element if the former does not exist.
     * 如果前者不存在，则轮询第一个优先级元素或非优先级元素。
     *
     * @return the first element or null.
     */
    @Nullable
    public T poll() {
        final T polled = deque.poll();
        if (polled != null && numPriorityElements > 0) {
            numPriorityElements--;
        }
        return polled;
    }

    /**
     * Returns the first priority element or non-priority element if the former does not exist.
     * 如果前者不存在，则返回第一个优先级元素或非优先级元素。
     *
     * @return the first element or null.
     */
    @Nullable
    public T peek() {
        return deque.peek();
    }

    /** Returns the current number of priority elements ([0; {@link #size()}]).
     * 返回当前优先级元素的数量 ([0; {@link #size()}])。
     * */
    public int getNumPriorityElements() {
        return numPriorityElements;
    }

    /**
     * Returns whether the given element is a known priority element. Test is performed by identity.
     * 返回给定元素是否是已知优先级元素。 测试是通过身份进行的。
     */
    public boolean containsPriorityElement(T element) {
        if (numPriorityElements == 0) {
            return false;
        }
        final Iterator<T> iterator = deque.iterator();
        for (int i = 0; i < numPriorityElements && iterator.hasNext(); i++) {
            if (iterator.next() == element) {
                return true;
            }
        }
        return false;
    }

    /** Returns the number of priority and non-priority elements.
     * 返回优先级和非优先级元素的数量。
     * */
    public int size() {
        return deque.size();
    }

    /** Returns the number of non-priority elements.
     * 返回非优先元素的数量。
     * */
    public int getNumUnprioritizedElements() {
        return size() - getNumPriorityElements();
    }

    /** @return read-only iterator */
    public Iterator<T> iterator() {
        return Collections.unmodifiableCollection(deque).iterator();
    }

    /** Removes all priority and non-priority elements. */
    public void clear() {
        deque.clear();
        numPriorityElements = 0;
    }

    /** Returns true if there are no elements. */
    public boolean isEmpty() {
        return deque.isEmpty();
    }

    /**
     * Returns whether the given element is contained in this list. Test is performed by identity.
     * 返回给定元素是否包含在此列表中。 测试是通过身份进行的。
     */
    public boolean contains(T element) {
        if (deque.isEmpty()) {
            return false;
        }
        final Iterator<T> iterator = deque.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == element) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PrioritizedDeque<?> that = (PrioritizedDeque<?>) o;
        return numPriorityElements == that.numPriorityElements && deque.equals(that.deque);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deque, numPriorityElements);
    }

    @Override
    public String toString() {
        return deque.toString();
    }

    /**
     * Returns the last non-priority element or priority element if the former does not exist.
     * 如果前者不存在，则返回最后一个非优先级元素或优先级元素。
     *
     * @return the last element or null.
     */
    @Nullable
    public T peekLast() {
        return deque.peekLast();
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
