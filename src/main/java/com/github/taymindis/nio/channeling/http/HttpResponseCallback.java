package com.github.taymindis.nio.channeling.http;

public interface HttpResponseCallback {
    void accept(HttpResponse response, Object attachment);
}
