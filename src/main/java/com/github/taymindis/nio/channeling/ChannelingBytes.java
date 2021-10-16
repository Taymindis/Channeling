package com.github.taymindis.nio.channeling;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;


public class ChannelingBytes implements AutoCloseable {

    // The buffers used to store the content bytes
    private static final int DEFAULT_BLOCK_SIZE = 1024;
    private byte[] buffs;
    private int totalBytes = 0, lastFlushIndex = 0;
    private boolean closed = false;
    private int _factor = 2;


    public ChannelingBytes() {
        this(DEFAULT_BLOCK_SIZE);
    }

    public ChannelingBytes(int initBlockSize) {
        buffs = new byte[initBlockSize];
    }

    public void write(ByteBuffer buffer) throws IOException {
        write(buffer, buffer.position(), buffer.limit() - buffer.position());
    }

    public void write(ByteBuffer buffer, int offset, int length) throws IOException {
        bufferCheck(offset, length);
        buffer.position(offset);
        buffer.get(buffs, totalBytes, length);
        totalBytes += length;
    }

//    public void write(byte[] data, int offset, int length) throws IOException {
//        bufferCheck(offset, length);
//        System.arraycopy(data, offset, buffs, totalBytes, length);
//        totalBytes += length;
//    }

//    public void write(byte[] data) throws IOException {
//        write(data, 0, data.length);
//    }

    private void bufferCheck(int offset, int length) throws IOException {
        if (offset < 0 || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }
        ifResizeRequired(length);
    }

    private void ifResizeRequired(int length) {
        if (length > remaining()) {
            int newSize = capacity() * _factor;
            while ((newSize - totalBytes) < length) {
                newSize = newSize * _factor;
            }
            resize(newSize);
        }
    }

    public void setExpandFactor(int expandFactor) {
        if (expandFactor > 2) {
            _factor = expandFactor;
        }
    }


    public byte[] toByteArray() throws IOException {
        return toByteArray(0, totalBytes);
    }

    public void setFlushedPosition(int position) throws IOException {
        this.lastFlushIndex = position;
    }


    public byte[] flushedBytes(int limit) throws IOException {
        return toByteArray(this.lastFlushIndex, limit);
    }

    public byte[] toByteArray(int offset, int length) throws IOException {
        if (offset < 0 || offset + length > totalBytes || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }

        return ByteBuffer.wrap(buffs, offset, length).array();
    }


//
//    public byte[] toByteArray(int limit) throws IOException {
//        if (this.closed) {
//            throw new IOException("Stream closed");
//        }
//        int len;
//        Iterator<byte[]> iterator = buffQ.iterator();
//        byte[] streamByte, values = new byte[0];
//        while (iterator.hasNext()) {
//            streamByte = iterator.next();
//            len = streamByte.length;
//            if(limit > len) {
//                values = putVal(values, streamByte);
//                limit-=streamByte.length;
//            } else if(limit == 0) {
//                break;
//            } else {
//                values = putVal(values, streamByte, 0, limit);
//                break;
//            }
//        }
//        return values;
//    }
//
//    private byte[] putVal(byte[] values, byte[] streamByte) {
//        int offset = values.length;
//        values = Arrays.copyOf(values, offset+streamByte.length);
//        System.arraycopy(streamByte, 0, values, offset, streamByte.length);
//        return values;
//    }
//
//    private byte[] putVal(byte[] values, byte[] streamByte, int i, int limit) {
//        int offset = values.length;
//        values = Arrays.copyOf(values, offset+limit);
//        System.arraycopy(streamByte, i, values, offset, limit);
//        return values;
//    }

