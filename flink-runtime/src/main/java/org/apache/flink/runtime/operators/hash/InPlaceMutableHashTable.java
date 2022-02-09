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

package org.apache.flink.runtime.operators.hash;

import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.typeutils.SameTypePairComparator;
import org.apache.flink.api.common.typeutils.TypeComparator;
import org.apache.flink.api.common.typeutils.TypePairComparator;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.runtime.io.disk.RandomAccessInputView;
import org.apache.flink.runtime.memory.AbstractPagedOutputView;
import org.apache.flink.util.Collector;
import org.apache.flink.util.MathUtils;
import org.apache.flink.util.MutableObjectIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This hash table supports updating elements. If the new element has the same size as the old
 * element, then the update is done in-place. Otherwise a hole is created at the place of the old
 * record, which will eventually be removed by a compaction.
 * 此哈希表支持更新元素。 如果新元素与旧元素具有相同的大小，则更新就地完成。
 * 否则会在旧记录的位置创建一个洞，最终将通过压缩将其删除。
 *
 * <p>The memory is divided into three areas: - Bucket area: they contain bucket heads: an 8 byte
 * pointer to the first link of a linked list in the record area - Record area: this contains the
 * actual data in linked list elements. A linked list element starts with an 8 byte pointer to the
 * next element, and then the record follows. - Staging area: This is a small, temporary storage
 * area for writing updated records. This is needed, because before serializing a record, there is
 * no way to know in advance how large will it be. Therefore, we can't serialize directly into the
 * record area when we are doing an update, because if it turns out to be larger than the old
 * record, then it would override some other record that happens to be after the old one in memory.
 * The solution is to serialize to the staging area first, and then copy it to the place of the
 * original if it has the same size, otherwise allocate a new linked list element at the end of the
 * record area, and mark the old one as abandoned. This creates "holes" in the record area, so
 * compactions are eventually needed.
 * 内存分为三个区域： - 桶区：它们包含桶头：一个 8 字节指针，指向记录区中链表的第一个链接
 * - 记录区：包含链表元素中的实际数据。链表元素以指向下一个元素的 8 字节指针开始，然后是记录。
 * - 暂存区：这是一个小的临时存储区，用于写入更新的记录。这是必需的，因为在序列化记录之前，无法提前知道它有多大。
 * 因此，我们不能在更新时直接序列化到记录区，因为如果它比旧记录大，那么它将覆盖内存中恰好位于旧记录之后的其他记录。
 * 解决方法是先序列化到暂存区，如果大小相同，再复制到原来的地方，否则在记录区末尾分配一个新的链表元素，旧的标记为废弃.
 * 这会在记录区域中创建“洞”，因此最终需要进行压缩。
 *
 * <p>Compaction happens by deleting everything in the bucket area, and then reinserting all
 * elements. The reinsertion happens by forgetting the structure (the linked lists) of the record
 * area, and reading it sequentially, and inserting all non-abandoned records, starting from the
 * beginning of the record area. Note, that insertions never override a record that hasn't been read
 * by the reinsertion sweep, because both the insertions and readings happen sequentially in the
 * record area, and the insertions obviously never overtake the reading sweep.
 * 通过删除存储桶区域中的所有内容，然后重新插入所有元素来进行压缩。
 * 重新插入是通过忘记记录区的结构（链表）而发生的，并顺序读取它，并从记录区的开头插入所有未放弃的记录。
 * 请注意，插入永远不会覆盖重新插入扫描尚未读取的记录，因为插入和读取都在记录区域中顺序发生，并且插入显然永远不会超过读取扫描。
 *
 * <p>Note: we have to abandon the old linked list element even when the updated record has a
 * smaller size than the original, because otherwise we wouldn't know where the next record starts
 * during a reinsertion sweep.
 * 注意：即使更新的记录比原始记录的大小更小，我们也必须放弃旧的链表元素，
 * 因为否则我们将不知道在重新插入扫描期间下一条记录从哪里开始。
 *
 * <p>The number of buckets depends on how large are the records. The serializer might be able to
 * tell us this, so in this case, we will calculate the number of buckets upfront, and won't do
 * resizes. If the serializer doesn't know the size, then we start with a small number of buckets,
 * and do resizes as more elements are inserted than the number of buckets.
 * 桶的数量取决于记录的大小。 序列化程序可能会告诉我们这一点，所以在这种情况下，我们将预先计算存储桶的数量，并且不会调整大小。
 * 如果序列化器不知道大小，那么我们从少量的桶开始，并随着插入的元素多于桶的数量来调整大小。
 *
 * <p>The number of memory segments given to the staging area is usually one, because it just needs
 * to hold one record.
 * 分配给暂存区的内存段数通常为 1，因为它只需要保存一条记录。
 *
 * <p>Note: For hashing, we couldn't just take the lower bits, but have to use a proper hash
 * function from MathUtils because of its avalanche property, so that changing only some high bits
 * of the original value won't leave the lower bits of the hash unaffected. This is because when
 * choosing the bucket for a record, we mask only the lower bits (see numBucketsMask). Lots of
 * collisions would occur when, for example, the original value that is hashed is some bitset, where
 * lots of different values that are different only in the higher bits will actually occur.
 * 注意：对于散列，我们不能只取低位，而是必须使用 MathUtils 中的适当散列函数，因为它具有雪崩特性，
 * 因此仅更改原始值的一些高位不会留下低位 哈希不受影响。 这是因为在为记录选择存储桶时，我们只屏蔽低位（参见 numBucketsMask）。
 * 例如，当散列的原始值是某个位集时，会发生很多冲突，其中实际上会发生许多仅在较高位上不同的不同值。
 */
