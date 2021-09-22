package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

import java.io.IOException;
import java.util.function.Consumer;

public interface HttpRequest {
    void connectAndThen(ChannelingSocket channelingSocket);

    void writeAndThen(ChannelingSocket channelingSocket);

    void readAndThen(ChannelingSocket channelingSocket);

    void closeAndThen(ChannelingSocket channelingSocket);

    void error(ChannelingSocket channelingSocket,Exception e);

    void execute(Consumer<HttpResponse> result, Consumer<Exception> error) throws IOException;

    void execute(HttpStreamRequestCallback callback, Consumer<Exception> error);
}
