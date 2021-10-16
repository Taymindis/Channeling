package com.github.taymindis.nio.channeling;



public interface BytesConsumer {

    /**
     *
     * @param bytes chunks of bytes
     * @return false means stop consuming
     *         true means as neutral
     */
    boolean accept(byte[] bytes);

}