public class InPlaceMutableHashTable<T> extends AbstractMutableHashTable<T> {

    private static final Logger LOG = LoggerFactory.getLogger(InPlaceMutableHashTable.class);

    /**
     * The minimum number of memory segments InPlaceMutableHashTable needs to be supplied with in
     * order to work.
     * 需要提供最小数量的内存段 InPlaceMutableHashTable 才能工作。
     */
    private static final int MIN_NUM_MEMORY_SEGMENTS = 3;

    // Note: the following two constants can't be negative, because negative values are reserved for
    // storing the
    // negated size of the record, when it is abandoned (not part of any linked list).
    // 注意：以下两个常量不能为负数，因为负值保留用于存储记录的否定大小，当它被放弃时（不是任何链表的一部分）。

    /** The last link in the linked lists will have this as next pointer.
     * 链表中的最后一个链接将 this 作为下一个指针。
     * */
    private static final long END_OF_LIST = Long.MAX_VALUE;

    /**
     * This value means that prevElemPtr is "pointing to the bucket head", and not into the record
     * segments.
     * 该值意味着 prevElemPtr 是“指向桶头”，而不是指向记录段。
     */
    private static final long INVALID_PREV_POINTER = Long.MAX_VALUE - 1;

    private static final long RECORD_OFFSET_IN_LINK = 8;

    /**
     * This initially contains all the memory we have, and then segments are taken from it by
     * bucketSegments, recordArea, and stagingSegments.
     * 这最初包含我们拥有的所有内存，然后由 bucketSegments、recordArea 和 stagingSegments 从中取出段。
     */
    private final ArrayList<MemorySegment> freeMemorySegments;

    private final int numAllMemorySegments;

    private final int segmentSize;

    /**
     * These will contain the bucket heads. The bucket heads are pointers to the linked lists
     * containing the actual records.
     * 这些将包含铲斗头。 桶头是指向包含实际记录的链表的指针。
     */
    private MemorySegment[] bucketSegments;

    private static final int bucketSize = 8, bucketSizeBits = 3;

    private int numBuckets;
    private int numBucketsMask;
    private final int numBucketsPerSegment, numBucketsPerSegmentBits, numBucketsPerSegmentMask;

    /** The segments where the actual data is stored. */
    private final RecordArea recordArea;

    /** Segments for the staging area. (It should contain at most one record at all times.)
     * 分段区域。 （它应始终包含最多一条记录。）
     * */
    private final ArrayList<MemorySegment> stagingSegments;

    private final RandomAccessInputView stagingSegmentsInView;
    private final StagingOutputView stagingSegmentsOutView;

    private T reuse;

    /** This is the internal prober that insertOrReplaceRecord uses.
     * 这是 insertOrReplaceRecord 使用的内部探测器。
     * */
    private final HashTableProber<T> prober;

    /** The number of elements currently held by the table. */
    private long numElements = 0;

    /**
     * The number of bytes wasted by updates that couldn't overwrite the old record due to size
     * change.
     * 由于大小更改而无法覆盖旧记录的更新所浪费的字节数。
     */
    private long holes = 0;

    /**
     * If the serializer knows the size of the records, then we can calculate the optimal number of
     * buckets upfront, so we don't need resizes.
     * 如果序列化程序知道记录的大小，那么我们可以预先计算最佳桶数，因此我们不需要调整大小。
     */
    private boolean enableResize;

    public InPlaceMutableHashTable(
            TypeSerializer<T> serializer,
            TypeComparator<T> comparator,
            List<MemorySegment> memory) {
        super(serializer, comparator);
        this.numAllMemorySegments = memory.size();
        this.freeMemorySegments = new ArrayList<>(memory);

        // some sanity checks first
        if (freeMemorySegments.size() < MIN_NUM_MEMORY_SEGMENTS) {
            throw new IllegalArgumentException(
                    "Too few memory segments provided. InPlaceMutableHashTable needs at least "
                            + MIN_NUM_MEMORY_SEGMENTS
                            + " memory segments.");
        }

        // Get the size of the first memory segment and record it. All further buffers must have the
        // same size.
        // the size must also be a power of 2
        segmentSize = freeMemorySegments.get(0).size();
        if ((segmentSize & segmentSize - 1) != 0) {
            throw new IllegalArgumentException(
                    "Hash Table requires buffers whose size is a power of 2.");
        }

        this.numBucketsPerSegment = segmentSize / bucketSize;
        this.numBucketsPerSegmentBits = MathUtils.log2strict(this.numBucketsPerSegment);
        this.numBucketsPerSegmentMask = (1 << this.numBucketsPerSegmentBits) - 1;

        recordArea = new RecordArea(segmentSize);

        stagingSegments = new ArrayList<>();
        stagingSegments.add(forcedAllocateSegment());
        stagingSegmentsInView = new RandomAccessInputView(stagingSegments, segmentSize);
        stagingSegmentsOutView = new StagingOutputView(stagingSegments, segmentSize);

        prober =
                new HashTableProber<>(
                        buildSideComparator, new SameTypePairComparator<>(buildSideComparator));

        enableResize = buildSideSerializer.getLength() == -1;
    }

    /**
     * Gets the total capacity of this hash table, in bytes.
     *
     * @return The hash table's total capacity.
     */
    public long getCapacity() {
        return numAllMemorySegments * (long) segmentSize;
    }

