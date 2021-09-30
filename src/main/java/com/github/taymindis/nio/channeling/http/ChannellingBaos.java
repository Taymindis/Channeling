package com.github.taymindis.nio.channeling.http;

import java.io.ByteArrayOutputStream;

public class ChannellingBaos extends ByteArrayOutputStream {
    public byte[] getBuf() {
        return this.buf;
    }

    public synchronized byte[] dupBytes() {
        return super.toByteArray();
    }
}
