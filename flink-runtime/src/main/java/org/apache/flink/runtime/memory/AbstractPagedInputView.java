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

package org.apache.flink.runtime.memory;

import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.MemorySegment;

import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;

/**
 * The base class for all input views that are backed by multiple memory pages. This base class
 * contains all decoding methods to read data from a page and detect page boundary crossing. The
 * concrete sub classes must implement the methods to provide the next memory page once the boundary
 * is crossed.
 * 由多个内存页面支持的所有输入视图的基类。 该基类包含从页面读取数据和检测页面边界交叉的所有解码方法。
 * 一旦跨越边界，具体的子类必须实现提供下一个内存页的方法。
 */
public abstract class AbstractPagedInputView implements DataInputView {

    private MemorySegment currentSegment;

    protected final int
            headerLength; // the number of bytes to skip at the beginning of each segment 每段开头要跳过的字节数

    private int positionInSegment; // the offset in the current segment 当前段的偏移量

    private int limitInSegment; // the limit in the current segment before switching to the next，切换到下一个之前当前段的limit

    private byte[] utfByteBuffer; // reusable byte buffer for utf-8 decoding
    private char[] utfCharBuffer; // reusable char buffer for utf-8 decoding

    // --------------------------------------------------------------------------------------------
    //                                    Constructors
    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new view that starts with the given segment. The input starts directly after the
     * header of the given page. If the header size is zero, it starts at the beginning. The
     * specified initial limit describes up to which position data may be read from the current
     * segment, before the view must advance to the next segment.
     * 创建一个以给定segment开头的新视图。 输入直接在给定page的header之后开始。
     * 如果header大小为零，则从头开始。 指定的initialLimit描述了在视图必须前进到下一个段之前可以从当前段读取的位置数据。
     *
     * @param initialSegment The memory segment to start reading from.
     * @param initialLimit The position one after the last valid byte in the initial segment.
     * @param headerLength The number of bytes to skip at the beginning of each segment for the
     *     header. This length must be the same for all memory segments.
     */
    protected AbstractPagedInputView(
            MemorySegment initialSegment, int initialLimit, int headerLength) {
        this.headerLength = headerLength;
        this.positionInSegment = headerLength;
        seekInput(initialSegment, headerLength, initialLimit);
    }

    /**
     * Creates a new view that is initially not bound to a memory segment. This constructor is
     * typically for views that always seek first.
     * 创建一个最初未绑定到内存段的新视图。 此构造函数通常用于始终首先查找的视图。
     *
     * <p>WARNING: The view is not readable until the first call to either {@link #advance()}, or to
     * {@link #seekInput(MemorySegment, int, int)}.
     * 在第一次调用 {@link #advance()} 或 {@link #seekInput(MemorySegment, int, int)} 之前，视图是不可读的。
     *
     * @param headerLength The number of bytes to skip at the beginning of each segment for the
     *     header.
     */
    protected AbstractPagedInputView(int headerLength) {
        this.headerLength = headerLength;
    }

    // --------------------------------------------------------------------------------------------
    //                                  Page Management
    // --------------------------------------------------------------------------------------------

    /**
     * Gets the memory segment that will be used to read the next bytes from. If the segment is
     * exactly exhausted, meaning that the last byte read was the last byte available in the
     * segment, then this segment will not serve the next bytes. The segment to serve the next bytes
     * will be obtained through the {@link #nextSegment(MemorySegment)} method.
     * 获取将用于从中读取下一个字节的内存段。
     * 如果该段完全耗尽，意味着读取的最后一个字节是该段中的最后一个可用字节，则该段将不会为下一个字节提供服务。
     * 服务下一个字节的段将通过 {@link #nextSegment(MemorySegment)} 方法获得。
     *
     * @return The current memory segment.
     */
    public MemorySegment getCurrentSegment() {
        return this.currentSegment;
    }

    /**
     * Gets the position from which the next byte will be read. If that position is equal to the
     * current limit, then the next byte will be read from next segment.
     * 获取将读取下一个字节的位置。 如果该位置等于当前限制，则将从下一个segment读取下一个字节。
     *
     * @return The position from which the next byte will be read.
     * @see #getCurrentSegmentLimit()
     */
    public int getCurrentPositionInSegment() {
        return this.positionInSegment;
    }

