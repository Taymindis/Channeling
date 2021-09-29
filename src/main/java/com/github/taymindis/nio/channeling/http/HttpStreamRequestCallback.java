package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;


public interface HttpStreamRequestCallback {
    void header(String headersContent, ChannelingSocket socket) throws Exception;
    void accept(byte[] chunked, ChannelingSocket socket);
    void last(byte[] chunked, ChannelingSocket socket);
    void error(Exception e, ChannelingSocket socket);
}