    /**
     * Gets the number of bytes currently occupied in this hash table.
     * 获取此哈希表中当前占用的字节数。
     *
     * @return The number of bytes occupied.
     */
    public long getOccupancy() {
        return numAllMemorySegments * segmentSize - freeMemorySegments.size() * segmentSize;
    }

    private void open(int numBucketSegments) {
        synchronized (stateLock) {
            if (!closed) {
                throw new IllegalStateException("currently not closed.");
            }
            closed = false;
        }

        allocateBucketSegments(numBucketSegments);

        stagingSegments.add(forcedAllocateSegment());

        reuse = buildSideSerializer.createInstance();
    }

    /** Initialize the hash table */
    @Override
    public void open() {
        open(calcInitialNumBucketSegments());
    }

    @Override
    public void close() {
        // make sure that we close only once
        synchronized (stateLock) {
            if (closed) {
                // We have to do this here, because the ctor already allocates a segment to the
                // record area and
                // the staging area, even before we are opened. So we might have segments to free,
                // even if we
                // are closed.
                recordArea.giveBackSegments();
                freeMemorySegments.addAll(stagingSegments);
                stagingSegments.clear();

                return;
            }
            closed = true;
        }

        LOG.debug("Closing InPlaceMutableHashTable and releasing resources.");

        releaseBucketSegments();

        recordArea.giveBackSegments();

        freeMemorySegments.addAll(stagingSegments);
        stagingSegments.clear();

        numElements = 0;
        holes = 0;
    }

    @Override
    public void abort() {
        LOG.debug("Aborting InPlaceMutableHashTable.");
        close();
    }

    @Override
    public List<MemorySegment> getFreeMemory() {
        if (!this.closed) {
            throw new IllegalStateException(
                    "Cannot return memory while InPlaceMutableHashTable is open.");
        }

        return freeMemorySegments;
    }

    private int calcInitialNumBucketSegments() {
        int recordLength = buildSideSerializer.getLength();
        double fraction; // fraction of memory to use for the buckets
        if (recordLength == -1) {
            // We don't know the record length, so we start with a small number of buckets, and do
            // resizes if
            // necessary.
            // It seems that resizing is quite efficient, so we can err here on the too few bucket
            // segments side.
            // Even with small records, we lose only ~15% speed.
            fraction = 0.1;
        } else {
            // We know the record length, so we can find a good value for the number of buckets
            // right away, and
            // won't need any resizes later. (enableResize is false in this case, so no resizing
            // will happen.)
            // Reasoning behind the formula:
            // We are aiming for one bucket per record, and one bucket contains one 8 byte pointer.
            // The total
            // memory overhead of an element will be approximately 8+8 bytes, as the record in the
            // record area
            // is preceded by a pointer (for the linked list).
            fraction = 8.0 / (16 + recordLength);
        }

        // We make the number of buckets a power of 2 so that taking modulo is efficient.
        int ret =
                Math.max(1, MathUtils.roundDownToPowerOf2((int) (numAllMemorySegments * fraction)));

        // We can't handle more than Integer.MAX_VALUE buckets (eg. because hash functions return
        // int)
        if ((long) ret * numBucketsPerSegment > Integer.MAX_VALUE) {
            ret = MathUtils.roundDownToPowerOf2(Integer.MAX_VALUE / numBucketsPerSegment);
        }
        return ret;
    }

    private void allocateBucketSegments(int numBucketSegments) {
        if (numBucketSegments < 1) {
            throw new RuntimeException("Bug in InPlaceMutableHashTable");
        }

        bucketSegments = new MemorySegment[numBucketSegments];
        for (int i = 0; i < bucketSegments.length; i++) {
            bucketSegments[i] = forcedAllocateSegment();
            // Init all pointers in all buckets to END_OF_LIST
            for (int j = 0; j < numBucketsPerSegment; j++) {
                bucketSegments[i].putLong(j << bucketSizeBits, END_OF_LIST);
            }
        }
        numBuckets = numBucketSegments * numBucketsPerSegment;
        numBucketsMask = (1 << MathUtils.log2strict(numBuckets)) - 1;
    }

    private void releaseBucketSegments() {
        freeMemorySegments.addAll(Arrays.asList(bucketSegments));
        bucketSegments = null;
    }

    private MemorySegment allocateSegment() {
        int s = freeMemorySegments.size();
        if (s > 0) {
            return freeMemorySegments.remove(s - 1);
        } else {
            return null;
        }
    }

    private MemorySegment forcedAllocateSegment() {
        MemorySegment segment = allocateSegment();
        if (segment == null) {
            throw new RuntimeException(
                    "Bug in InPlaceMutableHashTable: A free segment should have been available.");
        }
        return segment;
    }

    /**
     * Searches the hash table for a record with the given key. If it is found, then it is
     * overridden with the specified record. Otherwise, the specified record is inserted.
     * 在哈希表中搜索具有给定键的记录。 如果找到，则用指定的记录覆盖它。 否则，插入指定的记录。
     *
     * @param record The record to insert or to replace with.
     * @throws IOException (EOFException specifically, if memory ran out)
     */
    @Override
    public void insertOrReplaceRecord(T record) throws IOException {
        if (closed) {
            return;
        }

        T match = prober.getMatchFor(record, reuse);
        if (match == null) {
            prober.insertAfterNoMatch(record);
        } else {
            prober.updateMatch(record);
        }
    }

