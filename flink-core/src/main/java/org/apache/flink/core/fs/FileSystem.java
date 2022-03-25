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

/*
 * This file is based on source code from the Hadoop Project (http://hadoop.apache.org/), licensed by the Apache
 * Software Foundation (ASF) under the Apache License, Version 2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.apache.flink.core.fs;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.Public;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.configuration.IllegalConfigurationException;
import org.apache.flink.core.fs.local.LocalFileSystem;
import org.apache.flink.core.fs.local.LocalFileSystemFactory;
import org.apache.flink.core.plugin.PluginManager;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.TemporaryClassLoaderContext;

import org.apache.flink.shaded.guava18.com.google.common.base.Splitter;
import org.apache.flink.shaded.guava18.com.google.common.collect.ImmutableMultimap;
import org.apache.flink.shaded.guava18.com.google.common.collect.Iterators;
import org.apache.flink.shaded.guava18.com.google.common.collect.Multimap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Abstract base class of all file systems used by Flink. This class may be extended to implement
 * distributed file systems, or local file systems. The abstraction by this file system is very
 * simple, and the set of available operations quite limited, to support the common denominator of a
 * wide range of file systems. For example, appending to or mutating existing files is not
 * supported.
 * Flink 使用的所有文件系统的抽象基类。 此类可以扩展以实现分布式文件系统或本地文件系统。
 * 这个文件系统的抽象非常简单，可用的操作集非常有限，以支持广泛的文件系统的公分母。
 * 例如，不支持追加或改变现有文件。
 *
 * <p>Flink implements and supports some file system types directly (for example the default
 * machine-local file system). Other file system types are accessed by an implementation that
 * bridges to the suite of file systems supported by Hadoop (such as for example HDFS).
 * Flink 直接实现并支持一些文件系统类型（例如默认的机器本地文件系统）。
 * 其他文件系统类型由桥接到 Hadoop 支持的文件系统套件（例如 HDFS）的实现访问。
 *
 * <h2>Scope and Purpose</h2>
 * 范围和目的
 *
 * <p>The purpose of this abstraction is used to expose a common and well defined interface for
 * access to files. This abstraction is used both by Flink's fault tolerance mechanism (storing
 * state and recovery data) and by reusable built-in connectors (file sources / sinks).
 * 这种抽象的目的是为了公开一个通用的、定义良好的接口来访问文件。
 * Flink 的容错机制（存储状态和恢复数据）和可重用的内置连接器（文件源/接收器）都使用了这种抽象。
 *
 * <p>The purpose of this abstraction is <b>not</b> to give user programs an abstraction with
 * extreme flexibility and control across all possible file systems. That mission would be a folly,
 * as the differences in characteristics of even the most common file systems are already quite
 * large. It is expected that user programs that need specialized functionality of certain file
 * systems in their functions, operations, sources, or sinks instantiate the specialized file system
 * adapters directly.
 * 这种抽象的目的是<b>不是</b>为用户程序提供一个具有极大灵活性和控制所有可能文件系统的抽象。
 * 该任务将是愚蠢的，因为即使是最常见的文件系统的特征差异也已经很大。
 * 期望在其函数、操作、源或接收器中需要某些文件系统的专用功能的用户程序直接实例化专用文件系统适配器。
 *
 * <h2>Data Persistence Contract</h2>
 * 数据持久化合约
 *
 * <p>The FileSystem's {@link FSDataOutputStream output streams} are used to persistently store
 * data, both for results of streaming applications and for fault tolerance and recovery. It is
 * therefore crucial that the persistence semantics of these streams are well defined.
 * FileSystem 的 {@link FSDataOutputStream 输出流}用于持久存储数据，用于流式应用程序的结果以及容错和恢复。
 * 因此，明确定义这些流的持久性语义是至关重要的。
 *
 * <h3>Definition of Persistence Guarantees</h3>
 * 持久性保证的定义
 *
 * <p>Data written to an output stream is considered persistent, if two requirements are met:
 * 如果满足两个要求，则写入输出流的数据被认为是持久的：
 *
 * <ol>
 *   <li><b>Visibility Requirement:</b> It must be guaranteed that all other processes, machines,
 *       virtual machines, containers, etc. that are able to access the file see the data
 *       consistently when given the absolute file path. This requirement is similar to the
 *       <i>close-to-open</i> semantics defined by POSIX, but restricted to the file itself (by its
 *       absolute path).
 *   <li><b>可见性要求：</b> 必须保证所有其他能够访问文件的进程、机器、虚拟机、容器等在给定绝对文件路径时一致地看到数据。
 *   此要求类似于 POSIX 定义的 <i>close-to-open</i> 语义，但仅限于文件本身（通过其绝对路径）。
 *   <li><b>Durability Requirement:</b> The file system's specific durability/persistence
 *       requirements must be met. These are specific to the particular file system. For example the
 *       {@link LocalFileSystem} does not provide any durability guarantees for crashes of both
 *       hardware and operating system, while replicated distributed file systems (like HDFS)
 *       typically guarantee durability in the presence of at most <i>n</i> concurrent node
 *       failures, where <i>n</i> is the replication factor.
 *   <li><b>持久性要求：</b> 必须满足文件系统的特定持久性/持久性要求。 这些是特定于特定文件系统的。
 *   例如，{@link LocalFileSystem} 不为硬件和操作系统的崩溃提供任何持久性保证，
 *   而复制的分布式文件系统（如 HDFS）通常在最多 <i>n</i> 个并发的情况下保证持久性 节点故障，其中 <i>n</i> 是复制因子。
 * </ol>
 *
 * <p>Updates to the file's parent directory (such that the file shows up when listing the directory
 * contents) are not required to be complete for the data in the file stream to be considered
 * persistent. This relaxation is important for file systems where updates to directory contents are
 * only eventually consistent.
 * 对文件父目录的更新（例如在列出目录内容时显示文件）不需要完整，文件流中的数据就可以被视为持久的。
 * 这种放松对于目录内容更新仅最终保持一致的文件系统很重要。
 *
 * <p>The {@link FSDataOutputStream} has to guarantee data persistence for the written bytes once
 * the call to {@link FSDataOutputStream#close()} returns.
 * 一旦对 {@link FSDataOutputStream#close()} 的调用返回，
 * {@link FSDataOutputStream} 必须保证写入字节的数据持久性。
 *
 * <h3>Examples</h3>
 *
 * <h4>Fault-tolerant distributed file systems</h4>
 * 容错分布式文件系统
 *
 * <p>For <b>fault-tolerant distributed file systems</b>, data is considered persistent once it has
 * been received and acknowledged by the file system, typically by having been replicated to a
 * quorum of machines (<i>durability requirement</i>). In addition the absolute file path must be
 * visible to all other machines that will potentially access the file (<i>visibility
 * requirement</i>).
 * 对于<b>容错分布式文件系统</b>，一旦数据被文件系统接收并确认，数据就被认为是持久的，
 * 通常是通过复制到法定数量的机器（<i>持久性要求</i >)。
 * 此外，绝对文件路径必须对可能访问该文件的所有其他机器可见（<i>可见性要求</i>）。
 *
 * <p>Whether data has hit non-volatile storage on the storage nodes depends on the specific
 * guarantees of the particular file system.
 * 数据是否已命中存储节点上的非易失性存储取决于特定文件系统的具体保证。
 *
 * <p>The metadata updates to the file's parent directory are not required to have reached a
 * consistent state. It is permissible that some machines see the file when listing the parent
 * directory's contents while others do not, as long as access to the file by its absolute path is
 * possible on all nodes.
 * 对文件父目录的元数据更新不需要达到一致状态。 允许某些机器在列出父目录的内容时看到该文件，
 * 而其他机器则不会，只要在所有节点上都可以通过其绝对路径访问该文件。
 *
 * <h4>Local file systems</h4>
 *
 * <p>A <b>local file system</b> must support the POSIX <i>close-to-open</i> semantics. Because the
 * local file system does not have any fault tolerance guarantees, no further requirements exist.
 * <b>本地文件系统</b>必须支持 POSIX <i>close-to-open</i> 语义。
 * 因为本地文件系统没有任何容错保证，所以不存在进一步的要求。
 *
 * <p>The above implies specifically that data may still be in the OS cache when considered
 * persistent from the local file system's perspective. Crashes that cause the OS cache to loose
 * data are considered fatal to the local machine and are not covered by the local file system's
 * guarantees as defined by Flink.
 * 以上特别暗示，当从本地文件系统的角度考虑持久性时，数据可能仍在操作系统缓存中。
 * 导致操作系统缓存丢失数据的崩溃被认为对本地机器是致命的，并且不在 Flink 定义的本地文件系统的保证范围内。
 *
 * <p>That means that computed results, checkpoints, and savepoints that are written only to the
 * local filesystem are not guaranteed to be recoverable from the local machine's failure, making
 * local file systems unsuitable for production setups.
 * 这意味着仅写入本地文件系统的计算结果、检查点和保存点不能保证可以从本地机器的故障中恢复，
 * 从而使本地文件系统不适合生产设置。
 *
 * <h2>Updating File Contents</h2>
 *
 * <p>Many file systems either do not support overwriting contents of existing files at all, or do
 * not support consistent visibility of the updated contents in that case. For that reason, Flink's
 * FileSystem does not support appending to existing files, or seeking within output streams so that
 * previously written data could be overwritten.
 * 许多文件系统要么根本不支持覆盖现有文件的内容，要么在这种情况下不支持更新内容的一致可见性。
 * 出于这个原因，Flink 的 FileSystem 不支持附加到现有文件，或在输出流中查找，以便覆盖以前写入的数据。
 *
 * <h2>Overwriting Files</h2>
 *
 * <p>Overwriting files is in general possible. A file is overwritten by deleting it and creating a
 * new file. However, certain filesystems cannot make that change synchronously visible to all
 * parties that have access to the file. For example <a
 * href="https://aws.amazon.com/documentation/s3/">Amazon S3</a> guarantees only <i>eventual
 * consistency</i> in the visibility of the file replacement: Some machines may see the old file,
 * some machines may see the new file.
 * 覆盖文件通常是可能的。 通过删除文件并创建新文件来覆盖文件。
 * 但是，某些文件系统无法使所有有权访问该文件的各方同步看到该更改。
 * 例如，<a href="https://aws.amazon.com/documentation/s3/">Amazon S3</a>
 * 仅保证文件替换可见性的<i>最终一致性</i>：某些机器 可能会看到旧文件，有些机器可能会看到新文件。
 *
 * <p>To avoid these consistency issues, the implementations of failure/recovery mechanisms in Flink
 * strictly avoid writing to the same file path more than once.
 * 为了避免这些一致性问题，Flink 中故障/恢复机制的实现严格避免多次写入同一文件路径。
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Implementations of {@code FileSystem} must be thread-safe: The same instance of FileSystem is
 * frequently shared across multiple threads in Flink and must be able to concurrently create
 * input/output streams and list file metadata.
 * {@code FileSystem} 的实现必须是线程安全的：同一个 FileSystem 实例经常在 Flink 中的多个线程之间共享，并且必须能够同时创建输入/输出流和列出文件元数据。
 *
 * <p>The {@link FSDataInputStream} and {@link FSDataOutputStream} implementations are strictly
 * <b>not thread-safe</b>. Instances of the streams should also not be passed between threads in
 * between read or write operations, because there are no guarantees about the visibility of
 * operations across threads (many operations do not create memory fences).
 * {@link FSDataInputStream} 和 {@link FSDataOutputStream} 实现严格<b>不是线程安全的</b>。
 * 流的实例也不应该在读取或写入操作之间的线程之间传递，因为不能保证跨线程操作的可见性（许多操作不创建内存栅栏）。
 *
 * <h2>Streams Safety Net</h2>
 *
 * <p>When application code obtains a FileSystem (via {@link FileSystem#get(URI)} or via {@link
 * Path#getFileSystem()}), the FileSystem instantiates a safety net for that FileSystem. The safety
 * net ensures that all streams created from the FileSystem are closed when the application task
 * finishes (or is canceled or failed). That way, the task's threads do not leak connections.
 * 当应用程序代码获取文件系统（通过 {@link FileSystem#get(URI)} 或通过 {@link Path#getFileSystem()}）时，
 * 文件系统会为该文件系统实例化一个安全网。 安全网确保在应用程序任务完成（或取消或失败）时关闭从文件系统创建的所有流。
 * 这样，任务的线程就不会泄漏连接。
 *
 * <p>Internal runtime code can explicitly obtain a FileSystem that does not use the safety net via
 * {@link FileSystem#getUnguardedFileSystem(URI)}.
 * 内部运行时代码可以通过 {@link FileSystem#getUnguardedFileSystem(URI)} 显式获取不使用安全网的文件系统。
 *
 * @see FSDataInputStream
 * @see FSDataOutputStream
 */
