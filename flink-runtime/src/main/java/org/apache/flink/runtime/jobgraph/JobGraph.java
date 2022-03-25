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

package org.apache.flink.runtime.jobgraph;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.InvalidProgramException;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.cache.DistributedCache;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.blob.PermanentBlobKey;
import org.apache.flink.runtime.jobgraph.tasks.JobCheckpointingSettings;
import org.apache.flink.runtime.jobmanager.scheduler.CoLocationGroup;
import org.apache.flink.runtime.jobmanager.scheduler.SlotSharingGroup;
import org.apache.flink.util.InstantiationUtil;
import org.apache.flink.util.IterableUtils;
import org.apache.flink.util.SerializedValue;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The JobGraph represents a Flink dataflow program, at the low level that the JobManager accepts.
 * All programs from higher level APIs are transformed into JobGraphs.
 * JobGraph 代表一个 Flink 数据流程序，处于 JobManager 接受的底层。 所有来自更高级别 API 的程序都被转换为 JobGraphs。
 *
 * <p>The JobGraph is a graph of vertices and intermediate results that are connected together to
 * form a DAG. Note that iterations (feedback edges) are currently not encoded inside the JobGraph
 * but inside certain special vertices that establish the feedback channel amongst themselves.
 * JobGraph 是连接在一起形成 DAG 的顶点和中间结果的图。
 * 请注意，迭代（反馈边）当前不是在 JobGraph 内编码，而是在某些特殊顶点内编码，这些顶点在它们之间建立反馈通道。
 *
 * <p>The JobGraph defines the job-wide configuration settings, while each vertex and intermediate
 * result define the characteristics of the concrete operation and intermediate data.
 * JobGraph 定义了作业范围的配置设置，而每个顶点和中间结果定义了具体操作和中间数据的特征。
 */
public class JobGraph implements Serializable {

    private static final long serialVersionUID = 1L;

    // --- job and configuration ---

    /** List of task vertices included in this job graph.
     * 此作业图中包含的任务顶点列表。
     * */
    private final Map<JobVertexID, JobVertex> taskVertices =
            new LinkedHashMap<JobVertexID, JobVertex>();

    /** The job configuration attached to this job.
     * 附加到此作业的作业配置。
     * */
    private final Configuration jobConfiguration = new Configuration();

    /** ID of this job. May be set if specific job id is desired (e.g. session management)
     * 此作业的 ID。 如果需要特定的作业 ID，可以设置（例如会话管理）
     * */
    private JobID jobID;

    /** Name of this job. */
    private final String jobName;

    private JobType jobType = JobType.BATCH;

    /**
     * Whether approximate local recovery is enabled. This flag will be removed together with legacy
     * scheduling strategies.
     * 是否启用近似本地恢复。 此标志将与旧的调度策略一起被删除。
     */
    private boolean approximateLocalRecovery = false;

    // --- checkpointing ---

    /** Job specific execution config.
     * 作业特定的执行配置。
     * */
    private SerializedValue<ExecutionConfig> serializedExecutionConfig;

    /** The settings for the job checkpoints.
     * 作业检查点的设置。
     * */
    private JobCheckpointingSettings snapshotSettings;

    /** Savepoint restore settings.
     * 保存点恢复设置。
     * */
    private SavepointRestoreSettings savepointRestoreSettings = SavepointRestoreSettings.none();

    // --- attached resources ---

    /** Set of JAR files required to run this job.
     * 运行此作业所需的一组 JAR 文件。
     * */
    private final List<Path> userJars = new ArrayList<Path>();

    /** Set of custom files required to run this job.
     * 运行此作业所需的一组自定义文件。
     * */
    private final Map<String, DistributedCache.DistributedCacheEntry> userArtifacts =
            new HashMap<>();

    /** Set of blob keys identifying the JAR files required to run this job.
     * 一组标识运行此作业所需的 JAR 文件的 blob 键。
     * */
    private final List<PermanentBlobKey> userJarBlobKeys = new ArrayList<>();

    /** List of classpaths required to run this job.
     * 运行此作业所需的类路径列表。
     * */
    private List<URL> classpaths = Collections.emptyList();

    // --------------------------------------------------------------------------------------------