    private void resize(int newSize) {
        buffs = Arrays.copyOf(buffs, newSize);
    }

//    public void iterateBytesChunk(BytesConsumer consumer, int offset, int limit, boolean flushable) throws IOException {
//        if (this.closed) {
//            throw new IOException("Stream closed");
//        }
//
//        if (flushable) {
//            if (offset < lastFlushedIndex) {
//                offset = lastFlushedIndex;
//            }
//        }
//
//        Iterator<byte[]> iterator = buffQ.iterator();
//        byte[] streamByte;
//        int len;
//        while (iterator.hasNext()) {
//            streamByte = iterator.next();
//            len = streamByte.length;
//            offset -= len;
//            if (offset <= 0) {
//                do {
//                    if (limit > len) {
//                        if (!consumer.accept(streamByte))
//                            break;
//                        limit -= streamByte.length;
//                    } else if (limit == 0) {
//                        break;
//                    } else {
//                        consumer.accept(ByteBuffer.wrap(streamByte, 0, limit).array());
//                        break;
//                    }
//                    if (!iterator.hasNext()) {
//                        break;
//                    }
//                    streamByte = iterator.next();
//                    len = streamByte.length;
//                } while (true);
//                break;
//            }
//        }
//        if (flushable) {
//            lastFlushedIndex = offset + limit;
//        }
//    }

//    public void iterateBytesChunk(BytesDualConsumer consumer, int offset, int limit, boolean flushable) throws IOException {
//        if (this.closed) {
//            throw new IOException("Stream closed");
//        }
//
//        Iterator<byte[]> iterator = buffQ.iterator();
//        byte[] firstBytes, secondBytes;
//        int len;
//
//        if(flushable) {
//            if(offset<lastFlushedIndex) {
//                offset = lastFlushedIndex;
//            }
//        }
//
//
//        if(iterator.hasNext()) {
//            firstBytes = iterator.next();
//            len = firstBytes.length;
//            offset -= len;
//
////            if(offset <= 0 && len >= limit) {
////                consumer.accept(firstBytes, new byte[0]);
////                return;
////            }
//
//            while (iterator.hasNext()) {
//                secondBytes = iterator.next();
//                len = secondBytes.length;
//                offset -= len;
//                if (offset <= 0) {
//                    do {
//                        if (limit > len) {
//                            if (!consumer.accept(firstBytes, secondBytes))
//                                break;
//                            limit -= secondBytes.length;
//                        } else if (limit == 0) {
//                            break;
//                        } else {
//                            consumer.accept(firstBytes, secondBytes);
//                            break;
//                        }
//                        if (!iterator.hasNext()) {
//                            break;
//                        }
//                        firstBytes = secondBytes;
//                        secondBytes = iterator.next();
//                        len = secondBytes.length;
//                    } while (true);
//                    break;
//                }
//                firstBytes = secondBytes;
//            }
//            if (flushable) {
//                lastFlushedIndex = offset + limit;
//            }
//        }
//    }

//    public void iterateBytesChunk(BytesConsumer consumer) throws IOException {
//        iterateBytesChunk(consumer, 0, totalBytes, false);
//    }

//    public void iterateBytesChunk(BytesDualConsumer consumer) throws IOException {
//        iterateBytesChunk(consumer, 0, totalBytes, false);
//    }

//    private Iterator nextSearch(Iterator<byte[]> buffIterator, byte[] streamByte, int index, int lengthOfBytes) {
//
//    }
//
//    public void searchBytes(int offset, byte[] byteToSearch, int lengthOfBytes, BytesConsumer consumer, boolean consumeAfter) {
//
//        byte[] streamByte = null;
//        int len;
//        Iterator<byte[]> iterator = buffQ.iterator();
//        DONE:
//        while (iterator.hasNext()) {
//            streamByte = iterator.next();
//            len = streamByte.length;
//            offset -= len;
//            if (offset <= 0) {
//                RETRY:
//                for (int i = offset + len; i < len; i++) {
//                    for (int j = 0; j < lengthOfBytes; j++) {
//                        if (streamByte[i + j] != byteToSearch[i]) {
//                            break RETRY;
//                        }
//                        if ((i + j + 1) == len) {
//                            if (nextSearch(iterator, byteToSearch, j, lengthOfBytes)) {
//                                break DONE;
//                            } else {
//                                break RETRY;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        // Found
//        if (consumer.accept(ByteBuffer.wrap(streamByte, i, len - i).array())) {
//            while (iterator.hasNext()) {
//                if (!consumer.accept(iterator.next())) {
//                    break DONE;
//                }
//            }
//        }
//
//
//    }
//
//    public void searchBytes(int offset, byte[] byteToSearch, BytesConsumer consumer) {
//        searchBytes(offset, byteToSearch, consumer);
//    }
//
//    public void searchBytes(byte[] byteToSearch, BytesConsumer consumer) {
//        searchBytes(0, byteToSearch, consumer);
//    }
//
//    public void flushBytesChunk(BytesConsumer consumer) throws IOException {
//        iterateBytesChunk(consumer, 0, totalBytes, true);
//    }
//
////    public void flushBytesChunk(BytesDualConsumer consumer) throws IOException {
////        iterateBytesChunk(consumer, 0, totalBytes, true);
////    }
//
//    public byte[] dupByteArray(int offset, int limit) throws IOException {
//        if (this.closed) {
//            throw new IOException("Stream closed");
//        }
//        Iterator<byte[]> iterator = buffQ.iterator();
//        byte[] streamByte;
//        int len;
//        while (iterator.hasNext()) {
//            streamByte = iterator.next();
//            len = streamByte.length;
//            offset -= len;
//            if (offset <= 0) {
//                ByteBuffer buff = ByteBuffer.allocate(limit);
//                do {
//                    if (limit > len) {
//                        buff.put(streamByte);
//                        limit -= streamByte.length;
//                    } else if (limit == 0) {
//                        break;
//                    } else {
//                        buff.put(streamByte, 0, limit);
//                        break;
//                    }
//                    if (!iterator.hasNext()) {
//                        break;
//                    }
//                    streamByte = iterator.next();
//                    len = streamByte.length;
//                } while (true);
//                return buff.array();
//            }
//        }
//
//        return new byte[0];
//    }
//
//    public byte[] dupByteArray() throws IOException {
//        return dupByteArray(0, totalBytes);
//    }

    /**
     * If you are using flush
     */
    public void flip() {
        lastFlushIndex = 0;
    }


//
//    public byte[] toByteArray(int limit) throws IOException {
//        if (this.closed) {
//            throw new IOException("Stream closed");
//        }
//        ByteBuffer buff = ByteBuffer.allocate(limit);
//        int len;
//        Iterator<byte[]> iterator = buffQ.iterator();
//        byte[] streamByte;
//        while (iterator.hasNext()) {
//            streamByte = iterator.next();
//            len = streamByte.length;
//            if(limit > len) {
//                buff.put(streamByte);
//                limit-=streamByte.length;
//            } else if(limit == 0) {
//                break;
//            } else {
//                buff.put(streamByte, 0, limit);
//                break;
//            }
//        }
//        return buff.array();
//    }
//
//    public byte[] toByteArray() throws IOException {
//        return toByteArray(totalBytes);
//    }

    @Override
    public void close() {
        this.closed = true;
    }


    public int size() {
        return totalBytes;
    }

    public int remaining() {
        return buffs.length - totalBytes;
    }

    public int flushed() {
        return lastFlushIndex;
    }

    public int capacity() {
        return buffs.length;
    }

    public void clear() {
        lastFlushIndex = 0;
        totalBytes = 0;
    }
}