@Public
public abstract class FileSystem {

    /**
     * The possible write modes. The write mode decides what happens if a file should be created,
     * but already exists.
     * 可能的写入模式。 写模式决定如果文件应该被创建但已经存在会发生什么。
     */
    public enum WriteMode {

        /**
         * Creates the target file only if no file exists at that path already. Does not overwrite
         * existing files and directories.
         * 仅当该路径中不存在文件时才创建目标文件。 不覆盖现有文件和目录。
         */
        NO_OVERWRITE,

        /**
         * Creates a new target file regardless of any existing files or directories. Existing files
         * and directories will be deleted (recursively) automatically before creating the new file.
         * 无论任何现有文件或目录如何，都创建一个新的目标文件。 在创建新文件之前，现有文件和目录将被自动删除（递归）。
         */
        OVERWRITE
    }

    // ------------------------------------------------------------------------

    /** Logger for all FileSystem work. */
    private static final Logger LOG = LoggerFactory.getLogger(FileSystem.class);

    /**
     * This lock guards the methods {@link #initOutPathLocalFS(Path, WriteMode, boolean)} and {@link
     * #initOutPathDistFS(Path, WriteMode, boolean)} which are otherwise susceptible to races.
     * 此锁保护方法 {@link #initOutPathLocalFS(Path, WriteMode, boolean)} 和
     * {@link #initOutPathDistFS(Path, WriteMode, boolean)} 否则容易发生竞争。
     */
    private static final ReentrantLock OUTPUT_DIRECTORY_INIT_LOCK = new ReentrantLock(true);

