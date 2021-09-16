package com.github.taymindis.nio.channeling.http;

public interface RequestListener {
    // TODO just park httpRequest first, as we just need a message block
    HttpResponse handleRequest(HttpRequestMessage request);
}
