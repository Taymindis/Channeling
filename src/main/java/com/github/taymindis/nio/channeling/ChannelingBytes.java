package com.github.taymindis.nio.channeling;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ChannelingBytes extends OutputStream {

    private final byte[][] buffs;
    private int totalBytes = 0, buffCount = 0;
    private static final int DEFAULT_NUM_OF_WRITE = 256;
    private final int capacity;
    private boolean closed = false;
    private ChannelingBytesOverConsumer overWriteConsumer = new ChannelingBytesOverFIFOConsumer() {
        @Override
        public void consume(byte[] bytes) {

        }
    };

    public ChannelingBytes() {
        this(DEFAULT_NUM_OF_WRITE);
    }

    public ChannelingBytes(int numOfWrite) {
        this.buffs = new byte[numOfWrite][];
        this.capacity = numOfWrite;
    }


    @Override
    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        } else {
            addBuff(new byte[]{(byte) b});
            totalBytes++;
        }
    }

    private void addBuff(byte[] bytes) {
        if (buffCount < capacity) {
            buffs[buffCount++] = bytes;
        } else {
            overWriteConsumer.process(buffs, buffCount++, bytes);
        }
    }


    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || offset + length > data.length || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }
        addBuff(BytesHelper.subBytes(data, offset, offset + length));
        totalBytes += length;
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        }
        int len = data.length;
        if (len > 0) {
            addBuff(data);
            totalBytes += len;
        }
    }

    public void setOverWriteConsumer(ChannelingBytesOverLIFOConsumer overWriteConsumer) {
        this.overWriteConsumer = overWriteConsumer;
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

    public byte[] toByteArray(int limit) throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        }
        ByteBuffer buff = ByteBuffer.allocate(limit);
        int len;

        for (int i = 0; i < buffCount; i++) {
            byte[] streamByte = buffs[i];
            len = streamByte.length;
            if (limit > len) {
                buff.put(streamByte);
                limit -= streamByte.length;
            } else if (limit == 0) {
                break;
            } else {
                buff.put(streamByte, 0, limit);
                break;
            }
        }

        return buff.array();
    }

    public ChannelingBytesResult searchBytes(byte[] bytes, boolean matchAfter, boolean includeSearchBytes) {
        byte[] byteStream;
        int indexMatch = 0, searchByteLen = bytes.length;
        int i = 0;
        for (;i < buffCount; i++) {
            byteStream = buffs[i];
            for (int j = 0, len = byteStream.length; j < len; j++) {
                RETRY:
                if (bytes[indexMatch] == byteStream[j]) {
                    indexMatch++;
                    j++;
                    for (; i < buffCount; i++) {
                        byteStream = buffs[i];
                        for (len = byteStream.length; j < len; j++) {
                            if (bytes[indexMatch++] == byteStream[j]) {
                                if (indexMatch == searchByteLen) {
                                    final ChannelingBytesResult bytesResult;
                                    if (matchAfter) {
                                        /**
                                         * FIRST OFFSET
                                         */
//                                        byteStream = buffs[i];
//                                        channelingBytesLoop.consumer(byteStream, j, byteStream.length - j);
//                                        for (; z < sz; z++) {
//                                            byteStream = buffs[z];
//                                            channelingBytesLoop.consumer(byteStream, 0, byteStream.length);
//                                        }

                                        if (includeSearchBytes && j < searchByteLen) {
                                            searchByteLen -= j;

                                            do {
                                                i--;
                                                searchByteLen -= buffs[i].length;
                                            } while (searchByteLen > 0);

                                            j = buffs[i].length - (searchByteLen + buffs[i].length) ;
                                        }
                                        bytesResult = new ChannelingBytesResult(buffs, i, buffCount-1, j+1, buffs[buffCount-1].length);

                                    } else {
                                        if (!includeSearchBytes && j < searchByteLen) {
                                            searchByteLen -= j;

                                            do {
                                                i--;
                                                searchByteLen -= buffs[i].length;
                                            } while (searchByteLen > 0);

                                            j = buffs[i].length - (searchByteLen + buffs[i].length);
                                        }
                                        bytesResult = new ChannelingBytesResult(buffs, 0, i , 0, j+1);
                                    }

                                    return bytesResult;
                                }
                            } else {
                                indexMatch = 0;
                                break RETRY;
                            }
                        }
                        j = 0;
                    }
                }
            }
        }
        return null;
    }

    public ChannelingBytesResult searchBytesBefore(byte[] bytes, boolean includeSearchBytes) {
        return searchBytes(bytes, false, includeSearchBytes);
    }

    public ChannelingBytesResult searchBytesAfter(byte[] bytes, boolean includeSearchBytes) {
        return searchBytes(bytes, true, includeSearchBytes);
    }

    public void loopBuff(ChannelingBytesLoop channelingBytesLoop) {
        loopBuff(channelingBytesLoop, 0, totalBytes);
    }

    public void loopBuff(ChannelingBytesLoop channelingBytesLoop, int offset, int length) {
        int i = 0;
        byte[] streamByte;
        for (; i < buffCount; i++) {
            streamByte = buffs[i];
            offset -= streamByte.length;
            if (offset < 0) {
                offset += streamByte.length;
                int len = streamByte.length - offset;
                if (length > len) {
                    channelingBytesLoop.consumer(buffs[i], offset, len);
                    i++;
                    length -= len;
                    break;
                } else {
                    channelingBytesLoop.consumer(buffs[i], offset, length);
                    return;
                }
            }
        }


        for (; i < buffCount; i++) {
            streamByte = buffs[i];
            int len = streamByte.length;
            if (length > len) {
                channelingBytesLoop.consumer(buffs[i], offset, len);
                length = length - len;
            } else {
                channelingBytesLoop.consumer(buffs[i], offset, length);
                return;
            }
        }
    }

    public byte[] toByteArray() throws IOException {
        return toByteArray(totalBytes);
    }

    @Override
    public void close() {
        this.closed = true;
    }


    public int size() {
        return totalBytes;
    }

    public void reset() {
        buffCount = 0;
        totalBytes = 0;
    }
}


