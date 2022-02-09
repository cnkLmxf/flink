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

package org.apache.flink.api.common.operators;

import org.apache.flink.annotation.Public;

/**
 * Enumeration representing order. May represent no order, an ascending order or a descending order.
 * 表示顺序的枚举。 可以表示无顺序、升序或降序。
 */
@Public
public enum Order {

    /** Indicates no order. */
    NONE,

    /** Indicates an ascending order. */
    ASCENDING,

    /** Indicates a descending order. */
    DESCENDING,

    /**
     * Indicates an order without a direction. This constant is not used to indicate any existing
     * order, but for example to indicate that an order of any direction is desirable.
     * 表示没有方向的订单。 该常数不用于指示任何现有顺序，而是例如指示任何方向的顺序是可取的。
     */
    ANY;

    // --------------------------------------------------------------------------------------------

    /**
     * Checks, if this enum constant represents in fact an order. That is, whether this property is
     * not equal to <tt>Order.NONE</tt>.
     * 检查此枚举常量是否实际上代表一个订单。 即该属性是否不等于<tt>Order.NONE</tt>。
     *
     * @return True, if this enum constant is unequal to <tt>Order.NONE</tt>, false otherwise.
     */
    public boolean isOrdered() {
        return this != Order.NONE;
    }

    public String getShortName() {
        return this == ASCENDING ? "ASC" : this == DESCENDING ? "DESC" : this == ANY ? "*" : "-";
    }
}
