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

package org.apache.flink.util;

import org.apache.flink.annotation.Internal;

/**
 * This interface marks types as visitable during a traversal. The central method <i>accept(...)</i>
 * contains the logic about how to invoke the supplied {@link Visitor} on the visitable object, and
 * how to traverse further.
 * 此接口在遍历期间将类型标记为可访问。
 * 中心方法 <i>accept(...)</i> 包含有关如何在可访问对象上调用提供的 {@link Visitor} 以及如何进一步遍历的逻辑。
 *
 * <p>This concept makes it easy to implement for example a depth-first traversal of a tree or DAG
 * with different types of logic during the traversal. The <i>accept(...)</i> method calls the
 * visitor and then send the visitor to its children (or predecessors). Using different types of
 * visitors, different operations can be performed during the traversal, while writing the actual
 * traversal code only once.
 * 这个概念可以很容易地实现，例如在遍历期间使用不同类型的逻辑对树或 DAG 进行深度优先遍历。
 * <i>accept(...)</i> 方法调用访问者，然后将访问者发送给它的孩子（或前辈）。
 * 使用不同类型的访问者，在遍历过程中可以进行不同的操作，而真正的遍历代码只需编写一次。
 *
 * @see Visitor
 */
@Internal
public interface Visitable<T extends Visitable<T>> {

    /**
     * Contains the logic to invoke the visitor and continue the traversal. Typically invokes the
     * pre-visit method of the visitor, then sends the visitor to the children (or predecessors) and
     * then invokes the post-visit method.
     * 包含调用访问者并继续遍历的逻辑。 通常调用访问者的 pre-visit 方法，然后将访问者发送给孩子（或前辈），
     * 然后调用 post-visit 方法。
     *
     * <p>A typical code example is the following:
     *
     * <pre>{@code
     * public void accept(Visitor<Operator> visitor) {
     *     boolean descend = visitor.preVisit(this);
     *     if (descend) {
     *         if (this.input != null) {
     *             this.input.accept(visitor);
     *         }
     *         visitor.postVisit(this);
     *     }
     * }
     * }</pre>
     *
     * @param visitor The visitor to be called with this object as the parameter.
     * @see Visitor#preVisit(Visitable)
     * @see Visitor#postVisit(Visitable)
     */
    void accept(Visitor<T> visitor);
}