    /**
     * Inserts the given record into the hash table. Note: this method doesn't care about whether a
     * record with the same key is already present.
     * 将给定的记录插入到哈希表中。 注意：此方法不关心是否已经存在具有相同键的记录。
     *
     * @param record The record to insert.
     * @throws IOException (EOFException specifically, if memory ran out)
     */
    @Override
    public void insert(T record) throws IOException {
        if (closed) {
            return;
        }

        final int hashCode = MathUtils.jenkinsHash(buildSideComparator.hash(record));
        final int bucket = hashCode & numBucketsMask;
        final int bucketSegmentIndex =
                bucket >>> numBucketsPerSegmentBits; // which segment contains the bucket
        final MemorySegment bucketSegment = bucketSegments[bucketSegmentIndex];
        final int bucketOffset =
                (bucket & numBucketsPerSegmentMask)
                        << bucketSizeBits; // offset of the bucket in the segment
        final long firstPointer = bucketSegment.getLong(bucketOffset);

        try {
            final long newFirstPointer = recordArea.appendPointerAndRecord(firstPointer, record);
            bucketSegment.putLong(bucketOffset, newFirstPointer);
        } catch (EOFException ex) {
            compactOrThrow();
            insert(record);
            return;
        }

        numElements++;
        resizeTableIfNecessary();
    }

    private void resizeTableIfNecessary() throws IOException {
        if (enableResize && numElements > numBuckets) {
            final long newNumBucketSegments = 2L * bucketSegments.length;
            // Checks:
            // - we can't handle more than Integer.MAX_VALUE buckets
            // - don't take more memory than the free memory we have left
            // - the buckets shouldn't occupy more than half of all our memory
            if (newNumBucketSegments * numBucketsPerSegment < Integer.MAX_VALUE
                    && newNumBucketSegments - bucketSegments.length < freeMemorySegments.size()
                    && newNumBucketSegments < numAllMemorySegments / 2) {
                // do the resize
                rebuild(newNumBucketSegments);
            }
        }
    }

    /**
     * Returns an iterator that can be used to iterate over all the elements in the table. WARNING:
     * Doing any other operation on the table invalidates the iterator! (Even using getMatchFor of a
     * prober!)
     * 返回一个可用于迭代表中所有元素的迭代器。 警告：对表执行任何其他操作都会使迭代器无效！ （即使使用探测器的 getMatchFor ！）
     *
     * @return the iterator
     */
    @Override
    public EntryIterator getEntryIterator() {
        return new EntryIterator();
    }

    public <PT> HashTableProber<PT> getProber(
            TypeComparator<PT> probeTypeComparator, TypePairComparator<PT, T> pairComparator) {
        return new HashTableProber<>(probeTypeComparator, pairComparator);
    }

    /**
     * This function reinitializes the bucket segments, reads all records from the record segments
     * (sequentially, without using the pointers or the buckets), and rebuilds the hash table.
     * 此函数重新初始化桶段，从记录段中读取所有记录（按顺序，不使用指针或桶），并重建哈希表。
     */
    private void rebuild() throws IOException {
        rebuild(bucketSegments.length);
    }

    /** Same as above, but the number of bucket segments of the new table can be specified.
     * 同上，但可以指定新表的桶段数。
     * */
    private void rebuild(long newNumBucketSegments) throws IOException {
        // Get new bucket segments
        releaseBucketSegments();
        allocateBucketSegments((int) newNumBucketSegments);

        T record = buildSideSerializer.createInstance();
        try {
            EntryIterator iter = getEntryIterator();
            recordArea.resetAppendPosition();
            recordArea.setWritePosition(0);
            while ((record = iter.next(record)) != null && !closed) {
                final int hashCode = MathUtils.jenkinsHash(buildSideComparator.hash(record));
                final int bucket = hashCode & numBucketsMask;
                final int bucketSegmentIndex =
                        bucket >>> numBucketsPerSegmentBits; // which segment contains the bucket
                final MemorySegment bucketSegment = bucketSegments[bucketSegmentIndex];
                final int bucketOffset =
                        (bucket & numBucketsPerSegmentMask)
                                << bucketSizeBits; // offset of the bucket in the segment
                final long firstPointer = bucketSegment.getLong(bucketOffset);

                long ptrToAppended = recordArea.noSeekAppendPointerAndRecord(firstPointer, record);
                bucketSegment.putLong(bucketOffset, ptrToAppended);
            }
            recordArea.freeSegmentsAfterAppendPosition();
            holes = 0;

        } catch (EOFException ex) {
            throw new RuntimeException(
                    "Bug in InPlaceMutableHashTable: we shouldn't get out of memory during a rebuild, "
                            + "because we aren't allocating any new memory.");
        }
    }

    /**
     * If there is wasted space (due to updated records not fitting in their old places), then do a
     * compaction. Else, throw EOFException to indicate that memory ran out.
     * 如果有浪费的空间（由于更新的记录不适合它们的旧位置），那么进行压缩。 否则，抛出 EOFException 以指示内存已用完。
     *
     * @throws IOException
     */
    private void compactOrThrow() throws IOException {
        if (holes > (double) recordArea.getTotalSize() * 0.05) {
            rebuild();
        } else {
            throw new EOFException(
                    "InPlaceMutableHashTable memory ran out. " + getMemoryConsumptionString());
        }
    }

