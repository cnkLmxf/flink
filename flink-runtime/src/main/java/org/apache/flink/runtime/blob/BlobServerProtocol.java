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

package org.apache.flink.runtime.blob;

/**
 * Defines constants for the protocol between the BLOB {@link BlobServer server} and the {@link
 * AbstractBlobCache caches}.
 * 为 BLOB {@link BlobServer 服务器} 和 {@link AbstractBlobCache 缓存} 之间的协议定义常量。
 */
public class BlobServerProtocol {

    // --------------------------------------------------------------------------------------------
    //  Constants used in the protocol of the BLOB store
    // --------------------------------------------------------------------------------------------

    /** The buffer size in bytes for network transfers.
     * 网络传输的缓冲区大小（以字节为单位）。
     * */
    static final int BUFFER_SIZE = 65536; // 64 K

    /**
     * Internal code to identify a PUT operation.
     * 用于识别 PUT 操作的内部代码。
     *
     * <p>Note: previously, there was also <tt>DELETE_OPERATION</tt> (code <tt>2</tt>).
     * 注意：之前还有 <tt>DELETE_OPERATION</tt>（代码 <tt>2</tt>）。
     */
    static final byte PUT_OPERATION = 0;

    /**
     * Internal code to identify a GET operation.
     *
     * <p>Note: previously, there was also <tt>DELETE_OPERATION</tt> (code <tt>2</tt>).
     */
    static final byte GET_OPERATION = 1;

    /** Internal code to identify a successful operation. */
    static final byte RETURN_OKAY = 0;

    /** Internal code to identify an erroneous operation. */
    static final byte RETURN_ERROR = 1;

    /**
     * Internal code to identify a job-unrelated BLOBs (only for transient BLOBs!).
     * 用于识别与作业无关的 BLOB 的内部代码（仅适用于瞬态 BLOB！）。
     *
     * <p>Note: previously, there was also <tt>NAME_ADDRESSABLE</tt> (code <tt>1</tt>).
     */
    static final byte JOB_UNRELATED_CONTENT = 0;

    /**
     * Internal code to identify a job-related (permanent or transient) BLOBs.
     * 用于标识作业相关（永久或临时）BLOB 的内部代码。
     *
     * <p>Note: This is equal to the previous <tt>JOB_ID_SCOPE</tt> (code <tt>2</tt>).
     */
    static final byte JOB_RELATED_CONTENT = 2;

    // --------------------------------------------------------------------------------------------

    private BlobServerProtocol() {}
}
