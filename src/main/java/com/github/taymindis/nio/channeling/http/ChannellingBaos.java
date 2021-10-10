package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.BytesHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


public class ChannellingBaos extends OutputStream {

    private final List<byte[]> buffQ = new LinkedList<>();
    private int totalBytes = 0;
    private boolean closed = false;

    @Override
    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        } else {
            buffQ.add(new byte[]{(byte) b});
            totalBytes++;
        }
    }


    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || offset + length > data.length || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (this.closed) {
            throw new IOException("Stream closed");
        }
        buffQ.add(BytesHelper.subBytes(data, offset, offset + length));
        totalBytes += length;
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        }
        int len = data.length;
        if (len > 0) {
            buffQ.add(data);
            totalBytes+=len;
        }
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
        Iterator<byte[]> iterator = buffQ.iterator();
        byte[] streamByte;
        while (iterator.hasNext()) {
            streamByte = iterator.next();
            len = streamByte.length;
            if(limit > len) {
                buff.put(streamByte);
                limit-=streamByte.length;
            } else if(limit == 0) {
                break;
            } else {
                buff.put(streamByte, 0, limit);
                break;
            }
        }
        return buff.array();
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
        buffQ.clear();
        totalBytes = 0;
    }
}


