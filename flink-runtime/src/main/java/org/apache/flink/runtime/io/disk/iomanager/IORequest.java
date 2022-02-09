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

import java.io.IOException;

/**
 * Basic interface that I/O requests that are sent to the threads of the I/O manager need to
 * implement.
 * 发送到 I/O 管理器线程的 I/O 请求需要实现的基本接口。
 */
interface IORequest {

    /**
     * Method that is called by the target I/O thread after the request has been processed.
     * 处理请求后由目标 I/O 线程调用的方法。
     *
     * @param ioex The exception that occurred while processing the I/O request. Is <tt>null</tt> if
     *     everything was fine.
     */
    public void requestDone(IOException ioex);
}

/** Interface for I/O requests that are handled by the IOManager's reading thread.
 * 由 IOManager 的读取线程处理的 I/O 请求的接口。
 * */
interface ReadRequest extends IORequest {

    /**
     * Called by the target I/O thread to perform the actual reading operation.
     * 由目标 I/O 线程调用以执行实际的读取操作。
     *
     * @throws IOException My be thrown by the method to indicate an I/O problem.
     */
    public void read() throws IOException;
}

/** Interface for I/O requests that are handled by the IOManager's writing thread.
 * 由 IOManager 的写入线程处理的 I/O 请求的接口。
 * */
interface WriteRequest extends IORequest {

    /**
     * Called by the target I/O thread to perform the actual writing operation.
     * 由目标 I/O 线程调用以执行实际的写入操作。
     *
     * @throws IOException My be thrown by the method to indicate an I/O problem.
     */
    public void write() throws IOException;
}
