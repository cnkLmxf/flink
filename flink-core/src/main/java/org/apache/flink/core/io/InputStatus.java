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

package org.apache.flink.core.io;

import org.apache.flink.annotation.PublicEvolving;

/**
 * An {@code InputStatus} indicates the availability of data from an asynchronous input. When asking
 * an asynchronous input to produce data, it returns this status to indicate how to proceed.
 * {@code InputStatus} 指示来自异步输入的数据的可用性。 当要求异步输入生成数据时，它会返回此状态以指示如何继续。
 *
 * <p>When the input returns {@link InputStatus#NOTHING_AVAILABLE} it means that no data is
 * available at this time, but more will (most likely) be available in the future. The asynchronous
 * input will typically offer to register a <i>Notifier</i> or to obtain a <i>Future</i> that will
 * signal the availability of new data.
 * 当输入返回 {@link InputStatus#NOTHING_AVAILABLE} 时，这意味着此时没有可用的数据，但将来（很可能）会有更多可用的数据。
 * 异步输入通常会提供注册一个 <i>Notifier</i> 或获取一个 <i>Future</i> 来表示新数据的可用性。
 *
 * <p>When the input returns {@link InputStatus#MORE_AVAILABLE}, it can be immediately asked again
 * to produce more data. That readers from the asynchronous input can bypass subscribing to a
 * Notifier or a Future for efficiency.
 * 当输入返回 {@link InputStatus#MORE_AVAILABLE} 时，可以立即再次要求它产生更多数据。
 * 来自异步输入的读者可以绕过订阅通知程序或未来以提高效率。
 *
 * <p>When the input returns {@link InputStatus#END_OF_INPUT}, then no data will be available again
 * from this input. It has reached the end of its bounded data.
 * 当输入返回 {@link InputStatus#END_OF_INPUT} 时，该输入将不再提供任何数据。 它已到达有界数据的末尾。
 */
@PublicEvolving
public enum InputStatus {

    /**
     * Indicator that more data is available and the input can be called immediately again to
     * produce more data.
     * 指示更多数据可用并且可以立即再次调用输入以生成更多数据。
     */
    MORE_AVAILABLE,

    /**
     * Indicator that no data is currently available, but more data will be available in the future
     * again.
     * 表示目前没有数据可用，但将来会再次提供更多数据。
     */
    NOTHING_AVAILABLE,

    /** Indicator that all persisted data of the data exchange has been successfully restored.
     * 数据交换的所有持久化数据已成功恢复的指示。
     * */
    END_OF_RECOVERY,

    /** Indicator that the input has reached the end of data.
     * 指示输入已到达数据末尾。
     * */
    END_OF_INPUT
}
