package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.Then;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public interface ResponseCallback {
    void write(HttpResponseMessage responseMessage, Charset charset, Then $then);
    void streamWrite(ByteBuffer byteBuffer, Then $then);
}
