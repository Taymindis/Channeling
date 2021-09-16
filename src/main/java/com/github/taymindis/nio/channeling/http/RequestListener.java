package com.github.taymindis.nio.channeling.http;

public interface RequestListener {


    HttpResponse handleRequest(HttpRequest request);

}
