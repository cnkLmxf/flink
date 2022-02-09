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

package org.apache.flink.api.common;

import org.apache.flink.annotation.PublicEvolving;

/** Possible states of a job once it has been accepted by the dispatcher.
 * 作业被调度员接受后的可能状态。
 * */
@PublicEvolving
public enum JobStatus {
    /**
     * The job has been received by the Dispatcher, and is waiting for the job manager to receive
     * leadership and to be created.
     */
    INITIALIZING(TerminalState.NON_TERMINAL),

    /** Job is newly created, no task has started to run. */
    CREATED(TerminalState.NON_TERMINAL),

    /** Some tasks are scheduled or running, some may be pending, some may be finished. */
    RUNNING(TerminalState.NON_TERMINAL),

    /** The job has failed and is currently waiting for the cleanup to complete. */
    FAILING(TerminalState.NON_TERMINAL),

    /** The job has failed with a non-recoverable task failure. */
    FAILED(TerminalState.GLOBALLY),

    /** Job is being cancelled. */
    CANCELLING(TerminalState.NON_TERMINAL),

    /** Job has been cancelled. */
    CANCELED(TerminalState.GLOBALLY),

    /** All of the job's tasks have successfully finished. */
    FINISHED(TerminalState.GLOBALLY),

    /** The job is currently undergoing a reset and total restart. */
    RESTARTING(TerminalState.NON_TERMINAL),

    /**
     * The job has been suspended which means that it has been stopped but not been removed from a
     * potential HA job store.
     */
    SUSPENDED(TerminalState.LOCALLY),

    /** The job is currently reconciling and waits for task execution report to recover state. */
    RECONCILING(TerminalState.NON_TERMINAL);

    // --------------------------------------------------------------------------------------------

    private enum TerminalState {
        NON_TERMINAL,
        LOCALLY,
        GLOBALLY
    }

    private final TerminalState terminalState;

    JobStatus(TerminalState terminalState) {
        this.terminalState = terminalState;
    }

    /**
     * Checks whether this state is <i>globally terminal</i>. A globally terminal job is complete
     * and cannot fail any more and will not be restarted or recovered by another standby master
     * node.
     * 检查此状态是否为<i>全局终端</i>。 一个全局终端作业已经完成，不能再失败，也不会被另一个备用主节点重新启动或恢复。
     *
     * <p>When a globally terminal state has been reached, all recovery data for the job is dropped
     * from the high-availability services.
     * 当达到全局终端状态时，作业的所有恢复数据都会从高可用性服务中删除。
     *
     * @return True, if this job status is globally terminal, false otherwise.
     */
    public boolean isGloballyTerminalState() {
        return terminalState == TerminalState.GLOBALLY;
    }

    /**
     * Checks whether this state is <i>locally terminal</i>. Locally terminal refers to the state of
     * a job's execution graph within an executing JobManager. If the execution graph is locally
     * terminal, the JobManager will not continue executing or recovering the job.
     * 检查此状态是否为<i>本地终端</i>。 本地终端是指正在执行的 JobManager 中作业的执行图的状态。
     * 如果执行图是本地终端，JobManager 将不会继续执行或恢复作业。
     *
     * <p>The only state that is locally terminal, but not globally terminal is {@link #SUSPENDED},
     * which is typically entered when the executing JobManager looses its leader status.
     * 唯一的本地终端状态，而不是全局终端状态是 {@link #SUSPENDED}，通常在执行的 JobManager 失去其领导者状态时进入。
     *
     * @return True, if this job status is terminal, false otherwise.
     */
    public boolean isTerminalState() {
        return terminalState != TerminalState.NON_TERMINAL;
    }
}
