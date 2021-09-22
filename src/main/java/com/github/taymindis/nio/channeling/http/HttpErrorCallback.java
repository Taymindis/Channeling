package com.github.taymindis.nio.channeling.http;

public interface HttpErrorCallback {
    void accept(Exception exception, Object attachment);
}
