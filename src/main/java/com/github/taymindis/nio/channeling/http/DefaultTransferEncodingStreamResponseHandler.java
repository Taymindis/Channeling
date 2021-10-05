package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.BytesHelper;
import com.github.taymindis.nio.channeling.ChannelingSocket;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DefaultTransferEncodingStreamResponseHandler implements HttpStreamResponseHandler {
    private final  ResponseCallback callback;
    public DefaultTransferEncodingStreamResponseHandler(ResponseCallback callback) {
        this.callback = callback;
    }

    @Override
    public void accept(byte[] chunked, ChannelingSocket socket) throws IOException {
        callback.streamWrite(ByteBuffer.wrap(BytesHelper
                .concat(String.format("%s\r\n", HttpMessageHelper.intToHex(chunked.length)).getBytes(), chunked, "\r\n".getBytes())), clientSocket -> {
        });
    }

    @Override
    public void last(byte[] chunked, ChannelingSocket socket) throws IOException {
        if(chunked.length == 0){
            callback.streamWrite(ByteBuffer.wrap("0\r\n\r\n".getBytes()), this::close);
        }  else {
            if(BytesHelper.equals(chunked, "\r\n0\r\n\r\n".getBytes(),chunked.length - 7 )) {
                callback.streamWrite(ByteBuffer.wrap(BytesHelper
                        .concat(String.format(
                                "%s\r\n", HttpMessageHelper.intToHex(chunked.length - 7)).getBytes(),
                                chunked)), this::close);
            } else {
                callback.streamWrite(ByteBuffer.wrap(BytesHelper
                        .concat(String.format(
                                "%s\r\n", HttpMessageHelper.intToHex(chunked.length)).getBytes(),
                                chunked, "\r\n0\r\n\r\n".getBytes())), this::close);
            }
        }
    }

    private void close(ChannelingSocket socket) {
        socket.close(s -> {
        });
    }

}
