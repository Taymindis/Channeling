package com.github.taymindis.nio.channeling.http;

public interface RequestListener {
    // TODO just park httpRequest first, as we just need a message block
    HttpResponseMessage handleRequest(HttpRequestMessage request);
}
