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

package org.apache.flink.core.fs;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.IOException;

/**
 * The RecoverableWriter creates and recovers {@link RecoverableFsDataOutputStream}. It can be used
 * to write data to a file system in a way that the writing can be resumed consistently after a
 * failure and recovery without loss of data or possible duplication of bytes.
 * RecoverableWriter 创建并恢复 {@link RecoverableFsDataOutputStream}。
 * 它可用于将数据写入文件系统，在发生故障和恢复后可以一致地恢复写入，而不会丢失数据或可能出现重复的字节。
 *
 * <p>The streams do not make the files they write to immediately visible, but instead write to temp
 * files or other temporary storage. To publish the data atomically in the end, the stream offers
 * the {@link RecoverableFsDataOutputStream#closeForCommit()} method to create a committer that
 * publishes the result.
 * 流不会使它们写入的文件立即可见，而是写入临时文件或其他临时存储。
 * 为了最终以原子方式发布数据，流提供了 {@link RecoverableFsDataOutputStream#closeForCommit()}
 * 方法来创建发布结果的提交者。
 *
 * <p>These writers are useful in the context of checkpointing. The example below illustrates how to
 * use them:
 * 这些编写器在检查点的上下文中很有用。 下面的例子说明了如何使用它们：
 *
 * <pre>{@code
 * // --------- initial run --------
 * RecoverableWriter writer = fileSystem.createRecoverableWriter();
 * RecoverableFsDataOutputStream out = writer.open(path);
 * out.write(...);
 *
 * // persist intermediate state
 * ResumeRecoverable intermediateState = out.persist();
 * storeInCheckpoint(intermediateState);
 *
 * // --------- recovery --------
 * ResumeRecoverable lastCheckpointState = ...; // get state from checkpoint
 * RecoverableWriter writer = fileSystem.createRecoverableWriter();
 * RecoverableFsDataOutputStream out = writer.recover(lastCheckpointState);
 *
 * out.write(...); // append more data
 *
 * out.closeForCommit().commit(); // close stream and publish all the data
 *
 * // --------- recovery without appending --------
 * ResumeRecoverable lastCheckpointState = ...; // get state from checkpoint
 * RecoverableWriter writer = fileSystem.createRecoverableWriter();
 * Committer committer = writer.recoverForCommit(lastCheckpointState);
 * committer.commit(); // publish the state as of the last checkpoint
 * }</pre>
 *
 * <h3>Recovery</h3>
 *
 * <p>Recovery relies on data persistence in the target file system or object store. While the code
 * itself works with the specific primitives that the target storage offers, recovery will fail if
 * the data written so far was deleted by an external factor. For example, some implementations
 * stage data in temp files or object parts. If these were deleted by someone or by an automated
 * cleanup policy, then resuming may fail. This is not surprising and should be expected, but we
 * want to explicitly point this out here.
 * 恢复依赖于目标文件系统或对象存储中的数据持久性。 虽然代码本身与目标存储提供的特定原语一起工作，
 * 但如果到目前为止写入的数据被外部因素删除，恢复将失败。 例如，某些实现在临时文件或对象部分中暂存数据。
 * 如果这些被某人或自动清理策略删除，则恢复可能会失败。 这并不奇怪，应该在意料之中，但我们想在这里明确指出这一点。
 *
 * <p>Specific care is needed for systems like S3, where the implementation uses Multipart Uploads
 * to incrementally upload and persist parts of the result. Timeouts for Multipart Uploads and life
 * time of Parts in unfinished Multipart Uploads need to be set in the bucket policy high enough to
 * accommodate the recovery. These values are typically in the days, so regular recovery is
 * typically not a problem. What can become an issue is situations where a Flink application is hard
 * killed (all processes or containers removed) and then one tries to manually recover the
 * application from an externalized checkpoint some days later. In that case, systems like S3 may
 * have removed uncommitted parts and recovery will not succeed.
 * 对于像 S3 这样的系统，需要特别小心，其中实现使用分段上传来增量上传和保留部分结果。
 * 需要在存储桶策略中将分段上传超时和未完成分段上传中分段的生命周期设置得足够高以适应恢复。
 * 这些值通常在几天内，因此定期恢复通常不是问题。 可能会成为问题的情况是，Flink 应用程序被硬杀死（所有进程或容器被删除），
 * 然后几天后尝试从外部检查点手动恢复应用程序。 在这种情况下，像 S3 这样的系统可能已经删除了未提交的部分，恢复将不会成功。
 *
 * <h3>Implementer's Note</h3>
 *
 * <p>From the perspective of the implementer, it would be desirable to make this class generic with
 * respect to the concrete types of 'CommitRecoverable' and 'ResumeRecoverable'. However, we found
 * that this makes the code more clumsy to use and we hence dropped the generics at the cost of
 * doing some explicit casts in the implementation that would otherwise have been implicitly
 * generated by the generics compiler.
 * 从实现者的角度来看，最好使这个类对于“CommitRecoverable”和“ResumeRecoverable”的具体类型具有通用性。
 * 然而，我们发现这使得代码使用起来更加笨拙，因此我们放弃了泛型，代价是在实现中进行一些显式转换，否则这些转换会由泛型编译器隐式生成。
 */