    /** Object used to protect calls to specific methods.
     * 用于保护对特定方法的调用的对象。
     * */
    private static final ReentrantLock LOCK = new ReentrantLock(true);

    /** Cache for file systems, by scheme + authority.
     * 按schmea+authority缓存文件系统。
     * */
    private static final HashMap<FSKey, FileSystem> CACHE = new HashMap<>();

    /**
     * Mapping of file system schemes to the corresponding factories, populated in {@link
     * FileSystem#initialize(Configuration, PluginManager)}.
     * 将文件系统方案映射到相应的工厂，填充在 {@link FileSystem#initialize(Configuration, PluginManager)} 中。
     */
    private static final HashMap<String, FileSystemFactory> FS_FACTORIES = new HashMap<>();

    /** The default factory that is used when no scheme matches.
     * 没有方案匹配时使用的默认工厂。
     * */
    private static final FileSystemFactory FALLBACK_FACTORY = loadHadoopFsFactory();

    /** All known plugins for a given scheme, do not fallback for those.
     * 给定方案的所有已知插件，不回退那些。
     * */
    private static final Multimap<String, String> DIRECTLY_SUPPORTED_FILESYSTEM =
            ImmutableMultimap.<String, String>builder()
                    .put("wasb", "flink-fs-azure-hadoop")
                    .put("wasbs", "flink-fs-azure-hadoop")
                    .put("oss", "flink-oss-fs-hadoop")
                    .put("s3", "flink-s3-fs-hadoop")
                    .put("s3", "flink-s3-fs-presto")
                    .put("s3a", "flink-s3-fs-hadoop")
                    .put("s3p", "flink-s3-fs-presto")
                    // mapr deliberately omitted for now (no dedicated plugin)
                    .build();

    /** Exceptions for DIRECTLY_SUPPORTED_FILESYSTEM. */
    private static final Set<String> ALLOWED_FALLBACK_FILESYSTEMS = new HashSet<>();

    /**
     * The default filesystem scheme to be used, configured during process-wide initialization. This
     * value defaults to the local file systems scheme {@code 'file:///'} or {@code 'file:/'}.
     * 要使用的默认文件系统方案，在进程范围初始化期间配置。
     * 此值默认为本地文件系统方案 {@code 'file:///'} 或 {@code 'file:/'}。
     */
    private static URI defaultScheme;

    // ------------------------------------------------------------------------
    //  Initialization
    // ------------------------------------------------------------------------

    /**
     * Initializes the shared file system settings.
     * 初始化共享文件系统设置。
     *
     * <p>The given configuration is passed to each file system factory to initialize the respective
     * file systems. Because the configuration of file systems may be different subsequent to the
     * call of this method, this method clears the file system instance cache.
     * 给定的配置传递给每个文件系统工厂以初始化相应的文件系统。
     * 由于调用此方法后文件系统的配置可能会有所不同，因此此方法会清除文件系统实例缓存。
     *
     * <p>This method also reads the default file system URI from the configuration key {@link
     * CoreOptions#DEFAULT_FILESYSTEM_SCHEME}. All calls to {@link FileSystem#get(URI)} where the
     * URI has no scheme will be interpreted as relative to that URI. As an example, assume the
     * default file system URI is set to {@code 'hdfs://localhost:9000/'}. A file path of {@code
     * '/user/USERNAME/in.txt'} is interpreted as {@code
     * 'hdfs://localhost:9000/user/USERNAME/in.txt'}.
     * 此方法还会从配置键 {@link CoreOptions#DEFAULT_FILESYSTEM_SCHEME} 读取默认文件系统 URI。
     * 所有对 {@link FileSystem#get(URI)} 的调用（其中 URI 没有方案）都将被解释为相对于该 URI。
     * 例如，假设默认文件系统 URI 设置为 {@code 'hdfs://localhost:9000/'}。
     * {@code '/user/USERNAME/in.txt'} 的文件路径被解释为 {@code 'hdfs://localhost:9000/user/USERNAME/in.txt'}。
     *
     * @deprecated use {@link #initialize(Configuration, PluginManager)} instead.
     * @param config the configuration from where to fetch the parameter.
     */
    @Deprecated
    public static void initialize(Configuration config) throws IllegalConfigurationException {
        initializeWithoutPlugins(config);
    }

    private static void initializeWithoutPlugins(Configuration config)
            throws IllegalConfigurationException {
        initialize(config, null);
    }

