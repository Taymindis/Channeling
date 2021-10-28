package com.github.taymindis.nio.channeling;

public interface ChannelingBytesLoop {
    /**
     *
     * @param bytes of chunked
     * @return false to stop, true to continue
     */
    boolean consumer(byte[] bytes, int offset, int length);
}