    /**
     * Constructs a new job graph with the given name, the given {@link ExecutionConfig}, and a
     * random job ID. The ExecutionConfig will be serialized and can't be modified afterwards.
     * 使用给定名称、给定 {@link ExecutionConfig} 和随机作业 ID 构造一个新作业图。
     * ExecutionConfig 将被序列化，之后无法修改。
     *
     * @param jobName The name of the job.
     */
    public JobGraph(String jobName) {
        this(null, jobName);
    }

    /**
     * Constructs a new job graph with the given job ID (or a random ID, if {@code null} is passed),
     * the given name and the given execution configuration (see {@link ExecutionConfig}). The
     * ExecutionConfig will be serialized and can't be modified afterwards.
     * 使用给定的作业 ID（或随机 ID，如果 {@code null} 被传递）、给定的名称和给定的执行配置
     * \（参见 {@link ExecutionConfig}）构造一个新的作业图。 ExecutionConfig 将被序列化，之后无法修改。
     *
     * @param jobId The id of the job. A random ID is generated, if {@code null} is passed.
     * @param jobName The name of the job.
     */
    public JobGraph(@Nullable JobID jobId, String jobName) {
        this.jobID = jobId == null ? new JobID() : jobId;
        this.jobName = jobName == null ? "(unnamed job)" : jobName;

        try {
            setExecutionConfig(new ExecutionConfig());
        } catch (IOException e) {
            // this should never happen, since an empty execution config is always serializable
            throw new RuntimeException("bug, empty execution config is not serializable");
        }
    }