    /**
     * Initializes the shared file system settings.
     * 初始化共享文件系统设置。
     *
     * <p>The given configuration is passed to each file system factory to initialize the respective
     * file systems. Because the configuration of file systems may be different subsequent to the
     * call of this method, this method clears the file system instance cache.
     * 给定的配置传递给每个文件系统工厂以初始化相应的文件系统。
     * 由于调用此方法后文件系统的配置可能会有所不同，因此此方法会清除文件系统实例缓存。
     *
     * <p>This method also reads the default file system URI from the configuration key {@link
     * CoreOptions#DEFAULT_FILESYSTEM_SCHEME}. All calls to {@link FileSystem#get(URI)} where the
     * URI has no scheme will be interpreted as relative to that URI. As an example, assume the
     * default file system URI is set to {@code 'hdfs://localhost:9000/'}. A file path of {@code
     * '/user/USERNAME/in.txt'} is interpreted as {@code
     * 'hdfs://localhost:9000/user/USERNAME/in.txt'}.
     * 此方法还会从配置键 {@link CoreOptions#DEFAULT_FILESYSTEM_SCHEME} 读取默认文件系统 URI。
     * 所有对 {@link FileSystem#get(URI)} 的调用（其中 URI 没有方案）都将被解释为相对于该 URI。
     * 例如，假设默认文件系统 URI 设置为 {@code 'hdfs://localhost:9000/'}。
     * {@code '/user/USERNAME/in.txt'} 的文件路径被解释为 {@code 'hdfs://localhost:9000/user/USERNAME/in.txt'}。
     *
     * @param config the configuration from where to fetch the parameter.
     * @param pluginManager optional plugin manager that is used to initialized filesystems provided
     *     as plugins.
     */
    public static void initialize(Configuration config, PluginManager pluginManager)
            throws IllegalConfigurationException {

        LOCK.lock();
        try {
            // make sure file systems are re-instantiated after re-configuration
            CACHE.clear();
            FS_FACTORIES.clear();

            Collection<Supplier<Iterator<FileSystemFactory>>> factorySuppliers = new ArrayList<>(2);
            factorySuppliers.add(() -> ServiceLoader.load(FileSystemFactory.class).iterator());

            if (pluginManager != null) {
                factorySuppliers.add(
                        () ->
                                Iterators.transform(
                                        pluginManager.load(FileSystemFactory.class),
                                        PluginFileSystemFactory::of));
            }

            final List<FileSystemFactory> fileSystemFactories =
                    loadFileSystemFactories(factorySuppliers);

            // configure all file system factories
            for (FileSystemFactory factory : fileSystemFactories) {
                factory.configure(config);
                String scheme = factory.getScheme();

                FileSystemFactory fsf =
                        ConnectionLimitingFactory.decorateIfLimited(factory, scheme, config);
                FS_FACTORIES.put(scheme, fsf);
            }

            // configure the default (fallback) factory
            FALLBACK_FACTORY.configure(config);

            // also read the default file system scheme
            final String stringifiedUri =
                    config.getString(CoreOptions.DEFAULT_FILESYSTEM_SCHEME, null);
            if (stringifiedUri == null) {
                defaultScheme = null;
            } else {
                try {
                    defaultScheme = new URI(stringifiedUri);
                } catch (URISyntaxException e) {
                    throw new IllegalConfigurationException(
                            "The default file system scheme ('"
                                    + CoreOptions.DEFAULT_FILESYSTEM_SCHEME
                                    + "') is invalid: "
                                    + stringifiedUri,
                            e);
                }
            }
            //允许回滚的文件系统
            ALLOWED_FALLBACK_FILESYSTEMS.clear();
            final Iterable<String> allowedFallbackFilesystems =
                    Splitter.on(';')
                            .omitEmptyStrings()
                            .trimResults()
                            .split(config.getString(CoreOptions.ALLOWED_FALLBACK_FILESYSTEMS));
            allowedFallbackFilesystems.forEach(ALLOWED_FALLBACK_FILESYSTEMS::add);
        } finally {
            LOCK.unlock();
        }
    }

    // ------------------------------------------------------------------------
    //  Obtaining File System Instances
    // ------------------------------------------------------------------------

    /**
     * Returns a reference to the {@link FileSystem} instance for accessing the local file system.
     * 返回对 {@link FileSystem} 实例的引用，用于访问本地文件系统。
     *
     * @return a reference to the {@link FileSystem} instance for accessing the local file system.
     */
    public static FileSystem getLocalFileSystem() {
        return FileSystemSafetyNet.wrapWithSafetyNetWhenActivated(
                LocalFileSystem.getSharedInstance());
    }

    /**
     * Returns a reference to the {@link FileSystem} instance for accessing the file system
     * identified by the given {@link URI}.
     * 返回对 {@link FileSystem} 实例的引用，用于访问由给定 {@link URI} 标识的文件系统。
     *
     * @param uri the {@link URI} identifying the file system
     * @return a reference to the {@link FileSystem} instance for accessing the file system
     *     identified by the given {@link URI}.
     * @throws IOException thrown if a reference to the file system instance could not be obtained
     */
    public static FileSystem get(URI uri) throws IOException {
        return FileSystemSafetyNet.wrapWithSafetyNetWhenActivated(getUnguardedFileSystem(uri));
    }

