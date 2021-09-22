package com.github.taymindis.nio.channeling.http;

public interface HttpStreamRequestCallback {
    void accept(byte[] chunked, boolean isLast);
}
