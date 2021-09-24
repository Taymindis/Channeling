package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

import java.io.IOException;

public interface HttpStreamRequestCallback {
    void first(byte[] chunked, String headersContent, ChannelingSocket socket) throws Exception;
    void accept(byte[] chunked, ChannelingSocket socket);
    void last(byte[] chunked, ChannelingSocket socket);
    void error(Exception e, ChannelingSocket socket);
}
