package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingBytes;
import com.github.taymindis.nio.channeling.ChannelingSocket;


public interface HttpStreamRequestCallback {
    void headerAccept(ChannelingBytes bytes, ChannelingSocket socket) throws Exception;
    void afterHeader(ChannelingSocket socket) throws Exception;
    void accept(ChannelingBytes bytes, ChannelingSocket socket);
    void last(ChannelingBytes bytes, ChannelingSocket socket);
    void error(Exception e, ChannelingSocket socket);
}