@PublicEvolving
public interface RecoverableWriter {

    /**
     * Opens a new recoverable stream to write to the given path. Whether existing files will be
     * overwritten is implementation specific and should not be relied upon.
     * 打开一个新的可恢复流以写入给定的路径。 是否覆盖现有文件是特定于实现的，不应依赖。
     *
     * @param path The path of the file/object to write to.
     * @return A new RecoverableFsDataOutputStream writing a new file/object.
     * @throws IOException Thrown if the stream could not be opened/initialized.
     */
    RecoverableFsDataOutputStream open(Path path) throws IOException;

    /**
     * Resumes a recoverable stream consistently at the point indicated by the given
     * ResumeRecoverable. Future writes to the stream will continue / append the file as of that
     * point.
     * 在给定的 ResumeRecoverable 指示的点处一致地恢复可恢复的流。 从那时起，对流的未来写入将继续/追加文件。
     *
     * <p>This method is optional and whether it is supported is indicated through the {@link
     * #supportsResume()} method.
     * 此方法是可选的，是否支持通过 {@link #supportsResume()} 方法指示。
     *
     * @param resumable The opaque handle with the recovery information.
     * @return A recoverable stream writing to the file/object as it was at the point when the
     *     ResumeRecoverable was created.
     * @throws IOException Thrown, if resuming fails.
     * @throws UnsupportedOperationException Thrown if this optional method is not supported.
     */
    RecoverableFsDataOutputStream recover(ResumeRecoverable resumable) throws IOException;

    /**
     * Marks if the writer requires to do any additional cleanup/freeing of resources occupied as
     * part of a {@link ResumeRecoverable}, e.g. temporarily files created or objects uploaded to
     * external systems.
     * 标记作者是否需要对作为 {@link ResumeRecoverable} 的一部分占用的资源进行任何额外的清理/释放，
     * 例如 临时创建的文件或上传到外部系统的对象。
     *
     * <p>In case cleanup is required, then {@link #cleanupRecoverableState(ResumeRecoverable)}
     * should be called.
     * 如果需要清理，则应调用 {@link #cleanupRecoverableState(ResumeRecoverable)}。
     *
     * @return {@code true} if cleanup is required, {@code false} otherwise.
     */
    boolean requiresCleanupOfRecoverableState();