    @Internal
    public static FileSystem getUnguardedFileSystem(final URI fsUri) throws IOException {
        checkNotNull(fsUri, "file system URI");

        LOCK.lock();
        try {
            final URI uri;

            if (fsUri.getScheme() != null) {
                uri = fsUri;
            } else {
                // Apply the default fs scheme
                final URI defaultUri = getDefaultFsUri();
                URI rewrittenUri = null;

                try {
                    rewrittenUri =
                            new URI(
                                    defaultUri.getScheme(),
                                    null,
                                    defaultUri.getHost(),
                                    defaultUri.getPort(),
                                    fsUri.getPath(),
                                    null,
                                    null);
                } catch (URISyntaxException e) {
                    // for local URIs, we make one more try to repair the path by making it absolute
                    if (defaultUri.getScheme().equals("file")) {
                        try {
                            rewrittenUri =
                                    new URI(
                                            "file",
                                            null,
                                            new Path(new File(fsUri.getPath()).getAbsolutePath())
                                                    .toUri()
                                                    .getPath(),
                                            null);
                        } catch (URISyntaxException ignored) {
                            // could not help it...
                        }
                    }
                }

                if (rewrittenUri != null) {
                    uri = rewrittenUri;
                } else {
                    throw new IOException(
                            "The file system URI '"
                                    + fsUri
                                    + "' declares no scheme and cannot be interpreted relative to the default file system URI ("
                                    + defaultUri
                                    + ").");
                }
            }

            // print a helpful pointer for malformed local URIs (happens a lot to new users)
            if (uri.getScheme().equals("file")
                    && uri.getAuthority() != null
                    && !uri.getAuthority().isEmpty()) {
                String supposedUri = "file:///" + uri.getAuthority() + uri.getPath();

                throw new IOException(
                        "Found local file path with authority '"
                                + uri.getAuthority()
                                + "' in path '"
                                + uri.toString()
                                + "'. Hint: Did you forget a slash? (correct path would be '"
                                + supposedUri
                                + "')");
            }

            final FSKey key = new FSKey(uri.getScheme(), uri.getAuthority());

            // See if there is a file system object in the cache
            {
                FileSystem cached = CACHE.get(key);
                if (cached != null) {
                    return cached;
                }
            }

            // this "default" initialization makes sure that the FileSystem class works
            // even when not configured with an explicit Flink configuration, like on
            // JobManager or TaskManager setup
            if (FS_FACTORIES.isEmpty()) {
                initializeWithoutPlugins(new Configuration());
            }

            // Try to create a new file system
            final FileSystem fs;
            final FileSystemFactory factory = FS_FACTORIES.get(uri.getScheme());

            if (factory != null) {
                ClassLoader classLoader = factory.getClassLoader();
                try (TemporaryClassLoaderContext ignored =
                        TemporaryClassLoaderContext.of(classLoader)) {
                    fs = factory.create(uri);
                }
            } else if (!ALLOWED_FALLBACK_FILESYSTEMS.contains(uri.getScheme())
                    && DIRECTLY_SUPPORTED_FILESYSTEM.containsKey(uri.getScheme())) {
                final Collection<String> plugins =
                        DIRECTLY_SUPPORTED_FILESYSTEM.get(uri.getScheme());
                throw new UnsupportedFileSystemSchemeException(
                        String.format(
                                "Could not find a file system implementation for scheme '%s'. The scheme is "
                                        + "directly supported by Flink through the following plugin%s: %s. Please ensure that each "
                                        + "plugin resides within its own subfolder within the plugins directory. See https://ci.apache"
                                        + ".org/projects/flink/flink-docs-stable/ops/plugins.html for more information. If you want to "
                                        + "use a Hadoop file system for that scheme, please add the scheme to the configuration fs"
                                        + ".allowed-fallback-filesystems. For a full list of supported file systems, "
                                        + "please see https://ci.apache.org/projects/flink/flink-docs-stable/ops/filesystems/.",
                                uri.getScheme(),
                                plugins.size() == 1 ? "" : "s",
                                String.join(", ", plugins)));
            } else {
                try {
                    fs = FALLBACK_FACTORY.create(uri);
                } catch (UnsupportedFileSystemSchemeException e) {
                    throw new UnsupportedFileSystemSchemeException(
                            "Could not find a file system implementation for scheme '"
                                    + uri.getScheme()
                                    + "'. The scheme is not directly supported by Flink and no Hadoop file system to "
                                    + "support this scheme could be loaded. For a full list of supported file systems, "
                                    + "please see https://ci.apache.org/projects/flink/flink-docs-stable/ops/filesystems/.",
                            e);
                }
            }

            CACHE.put(key, fs);
            return fs;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Gets the default file system URI that is used for paths and file systems that do not specify
     * and explicit scheme.
     * 获取用于未指定显式方案的路径和文件系统的默认文件系统 URI。
     *
     * <p>As an example, assume the default file system URI is set to {@code
     * 'hdfs://someserver:9000/'}. A file path of {@code '/user/USERNAME/in.txt'} is interpreted as
     * {@code 'hdfs://someserver:9000/user/USERNAME/in.txt'}.
     * 例如，假设默认文件系统 URI 设置为 {@code 'hdfs://someserver:9000/'}。
     * {@code '/user/USERNAME/in.txt'} 的文件路径被解释为 {@code 'hdfs://someserver:9000/user/USERNAME/in.txt'}。
     *
     * @return The default file system URI
     */
    public static URI getDefaultFsUri() {
        return defaultScheme != null ? defaultScheme : LocalFileSystem.getLocalFsURI();
    }

    // ------------------------------------------------------------------------
    //  File System Methods
    // ------------------------------------------------------------------------

    /**
     * Returns the path of the file system's current working directory.
     * 返回文件系统当前工作目录的路径。
     *
     * @return the path of the file system's current working directory
     */
    public abstract Path getWorkingDirectory();

    /**
     * Returns the path of the user's home directory in this file system.
     * 返回此文件系统中用户主目录的路径。
     *
     * @return the path of the user's home directory in this file system.
     */
    public abstract Path getHomeDirectory();

    /**
     * Returns a URI whose scheme and authority identify this file system.
     * 返回其方案和权限标识此文件系统的 URI。
     *
     * @return a URI whose scheme and authority identify this file system
     */
    public abstract URI getUri();

    /**
     * Return a file status object that represents the path.
     * 返回表示路径的文件状态对象。
     *
     * @param f The path we want information from
     * @return a FileStatus object
     * @throws FileNotFoundException when the path does not exist; IOException see specific
     *     implementation
     */
    public abstract FileStatus getFileStatus(Path f) throws IOException;

    /**
     * Return an array containing hostnames, offset and size of portions of the given file. For a
     * nonexistent file or regions, null will be returned. This call is most helpful with DFS, where
     * it returns hostnames of machines that contain the given file. The FileSystem will simply
     * return an elt containing 'localhost'.
     * 返回一个包含主机名、偏移量和给定文件部分大小的数组。 对于不存在的文件或区域，将返回 null。
     * 此调用对 DFS 最有帮助，它返回包含给定文件的机器的主机名。 FileSystem 将简单地返回一个包含“localhost”的 elt。
     */
    public abstract BlockLocation[] getFileBlockLocations(FileStatus file, long start, long len)
            throws IOException;

    /**
     * Opens an FSDataInputStream at the indicated Path.
     * 在指定的路径上打开一个 FSDataInputStream。
     *
     * @param f the file name to open
     * @param bufferSize the size of the buffer to be used.
     */
    public abstract FSDataInputStream open(Path f, int bufferSize) throws IOException;

    /**
     * Opens an FSDataInputStream at the indicated Path.
     * 在指定的路径上打开一个 FSDataInputStream。
     *
     * @param f the file to open
     */
    public abstract FSDataInputStream open(Path f) throws IOException;

    /**
     * Creates a new {@link RecoverableWriter}. A recoverable writer creates streams that can
     * persist and recover their intermediate state. Persisting and recovering intermediate state is
     * a core building block for writing to files that span multiple checkpoints.
     * 创建一个新的 {@link RecoverableWriter}。 可恢复的编写器创建可以持久化和恢复其中间状态的流。
     * 持久化和恢复中间状态是写入跨越多个检查点的文件的核心构建块。
     *
     * <p>The returned object can act as a shared factory to open and recover multiple streams.
     * 返回的对象可以作为一个共享工厂来打开和恢复多个流。
     *
     * <p>This method is optional on file systems and various file system implementations may not
     * support this method, throwing an {@code UnsupportedOperationException}.
     * 此方法在文件系统上是可选的，并且各种文件系统实现可能不支持此方法，从而抛出 {@code UnsupportedOperationException}。
     *
     * @return A RecoverableWriter for this file system.
     * @throws IOException Thrown, if the recoverable writer cannot be instantiated.
     */
    public RecoverableWriter createRecoverableWriter() throws IOException {
        throw new UnsupportedOperationException(
                "This file system does not support recoverable writers.");
    }

    /**
     * Return the number of bytes that large input files should be optimally be split into to
     * minimize I/O time.
     * 返回大输入文件应该被最佳分割成的字节数以最小化 I/O 时间。
     *
     * @return the number of bytes that large input files should be optimally be split into to
     *     minimize I/O time
     * @deprecated This value is no longer used and is meaningless.
     */
    @Deprecated
    public long getDefaultBlockSize() {
        return 32 * 1024 * 1024; // 32 MB;
    }

    /**
     * List the statuses of the files/directories in the given path if the path is a directory.
     * 如果路径是目录，则列出给定路径中文件/目录的状态。
     *
     * @param f given path
     * @return the statuses of the files/directories in the given path
     * @throws IOException
     */
    public abstract FileStatus[] listStatus(Path f) throws IOException;

    /**
     * Check if exists.
     *
     * @param f source file
     */
    public boolean exists(final Path f) throws IOException {
        try {
            return (getFileStatus(f) != null);
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    /**
     * Delete a file.
     *
     * @param f the path to delete
     * @param recursive if path is a directory and set to <code>true</code>, the directory is
     *     deleted else throws an exception. In case of a file the recursive can be set to either
     *     <code>true</code> or <code>false</code>
     * @return <code>true</code> if delete is successful, <code>false</code> otherwise
     * @throws IOException
     */
    public abstract boolean delete(Path f, boolean recursive) throws IOException;

    /**
     * Make the given file and all non-existent parents into directories. Has the semantics of Unix
     * 'mkdir -p'. Existence of the directory hierarchy is not an error.
     * 将给定的文件和所有不存在的父文件放入目录中。 具有 Unix 'mkdir -p' 的语义。 目录层次结构的存在不是错误。
     *
     * @param f the directory/directories to be created
     * @return <code>true</code> if at least one new directory has been created, <code>false</code>
     *     otherwise
     * @throws IOException thrown if an I/O error occurs while creating the directory
     */
    public abstract boolean mkdirs(Path f) throws IOException;

    /**
     * Opens an FSDataOutputStream at the indicated Path.
     * 在指定的路径打开一个 FSDataOutputStream。
     *
     * <p>This method is deprecated, because most of its parameters are ignored by most file
     * systems. To control for example the replication factor and block size in the Hadoop
     * Distributed File system, make sure that the respective Hadoop configuration file is either
     * linked from the Flink configuration, or in the classpath of either Flink or the user code.
     * 此方法已被弃用，因为大多数文件系统都忽略了它的大部分参数。
     * 例如，要控制 Hadoop 分布式文件系统中的复制因子和块大小，请确保相应的 Hadoop 配置文件是从 Flink 配置链接的，
     * 或者在 Flink 或用户代码的类路径中。
     *
     * @param f the file name to open
     * @param overwrite if a file with this name already exists, then if true, the file will be
     *     overwritten, and if false an error will be thrown.
     * @param bufferSize the size of the buffer to be used.
     * @param replication required block replication for the file.
     * @param blockSize the size of the file blocks
     * @throws IOException Thrown, if the stream could not be opened because of an I/O, or because a
     *     file already exists at that path and the write mode indicates to not overwrite the file.
     * @deprecated Deprecated because not well supported across types of file systems. Control the
     *     behavior of specific file systems via configurations instead.
     */
    @Deprecated
    public FSDataOutputStream create(
            Path f, boolean overwrite, int bufferSize, short replication, long blockSize)
            throws IOException {

        return create(f, overwrite ? WriteMode.OVERWRITE : WriteMode.NO_OVERWRITE);
    }

    /**
     * Opens an FSDataOutputStream at the indicated Path.
     * 在指定的路径打开一个 FSDataOutputStream。
     *
     * @param f the file name to open
     * @param overwrite if a file with this name already exists, then if true, the file will be
     *     overwritten, and if false an error will be thrown.
     * @throws IOException Thrown, if the stream could not be opened because of an I/O, or because a
     *     file already exists at that path and the write mode indicates to not overwrite the file.
     * @deprecated Use {@link #create(Path, WriteMode)} instead.
     */
    @Deprecated
    public FSDataOutputStream create(Path f, boolean overwrite) throws IOException {
        return create(f, overwrite ? WriteMode.OVERWRITE : WriteMode.NO_OVERWRITE);
    }

    /**
     * Opens an FSDataOutputStream to a new file at the given path.
     * 在给定路径打开一个 FSDataOutputStream 到一个新文件。
     *
     * <p>If the file already exists, the behavior depends on the given {@code WriteMode}. If the
     * mode is set to {@link WriteMode#NO_OVERWRITE}, then this method fails with an exception.
     * 如果文件已存在，则行为取决于给定的 {@code WriteMode}。 如果模式设置为 {@link WriteMode#NO_OVERWRITE}，则此方法失败并出现异常。
     *
     * @param f The file path to write to
     * @param overwriteMode The action to take if a file or directory already exists at the given
     *     path.
     * @return The stream to the new file at the target path.
     * @throws IOException Thrown, if the stream could not be opened because of an I/O, or because a
     *     file already exists at that path and the write mode indicates to not overwrite the file.
     */
    public abstract FSDataOutputStream create(Path f, WriteMode overwriteMode) throws IOException;

    /**
     * Renames the file/directory src to dst.
     * 将文件/目录 src 重命名为 dst。
     *
     * @param src the file/directory to rename
     * @param dst the new name of the file/directory
     * @return <code>true</code> if the renaming was successful, <code>false</code> otherwise
     * @throws IOException
     */
    public abstract boolean rename(Path src, Path dst) throws IOException;

    /**
     * Returns true if this is a distributed file system. A distributed file system here means that
     * the file system is shared among all Flink processes that participate in a cluster or job and
     * that all these processes can see the same files.
     * 如果这是分布式文件系统，则返回 true。
     * 这里的分布式文件系统意味着文件系统在所有参与集群或作业的 Flink 进程之间共享，并且所有这些进程都可以看到相同的文件。
     *
     * @return True, if this is a distributed file system, false otherwise.
     */
    public abstract boolean isDistributedFS();

    /**
     * Gets a description of the characteristics of this file system.
     * 获取此文件系统特征的描述。
     *
     * @deprecated this method is not used anymore.
     */
    @Deprecated
    public abstract FileSystemKind getKind();

    // ------------------------------------------------------------------------
    //  output directory initialization
    // ------------------------------------------------------------------------

    /**
     * Initializes output directories on local file systems according to the given write mode.
     * 根据给定的写入模式初始化本地文件系统上的输出目录。
     *
     * <ul>
     *   <li>WriteMode.NO_OVERWRITE &amp; parallel output:
     *       <ul>
     *         <li>A directory is created if the output path does not exist.
     *         <li>An existing directory is reused, files contained in the directory are NOT
     *             deleted.
     *         <li>An existing file raises an exception.
     *       </ul>
     *   <li>WriteMode.NO_OVERWRITE &amp; NONE parallel output:
     *       <ul>
     *         <li>An existing file or directory raises an exception.
     *       </ul>
     *   <li>WriteMode.OVERWRITE &amp; parallel output:
     *       <ul>
     *         <li>A directory is created if the output path does not exist.
     *         <li>An existing directory is reused, files contained in the directory are NOT
     *             deleted.
     *         <li>An existing file is deleted and replaced by a new directory.
     *       </ul>
     *   <li>WriteMode.OVERWRITE &amp; NONE parallel output:
     *       <ul>
     *         <li>An existing file or directory (and all its content) is deleted
     *       </ul>
     * </ul>
     * <ul>
     *     <li>WriteMode.NO_OVERWRITE &amp; 并行输出：
     *         <ul>
     *           <li>如果输出路径不存在，则会创建一个目录。
     *           <li>现有目录被重用，目录中包含的文件不会被删除。
     *           <li>现有文件引发异常。
     *         </ul>
     *     <li>WriteMode.NO_OVERWRITE &amp; NONE 并行输出：
     *         <ul>
     *           <li>现有文件或目录引发异常。
     *         </ul>
     *     <li>WriteMode.OVERWRITE &amp; 并行输出：
     *         <ul>
     *           <li>如果输出路径不存在，则会创建一个目录。
     *           <li>现有目录被重用，目录中包含的文件不会被删除。
     *           <li>现有文件被删除并替换为新目录。
     *         </ul>
     *     <li>WriteMode.OVERWRITE &amp; NONE 并行输出：
     *         <ul>
     *           <li>删除现有文件或目录（及其所有内容）
     *         </ul>
     *  </ul>
     *
     * <p>Files contained in an existing directory are not deleted, because multiple instances of a
     * DataSinkTask might call this function at the same time and hence might perform concurrent
     * delete operations on the file system (possibly deleting output files of concurrently running
     * tasks). Since concurrent DataSinkTasks are not aware of each other, coordination of delete
     * and create operations would be difficult.
     * 包含在现有目录中的文件不会被删除，因为 DataSinkTask 的多个实例可能会同时调用此函数，
     * 因此可能会在文件系统上执行并发删除操作（可能删除并发运行任务的输出文件）。
     * 由于并发 DataSinkTask 彼此不知道，因此删除和创建操作的协调将很困难。
     *
     * @param outPath Output path that should be prepared.
     * @param writeMode Write mode to consider.
     * @param createDirectory True, to initialize a directory at the given path, false to prepare
     *     space for a file.
     * @return True, if the path was successfully prepared, false otherwise.
     * @throws IOException Thrown, if any of the file system access operations failed.
     */
    public boolean initOutPathLocalFS(Path outPath, WriteMode writeMode, boolean createDirectory)
            throws IOException {
        if (isDistributedFS()) {
            return false;
        }

        // NOTE: We actually need to lock here (process wide). Otherwise, multiple threads that
        // concurrently work in this method (multiple output formats writing locally) might end
        // up deleting each other's directories and leave non-retrievable files, without necessarily
        // causing an exception. That results in very subtle issues, like output files looking as if
        // they are not getting created.

        // we acquire the lock interruptibly here, to make sure that concurrent threads waiting
        // here can cancel faster
        try {
            OUTPUT_DIRECTORY_INIT_LOCK.lockInterruptibly();
        } catch (InterruptedException e) {
            // restore the interruption state
            Thread.currentThread().interrupt();

            // leave the method - we don't have the lock anyways
            throw new IOException(
                    "The thread was interrupted while trying to initialize the output directory");
        }

        try {
            FileStatus status;
            try {
                status = getFileStatus(outPath);
            } catch (FileNotFoundException e) {
                // okay, the file is not there
                status = null;
            }

            // check if path exists
            if (status != null) {
                // path exists, check write mode
                switch (writeMode) {
                    case NO_OVERWRITE:
                        if (status.isDir() && createDirectory) {
                            return true;
                        } else {
                            // file may not be overwritten
                            throw new IOException(
                                    "File or directory "
                                            + outPath
                                            + " already exists. Existing files and directories "
                                            + "are not overwritten in "
                                            + WriteMode.NO_OVERWRITE.name()
                                            + " mode. Use "
                                            + WriteMode.OVERWRITE.name()
                                            + " mode to overwrite existing files and directories.");
                        }

                    case OVERWRITE:
                        if (status.isDir()) {
                            if (createDirectory) {
                                // directory exists and does not need to be created
                                return true;
                            } else {
                                // we will write in a single file, delete directory
                                try {
                                    delete(outPath, true);
                                } catch (IOException e) {
                                    throw new IOException(
                                            "Could not remove existing directory '"
                                                    + outPath
                                                    + "' to allow overwrite by result file",
                                            e);
                                }
                            }
                        } else {
                            // delete file
                            try {
                                delete(outPath, false);
                            } catch (IOException e) {
                                throw new IOException(
                                        "Could not remove existing file '"
                                                + outPath
                                                + "' to allow overwrite by result file/directory",
                                        e);
                            }
                        }
                        break;

                    default:
                        throw new IllegalArgumentException("Invalid write mode: " + writeMode);
                }
            }

            if (createDirectory) {
                // Output directory needs to be created
                if (!exists(outPath)) {
                    mkdirs(outPath);
                }

                // double check that the output directory exists
                try {
                    return getFileStatus(outPath).isDir();
                } catch (FileNotFoundException e) {
                    return false;
                }
            } else {
                // check that the output path does not exist and an output file
                // can be created by the output format.
                return !exists(outPath);
            }
        } finally {
            OUTPUT_DIRECTORY_INIT_LOCK.unlock();
        }
    }

    /**
     * Initializes output directories on distributed file systems according to the given write mode.
     * 根据给定的写入模式初始化分布式文件系统上的输出目录。
     *
     * <p>WriteMode.NO_OVERWRITE &amp; parallel output: - A directory is created if the output path
     * does not exist. - An existing file or directory raises an exception.
     * WriteMode.NO_OVERWRITE &amp; 并行输出： - 如果输出路径不存在，则创建一个目录。 - 现有文件或目录引发异常。
     *
     * <p>WriteMode.NO_OVERWRITE &amp; NONE parallel output: - An existing file or directory raises
     * an exception.
     * WriteMode.NO_OVERWRITE &amp; NONE 并行输出： - 现有文件或目录引发异常。
     *
     * <p>WriteMode.OVERWRITE &amp; parallel output: - A directory is created if the output path
     * does not exist. - An existing directory and its content is deleted and a new directory is
     * created. - An existing file is deleted and replaced by a new directory.
     * WriteMode.OVERWRITE &amp; 并行输出： - 如果输出路径不存在，则创建一个目录。
     * - 删除现有目录及其内容并创建新目录。
     * - 现有文件被删除并替换为新目录。
     *
     * <p>WriteMode.OVERWRITE &amp; NONE parallel output: - An existing file or directory is deleted
     * and replaced by a new directory.
     * WriteMode.OVERWRITE &amp; NONE 并行输出： - 现有文件或目录被删除并替换为新目录。
     *
     * @param outPath Output path that should be prepared.
     * @param writeMode Write mode to consider.
     * @param createDirectory True, to initialize a directory at the given path, false otherwise.
     * @return True, if the path was successfully prepared, false otherwise.
     * @throws IOException Thrown, if any of the file system access operations failed.
     */
    public boolean initOutPathDistFS(Path outPath, WriteMode writeMode, boolean createDirectory)
            throws IOException {
        if (!isDistributedFS()) {
            return false;
        }

        // NOTE: We actually need to lock here (process wide). Otherwise, multiple threads that
        // concurrently work in this method (multiple output formats writing locally) might end
        // up deleting each other's directories and leave non-retrievable files, without necessarily
        // causing an exception. That results in very subtle issues, like output files looking as if
        // they are not getting created.

        // we acquire the lock interruptibly here, to make sure that concurrent threads waiting
        // here can cancel faster
        try {
            OUTPUT_DIRECTORY_INIT_LOCK.lockInterruptibly();
        } catch (InterruptedException e) {
            // restore the interruption state
            Thread.currentThread().interrupt();

            // leave the method - we don't have the lock anyways
            throw new IOException(
                    "The thread was interrupted while trying to initialize the output directory");
        }

        try {
            // check if path exists
            if (exists(outPath)) {
                // path exists, check write mode
                switch (writeMode) {
                    case NO_OVERWRITE:
                        // file or directory may not be overwritten
                        throw new IOException(
                                "File or directory already exists. Existing files and directories are not overwritten in "
                                        + WriteMode.NO_OVERWRITE.name()
                                        + " mode. Use "
                                        + WriteMode.OVERWRITE.name()
                                        + " mode to overwrite existing files and directories.");

                    case OVERWRITE:
                        // output path exists. We delete it and all contained files in case of a
                        // directory.
                        try {
                            delete(outPath, true);
                        } catch (IOException e) {
                            // Some other thread might already have deleted the path.
                            // If - for some other reason - the path could not be deleted,
                            // this will be handled later.
                        }
                        break;

                    default:
                        throw new IllegalArgumentException("Invalid write mode: " + writeMode);
                }
            }

            if (createDirectory) {
                // Output directory needs to be created
                try {
                    if (!exists(outPath)) {
                        mkdirs(outPath);
                    }
                } catch (IOException ioe) {
                    // Some other thread might already have created the directory.
                    // If - for some other reason - the directory could not be created
                    // and the path does not exist, this will be handled later.
                }

                // double check that the output directory exists
                return exists(outPath) && getFileStatus(outPath).isDir();
            } else {
                // single file case: check that the output path does not exist and
                // an output file can be created by the output format.
                return !exists(outPath);
            }
        } finally {
            OUTPUT_DIRECTORY_INIT_LOCK.unlock();
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Loads the factories for the file systems directly supported by Flink. Aside from the {@link
     * LocalFileSystem}, these file systems are loaded via Java's service framework.
     * 加载 Flink 直接支持的文件系统的工厂。 除了 {@link LocalFileSystem}，这些文件系统是通过 Java 的服务框架加载的。
     *
     * @return A map from the file system scheme to corresponding file system factory.
     */
    private static List<FileSystemFactory> loadFileSystemFactories(
            Collection<Supplier<Iterator<FileSystemFactory>>> factoryIteratorsSuppliers) {

        final ArrayList<FileSystemFactory> list = new ArrayList<>();

        // by default, we always have the local file system factory
        list.add(new LocalFileSystemFactory());

        LOG.debug("Loading extension file systems via services");

        for (Supplier<Iterator<FileSystemFactory>> factoryIteratorsSupplier :
                factoryIteratorsSuppliers) {
            try {
                addAllFactoriesToList(factoryIteratorsSupplier.get(), list);
            } catch (Throwable t) {
                // catching Throwable here to handle various forms of class loading
                // and initialization errors
                ExceptionUtils.rethrowIfFatalErrorOrOOM(t);
                LOG.error("Failed to load additional file systems via services", t);
            }
        }

        return Collections.unmodifiableList(list);
    }

    private static void addAllFactoriesToList(
            Iterator<FileSystemFactory> iter, List<FileSystemFactory> list) {
        // we explicitly use an iterator here (rather than for-each) because that way
        // we can catch errors in individual service instantiations

        while (iter.hasNext()) {
            try {
                FileSystemFactory factory = iter.next();
                list.add(factory);
                LOG.debug(
                        "Added file system {}:{}",
                        factory.getScheme(),
                        factory.getClass().getSimpleName());
            } catch (Throwable t) {
                // catching Throwable here to handle various forms of class loading
                // and initialization errors
                ExceptionUtils.rethrowIfFatalErrorOrOOM(t);
                LOG.error("Failed to load a file system via services", t);
            }
        }
    }

    /**
     * Utility loader for the Hadoop file system factory. We treat the Hadoop FS factory in a
     * special way, because we use it as a catch all for file systems schemes not supported directly
     * in Flink.
     * Hadoop 文件系统工厂的实用程序加载程序。
     * 我们以一种特殊的方式对待 Hadoop FS 工厂，因为我们将其用作 Flink 不直接支持的文件系统方案的全部。
     *
     * <p>This method does a set of eager checks for availability of certain classes, to be able to
     * give better error messages.
     * 此方法对某些类的可用性进行一组急切检查，以便能够提供更好的错误消息。
     */
    private static FileSystemFactory loadHadoopFsFactory() {
        final ClassLoader cl = FileSystem.class.getClassLoader();

        // first, see if the Flink runtime classes are available
        final Class<? extends FileSystemFactory> factoryClass;
        try {
            factoryClass =
                    Class.forName("org.apache.flink.runtime.fs.hdfs.HadoopFsFactory", false, cl)
                            .asSubclass(FileSystemFactory.class);
        } catch (ClassNotFoundException e) {
            LOG.info(
                    "No Flink runtime dependency present. "
                            + "The extended set of supported File Systems via Hadoop is not available.");
            return new UnsupportedSchemeFactory(
                    "Flink runtime classes missing in classpath/dependencies.");
        } catch (Exception | LinkageError e) {
            LOG.warn("Flink's Hadoop file system factory could not be loaded", e);
            return new UnsupportedSchemeFactory(
                    "Flink's Hadoop file system factory could not be loaded", e);
        }

        // check (for eager and better exception messages) if the Hadoop classes are available here
        try {
            Class.forName("org.apache.hadoop.conf.Configuration", false, cl);
            Class.forName("org.apache.hadoop.fs.FileSystem", false, cl);
        } catch (ClassNotFoundException e) {
            LOG.info(
                    "Hadoop is not in the classpath/dependencies. "
                            + "The extended set of supported File Systems via Hadoop is not available.");
            return new UnsupportedSchemeFactory("Hadoop is not in the classpath/dependencies.");
        }

        // Create the factory.
        try {
            return factoryClass.newInstance();
        } catch (Exception | LinkageError e) {
            LOG.warn("Flink's Hadoop file system factory could not be created", e);
            return new UnsupportedSchemeFactory(
                    "Flink's Hadoop file system factory could not be created", e);
        }
    }

    // ------------------------------------------------------------------------

    /** An identifier of a file system, via its scheme and its authority.
     * 文件系统的标识符，通过其方案和权限。
     * */
    private static final class FSKey {

        /** The scheme of the file system. */
        private final String scheme;

        /** The authority of the file system. */
        @Nullable private final String authority;

        /**
         * Creates a file system key from a given scheme and an authority.
         * 根据给定的方案和权限创建文件系统密钥。
         *
         * @param scheme The scheme of the file system
         * @param authority The authority of the file system
         */
        public FSKey(String scheme, @Nullable String authority) {
            this.scheme = checkNotNull(scheme, "scheme");
            this.authority = authority;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            } else if (obj != null && obj.getClass() == FSKey.class) {
                final FSKey that = (FSKey) obj;
                return this.scheme.equals(that.scheme)
                        && (this.authority == null
                                ? that.authority == null
                                : (that.authority != null
                                        && this.authority.equals(that.authority)));
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return 31 * scheme.hashCode() + (authority == null ? 17 : authority.hashCode());
        }

        @Override
        public String toString() {
            return scheme + "://" + (authority != null ? authority : "");
        }
    }
}