    /** @return String containing a summary of the memory consumption for error messages */
    private String getMemoryConsumptionString() {
        return "InPlaceMutableHashTable memory stats:\n"
                + "Total memory:     "
                + numAllMemorySegments * segmentSize
                + "\n"
                + "Free memory:      "
                + freeMemorySegments.size() * segmentSize
                + "\n"
                + "Bucket area:      "
                + numBuckets * 8
                + "\n"
                + "Record area:      "
                + recordArea.getTotalSize()
                + "\n"
                + "Staging area:     "
                + stagingSegments.size() * segmentSize
                + "\n"
                + "Num of elements:  "
                + numElements
                + "\n"
                + "Holes total size: "
                + holes;
    }

    /**
     * This class encapsulates the memory segments that belong to the record area. It - can append a
     * record - can overwrite a record at an arbitrary position (WARNING: the new record must have
     * the same size as the old one) - can be rewritten by calling resetAppendPosition - takes
     * memory from InPlaceMutableHashTable.freeMemorySegments on append
     * 该类封装了属于记录区的内存段。 它
     * - 可以追加记录
     * - 可以覆盖任意位置的记录（警告：新记录的大小必须与旧记录相同）
     * - 可以通过调用 resetAppendPosition 重写
     * - 在追加时从 InPlaceMutableHashTable.freeMemorySegments 获取内存
     */
    private final class RecordArea {
        private final ArrayList<MemorySegment> segments = new ArrayList<>();

        private final RecordAreaOutputView outView;
        private final RandomAccessInputView inView;

        private final int segmentSizeBits;
        private final int segmentSizeMask;

        private long appendPosition = 0;

        public RecordArea(int segmentSize) {
            int segmentSizeBits = MathUtils.log2strict(segmentSize);

            if ((segmentSize & (segmentSize - 1)) != 0) {
                throw new IllegalArgumentException("Segment size must be a power of 2!");
            }

            this.segmentSizeBits = segmentSizeBits;
            this.segmentSizeMask = segmentSize - 1;

            outView = new RecordAreaOutputView(segmentSize);
            try {
                addSegment();
            } catch (EOFException ex) {
                throw new RuntimeException(
                        "Bug in InPlaceMutableHashTable: we should have caught it earlier "
                                + "that we don't have enough segments.");
            }
            inView = new RandomAccessInputView(segments, segmentSize);
        }

        private void addSegment() throws EOFException {
            MemorySegment m = allocateSegment();
            if (m == null) {
                throw new EOFException();
            }
            segments.add(m);
        }

        /**
         * Moves all its memory segments to freeMemorySegments. Warning: this will leave the
         * RecordArea in an unwritable state: you have to call setWritePosition before writing
         * again.
         * 将其所有内存段移动到 freeMemorySegments。
         * 警告：这将使 RecordArea 处于不可写状态：您必须在再次写入之前调用 setWritePosition。
         */
        public void giveBackSegments() {
            freeMemorySegments.addAll(segments);
            segments.clear();

            resetAppendPosition();
        }

        public long getTotalSize() {
            return segments.size() * (long) segmentSize;
        }

        // ----------------------- Output -----------------------

        private void setWritePosition(long position) throws EOFException {
            if (position > appendPosition) {
                throw new IndexOutOfBoundsException();
            }

            final int segmentIndex = (int) (position >>> segmentSizeBits);
            final int offset = (int) (position & segmentSizeMask);

            // If position == appendPosition and the last buffer is full,
            // then we will be seeking to the beginning of a new segment
            if (segmentIndex == segments.size()) {
                addSegment();
            }

            outView.currentSegmentIndex = segmentIndex;
            outView.seekOutput(segments.get(segmentIndex), offset);
        }

        /**
         * Sets appendPosition and the write position to 0, so that appending starts overwriting
         * elements from the beginning. (This is used in rebuild.)
         * 将 appendPosition 和写入位置设置为 0，以便追加从头开始覆盖元素。 （这用于重建。）
         *
         * <p>Note: if data was written to the area after the current appendPosition before a call
         * to resetAppendPosition, it should still be readable. To release the segments after the
         * current append position, call freeSegmentsAfterAppendPosition()
         * 注意：如果在调用 resetAppendPosition 之前将数据写入当前 appendPosition 之后的区域，它应该仍然是可读的。
         * 要释放当前附加位置之后的段，请调用 freeSegmentsAfterAppendPosition()
         */
        public void resetAppendPosition() {
            appendPosition = 0;

            // this is just for safety (making sure that we fail immediately
            // if a write happens without calling setWritePosition)
            outView.currentSegmentIndex = -1;
            outView.seekOutput(null, -1);
        }

        /**
         * Releases the memory segments that are after the current append position. Note: The
         * situation that there are segments after the current append position can arise from a call
         * to resetAppendPosition().
         * 释放当前追加位置之后的内存段。 注意：在当前追加位置之后有段的情况可能是调用 resetAppendPosition() 引起的。
         */
        public void freeSegmentsAfterAppendPosition() {
            final int appendSegmentIndex = (int) (appendPosition >>> segmentSizeBits);
            while (segments.size() > appendSegmentIndex + 1 && !closed) {
                freeMemorySegments.add(segments.get(segments.size() - 1));
                segments.remove(segments.size() - 1);
            }
        }

        /**
         * Overwrites the long value at the specified position.
         * 覆盖指定位置的 long 值。
         *
         * @param pointer Points to the position to overwrite.
         * @param value The value to write.
         * @throws IOException
         */
        public void overwritePointerAt(long pointer, long value) throws IOException {
            setWritePosition(pointer);
            outView.writeLong(value);
        }

