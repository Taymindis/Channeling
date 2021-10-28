package com.github.taymindis.nio.channeling;

public abstract class ChannelingBytesOverFIFOConsumer implements ChannelingBytesOverConsumer {

    /**
     * @param bytes of over size bytes discard from first
     */

    public void process(byte[][] buffs, int byteCount, byte[] bytes) {
        int targetIndex = byteCount % buffs.length;
        byte[] targetBytes = buffs[targetIndex];
        consume(targetBytes);
        buffs[targetIndex] = bytes;
    }


    /**
     * @param bytes of over size bytes discard
     */
    public abstract void consume(byte[] bytes);
}


