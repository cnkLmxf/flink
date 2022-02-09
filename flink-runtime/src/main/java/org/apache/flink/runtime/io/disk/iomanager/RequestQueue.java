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

package org.apache.flink.runtime.io.disk.iomanager;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingQueue;

/** A {@link LinkedBlockingQueue} that is extended with closing methods.
 * 使用关闭方法扩展的 {@link LinkedBlockingQueue}。
 * */
public final class RequestQueue<E> extends LinkedBlockingQueue<E> implements Closeable {

    private static final long serialVersionUID = 3804115535778471680L;

    /** Flag marking this queue as closed.
     * 标记将此队列标记为已关闭。
     * */
    private volatile boolean closed = false;

    /**
     * Closes this request queue.
     * 关闭此请求队列。
     *
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() {
        this.closed = true;
    }

    /**
     * Checks whether this request queue is closed.
     * 检查此请求队列是否已关闭。
     *
     * @return True, if the queue is closed, false otherwise.
     */
    public boolean isClosed() {
        return this.closed;
    }
}