        /**
         * Overwrites a record at the specified position. The record is read from a DataInputView
         * (this will be the staging area). WARNING: The record must not be larger than the original
         * record.
         * 覆盖指定位置的记录。 记录是从 DataInputView 中读取的（这将是暂存区）。 警告：记录不得大于原始记录。
         *
         * @param pointer Points to the position to overwrite.
         * @param input The DataInputView to read the record from
         * @param size The size of the record
         * @throws IOException
         */
        public void overwriteRecordAt(long pointer, DataInputView input, int size)
                throws IOException {
            setWritePosition(pointer);
            outView.write(input, size);
        }

        /**
         * Appends a pointer and a record. The record is read from a DataInputView (this will be the
         * staging area).
         * 附加一个指针和一条记录。 记录是从 DataInputView 中读取的（这将是暂存区）。
         *
         * @param pointer The pointer to write (Note: this is NOT the position to write to!)
         * @param input The DataInputView to read the record from
         * @param recordSize The size of the record
         * @return A pointer to the written data
         * @throws IOException (EOFException specifically, if memory ran out)
         */
        public long appendPointerAndCopyRecord(long pointer, DataInputView input, int recordSize)
                throws IOException {
            setWritePosition(appendPosition);
            final long oldLastPosition = appendPosition;
            outView.writeLong(pointer);
            outView.write(input, recordSize);
            appendPosition += 8 + recordSize;
            return oldLastPosition;
        }

        /**
         * Appends a pointer and a record.
         * 附加一个指针和一条记录。
         *
         * @param pointer The pointer to write (Note: this is NOT the position to write to!)
         * @param record The record to write
         * @return A pointer to the written data
         * @throws IOException (EOFException specifically, if memory ran out)
         */
        public long appendPointerAndRecord(long pointer, T record) throws IOException {
            setWritePosition(appendPosition);
            return noSeekAppendPointerAndRecord(pointer, record);
        }

        /**
         * Appends a pointer and a record. Call this function only if the write position is at the
         * end!
         * 附加一个指针和一条记录。 仅当写入位置在末尾时才调用此函数！
         *
         * @param pointer The pointer to write (Note: this is NOT the position to write to!)
         * @param record The record to write
         * @return A pointer to the written data
         * @throws IOException (EOFException specifically, if memory ran out)
         */
        public long noSeekAppendPointerAndRecord(long pointer, T record) throws IOException {
            final long oldLastPosition = appendPosition;
            final long oldPositionInSegment = outView.getCurrentPositionInSegment();
            final long oldSegmentIndex = outView.currentSegmentIndex;
            outView.writeLong(pointer);
            buildSideSerializer.serialize(record, outView);
            appendPosition +=
                    outView.getCurrentPositionInSegment()
                            - oldPositionInSegment
                            + outView.getSegmentSize()
                                    * (outView.currentSegmentIndex - oldSegmentIndex);
            return oldLastPosition;
        }

        public long getAppendPosition() {
            return appendPosition;
        }

        // ----------------------- Input -----------------------

        public void setReadPosition(long position) {
            inView.setReadPosition(position);
        }

        public long getReadPosition() {
            return inView.getReadPosition();
        }

        /**
         * Note: this is sometimes a negated length instead of a pointer (see
         * HashTableProber.updateMatch).
         * 注意：这有时是取反的长度而不是指针（参见 HashTableProber.updateMatch）。
         */
        public long readPointer() throws IOException {
            return inView.readLong();
        }

        public T readRecord(T reuse) throws IOException {
            return buildSideSerializer.deserialize(reuse, inView);
        }

        public void skipBytesToRead(int numBytes) throws IOException {
            inView.skipBytesToRead(numBytes);
        }

        // -----------------------------------------------------

        private final class RecordAreaOutputView extends AbstractPagedOutputView {

            public int currentSegmentIndex;

            public RecordAreaOutputView(int segmentSize) {
                super(segmentSize, 0);
            }

            @Override
            protected MemorySegment nextSegment(MemorySegment current, int positionInCurrent)
                    throws EOFException {
                currentSegmentIndex++;
                if (currentSegmentIndex == segments.size()) {
                    addSegment();
                }
                return segments.get(currentSegmentIndex);
            }

            @Override
            public void seekOutput(MemorySegment seg, int position) {
                super.seekOutput(seg, position);
            }
        }
    }

    private final class StagingOutputView extends AbstractPagedOutputView {

        private final ArrayList<MemorySegment> segments;

        private final int segmentSizeBits;

        private int currentSegmentIndex;

        public StagingOutputView(ArrayList<MemorySegment> segments, int segmentSize) {
            super(segmentSize, 0);
            this.segmentSizeBits = MathUtils.log2strict(segmentSize);
            this.segments = segments;
        }

        /** Seeks to the beginning. */
        public void reset() {
            seekOutput(segments.get(0), 0);
            currentSegmentIndex = 0;
        }

        @Override
        protected MemorySegment nextSegment(MemorySegment current, int positionInCurrent)
                throws EOFException {
            currentSegmentIndex++;
            if (currentSegmentIndex == segments.size()) {
                MemorySegment m = allocateSegment();
                if (m == null) {
                    throw new EOFException();
                }
                segments.add(m);
            }
            return segments.get(currentSegmentIndex);
        }

        public long getWritePosition() {
            return (((long) currentSegmentIndex) << segmentSizeBits)
                    + getCurrentPositionInSegment();
        }
    }

    /**
     * A prober for accessing the table. In addition to getMatchFor and updateMatch, it also has
     * insertAfterNoMatch. Warning: Don't modify the table between calling getMatchFor and the other
     * methods!
     * 用于访问表的探测器。 除了getMatchFor和updateMatch之外，还有insertAfterNoMatch。
     * 警告：不要在调用 getMatchFor 和其他方法之间修改表！
     *
     * @param <PT> The type of the records that we are probing with
     */
    public final class HashTableProber<PT> extends AbstractHashTableProber<PT, T> {

