package com.github.taymindis.nio.channeling;

public interface BytesDualConsumer {

    /**
     *
     * @param firstBytes first of bytes
     * @param secondBytes second of bytes
     * @return false means stop consuming
     *      * true means as neutral
     */
    boolean accept(byte[] firstBytes, byte[] secondBytes);
}
