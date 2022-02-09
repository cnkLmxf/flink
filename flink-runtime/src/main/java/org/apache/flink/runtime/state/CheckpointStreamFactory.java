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

import org.apache.flink.core.fs.FSDataOutputStream;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A factory for checkpoint output streams, which are used to persist data for checkpoints.
 * 检查点输出流的工厂，用于保存检查点的数据。
 *
 * <p>Stream factories can be created from the {@link CheckpointStorageAccess} through {@link
 * CheckpointStorageAccess#resolveCheckpointStorageLocation(long,
 * CheckpointStorageLocationReference)}.
 * 流工厂可以从 {@link CheckpointStorageAccess} 通过
 * {@link CheckpointStorageAccess#resolveCheckpointStorageLocation(long, CheckpointStorageLocationReference)} 创建。
 */
public interface CheckpointStreamFactory {

    /**
     * Creates an new {@link CheckpointStateOutputStream}. When the stream is closed, it returns a
     * state handle that can retrieve the state back.
     * 创建一个新的 {@link CheckpointStateOutputStream}。 当流关闭时，它返回一个可以检索状态的状态句柄。
     *
     * @param scope The state's scope, whether it is exclusive or shared.
     * @return An output stream that writes state for the given checkpoint.
     * @throws IOException Exceptions may occur while creating the stream and should be forwarded.
     */
    CheckpointStateOutputStream createCheckpointStateOutputStream(CheckpointedStateScope scope)
            throws IOException;

    /**
     * A dedicated output stream that produces a {@link StreamStateHandle} when closed.
     * 关闭时生成 {@link StreamStateHandle} 的专用输出流。
     *
     * <p><b>Important:</b> When closing this stream after the successful case, you must call {@link
     * #closeAndGetHandle()} - only that method will actually retain the resource written to. The
     * method has the semantics of "close on success". The {@link #close()} method is supposed to
     * remove the target resource if called before {@link #closeAndGetHandle()}, hence having the
     * semantics of "close on failure". That way, simple try-with-resources statements automatically
     * clean up unsuccessful partial state resources in case the writing does not complete.
     * <b>重要提示：</b> 在成功case关闭此流时，您必须调用 {@link #closeAndGetHandle()} - 只有该方法才会真正保留写入的资源。
     * 该方法具有“接近成功”的语义。 如果在 {@link #closeAndGetHandle()} 之前调用，
     * {@link #close()} 方法应该删除目标资源，因此具有“失败时关闭”的语义。
     * 这样，简单的 try-with-resources 语句会在写入未完成的情况下自动清理不成功的部分状态资源。
     *
     * <p>Note: This is an abstract class and not an interface because {@link OutputStream} is an
     * abstract class.
     * 注意：这是一个抽象类而不是一个接口，因为 {@link OutputStream} 是一个抽象类。
     */
    abstract class CheckpointStateOutputStream extends FSDataOutputStream {

        /**
         * Closes the stream and gets a state handle that can create an input stream producing the
         * data written to this stream.
         * 关闭流并获取一个状态句柄，该句柄可以创建一个输入流，产生写入该流的数据。
         *
         * <p>This closing must be called (also when the caller is not interested in the handle) to
         * successfully close the stream and retain the produced resource. In contrast, the {@link
         * #close()} method removes the target resource when called.
         * 必须调用此关闭（当调用者对句柄不感兴趣时）才能成功关闭流并保留生成的资源。
         * 相比之下，{@link #close()} 方法在调用时删除目标资源。
         *
         * @return A state handle that can create an input stream producing the data written to this
         *     stream.
         * @throws IOException Thrown, if the stream cannot be closed.
         */
        @Nullable
        public abstract StreamStateHandle closeAndGetHandle() throws IOException;

        /**
         * This method should close the stream, if has not been closed before. If this method
         * actually closes the stream, it should delete/release the resource behind the stream, such
         * as the file that the stream writes to.
         * 这个方法应该关闭流，如果之前没有关闭。 如果这个方法真的关闭了流，它应该删除/释放流后面的资源，比如流写入的文件。
         *
         * <p>The above implies that this method is intended to be the "unsuccessful close", such as
         * when cancelling the stream writing, or when an exception occurs. Closing the stream for
         * the successful case must go through {@link #closeAndGetHandle()}.
         * 上面暗示这个方法是为了“不成功的关闭”，比如取消流写入的时候，或者发生异常的时候。
         * 关闭成功案例的流必须通过 {@link #closeAndGetHandle()}。
         *
         * @throws IOException Thrown, if the stream cannot be closed.
         */
        @Override
        public abstract void close() throws IOException;
    }
}
