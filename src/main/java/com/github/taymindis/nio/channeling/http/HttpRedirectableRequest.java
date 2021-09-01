package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

public class HttpRedirectableRequest extends HttpRequest {

    public HttpRedirectableRequest(ChannelingSocket socket,
                                   String host, int port,
                                   String messageToSend,
                                   RedirectionSocket redirectionSocket) {
        this(socket, host, port, messageToSend, 1024, redirectionSocket);
    }

    public HttpRedirectableRequest(ChannelingSocket socket,
                                   String host,
                                   int port,
                                   String messageToSend,
                                   int minInputBufferSize,
                                   RedirectionSocket redirectionSocket) {
        super(socket, host, port, messageToSend, minInputBufferSize, false, redirectionSocket);
    }

}
