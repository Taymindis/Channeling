package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingBytes;
import com.github.taymindis.nio.channeling.ChannelingSocket;


public interface HttpStreamRequestCallback {
    void headerAccept(byte[] chunked, int offset, int length, ChannelingSocket socket) throws Exception;
    void afterHeader(ChannelingSocket socket) throws Exception;
    void accept(byte[] chunked, int offset, int length, ChannelingSocket socket);
    void last(byte[] chunked, int offset, int length, ChannelingSocket socket);
    void error(Exception e, ChannelingSocket socket);
}
