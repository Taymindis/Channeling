package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

public interface RedirectionSocket {
    ChannelingSocket request(String host, int port, boolean isSSL) throws Exception;
}
