package com.github.taymindis.nio.channeling;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;

public class ChannelProxySSLRunner extends ChannelSSLRunner  {
    private final ChannelingProxySSLHandler proxyHandler;

    public ChannelProxySSLRunner(ChannelingProxy proxy, SSLEngine sslEngine, int numOfSSLWoker, Object attachment, int buffSize, Queue<ChannelingSocket> channelQueue) throws IOException {
        super(sslEngine, numOfSSLWoker, attachment, buffSize, channelQueue);
        proxyHandler = new ChannelingProxySSLHandler(this, proxy);
    }

    public void proxyConnectRead(ByteBuffer messageBuffer, Then then) {
        super.withRead(messageBuffer);

        super.setIoTask(ChannelingTask.DO_PROXY_SSL_CONNECT_READ);

        super.then(then);
    }
    public void proxyConnectWrite(ByteBuffer messageBuffer, Then then) {
        super.withWrite(messageBuffer);

        super.setIoTask(ChannelingTask.DO_PROXY_SSL_CONNECT_WRITE);

        super.then(then);
    }

    @Override
    public ChannelingSocket withConnect(SocketAddress remote) {
        this.then = DEFAULT_CALLBACK;
        if (getErrorCallBack() == null) {
            errorCallback = DEFAULT_ERRORCALLBACK;
        }
        this.remoteAddress = new InetSocketAddress(proxyHandler.getHost(), proxyHandler.getPort());
        this.setIoTask(ChannelingTask.DO_PROXY_CONNECT);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) remote;
        proxyHandler.setExternalHost(inetSocketAddress.getHostName());
        proxyHandler.setExternalPort(inetSocketAddress.getPort());
        return this;
    }

    @Override
    public void then(Then $then) {
        this.then($then, getErrorCallBack());
    }


    @Override
    public void then(Then $then, ErrorCallback $errorCallback) {
        if(getIoTask() == ChannelingTask.DO_PROXY_CONNECT) {
            // Established proxy socket
            proxyHandler.setSuccess($then::callback);
            proxyHandler.setError(e -> {
                $errorCallback.error(this, e);
            });
            this.setIoTask(ChannelingTask.DO_CONNECT);
            super.then(proxyHandler::connectThen);
        } else {
            super.then($then, $errorCallback);
        }
    }
}