        public HashTableProber(
                TypeComparator<PT> probeTypeComparator, TypePairComparator<PT, T> pairComparator) {
            super(probeTypeComparator, pairComparator);
        }

        private int bucketSegmentIndex;
        private int bucketOffset;
        private long curElemPtr;
        private long prevElemPtr;
        private long nextPtr;
        private long recordEnd;

        /**
         * Searches the hash table for the record with the given key. (If there would be multiple
         * matches, only one is returned.)
         * 在哈希表中搜索具有给定键的记录。 （如果有多个匹配项，则只返回一个。）
         *
         * @param record The record whose key we are searching for
         * @param targetForMatch If a match is found, it will be written here
         * @return targetForMatch if a match is found, otherwise null.
         */
        @Override
        public T getMatchFor(PT record, T targetForMatch) {
            if (closed) {
                return null;
            }

            final int hashCode = MathUtils.jenkinsHash(probeTypeComparator.hash(record));
            final int bucket = hashCode & numBucketsMask;
            bucketSegmentIndex =
                    bucket >>> numBucketsPerSegmentBits; // which segment contains the bucket
            final MemorySegment bucketSegment = bucketSegments[bucketSegmentIndex];
            bucketOffset =
                    (bucket & numBucketsPerSegmentMask)
                            << bucketSizeBits; // offset of the bucket in the segment

            curElemPtr = bucketSegment.getLong(bucketOffset);

            pairComparator.setReference(record);

            T currentRecordInList = targetForMatch;

            prevElemPtr = INVALID_PREV_POINTER;
            try {
                while (curElemPtr != END_OF_LIST && !closed) {
                    recordArea.setReadPosition(curElemPtr);
                    nextPtr = recordArea.readPointer();

                    currentRecordInList = recordArea.readRecord(currentRecordInList);
                    recordEnd = recordArea.getReadPosition();
                    if (pairComparator.equalToReference(currentRecordInList)) {
                        // we found an element with a matching key, and not just a hash collision
                        return currentRecordInList;
                    }

                    prevElemPtr = curElemPtr;
                    curElemPtr = nextPtr;
                }
            } catch (IOException ex) {
                throw new RuntimeException(
                        "Error deserializing record from the hashtable: " + ex.getMessage(), ex);
            }
            return null;
        }

        @Override
        public T getMatchFor(PT probeSideRecord) {
            return getMatchFor(probeSideRecord, buildSideSerializer.createInstance());
        }

        /**
         * This method can be called after getMatchFor returned a match. It will overwrite the
         * record that was found by getMatchFor. Warning: The new record should have the same key as
         * the old! WARNING; Don't do any modifications to the table between getMatchFor and
         * updateMatch!
         * 该方法可以在 getMatchFor 返回匹配后调用。 它将覆盖 getMatchFor 找到的记录。
         * 警告：新记录应该与旧记录具有相同的键！ 警告; 不要对 getMatchFor 和 updateMatch 之间的表进行任何修改！
         *
         * @param newRecord The record to override the old record with.
         * @throws IOException (EOFException specifically, if memory ran out)
         */
        @Override
        public void updateMatch(T newRecord) throws IOException {
            if (closed) {
                return;
            }
            if (curElemPtr == END_OF_LIST) {
                throw new RuntimeException(
                        "updateMatch was called after getMatchFor returned no match");
            }

            try {
                // determine the new size
                stagingSegmentsOutView.reset();
                buildSideSerializer.serialize(newRecord, stagingSegmentsOutView);
                final int newRecordSize = (int) stagingSegmentsOutView.getWritePosition();
                stagingSegmentsInView.setReadPosition(0);

                // Determine the size of the place of the old record.
                final int oldRecordSize = (int) (recordEnd - (curElemPtr + RECORD_OFFSET_IN_LINK));

                if (newRecordSize == oldRecordSize) {
                    // overwrite record at its original place
                    recordArea.overwriteRecordAt(
                            curElemPtr + RECORD_OFFSET_IN_LINK,
                            stagingSegmentsInView,
                            newRecordSize);
                } else {
                    // new record has a different size than the old one, append new at the end of
                    // the record area.
                    // Note: we have to do this, even if the new record is smaller, because
                    // otherwise EntryIterator
                    // wouldn't know the size of this place, and wouldn't know where does the next
                    // record start.

                    final long pointerToAppended =
                            recordArea.appendPointerAndCopyRecord(
                                    nextPtr, stagingSegmentsInView, newRecordSize);

                    // modify the pointer in the previous link
                    if (prevElemPtr == INVALID_PREV_POINTER) {
                        // list had only one element, so prev is in the bucketSegments
                        bucketSegments[bucketSegmentIndex].putLong(bucketOffset, pointerToAppended);
                    } else {
                        recordArea.overwritePointerAt(prevElemPtr, pointerToAppended);
                    }

                    // write the negated size of the hole to the place where the next pointer was,
                    // so that EntryIterator
                    // will know the size of the place without reading the old record.
                    // The negative sign will mean that the record is abandoned, and the
                    // the -1 is for avoiding trouble in case of a record having 0 size. (though I
                    // think this should
                    // never actually happen)
                    // Note: the last record in the record area can't be abandoned. (EntryIterator
                    // makes use of this fact.)
                    recordArea.overwritePointerAt(curElemPtr, -oldRecordSize - 1);

                    holes += oldRecordSize;
                }
            } catch (EOFException ex) {
                compactOrThrow();
                insertOrReplaceRecord(newRecord);
            }
        }