    /**
     * Constructs a new job graph with the given name, the given {@link ExecutionConfig}, the given
     * jobId or a random one if null supplied, and the given job vertices. The ExecutionConfig will
     * be serialized and can't be modified afterwards.
     * 使用给定的名称、给定的 {@link ExecutionConfig}、
     * 给定的 jobId 或随机一个（如果提供了 null）以及给定的作业顶点构造一个新的作业图。 ExecutionConfig 将被序列化，之后无法修改。
     *
     * @param jobId The id of the job. A random ID is generated, if {@code null} is passed.
     * @param jobName The name of the job.
     * @param vertices The vertices to add to the graph.
     */
    public JobGraph(@Nullable JobID jobId, String jobName, JobVertex... vertices) {
        this(jobId, jobName);

        for (JobVertex vertex : vertices) {
            addVertex(vertex);
        }
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Returns the ID of the job.
     *
     * @return the ID of the job
     */
    public JobID getJobID() {
        return this.jobID;
    }

    /** Sets the ID of the job. */
    public void setJobID(JobID jobID) {
        this.jobID = jobID;
    }

    /**
     * Returns the name assigned to the job graph.
     * 返回分配给作业图的名称。
     *
     * @return the name assigned to the job graph
     */
    public String getName() {
        return this.jobName;
    }

    /**
     * Returns the configuration object for this job. Job-wide parameters should be set into that
     * configuration object.
     * 返回此作业的配置对象。 作业范围的参数应设置到该配置对象中。
     *
     * @return The configuration object for this job.
     */
    public Configuration getJobConfiguration() {
        return this.jobConfiguration;
    }

    /**
     * Returns the {@link ExecutionConfig}.
     *
     * @return ExecutionConfig
     */
    public SerializedValue<ExecutionConfig> getSerializedExecutionConfig() {
        return serializedExecutionConfig;
    }

    public void setJobType(JobType type) {
        this.jobType = type;
    }

    public JobType getJobType() {
        return jobType;
    }

    public void enableApproximateLocalRecovery(boolean enabled) {
        this.approximateLocalRecovery = enabled;
    }

    public boolean isApproximateLocalRecoveryEnabled() {
        return approximateLocalRecovery;
    }

    /**
     * Sets the savepoint restore settings.
     * 设置保存点恢复设置。
     *
     * @param settings The savepoint restore settings.
     */
    public void setSavepointRestoreSettings(SavepointRestoreSettings settings) {
        this.savepointRestoreSettings = checkNotNull(settings, "Savepoint restore settings");
    }

    /**
     * Returns the configured savepoint restore setting.
     * 返回配置的保存点恢复设置。
     *
     * @return The configured savepoint restore settings.
     */
    public SavepointRestoreSettings getSavepointRestoreSettings() {
        return savepointRestoreSettings;
    }

    /**
     * Sets the execution config. This method eagerly serialized the ExecutionConfig for future RPC
     * transport. Further modification of the referenced ExecutionConfig object will not affect this
     * serialized copy.
     * 设置执行配置。 此方法急切地序列化 ExecutionConfig 以供将来的 RPC 传输。
     * 对引用的 ExecutionConfig 对象的进一步修改不会影响这个序列化副本。
     *
     * @param executionConfig The ExecutionConfig to be serialized.
     * @throws IOException Thrown if the serialization of the ExecutionConfig fails
     */
    public void setExecutionConfig(ExecutionConfig executionConfig) throws IOException {
        checkNotNull(executionConfig, "ExecutionConfig must not be null.");
        setSerializedExecutionConfig(new SerializedValue<>(executionConfig));
    }

    void setSerializedExecutionConfig(SerializedValue<ExecutionConfig> serializedExecutionConfig) {
        this.serializedExecutionConfig =
                checkNotNull(
                        serializedExecutionConfig,
                        "The serialized ExecutionConfig must not be null.");
    }

    /**
     * Adds a new task vertex to the job graph if it is not already included.
     * 如果尚未包含新任务顶点，则将其添加到作业图中。
     *
     * @param vertex the new task vertex to be added
     */
    public void addVertex(JobVertex vertex) {
        final JobVertexID id = vertex.getID();
        JobVertex previous = taskVertices.put(id, vertex);

        // if we had a prior association, restore and throw an exception
        if (previous != null) {
            taskVertices.put(id, previous);
            throw new IllegalArgumentException(
                    "The JobGraph already contains a vertex with that id.");
        }
    }

    /**
     * Returns an Iterable to iterate all vertices registered with the job graph.
     * 返回一个 Iterable 以迭代在作业图上注册的所有顶点。
     *
     * @return an Iterable to iterate all vertices registered with the job graph
     */
    public Iterable<JobVertex> getVertices() {
        return this.taskVertices.values();
    }

    /**
     * Returns an array of all job vertices that are registered with the job graph. The order in
     * which the vertices appear in the list is not defined.
     * 返回向作业图注册的所有作业顶点的数组。 未定义顶点在列表中出现的顺序。
     *
     * @return an array of all job vertices that are registered with the job graph
     */
    public JobVertex[] getVerticesAsArray() {
        return this.taskVertices.values().toArray(new JobVertex[this.taskVertices.size()]);
    }

    /**
     * Returns the number of all vertices.
     *
     * @return The number of all vertices.
     */
    public int getNumberOfVertices() {
        return this.taskVertices.size();
    }

    public Set<SlotSharingGroup> getSlotSharingGroups() {
        final Set<SlotSharingGroup> slotSharingGroups =
                IterableUtils.toStream(getVertices())
                        .map(JobVertex::getSlotSharingGroup)
                        .collect(Collectors.toSet());
        return Collections.unmodifiableSet(slotSharingGroups);
    }

    /**
     * Returns all {@link CoLocationGroup} instances associated with this {@code JobGraph}.
     * 返回与此 {@code JobGraph} 关联的所有 {@link CoLocationGroup} 实例。
     *
     * @return The associated {@code CoLocationGroup} instances.
     */
    public Set<CoLocationGroup> getCoLocationGroups() {
        final Set<CoLocationGroup> coLocationGroups =
                IterableUtils.toStream(getVertices())
                        .map(JobVertex::getCoLocationGroup)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return Collections.unmodifiableSet(coLocationGroups);
    }

    /**
     * Sets the settings for asynchronous snapshots. A value of {@code null} means that snapshotting
     * is not enabled.
     * 设置异步快照的设置。 {@code null} 的值表示未启用快照。
     *
     * @param settings The snapshot settings
     */
    public void setSnapshotSettings(JobCheckpointingSettings settings) {
        this.snapshotSettings = settings;
    }

    /**
     * Gets the settings for asynchronous snapshots. This method returns null, when checkpointing is
     * not enabled.
     *
     * @return The snapshot settings
     */
    public JobCheckpointingSettings getCheckpointingSettings() {
        return snapshotSettings;
    }

    /**
     * Checks if the checkpointing was enabled for this job graph.
     *
     * @return true if checkpointing enabled
     */
    public boolean isCheckpointingEnabled() {

        if (snapshotSettings == null) {
            return false;
        }

        long checkpointInterval =
                snapshotSettings.getCheckpointCoordinatorConfiguration().getCheckpointInterval();
        return checkpointInterval > 0 && checkpointInterval < Long.MAX_VALUE;
    }

    /**
     * Searches for a vertex with a matching ID and returns it.
     * 搜索具有匹配 ID 的顶点并返回它。
     *
     * @param id the ID of the vertex to search for
     * @return the vertex with the matching ID or <code>null</code> if no vertex with such ID could
     *     be found
     */
    public JobVertex findVertexByID(JobVertexID id) {
        return this.taskVertices.get(id);
    }

    /**
     * Sets the classpaths required to run the job on a task manager.
     * 设置在任务管理器上运行作业所需的类路径。
     *
     * @param paths paths of the directories/JAR files required to run the job on a task manager
     */
    public void setClasspaths(List<URL> paths) {
        classpaths = paths;
    }

    public List<URL> getClasspaths() {
        return classpaths;
    }

    /**
     * Gets the maximum parallelism of all operations in this job graph.
     * 获取此作业图中所有操作的最大并行度。
     *
     * @return The maximum parallelism of this job graph
     */
    public int getMaximumParallelism() {
        int maxParallelism = -1;
        for (JobVertex vertex : taskVertices.values()) {
            maxParallelism = Math.max(vertex.getParallelism(), maxParallelism);
        }
        return maxParallelism;
    }

    // --------------------------------------------------------------------------------------------
    //  Topological Graph Access
    // --------------------------------------------------------------------------------------------

    public List<JobVertex> getVerticesSortedTopologicallyFromSources()
            throws InvalidProgramException {
        // early out on empty lists
        if (this.taskVertices.isEmpty()) {
            return Collections.emptyList();
        }

        List<JobVertex> sorted = new ArrayList<JobVertex>(this.taskVertices.size());
        Set<JobVertex> remaining = new LinkedHashSet<JobVertex>(this.taskVertices.values());

        // start by finding the vertices with no input edges
        // and the ones with disconnected inputs (that refer to some standalone data set)
        {
            Iterator<JobVertex> iter = remaining.iterator();
            while (iter.hasNext()) {
                JobVertex vertex = iter.next();

                if (vertex.hasNoConnectedInputs()) {
                    sorted.add(vertex);
                    iter.remove();
                }
            }
        }

        int startNodePos = 0;

        // traverse from the nodes that were added until we found all elements
        while (!remaining.isEmpty()) {

            // first check if we have more candidates to start traversing from. if not, then the
            // graph is cyclic, which is not permitted
            if (startNodePos >= sorted.size()) {
                throw new InvalidProgramException("The job graph is cyclic.");
            }

            JobVertex current = sorted.get(startNodePos++);
            addNodesThatHaveNoNewPredecessors(current, sorted, remaining);
        }

        return sorted;
    }

    private void addNodesThatHaveNoNewPredecessors(
            JobVertex start, List<JobVertex> target, Set<JobVertex> remaining) {

        // forward traverse over all produced data sets and all their consumers
        for (IntermediateDataSet dataSet : start.getProducedDataSets()) {
            for (JobEdge edge : dataSet.getConsumers()) {

                // a vertex can be added, if it has no predecessors that are still in the
                // 'remaining' set
                JobVertex v = edge.getTarget();
                if (!remaining.contains(v)) {
                    continue;
                }

                boolean hasNewPredecessors = false;

                for (JobEdge e : v.getInputs()) {
                    // skip the edge through which we came
                    if (e == edge) {
                        continue;
                    }

                    IntermediateDataSet source = e.getSource();
                    if (remaining.contains(source.getProducer())) {
                        hasNewPredecessors = true;
                        break;
                    }
                }

                if (!hasNewPredecessors) {
                    target.add(v);
                    remaining.remove(v);
                    addNodesThatHaveNoNewPredecessors(v, target, remaining);
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    //  Handling of attached JAR files
    // --------------------------------------------------------------------------------------------

    /**
     * Adds the path of a JAR file required to run the job on a task manager.
     * 添加在任务管理器上运行作业所需的 JAR 文件的路径。
     *
     * @param jar path of the JAR file required to run the job on a task manager
     */
    public void addJar(Path jar) {
        if (jar == null) {
            throw new IllegalArgumentException();
        }

        if (!userJars.contains(jar)) {
            userJars.add(jar);
        }
    }

    /**
     * Adds the given jar files to the {@link JobGraph} via {@link JobGraph#addJar}.
     * 通过 {@link JobGraph#addJar} 将给定的 jar 文件添加到 {@link JobGraph}。
     *
     * @param jarFilesToAttach a list of the {@link URL URLs} of the jar files to attach to the
     *     jobgraph.
     * @throws RuntimeException if a jar URL is not valid.
     */
    public void addJars(final List<URL> jarFilesToAttach) {
        for (URL jar : jarFilesToAttach) {
            try {
                addJar(new Path(jar.toURI()));
            } catch (URISyntaxException e) {
                throw new RuntimeException("URL is invalid. This should not happen.", e);
            }
        }
    }

    /**
     * Gets the list of assigned user jar paths.
     * 获取分配的用户 jar 路径列表。
     *
     * @return The list of assigned user jar paths
     */
    public List<Path> getUserJars() {
        return userJars;
    }

    /**
     * Adds the path of a custom file required to run the job on a task manager.
     * 添加在任务管理器上运行作业所需的自定义文件的路径。
     *
     * @param name a name under which this artifact will be accessible through {@link
     *     DistributedCache}
     * @param file path of a custom file required to run the job on a task manager
     */
    public void addUserArtifact(String name, DistributedCache.DistributedCacheEntry file) {
        if (file == null) {
            throw new IllegalArgumentException();
        }

        userArtifacts.putIfAbsent(name, file);
    }

    /**
     * Gets the list of assigned user jar paths.
     * 获取分配的用户 jar 路径列表。
     * 这里存放的不光是jar文件，用户自定义的非jar文件也会在里边
     *
     * @return The list of assigned user jar paths
     */
    public Map<String, DistributedCache.DistributedCacheEntry> getUserArtifacts() {
        return userArtifacts;
    }

    /**
     * Adds the BLOB referenced by the key to the JobGraph's dependencies.
     * 将键引用的 BLOB 添加到 JobGraph 的依赖项中。
     *
     * @param key path of the JAR file required to run the job on a task manager
     */
    public void addUserJarBlobKey(PermanentBlobKey key) {
        if (key == null) {
            throw new IllegalArgumentException();
        }

        if (!userJarBlobKeys.contains(key)) {
            userJarBlobKeys.add(key);
        }
    }

    /**
     * Checks whether the JobGraph has user code JAR files attached.
     * 检查 JobGraph 是否附加了用户代码 JAR 文件。
     *
     * @return True, if the JobGraph has user code JAR files attached, false otherwise.
     */
    public boolean hasUsercodeJarFiles() {
        return this.userJars.size() > 0;
    }

    /**
     * Returns a set of BLOB keys referring to the JAR files required to run this job.
     * 返回一组引用运行此作业所需的 JAR 文件的 BLOB 键。
     *
     * @return set of BLOB keys referring to the JAR files required to run this job
     */
    public List<PermanentBlobKey> getUserJarBlobKeys() {
        return this.userJarBlobKeys;
    }

    @Override
    public String toString() {
        return "JobGraph(jobId: " + jobID + ")";
    }

    public void setUserArtifactBlobKey(String entryName, PermanentBlobKey blobKey)
            throws IOException {
        byte[] serializedBlobKey;
        serializedBlobKey = InstantiationUtil.serializeObject(blobKey);

        userArtifacts.computeIfPresent(
                entryName,
                (key, originalEntry) ->
                        new DistributedCache.DistributedCacheEntry(
                                originalEntry.filePath,
                                originalEntry.isExecutable,
                                serializedBlobKey,
                                originalEntry.isZipped));
    }

    public void setUserArtifactRemotePath(String entryName, String remotePath) {
        userArtifacts.computeIfPresent(
                entryName,
                (key, originalEntry) ->
                        new DistributedCache.DistributedCacheEntry(
                                remotePath,
                                originalEntry.isExecutable,
                                null,
                                originalEntry.isZipped));
    }

    public void writeUserArtifactEntriesToConfiguration() {
        for (Map.Entry<String, DistributedCache.DistributedCacheEntry> userArtifact :
                userArtifacts.entrySet()) {
            //指定各个文件的信息，比如名称，位置，是否可执行等
            DistributedCache.writeFileInfoToConfig(
                    userArtifact.getKey(), userArtifact.getValue(), jobConfiguration);
        }
    }
}
