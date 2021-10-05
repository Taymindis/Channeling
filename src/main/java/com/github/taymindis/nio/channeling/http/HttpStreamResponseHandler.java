package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

public interface HttpStreamResponseHandler {
    void accept(byte[] chunked, ChannelingSocket socket) throws Exception;
    void last(byte[] chunked, ChannelingSocket socket) throws Exception;
}
