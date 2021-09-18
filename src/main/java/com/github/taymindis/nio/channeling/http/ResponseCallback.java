package com.github.taymindis.nio.channeling.http;

import java.nio.charset.Charset;

public interface ResponseCallback {
    void write(HttpResponseMessage responseMessage, Charset charset);
}
