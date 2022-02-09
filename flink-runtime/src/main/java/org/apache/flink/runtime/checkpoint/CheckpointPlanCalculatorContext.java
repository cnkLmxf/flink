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

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.runtime.concurrent.ScheduledExecutor;

/**
 * Provides the context for {@link DefaultCheckpointPlanCalculator} to compute the plan of
 * checkpoints.
 * 为 {@link DefaultCheckpointPlanCalculator} 提供上下文以计算检查点计划。
 */
public interface CheckpointPlanCalculatorContext {

    /**
     * Acquires the main thread executor for this job.
     * 获取此作业的主线程执行器。
     *
     * @return The main thread executor.
     */
    ScheduledExecutor getMainExecutor();

    /**
     * Detects whether there are already some tasks finished.
     * 检测是否已经有一些任务完成。
     *
     * @return Whether there are finished tasks.
     */
    boolean hasFinishedTasks();
}