        /**
         * This method can be called after getMatchFor returned null. It inserts the given record to
         * the hash table. Important: The given record should have the same key as the record that
         * was given to getMatchFor! WARNING; Don't do any modifications to the table between
         * getMatchFor and insertAfterNoMatch!
         * 该方法可以在 getMatchFor 返回 null 后调用。 它将给定的记录插入到哈希表中。
         * 重要提示：给定的记录应该与提供给 getMatchFor 的记录具有相同的键！
         * 警告; 不要对 getMatchFor 和 insertAfterNoMatch 之间的表进行任何修改！
         *
         * @throws IOException (EOFException specifically, if memory ran out)
         */
        public void insertAfterNoMatch(T record) throws IOException {
            if (closed) {
                return;
            }

            // create new link
            long pointerToAppended;
            try {
                pointerToAppended = recordArea.appendPointerAndRecord(END_OF_LIST, record);
            } catch (EOFException ex) {
                compactOrThrow();
                insert(record);
                return;
            }

            // add new link to the end of the list
            if (prevElemPtr == INVALID_PREV_POINTER) {
                // list was empty
                bucketSegments[bucketSegmentIndex].putLong(bucketOffset, pointerToAppended);
            } else {
                // update the pointer of the last element of the list.
                recordArea.overwritePointerAt(prevElemPtr, pointerToAppended);
            }

            numElements++;
            resizeTableIfNecessary();
        }
    }

    /**
     * WARNING: Doing any other operation on the table invalidates the iterator! (Even using
     * getMatchFor of a prober!)
     * 警告：对表执行任何其他操作都会使迭代器无效！ （即使使用探测器的 getMatchFor ！）
     */
    public final class EntryIterator implements MutableObjectIterator<T> {

        private final long endPosition;

        public EntryIterator() {
            endPosition = recordArea.getAppendPosition();
            if (endPosition == 0) {
                return;
            }
            recordArea.setReadPosition(0);
        }

        @Override
        public T next(T reuse) throws IOException {
            if (endPosition != 0 && recordArea.getReadPosition() < endPosition) {
                // Loop until we find a non-abandoned record.
                // Note: the last record in the record area can't be abandoned.
                while (!closed) {
                    final long pointerOrNegatedLength = recordArea.readPointer();
                    final boolean isAbandoned = pointerOrNegatedLength < 0;
                    if (!isAbandoned) {
                        reuse = recordArea.readRecord(reuse);
                        return reuse;
                    } else {
                        // pointerOrNegatedLength is storing a length, because the record was
                        // abandoned.
                        recordArea.skipBytesToRead((int) -(pointerOrNegatedLength + 1));
                    }
                }
                return null; // (we were closed)
            } else {
                return null;
            }
        }

        @Override
        public T next() throws IOException {
            return next(buildSideSerializer.createInstance());
        }
    }

    /**
     * A facade for doing such operations on the hash table that are needed for a reduce operator
     * driver.
     * 用于在哈希表上执行 reduce 运算符驱动程序所需的此类操作的外观
     */
    public final class ReduceFacade {

        private final HashTableProber<T> prober;

        private final boolean objectReuseEnabled;

        private final ReduceFunction<T> reducer;

        private final Collector<T> outputCollector;

        private T reuse;

        public ReduceFacade(
                ReduceFunction<T> reducer,
                Collector<T> outputCollector,
                boolean objectReuseEnabled) {
            this.reducer = reducer;
            this.outputCollector = outputCollector;
            this.objectReuseEnabled = objectReuseEnabled;
            this.prober =
                    getProber(
                            buildSideComparator, new SameTypePairComparator<>(buildSideComparator));
            this.reuse = buildSideSerializer.createInstance();
        }

        /**
         * Looks up the table entry that has the same key as the given record, and updates it by
         * performing a reduce step.
         * 查找与给定记录具有相同键的表条目，并通过执行减少步骤来更新它。
         *
         * @param record The record to update.
         * @throws Exception
         */
        public void updateTableEntryWithReduce(T record) throws Exception {
            T match = prober.getMatchFor(record, reuse);
            if (match == null) {
                prober.insertAfterNoMatch(record);
            } else {
                // do the reduce step
                T res = reducer.reduce(match, record);

                // We have given reuse to the reducer UDF, so create new one if object reuse is
                // disabled
                if (!objectReuseEnabled) {
                    reuse = buildSideSerializer.createInstance();
                }

                prober.updateMatch(res);
            }
        }

        /** Emits all elements currently held by the table to the collector. */
        public void emit() throws IOException {
            T record = buildSideSerializer.createInstance();
            EntryIterator iter = getEntryIterator();
            while ((record = iter.next(record)) != null && !closed) {
                outputCollector.collect(record);
                if (!objectReuseEnabled) {
                    record = buildSideSerializer.createInstance();
                }
            }
        }

        /**
         * Emits all elements currently held by the table to the collector, and resets the table.
         * The table will have the same number of buckets as before the reset, to avoid doing
         * resizes again.
         * 将表当前持有的所有元素发送到收集器，并重置表。 该表将具有与重置前相同数量的存储桶，以避免再次调整大小。
         */
        public void emitAndReset() throws IOException {
            final int oldNumBucketSegments = bucketSegments.length;
            emit();
            close();
            open(oldNumBucketSegments);
        }
    }
}