    /**
     * Gets the current limit in the memory segment. This value points to the byte one after the
     * last valid byte in the memory segment.
     * 获取内存段中的当前限制。 该值指向内存段中最后一个有效字节之后的字节。
     *
     * @return The current limit in the memory segment.
     * @see #getCurrentPositionInSegment()
     */
    public int getCurrentSegmentLimit() {
        return this.limitInSegment;
    }

    /**
     * The method by which concrete subclasses realize page crossing. This method is invoked when
     * the current page is exhausted and a new page is required to continue the reading. If no
     * further page is available, this method must throw an {@link EOFException}.
     * 具体子类实现跨页的方法。 当前页用完，需要新页继续读取时调用此方法。
     * 如果没有其他页面可用，则此方法必须抛出 {@link EOFException}。
     *
     * @param current The current page that was read to its limit. May be {@code null}, if this
     *     method is invoked for the first time.
     *     已读取到limit限制的当前页面。 如果第一次调用此方法，则可能为 {@code null}。
     * @return The next page from which the reading should continue. May not be {@code null}. If the
     *     input is exhausted, an {@link EOFException} must be thrown instead.
     *     应继续阅读的下一页。 可能不是 {@code null}。 如果输入已用尽，则必须抛出 {@link EOFException}。
     * @throws EOFException Thrown, if no further segment is available.
     * @throws IOException Thrown, if the method cannot provide the next page due to an I/O related
     *     problem.
     */
    protected abstract MemorySegment nextSegment(MemorySegment current)
            throws EOFException, IOException;

    /**
     * Gets the limit for reading bytes from the given memory segment. This method must return the
     * position of the byte after the last valid byte in the given memory segment. When the position
     * returned by this method is reached, the view will attempt to switch to the next memory
     * segment.
     * 获取从给定内存段读取字节的限制。 此方法必须返回给定内存段中最后一个有效字节之后的字节位置。
     * 当到达此方法返回的位置时，视图将尝试切换到下一个内存段。
     *
     * @param segment The segment to determine the limit for.
     * @return The limit for the given memory segment.
     */
    protected abstract int getLimitForSegment(MemorySegment segment);

    /**
     * Advances the view to the next memory segment. The reading will continue after the header of
     * the next segment. This method uses {@link #nextSegment(MemorySegment)} and {@link
     * #getLimitForSegment(MemorySegment)} to get the next segment and set its limit.
     * 将视图推进到下一个内存段。 读取将在下一个段的标题之后继续。
     * 此方法使用 {@link #nextSegment(MemorySegment)} 和 {@link #getLimitForSegment(MemorySegment)}
     * 来获取下一个段并设置其限制。
     *
     * @throws IOException Thrown, if the next segment could not be obtained.
     * @see #nextSegment(MemorySegment)
     * @see #getLimitForSegment(MemorySegment)
     */
    public void advance() throws IOException {
        doAdvance();
    }

    protected void doAdvance() throws IOException {
        // note: this code ensures that in case of EOF, we stay at the same position such that
        // EOF is reproducible (if nextSegment throws a reproducible EOFException)
        // 注意：这段代码确保在 EOF 的情况下，我们保持在相同的位置，这样 EOF 是可重现的（如果 nextSegment 抛出一个可重现的 EOFException）
        this.currentSegment = nextSegment(this.currentSegment);
        this.limitInSegment = getLimitForSegment(this.currentSegment);
        this.positionInSegment = this.headerLength;
    }

    /** @return header length. */
    public int getHeaderLength() {
        return headerLength;
    }

    /**
     * Sets the internal state of the view such that the next bytes will be read from the given
     * memory segment, starting at the given position. The memory segment will provide bytes up to
     * the given limit position.
     * 设置视图的内部状态，以便从给定的内存段读取下一个字节，从给定的位置开始。 内存段将提供直到给定限制位置的字节。
     *
     * @param segment The segment to read the next bytes from.
     * @param positionInSegment The position in the segment to start reading from.
     * @param limitInSegment The limit in the segment. When reached, the view will attempt to switch
     *     to the next segment.
     */
    protected void seekInput(MemorySegment segment, int positionInSegment, int limitInSegment) {
        this.currentSegment = segment;
        this.positionInSegment = positionInSegment;
        this.limitInSegment = limitInSegment;
    }

