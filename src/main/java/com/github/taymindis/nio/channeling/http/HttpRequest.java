package com.github.taymindis.nio.channeling.http;

public interface HttpRequest {
//    void connectAndThen(ChannelingSocket channelingSocket);
//
//    void writeAndThen(ChannelingSocket channelingSocket);
//
//    void readAndThen(ChannelingSocket channelingSocket);
//
//    void closeAndThen(ChannelingSocket channelingSocket);

//    void error(ChannelingSocket channelingSocket,Exception e);

    void execute(HttpSingleRequestCallback callback);

    void execute(HttpStreamRequestCallback callback);
}
