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

package org.apache.flink.api.common.io;

import org.apache.flink.annotation.Public;

import java.io.IOException;

/**
 * This interface may be implemented by {@link OutputFormat}s to have the master finalize them
 * globally.
 * 这个接口可以由 {@link OutputFormat} 实现，让主控全局完成它们。
 */
@Public
public interface FinalizeOnMaster {

    /**
     * The method is invoked on the master (JobManager) after all (parallel) instances of an
     * OutputFormat finished.
     * 在 OutputFormat 的所有（并行）实例完成后，在主 (JobManager) 上调用该方法。
     *
     * @param parallelism The parallelism with which the format or functions was run.
     * @throws IOException The finalization may throw exceptions, which may cause the job to abort.
     */
    void finalizeGlobal(int parallelism) throws IOException;
}
