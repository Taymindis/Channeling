package com.github.taymindis.nio.channeling;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ChannelingByteWriter {

    private byte[] buffs;
    private static final int DEFAULT_NUM_OF_WRITE = 64;
    private int capacity, readIdx = 0, size = 0;
    private boolean closed = false;

    public ChannelingByteWriter() {
        this(DEFAULT_NUM_OF_WRITE);
    }

    public ChannelingByteWriter(int size) {
        this.buffs = new byte[size];
        this.capacity = size;
    }

    private void shouldResize(int upComingSize) {
        while (upComingSize > (capacity - size)) {
            capacity *= 2;
        }
        buffs = Arrays.copyOf(buffs, capacity);
    }

    public void write(ByteBuffer byteBuffer, int offset, int length) throws IOException {
        if (offset < 0 || offset + length > byteBuffer.limit() || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }

        if (offset > 0) {
            byteBuffer.position(byteBuffer.position() + offset);
        }

        shouldResize(length);

        byteBuffer.get(buffs, size, length);

        size += length;
    }

    public void write(ByteBuffer byteBuffer) throws IOException {
        write(byteBuffer, 0, byteBuffer.limit() - byteBuffer.position());
    }

    @Deprecated
    public void write(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || offset + length > data.length || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }

        shouldResize(length);

        System.arraycopy(data, offset, buffs, size, length);

        size += length;
    }

    @Deprecated
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    public ChannelingBytes toChannelingBytes(int offset, int length) throws IOException {
        if (offset < 0 || offset + length > size || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }
        return new ChannelingBytes(buffs, offset, length);
    }

    public ChannelingBytes toChannelingBytes() throws IOException {
        return toChannelingBytes(0, size);
    }

    public ChannelingBytes toChannelingBytes(int offset) throws IOException {
        return toChannelingBytes(offset, size-offset);
    }

    public ChannelingBytes readToChannelingBytes(int length) throws IOException {
        int currReadIdx = readIdx;
        readIdx += length;
        return toChannelingBytes(currReadIdx, length);
    }

    public ChannelingBytes readToChannelingBytes() throws IOException {
        return readToChannelingBytes(size - readIdx);
    }


    public String toString(int offset, int length, Charset charset) throws IOException {
        if (offset < 0 || offset + length > size || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }
        return new String(buffs, offset, length, charset);
    }

    public String toString(Charset charset) throws IOException {
        return toString(0, size, charset);
    }

    public String toString(int length, Charset charset) throws IOException {
        return toString(0, size, charset);
    }

    public String toString(int length) throws IOException {
        return toString(0, size, StandardCharsets.UTF_8);
    }

    public String readToString(int length, Charset charset) throws IOException {
        int currReadIdx = readIdx;
        readIdx += length;
        return toString(currReadIdx, length, charset);
    }

    public String readToString(Charset charset) throws IOException {
        return readToString(size - readIdx, charset);
    }

    public String readToString() throws IOException {
        return readToString(StandardCharsets.UTF_8);
    }
    public String readToString(int length) throws IOException {
        return readToString(length, StandardCharsets.UTF_8);
    }

    public void skipBytes(int length) throws IOException {
        readIdx += length;
    }

    public void close() {
        this.closed = true;
    }

    public int capacity() {
        return capacity;
    }

    public int getReadIdx() {
        return readIdx;
    }

    public void setReadIdx(int readIdx) {
        this.readIdx = readIdx;
    }

    public int size() {
        return size;
    }

    public void resetRead() {
        readIdx = 0;
    }

    private void reset() {
        size = 0;
        readIdx = 0;
    }

    public int indexOf(byte[] target) {
        return indexOf(0, target);
    }

    public int indexOf(int offset, byte[] target) {
        int i = offset, sz = size - target.length + 1;
        tryNext:
        for (; i < sz; i++) {
            for (int x = 0, y = i; x < target.length; x++, y++) {
                if (buffs[y] != target[x]) {
                    continue tryNext;
                }
            }
            return i;
        }
        return -1;
    }

    public int lastIndexOf(byte[] target) {
        int i = size - 1, minLen = target.length - 1;
        tryNext:
        for (; i >= minLen; i--) {
            for (int x = minLen, y = i; x >= 0; x--, y--) {
                if (buffs[y] != target[x]) {
                    continue tryNext;
                }
            }
            return i;
        }
        return -1;
    }

    public boolean endsWith(byte[] target) {
        int y = size - 1;
        for (int x = target.length - 1; x >= 0; x--, y--) {
            if (buffs[y] != target[x]) {
                return false;
            }
        }
        return true;
    }


    public boolean startsWith(byte[] target) {
        for (int x = 0, y = 0; x < target.length; x++, y++) {
            if (buffs[y] != target[x]) {
                return false;
            }
        }
        return true;
    }
}


