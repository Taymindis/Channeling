package com.github.taymindis.nio.channeling;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Queue;

public class ChannelProxyRunner extends ChannelRunner {
    private final ChannelingProxyHandler proxyHandler;
    public ChannelProxyRunner(ChannelingProxy proxy, SocketChannel socketChannel, Object attachment, int bufferSize, Queue<ChannelingSocket> channelQueue) {
        super(socketChannel, attachment, bufferSize, channelQueue);
        proxyHandler = new ChannelingProxyHandler(this, proxy);
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
