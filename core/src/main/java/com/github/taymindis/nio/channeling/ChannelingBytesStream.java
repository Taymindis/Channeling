package com.github.taymindis.nio.channeling;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ChannelingBytesStream {

    private byte[][] buffs;
    private int totalBytes = 0, buffCount = 0;
    private static final int DEFAULT_NUM_OF_WRITE = 256;
    private int capacity, readIdx = 0;
    private boolean closed = false;
    private ChannelingBytesOverConsumer overWriteConsumer = new ChannelingBytesOverFIFOConsumer() {
        @Override
        public void consume(byte[] bytes) {

        }
    };

    public ChannelingBytesStream() {
        this(DEFAULT_NUM_OF_WRITE);
    }

    public ChannelingBytesStream(int numOfWrite) {
        this.buffs = new byte[numOfWrite][];
        this.capacity = numOfWrite;
    }

    private void addBuff(byte[] bytes) {
        if (buffCount >= capacity) {
            capacity *= 2;
            buffs = Arrays.copyOfRange(buffs, 0, capacity);
//            overWriteConsumer.process(buffs, buffCount++, bytes);
        }
        buffs[buffCount++] = bytes;
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || offset + length > data.length || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }
        if (data.length == length) {
            addBuff(data);
        } else {
            addBuff(BytesHelper.subBytes(data, offset, offset + length));
        }
        totalBytes += length;
    }

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
        for (int i = 0, sz = buffCount; i < sz; i++) {
            byteStream = buffs[i];
            for (int j = 0, len = byteStream.length; j < len; j++) {
                RETRY:
                if (bytes[indexMatch] == byteStream[j]) {
                    indexMatch++;
                    j++;
                    for (; i < sz; i++) {
                        byteStream = buffs[i];
                        for (len = byteStream.length; j < len; j++) {
                            if (bytes[indexMatch++] == byteStream[j]) {
                                if (indexMatch == searchByteLen) {
                                    final ChannelingBytesResult bytesResult;
                                    if (matchAfter) {
                                        if (includeSearchBytes) {
                                            if (j < searchByteLen) {
                                                searchByteLen -= j;

                                                do {
                                                    i--;
                                                    searchByteLen -= buffs[i].length;
                                                } while (searchByteLen > 0);

                                                j = buffs[i].length - (searchByteLen + buffs[i].length);
                                            } else {
                                                j -= searchByteLen;
                                            }
                                        }
                                        bytesResult = new ChannelingBytesResult(this, buffs, i, sz - 1, j + 1, buffs[sz - 1].length);

                                    } else {
                                        if (!includeSearchBytes) {
                                            if (j < searchByteLen) {
                                                searchByteLen -= j;

                                                do {
                                                    i--;
                                                    searchByteLen -= buffs[i].length;
                                                } while (searchByteLen > 0);

                                                j = buffs[i].length - (searchByteLen + buffs[i].length);
                                            } else {
                                                j -= searchByteLen;
                                            }
                                        }
                                        bytesResult = new ChannelingBytesResult(this, buffs, 0, i, 0, j + 1);
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


    public ChannelingBytesResult searchBytes(byte[] bytes, boolean matchAfter, boolean includeSearchBytes, ChannelingBytesResult basedOnResult) {
        byte[] byteStream;
        int indexMatch = 0, searchByteLen = bytes.length;
        int i = basedOnResult.getBuffIndexStart(),
                buffIndexEnd = basedOnResult.getBuffIndexEnd();
        boolean onlyOne = i == buffIndexEnd;

        byteStream = buffs[i];
        for (int j = basedOnResult.getOffSetOfFirst(), len = onlyOne ? basedOnResult.getLimitOfEnd() : byteStream.length;
             j < len; j++) {
            RETRY:
            if (bytes[indexMatch] == byteStream[j]) {
                indexMatch++;
                j++;
                for (len = byteStream.length; j < len; j++) {
                    if (bytes[indexMatch++] == byteStream[j]) {
                        if (indexMatch == searchByteLen) {
                            final ChannelingBytesResult bytesResult;
                            if (matchAfter) {
                                if (includeSearchBytes) {
                                    if (j < searchByteLen) {
                                        searchByteLen -= j;

                                        do {
                                            i--;
                                            searchByteLen -= buffs[i].length;
                                        } while (searchByteLen > 0);

                                        j = buffs[i].length - (searchByteLen + buffs[i].length);
                                    } else {
                                        j -= searchByteLen;
                                    }
                                }
                                bytesResult = new ChannelingBytesResult(this, buffs, i, buffIndexEnd, j + 1, basedOnResult.getLimitOfEnd());
                            } else {
                                if (!includeSearchBytes) {
                                    if (j < searchByteLen) {
                                        searchByteLen -= j;

                                        do {
                                            i--;
                                            searchByteLen -= buffs[i].length;
                                        } while (searchByteLen > 0);

                                        j = buffs[i].length - (searchByteLen + buffs[i].length);
                                    } else {
                                        j -= searchByteLen;
                                    }
                                }
                                bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), i,
                                        basedOnResult.getOffSetOfFirst(), j + 1);
                            }
                            return bytesResult;
                        }
                    } else {
                        indexMatch = 0;
                        break RETRY;
                    }
                }
            }
        }

        if (onlyOne) {
            return null;
        }
        i++;

        for (; i < buffIndexEnd; i++) {
            byteStream = buffs[i];
            for (int j = 0, len = byteStream.length; j < len; j++) {
                RETRY:
                if (bytes[indexMatch] == byteStream[j]) {
                    indexMatch++;
                    j++;
                    for (; i < buffIndexEnd; i++) {
                        byteStream = buffs[i];
                        for (len = byteStream.length; j < len; j++) {
                            if (bytes[indexMatch++] == byteStream[j]) {
                                if (indexMatch == searchByteLen) {
                                    final ChannelingBytesResult bytesResult;
                                    if (matchAfter) {
                                        if (includeSearchBytes) {
                                            if (j < searchByteLen) {
                                                searchByteLen -= j;

                                                do {
                                                    i--;
                                                    searchByteLen -= buffs[i].length;
                                                } while (searchByteLen > 0);

                                                j = buffs[i].length - (searchByteLen + buffs[i].length);
                                            } else {
                                                j -= searchByteLen;
                                            }
                                        }
                                        bytesResult = new ChannelingBytesResult(this, buffs, i, buffIndexEnd, j + 1, basedOnResult.getLimitOfEnd());
                                    } else {
                                        if (!includeSearchBytes) {
                                            if (j < searchByteLen) {
                                                searchByteLen -= j;

                                                do {
                                                    i--;
                                                    searchByteLen -= buffs[i].length;
                                                } while (searchByteLen > 0);

                                                j = buffs[i].length - (searchByteLen + buffs[i].length);
                                            } else {
                                                j -= searchByteLen;
                                            }
                                        }
                                        bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), i,
                                                basedOnResult.getOffSetOfFirst(), j + 1);
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

        byteStream = buffs[i];
        for (int j = 0, len = basedOnResult.getLimitOfEnd();
             j < len; j++) {
            RETRY:
            if (bytes[indexMatch] == byteStream[j]) {
                indexMatch++;
                j++;
                for (len = byteStream.length; j < len; j++) {
                    if (bytes[indexMatch++] == byteStream[j]) {
                        if (indexMatch == searchByteLen) {
                            final ChannelingBytesResult bytesResult;
                            if (matchAfter) {
                                if (includeSearchBytes) {
                                    if (j < searchByteLen) {
                                        searchByteLen -= j;

                                        do {
                                            i--;
                                            searchByteLen -= buffs[i].length;
                                        } while (searchByteLen > 0);

                                        j = buffs[i].length - (searchByteLen + buffs[i].length);
                                    } else {
                                        j -= searchByteLen;
                                    }
                                }
                                bytesResult = new ChannelingBytesResult(this, buffs, i, buffIndexEnd, j + 1, basedOnResult.getLimitOfEnd());
                            } else {
                                if (!includeSearchBytes) {
                                    if (j < searchByteLen) {
                                        searchByteLen -= j;

                                        do {
                                            i--;
                                            searchByteLen -= buffs[i].length;
                                        } while (searchByteLen > 0);

                                        j = buffs[i].length - (searchByteLen + buffs[i].length);
                                    } else {
                                        j -= searchByteLen;
                                    }
                                }
                                bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), i,
                                        basedOnResult.getOffSetOfFirst(), j + 1);
                            }
                            return bytesResult;
                        }
                    } else {
                        indexMatch = 0;
                        break RETRY;
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

    public ChannelingBytesResult searchBytesBefore(byte[] bytes, boolean includeSearchBytes, ChannelingBytesResult basedOnResult) {
        return searchBytes(bytes, false, includeSearchBytes, basedOnResult);
    }

    public ChannelingBytesResult searchBytesAfter(byte[] bytes, boolean includeSearchBytes, ChannelingBytesResult basedOnResult) {
        return searchBytes(bytes, true, includeSearchBytes, basedOnResult);
    }

    public ChannelingBytesResult reverseSearchBytes(byte[] bytes, boolean matchAfter, boolean includeSearchBytes) {
        byte[] byteStream;
        int searchByteLen = bytes.length;
        int indexMatch = searchByteLen - 1;
        int searchPointI, searchPointJ;
        for (int i = buffCount - 1; i >= 0; i--) {
            byteStream = buffs[i];
            for (int j = byteStream.length - 1; j >= 0; j--) {
                RETRY:
                if (bytes[indexMatch] == byteStream[j]) {
                    searchPointI = i;
                    searchPointJ = j;
                    indexMatch--;
                    j--;
                    for (; i >= 0; i--) {
                        byteStream = buffs[i];
                        for (; j >= 0; j--) {
                            if (bytes[indexMatch--] == byteStream[j]) {
                                if (indexMatch < 0) {
                                    final ChannelingBytesResult bytesResult;
                                    if (matchAfter) {
                                        if (includeSearchBytes) {
                                            bytesResult = new ChannelingBytesResult(this, buffs, i, buffCount - 1, j, buffs[buffCount - 1].length);
                                        } else {
                                            bytesResult = new ChannelingBytesResult(this, buffs, searchPointI, buffCount - 1, searchPointJ + 1, buffs[buffCount - 1].length);
                                        }
                                    } else {
                                        if (includeSearchBytes) {
                                            bytesResult = new ChannelingBytesResult(this, buffs, 0, searchPointI, 0, searchPointJ + 1);
                                        } else {
                                            bytesResult = new ChannelingBytesResult(this, buffs, 0, i, 0, j);
                                        }
                                    }

                                    return bytesResult;
                                }
                            } else {
                                indexMatch = searchByteLen - 1;
                                break RETRY;
                            }
                        }
                        if (i != 0) {
                            j = buffs[i - 1].length - 1;
                        }
                    }
                }
            }
        }
        return null;
    }


    public ChannelingBytesResult reverseSearchBytes(byte[] bytes, boolean matchAfter, boolean includeSearchBytes, ChannelingBytesResult basedOnResult) {
        byte[] byteStream;
        int searchByteLen = bytes.length;
        int indexMatch = searchByteLen - 1;
        int start = basedOnResult.getBuffIndexStart(),
                i = basedOnResult.getBuffIndexEnd();
        boolean onlyOne = start == i;
        int searchPointI, searchPointJ;

        byteStream = buffs[i];
        for (int j = basedOnResult.getLimitOfEnd() - 1, minLen = onlyOne ? basedOnResult.getOffSetOfFirst() : 0;
             j >= minLen; j--) {
            RETRY:
            if (bytes[indexMatch] == byteStream[j]) {
                searchPointI = i;
                searchPointJ = j;
                indexMatch--;
                j--;
                for (; i >= start; i--) {
                    byteStream = buffs[i];
                    for (; j >= 0; j--) {
                        if (bytes[indexMatch--] == byteStream[j]) {
                            if (indexMatch < 0) {
                                final ChannelingBytesResult bytesResult;
                                if (matchAfter) {
                                    if (includeSearchBytes) {
                                        bytesResult = new ChannelingBytesResult(this, buffs, i, basedOnResult.getBuffIndexEnd(), j, basedOnResult.getLimitOfEnd());
                                    } else {
                                        bytesResult = new ChannelingBytesResult(this, buffs, searchPointI, basedOnResult.getBuffIndexEnd(), searchPointJ + 1, basedOnResult.getLimitOfEnd());
                                    }
                                } else {
                                    if (includeSearchBytes) {
                                        bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), searchPointI, basedOnResult.getOffSetOfFirst(), searchPointJ + 1);
                                    } else {
                                        bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), i, basedOnResult.getOffSetOfFirst(), j);
                                    }
                                }
                                return bytesResult;
                            }
                        } else {
                            indexMatch = searchByteLen - 1;
                            break RETRY;
                        }
                    }
                    if (i != start) {
                        j = buffs[i - 1].length - 1;
                    }
                }
            }
        }
        if (onlyOne) {
            // Means not found
            return null;
        }
        i--;

        for (; i > start; i--) {
            byteStream = buffs[i];
            for (int j = byteStream.length - 1; j >= 0; j--) {
                RETRY:
                if (bytes[indexMatch] == byteStream[j]) {
                    searchPointI = i;
                    searchPointJ = j;
                    indexMatch--;
                    j--;
                    for (; i >= start; i--) {
                        byteStream = buffs[i];
                        for (; j >= 0; j--) {
                            if (bytes[indexMatch--] == byteStream[j]) {
                                if (indexMatch < 0) {
                                    final ChannelingBytesResult bytesResult;
                                    if (matchAfter) {
                                        if (includeSearchBytes) {
                                            bytesResult = new ChannelingBytesResult(this, buffs, i, basedOnResult.getBuffIndexEnd(), j, basedOnResult.getLimitOfEnd());
                                        } else {
                                            bytesResult = new ChannelingBytesResult(this, buffs, searchPointI, basedOnResult.getBuffIndexEnd(), searchPointJ + 1, basedOnResult.getLimitOfEnd());
                                        }
                                    } else {
                                        if (includeSearchBytes) {
                                            bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), searchPointI, basedOnResult.getOffSetOfFirst(), searchPointJ + 1);
                                        } else {
                                            bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), i, basedOnResult.getOffSetOfFirst(), j);
                                        }
                                    }
                                    return bytesResult;
                                }
                            } else {
                                indexMatch = searchByteLen - 1;
                                break RETRY;
                            }
                        }
                        if (i != start) {
                            j = buffs[i - 1].length - 1;
                        }
                    }
                }
            }
        }

        byteStream = buffs[i];
        for (int j = byteStream.length - 1, minLen = basedOnResult.getOffSetOfFirst();
             j >= minLen; j--) {
            RETRY:
            if (bytes[indexMatch] == byteStream[j]) {
                searchPointI = i;
                searchPointJ = j;
                indexMatch--;
                j--;
                for (; i >= start; i--) {
                    byteStream = buffs[i];
                    for (; j >= 0; j--) {
                        if (bytes[indexMatch--] == byteStream[j]) {
                            if (indexMatch < 0) {
                                final ChannelingBytesResult bytesResult;
                                if (matchAfter) {
                                    if (includeSearchBytes) {
                                        bytesResult = new ChannelingBytesResult(this, buffs, i, basedOnResult.getBuffIndexEnd(), j, basedOnResult.getLimitOfEnd());
                                    } else {
                                        bytesResult = new ChannelingBytesResult(this, buffs, searchPointI, basedOnResult.getBuffIndexEnd(), searchPointJ + 1, basedOnResult.getLimitOfEnd());
                                    }
                                } else {
                                    if (includeSearchBytes) {
                                        bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), searchPointI, basedOnResult.getOffSetOfFirst(), searchPointJ + 1);
                                    } else {
                                        bytesResult = new ChannelingBytesResult(this, buffs, basedOnResult.getBuffIndexStart(), i, basedOnResult.getOffSetOfFirst(), j);
                                    }
                                }

                                return bytesResult;
                            }
                        } else {
                            indexMatch = searchByteLen - 1;
                            break RETRY;
                        }
                    }
                    if (i != start) {
                        j = buffs[i - 1].length - 1;
                    }
                }
            }
        }
        return null;
    }

    public ChannelingBytesResult reverseSearchBytesBefore(byte[] bytes, boolean includeSearchBytes) {
        return reverseSearchBytes(bytes, false, includeSearchBytes);
    }

    public ChannelingBytesResult reverseSearchBytesAfter(byte[] bytes, boolean includeSearchBytes) {
        return reverseSearchBytes(bytes, true, includeSearchBytes);
    }

    public ChannelingBytesResult reverseSearchBytesBefore(byte[] bytes, boolean includeSearchBytes, ChannelingBytesResult basedOnResult) {
        return reverseSearchBytes(bytes, false, includeSearchBytes, basedOnResult);
    }

    public ChannelingBytesResult reverseSearchBytesAfter(byte[] bytes, boolean includeSearchBytes, ChannelingBytesResult basedOnResult) {
        return reverseSearchBytes(bytes, true, includeSearchBytes, basedOnResult);
    }

    public boolean read(ChannelingBytes bytes) {
        if (readIdx < buffCount) {
            byte[] buff = buffs[readIdx];
            bytes.setBuff(buff);
            bytes.setOffset(0);
            bytes.setLength(buff.length);
            readIdx++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param bytes
     * @return false if it is last unit or null
     */
    public boolean readUntilLast(ChannelingBytes bytes) {
        if (readIdx < buffCount) {
            byte[] buff = buffs[readIdx];
            bytes.setBuff(buff);
            bytes.setOffset(0);
            bytes.setLength(buff.length);
            readIdx++;
            return readIdx < buffCount;
        }
        bytes.setBuff(null);
        bytes.setOffset(0);
        bytes.setLength(0);
        return false;
    }

    public void forEach(ChannelingBytesLoop channelingBytesLoop) {
        forEach(channelingBytesLoop, 0, totalBytes);
    }

    public void forEach(ChannelingBytesLoop channelingBytesLoop, int offset, int length) {
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
        if (this.closed) {
            throw new IOException("Stream closed");
        }
        ByteBuffer buff = ByteBuffer.allocate(totalBytes);

        for (int i = 0; i < buffCount; i++) {
            buff.put(buffs[i]);
        }

        return buff.array();
    }

    public void close() {
        this.closed = true;
    }

    public int getTotalBuffers() {
        return buffCount;
    }

    public int size() {
        return totalBytes;
    }

    public void resetRead() {
        readIdx = 0;
    }

    public void reset() {
        buffCount = 0;
        totalBytes = 0;
        readIdx = 0;
    }
}


