package com.github.taymindis.nio.channeling;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Predicate;

class ChannelServerRunner extends AbstractChannelRunner {

    private final ServerSocketChannel ssc;
    private final SSLContext sslContext;
    private final int sslWorker;
    private final int buffSize;
    private Object context;
    private final Queue<ChannelingSocket> forRunners;
    private ChannelingTask ioTask;
    private long actionTime;
    Then then;
    ErrorCallback errorCallback = null;
    private Predicate<?> currentPredicate = null;
    private ChannelingTask predicateTask;

    static final Then DEFAULT_CALLBACK = sc -> {
        /** Do nothing **/
    };

    static final ErrorCallback DEFAULT_ERRORCALLBACK = (sc, e) -> e.printStackTrace();
    private final boolean isSSL;

    /**
     * @param sslContext     sslContext
     * @param hostAddress    hostAddres
     * @param port           port
     * @param context        context attachment
     * @param channelRunners runner processor
     */
    ChannelServerRunner(SSLContext sslContext, int sslWorker, Object context,
                        int buffSize, String hostAddress, int port,
                        Queue<ChannelingSocket> channelRunners) throws IOException {
        this.ssc = ServerSocketChannel.open();
        this.context = context;
        this.forRunners = channelRunners;
        this.ssc.socket().bind(new InetSocketAddress(hostAddress, port));
        this.ssc.configureBlocking(false);
        this.sslContext=sslContext;
        this.isSSL = sslContext != null;
        this.sslWorker = sslWorker;
        this.buffSize = buffSize;
    }

    // TODO create start interface
    @Override
    public ChannelingSocket withAccept() {
        this.setIoTask(ChannelingTask.DO_ACCEPT);
        return this;
    }


    @Override
    public Object getContext() {
        return this.context;
    }

    @Override
    public void setContext(Object context) {
        this.context = context;
    }

    @Override
    public ServerSocketChannel getServerSocketChannel() {
        return ssc;
    }

    @Override
    public long getActionTime() {
        return actionTime;
    }

    @Override
    public int getLastProcessedBytes() {
        return 0;
    }
    @Override
    public void setLastProcessedBytes(int rt) {
    }


    @Override
    public void then(Then $then, ErrorCallback $errorCallback) {
        then = $then;
        errorCallback = $errorCallback;
        triggerEvent();
    }

    private void triggerEvent() {
        actionTime = new Date().getTime();
        forRunners.offer(this);
    }

    // Inherit super error call back
    @Override
    public void then(Then then$) {
        then(then$, errorCallback != null ? errorCallback : DEFAULT_ERRORCALLBACK);
    }


    @Override
    public Predicate getCurrentPredicate() {
        return currentPredicate;
    }

    @Override
    public ChannelingTask getPredicateTask() {
        return predicateTask;
    }

    @Override
    public ChannelingTask getIoTask() {
        return ioTask;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return null;
    }


    @Override
    public Then getThen() {
        return then;
    }

    @Override
    public ErrorCallback getErrorCallBack() {
        return errorCallback;
    }

    @Override
    public int getSSLMinimumInputBufferSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setIoTask(ChannelingTask channelingTask) {
        this.ioTask = channelingTask;
    }

    @Override
    public boolean isSSL() {
        return isSSL;
    }

    SSLContext getSslContext() {
        return sslContext;
    }



    @Override
    public ChannelingSocket withClose() {
//        then = DEFAULT_CALLBACK;
//        if (errorCallback == null) {
//            errorCallback = DEFAULT_ERRORCALLBACK;
//        }
//        this.setIoTask(ChannelingTask.DO_CLOSE);

        return this;
    }

    @Override
    public void close(Then then) {
        // TODO Shouldn't be here
    }

    @Override
    public void close(WhenChannelingSocket when, Then then) {
        // TODO Shouldn't be here
    }

}
