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

package org.apache.flink.runtime.iterative.concurrent;

import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.runtime.iterative.io.SerializedUpdateBuffer;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A concurrent datastructure that establishes a backchannel buffer between an iteration head and an
 * iteration tail.
 * 在迭代头和迭代尾之间建立反向通道缓冲区的并发数据结构。
 */
public class BlockingBackChannel {

    /** Buffer to send back the superstep results.
     * 缓冲区以发送回超步结果。
     * */
    private final SerializedUpdateBuffer buffer;

    /** A one element queue used for blocking hand over of the buffer.
     * 用于阻止缓冲区移交的单元素队列。
     * */
    private final BlockingQueue<SerializedUpdateBuffer> queue;

    public BlockingBackChannel(SerializedUpdateBuffer buffer) {
        this.buffer = buffer;
        queue = new ArrayBlockingQueue<SerializedUpdateBuffer>(1);
    }

    /**
     * Called by iteration head after it has sent all input for the current superstep through the
     * data channel (blocks iteration head).
     * 在通过数据通道（块迭代头）发送当前超级步的所有输入后由迭代头调用。
     */
    public DataInputView getReadEndAfterSuperstepEnded() {
        try {
            return queue.take().switchBuffers();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Called by iteration tail to save the output of the current superstep.
     * 由迭代尾部调用以保存当前超级步的输出。
     * */
    public DataOutputView getWriteEnd() {
        return buffer;
    }

    /**
     * Called by iteration tail to signal that all input of a superstep has been processed (unblocks
     * iteration head).
     * 由迭代尾部调用以表示超级步的所有输入都已被处理（解除阻塞迭代头部）。
     */
    public void notifyOfEndOfSuperstep() {
        queue.offer(buffer);
    }
}
