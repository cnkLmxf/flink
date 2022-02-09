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

package org.apache.flink.runtime.util;

import java.io.IOException;

/** The memory block iterator is an iterator that always buffers a block of elements in memory.
 * 内存块迭代器是一个总是在内存中缓冲一个元素块的迭代器。
 * */
public interface MemoryBlockIterator {
    /**
     * Move the iterator to the next memory block. The next memory block starts at the first element
     * that was not in the block before. A special case is when no record was in the block before,
     * which happens when this function is invoked two times directly in a sequence, without calling
     * hasNext() or next in between. Then the block moves one element.
     * 将迭代器移动到下一个内存块。 下一个内存块从之前不在块中的第一个元素开始。
     * 一种特殊情况是之前的块中没有记录，这种情况发生在此函数按顺序直接调用两次，而没有调用 hasNext() 或 next 之间。
     * 然后该块移动一个元素。
     *
     * @return True if a new memory block was loaded, false if there were no further records and
     *     hence no further memory block.
     * @throws IOException Thrown, when advancing to the next block failed.
     */
    public boolean nextBlock() throws IOException;
}
