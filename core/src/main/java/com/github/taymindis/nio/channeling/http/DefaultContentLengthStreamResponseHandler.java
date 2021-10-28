package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

import java.nio.ByteBuffer;

/**
 Note that the Content-Length is equal to the length of the body after the Content-Encoding.
 This means if you have gzipped your response, then the length calculation happens after compression.
 You will need to be able to load the entire body in memory if you want to calculate the length
 (unless you have that information elsewhere).
 */
public class DefaultContentLengthStreamResponseHandler implements HttpStreamResponseHandler {
    private final  ResponseCallback callback;
    public DefaultContentLengthStreamResponseHandler(ResponseCallback callback) {
        this.callback = callback;
    }

    @Override
    public void accept(byte[] chunked, ChannelingSocket socket) {
        callback.streamWrite(ByteBuffer.wrap(chunked), clientSocket -> {
        });
    }

    @Override
    public void last(byte[] chunked, ChannelingSocket socket) {
        callback.streamWrite(ByteBuffer.wrap(chunked), this::close);
    }

    private void close(ChannelingSocket socket) {
        socket.close(s -> {
        });
    }

}
