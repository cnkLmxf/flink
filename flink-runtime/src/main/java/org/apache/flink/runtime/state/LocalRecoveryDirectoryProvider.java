/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.state;

import java.io.File;
import java.io.Serializable;

/**
 * Provides directories for local recovery. It offers access to the allocation base directories
 * (i.e. the root directories for all local state that is created under the same allocation id) and
 * the subtask-specific paths, which contain the local state for one subtask. Access by checkpoint
 * id rotates over all root directory indexes, in case that there is more than one. Selection
 * methods are provided to pick the directory under a certain index. Directory structures are of the
 * following shape:
 * 提供用于本地恢复的目录。 它提供对分配基目录（即在同一分配 ID 下创建的所有本地状态的根目录）和子任务特定路径的访问，
 * 其中包含一个子任务的本地状态。 检查点 id 的访问会在所有根目录索引上循环，以防有多个。
 * 提供了选择方法来选择某个索引下的目录。 目录结构具有以下形状：
 *
 * <p>
 *
 * <blockquote>
 *
 * <pre>
 * |-----allocationBaseDirectory------|
 * |-----subtaskBaseDirectory--------------------------------------|
 * |-----subtaskSpecificCheckpointDirectory------------------------------|
 *
 * ../local_state_root_1/allocation_id/job_id/vertex_id_subtask_idx/chk_1/(state)
 * ../local_state_root_2/allocation_id/job_id/vertex_id_subtask_idx/chk_2/(state)
 *
 * (...)
 * </pre>
 *
 * </blockquote>
 *
 * <p>
 */
public interface LocalRecoveryDirectoryProvider extends Serializable {

    /**
     * Returns the local state allocation base directory for given checkpoint id w.r.t. our rotation
     * over all available allocation base directories.
     * 返回给定检查点 ID w.r.t 的本地状态分配基目录。 我们对所有可用的分配基目录进行轮换。
     */
    File allocationBaseDirectory(long checkpointId);

    /**
     * Returns the local state directory for the owning subtask the given checkpoint id w.r.t. our
     * rotation over all available available allocation base directories. This directory is
     * contained in the directory returned by {@link #allocationBaseDirectory(long)} for the same
     * checkpoint id.
     * 返回给定检查点 id w.r.t 所属子任务的本地状态目录。 我们轮换所有可用的可用分配基目录。
     * 此目录包含在 {@link #allocationBaseDirectory(long)} 为相同检查点 id 返回的目录中。
     */
    File subtaskBaseDirectory(long checkpointId);

    /**
     * Returns the local state directory for the specific operator subtask and the given checkpoint
     * id w.r.t. our rotation over all available root dirs. This directory is contained in the
     * directory returned by {@link #subtaskBaseDirectory(long)} for the same checkpoint id.
     * 返回特定操作员子任务的本地状态目录和给定的检查点 ID w.r.t。 我们对所有可用的根目录进行轮换。
     * 此目录包含在 {@link #subtaskBaseDirectory(long)} 为相同检查点 id 返回的目录中。
     */
    File subtaskSpecificCheckpointDirectory(long checkpointId);

    /**
     * Returns a specific allocation base directory. The index must be between 0 (incl.) and {@link
     * #allocationBaseDirsCount()} (excl.).
     * 返回特定的分配基目录。 索引必须介于 0（包括）和 {@link #allocationBaseDirsCount()}（不包括）之间。
     */
    File selectAllocationBaseDirectory(int idx);

    /**
     * Returns a specific subtask base directory. The index must be between 0 (incl.) and {@link
     * #allocationBaseDirsCount()} (excl.). This directory is direct a child of {@link
     * #selectSubtaskBaseDirectory(int)} given the same index.
     * 返回特定的子任务基目录。 索引必须介于 0（包括）和 {@link #allocationBaseDirsCount()}（不包括）之间。
     * 在给定相同索引的情况下，此目录是 {@link #selectSubtaskBaseDirectory(int)} 的直接子目录。
     */
    File selectSubtaskBaseDirectory(int idx);

    /** Returns the total number of allocation base directories.
     * 返回分配基目录的总数。
     * */
    int allocationBaseDirsCount();
}
