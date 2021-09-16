package com.github.taymindis.nio.channeling.http;

public class HttpRequestMessage {
    private final byte[] rawBytes;

    public HttpRequestMessage(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }


    public byte[] getRawBytes() {
        return rawBytes;
    }
}
