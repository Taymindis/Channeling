package com.github.taymindis.nio.channeling;

public interface ErrorCallback {
    void error(ChannelingSocket sc, Exception e);
}
