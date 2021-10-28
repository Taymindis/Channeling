package com.github.taymindis.nio.channeling;

public abstract class ChannelingBytesOverLIFOConsumer implements ChannelingBytesOverConsumer {

    /**
     * @param bytes of over size bytes discard from first
     */

    public void process(byte[][] buffs, int byteCount, byte[] bytes) {
        consume(bytes);
    }


    /**
     * @param bytes of over size bytes discard
     */
    public abstract void consume(byte[] bytes);
}


