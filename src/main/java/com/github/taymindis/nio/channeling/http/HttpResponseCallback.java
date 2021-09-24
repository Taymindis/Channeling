package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

public interface HttpResponseCallback {
    void accept(HttpResponse response, Object attachment);
    void error(Exception e, ChannelingSocket socket);

}