    /**
     * Clears the internal state of the view. After this call, all read attempts will fail, until
     * the {@link #advance()} or {@link #seekInput(MemorySegment, int, int)} method have been
     * invoked.
     * 清除视图的内部状态。
     * 在此调用之后，所有读取尝试都将失败，直到调用了 {@link #advance()} 或 {@link #seekInput(MemorySegment, int, int)} 方法。
     */
    protected void clear() {
        this.currentSegment = null;
        this.positionInSegment = this.headerLength;
        this.limitInSegment = headerLength;
    }

    // --------------------------------------------------------------------------------------------
    //                               Data Input Specific methods
    // --------------------------------------------------------------------------------------------

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }

        int remaining = this.limitInSegment - this.positionInSegment;
        if (remaining >= len) {
            this.currentSegment.get(this.positionInSegment, b, off, len);
            this.positionInSegment += len;
            return len;
        } else {
            if (remaining == 0) {
                try {
                    advance();
                } catch (EOFException eof) {
                    return -1;
                }
                remaining = this.limitInSegment - this.positionInSegment;
            }

            int bytesRead = 0;
            while (true) {
                int toRead = Math.min(remaining, len - bytesRead);
                this.currentSegment.get(this.positionInSegment, b, off, toRead);
                off += toRead;
                bytesRead += toRead;

                if (len > bytesRead) {
                    try {
                        advance();
                    } catch (EOFException eof) {
                        this.positionInSegment += toRead;
                        return bytesRead;
                    }
                    remaining = this.limitInSegment - this.positionInSegment;
                } else {
                    this.positionInSegment += toRead;
                    break;
                }
            }
            return len;
        }
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        int bytesRead = read(b, off, len);

        if (bytesRead < len) {
            throw new EOFException("There is no enough data left in the DataInputView.");
        }
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readByte() == 1;
    }

    @Override
    public byte readByte() throws IOException {
        if (this.positionInSegment < this.limitInSegment) {
            return this.currentSegment.get(this.positionInSegment++);
        } else {
            advance();
            return readByte();
        }
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0xff;
    }

    @Override
    public short readShort() throws IOException {
        if (this.positionInSegment < this.limitInSegment - 1) {
            final short v = this.currentSegment.getShortBigEndian(this.positionInSegment);
            this.positionInSegment += 2;
            return v;
        } else if (this.positionInSegment == this.limitInSegment) {
            advance();
            return readShort();
        } else {
            return (short) ((readUnsignedByte() << 8) | readUnsignedByte());
        }
    }

    @Override
    public int readUnsignedShort() throws IOException {
        if (this.positionInSegment < this.limitInSegment - 1) {
            final int v = this.currentSegment.getShortBigEndian(this.positionInSegment) & 0xffff;
            this.positionInSegment += 2;
            return v;
        } else if (this.positionInSegment == this.limitInSegment) {
            advance();
            return readUnsignedShort();
        } else {
            return (readUnsignedByte() << 8) | readUnsignedByte();
        }
    }

    @Override
    public char readChar() throws IOException {
        if (this.positionInSegment < this.limitInSegment - 1) {
            final char v = this.currentSegment.getCharBigEndian(this.positionInSegment);
            this.positionInSegment += 2;
            return v;
        } else if (this.positionInSegment == this.limitInSegment) {
            advance();
            return readChar();
        } else {
            return (char) ((readUnsignedByte() << 8) | readUnsignedByte());
        }
    }

    @Override
    public int readInt() throws IOException {
        if (this.positionInSegment < this.limitInSegment - 3) {
            final int v = this.currentSegment.getIntBigEndian(this.positionInSegment);
            this.positionInSegment += 4;
            return v;
        } else if (this.positionInSegment == this.limitInSegment) {
            advance();
            return readInt();
        } else {
            return (readUnsignedByte() << 24)
                    | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8)
                    | readUnsignedByte();
        }
    }

    @Override
    public long readLong() throws IOException {
        if (this.positionInSegment < this.limitInSegment - 7) {
            final long v = this.currentSegment.getLongBigEndian(this.positionInSegment);
            this.positionInSegment += 8;
            return v;
        } else if (this.positionInSegment == this.limitInSegment) {
            advance();
            return readLong();
        } else {
            long l = 0L;
            l |= ((long) readUnsignedByte()) << 56;
            l |= ((long) readUnsignedByte()) << 48;
            l |= ((long) readUnsignedByte()) << 40;
            l |= ((long) readUnsignedByte()) << 32;
            l |= ((long) readUnsignedByte()) << 24;
            l |= ((long) readUnsignedByte()) << 16;
            l |= ((long) readUnsignedByte()) << 8;
            l |= (long) readUnsignedByte();
            return l;
        }
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() throws IOException {
        final StringBuilder bld = new StringBuilder(32);

        try {
            int b;
            while ((b = readUnsignedByte()) != '\n') {
                if (b != '\r') {
                    bld.append((char) b);
                }
            }
        } catch (EOFException eofex) {
        }

        if (bld.length() == 0) {
            return null;
        }

        // trim a trailing carriage return
        int len = bld.length();
        if (len > 0 && bld.charAt(len - 1) == '\r') {
            bld.setLength(len - 1);
        }
        return bld.toString();
    }

    @Override
    public String readUTF() throws IOException {
        final int utflen = readUnsignedShort();

        final byte[] bytearr;
        final char[] chararr;

        if (this.utfByteBuffer == null || this.utfByteBuffer.length < utflen) {
            bytearr = new byte[utflen];
            this.utfByteBuffer = bytearr;
        } else {
            bytearr = this.utfByteBuffer;
        }
        if (this.utfCharBuffer == null || this.utfCharBuffer.length < utflen) {
            chararr = new char[utflen];
            this.utfCharBuffer = chararr;
        } else {
            chararr = this.utfCharBuffer;
        }

        int c, char2, char3;
        int count = 0;
        int chararrCount = 0;

        readFully(bytearr, 0, utflen);

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            if (c > 127) {
                break;
            }
            count++;
            chararr[chararrCount++] = (char) c;
        }

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx */
                    count++;
                    chararr[chararrCount++] = (char) c;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx 10xx xxxx */
                    count += 2;
                    if (count > utflen) {
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    }
                    char2 = (int) bytearr[count - 1];
                    if ((char2 & 0xC0) != 0x80) {
                        throw new UTFDataFormatException("malformed input around byte " + count);
                    }
                    chararr[chararrCount++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    count += 3;
                    if (count > utflen) {
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    }
                    char2 = (int) bytearr[count - 2];
                    char3 = (int) bytearr[count - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                        throw new UTFDataFormatException(
                                "malformed input around byte " + (count - 1));
                    }
                    chararr[chararrCount++] =
                            (char)
                                    (((c & 0x0F) << 12)
                                            | ((char2 & 0x3F) << 6)
                                            | ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx, 1111 xxxx */
                    throw new UTFDataFormatException("malformed input around byte " + count);
            }
        }
        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararrCount);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException();
        }

        int remaining = this.limitInSegment - this.positionInSegment;
        if (remaining >= n) {
            this.positionInSegment += n;
            return n;
        } else {
            if (remaining == 0) {
                try {
                    advance();
                } catch (EOFException eofex) {
                    return 0;
                }
                remaining = this.limitInSegment - this.positionInSegment;
            }

            int skipped = 0;
            while (true) {
                int toSkip = Math.min(remaining, n);
                n -= toSkip;
                skipped += toSkip;

                if (n > 0) {
                    try {
                        advance();
                    } catch (EOFException eofex) {
                        return skipped;
                    }
                    remaining = this.limitInSegment - this.positionInSegment;
                } else {
                    this.positionInSegment += toSkip;
                    break;
                }
            }
            return skipped;
        }
    }

    @Override
    public void skipBytesToRead(int numBytes) throws IOException {
        if (numBytes < 0) {
            throw new IllegalArgumentException();
        }

        int remaining = this.limitInSegment - this.positionInSegment;
        if (remaining >= numBytes) {
            this.positionInSegment += numBytes;
        } else {
            if (remaining == 0) {
                advance();
                remaining = this.limitInSegment - this.positionInSegment;
            }

            while (true) {
                if (numBytes > remaining) {
                    numBytes -= remaining;
                    advance();
                    remaining = this.limitInSegment - this.positionInSegment;
                } else {
                    this.positionInSegment += numBytes;
                    break;
                }
            }
        }
    }
}
