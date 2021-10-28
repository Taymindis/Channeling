package com.github.taymindis.nio.channeling;


import java.util.Arrays;

public class ChannelingBytes {

    private byte[] buff;
    private int offset, length;
    public ChannelingBytes(){}
    public ChannelingBytes(byte[] buff, int offset, int length) {
        this.buff = buff;
        this.offset = offset;
        this.length = length;
    }

    public byte[] getBuff() {
        return buff;
    }

    public void setBuff(byte[] buff) {
        this.buff = buff;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public byte[] toByteArray() {
       return Arrays.copyOfRange(buff, offset, offset + length);
    }

    @Override
    public String toString() {
       return new String(buff, offset, length);
    }
}



