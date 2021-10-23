package com.github.taymindis.nio.channeling;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ChannelingBytesStream2 {

    private byte[] buffs;
    private static final int DEFAULT_NUM_OF_WRITE = 1024 * 10;
    private int capacity, readIdx = 0, size = 0;
    private boolean closed = false;

    public ChannelingBytesStream2() {
        this(DEFAULT_NUM_OF_WRITE);
    }

    public ChannelingBytesStream2(int size) {
        this.buffs = new byte[size];
        this.capacity = size;
    }

    private void shouldResize(int upComingSize) {
        while(upComingSize > (capacity - size)) {
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

        if(offset > 0) {
            byteBuffer.position(byteBuffer.position() + offset);
        }

        shouldResize(length);

        byteBuffer.get(buffs, size, length);

        size += length;
    }

    public void write(ByteBuffer byteBuffer) throws IOException {
        write(byteBuffer, 0, byteBuffer.limit() - byteBuffer.position());
    }

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

    public void skipRead(int length) throws IOException {
        readIdx += length;
    }

    public ChannelingBytes readToChannelingBytes(int length) throws IOException {
        int currReadIdx = readIdx;
        readIdx += length;
        return toChannelingBytes(currReadIdx, length);
    }

    public ChannelingBytes readToChannelingBytes() throws IOException {
        return readToChannelingBytes(size - readIdx);
    }

    public void close() {
        this.closed = true;
    }

    public int getCapacity() {
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

    public void reset() {
        size = 0;
        readIdx = 0;
    }
}