    /**
     * Frees up any resources that were previously occupied in order to be able to recover from a
     * (potential) failure. These can be temporary files that were written to the filesystem or
     * objects that were uploaded to S3.
     * 释放之前占用的所有资源，以便能够从（潜在）故障中恢复。 这些可以是写入文件系统的临时文件或上传到 S3 的对象。
     *
     * <p><b>NOTE:</b> This operation should not throw an exception, but return false if the cleanup
     * did not happen for any reason.
     * <b>注意：</b> 此操作不应抛出异常，但如果由于任何原因未进行清理，则返回 false。
     *
     * @param resumable The {@link ResumeRecoverable} whose state we want to clean-up.
     * @return {@code true} if the resources were successfully freed, {@code false} otherwise (e.g.
     *     the file to be deleted was not there for any reason - already deleted or never created).
     */
    boolean cleanupRecoverableState(ResumeRecoverable resumable) throws IOException;

    /**
     * Recovers a recoverable stream consistently at the point indicated by the given
     * CommitRecoverable for finalizing and committing. This will publish the target file with
     * exactly the data that was written up to the point then the CommitRecoverable was created.
     * 在给定的 CommitRecoverable 指示的点一致地恢复可恢复的流以进行最终确定和提交。
     * 这将发布目标文件，其中包含在创建 CommitRecoverable 之前写入的数据。
     *
     * @param resumable The opaque handle with the recovery information.
     * @return A committer that publishes the target file.
     * @throws IOException Thrown, if recovery fails.
     */
    RecoverableFsDataOutputStream.Committer recoverForCommit(CommitRecoverable resumable)
            throws IOException;

    /**
     * The serializer for the CommitRecoverable types created in this writer. This serializer should
     * be used to store the CommitRecoverable in checkpoint state or other forms of persistent
     * state.
     * 在此编写器中创建的 CommitRecoverable 类型的序列化程序。
     * 此序列化程序应用于将 CommitRecoverable 存储在检查点状态或其他形式的持久状态中。
     */
    SimpleVersionedSerializer<CommitRecoverable> getCommitRecoverableSerializer();

    /**
     * The serializer for the ResumeRecoverable types created in this writer. This serializer should
     * be used to store the ResumeRecoverable in checkpoint state or other forms of persistent
     * state.
     * 在此编写器中创建的 ResumeRecoverable 类型的序列化程序。
     * 此序列化程序应用于将 ResumeRecoverable 存储在检查点状态或其他形式的持久状态中。
     */
    SimpleVersionedSerializer<ResumeRecoverable> getResumeRecoverableSerializer();

    /**
     * Checks whether the writer and its streams support resuming (appending to) files after
     * recovery (via the {@link #recover(ResumeRecoverable)} method).
     * 检查写入器及其流是否支持在恢复后恢复（附加到）文件（通过 {@link #recover(ResumeRecoverable)} 方法）。
     *
     * <p>If true, then this writer supports the {@link #recover(ResumeRecoverable)} method. If
     * false, then that method may not be supported and streams can only be recovered via {@link
     * #recoverForCommit(CommitRecoverable)}.
     * 如果为 true，则此作者支持 {@link #recover(ResumeRecoverable)} 方法。
     * 如果为 false，则可能不支持该方法，并且只能通过 {@link #recoverForCommit(CommitRecoverable)} 恢复流。
     */
    boolean supportsResume();

    // ------------------------------------------------------------------------

    /**
     * A handle to an in-progress stream with a defined and persistent amount of data. The handle
     * can be used to recover the stream as of exactly that point and publish the result file.
     * 具有定义和持久数据量的正在进行的流的句柄。 句柄可用于恢复该点的流并发布结果文件。
     */
    interface CommitRecoverable {}

    /**
     * A handle to an in-progress stream with a defined and persistent amount of data. The handle
     * can be used to recover the stream exactly as of that point and either publish the result file
     * or keep appending data to the stream.
     * 具有定义和持久数据量的正在进行的流的句柄。 句柄可用于完全恢复该点的流，并发布结果文件或继续将数据附加到流中。
     */
    interface ResumeRecoverable extends CommitRecoverable {}
}
