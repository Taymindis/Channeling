package com.github.taymindis.nio.channeling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeoutException;

class ChannelingProcessor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ChannelingProcessor.class);
    private final Queue<ChannelingSocket> queue;
    private final Channeling channeling;
    private final Selector nioSelector;
    private final int peekPerNano;

    ChannelingProcessor(Queue<ChannelingSocket> queue, Channeling channeling, int peekPerNano) throws IOException {
        this.queue = queue;
        this.channeling = channeling;
        this.peekPerNano = peekPerNano;
        this.nioSelector = initSelector();
    }

    private Selector initSelector() throws IOException {
        return Selector.open();// SelectorProvider.provider().openSelector();
    }

    @Override
    public void run() {
        ChannelingSocket socket;
//        long connectingWaitCount = channeling.connectingWaitCount;
//        long readwriteWaitCount = channeling.readWriteWaitCount;
        while (channeling.active) {
            try {
                while (queue.peek() != null) {
                    socket = queue.poll();
                    try {
                        if (socket instanceof ChannelServerRunner) {
                            registerServerIOTask(socket);
                        } else {
                            registerIOTask(socket);
                        }
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                        socket.close(channelingSocket -> {
                        });
                        socket.getErrorCallBack().error(socket, e);
                    }
                }
                runIOTask();

//                Thread.sleep(0, peekPerNano);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            Set<SelectionKey> allKeys = nioSelector.keys();
            for (SelectionKey key : allKeys) {
                key.cancel();
            }
        } finally {
            try {
                nioSelector.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

        }
    }

    private void runIOTask() throws IOException, TimeoutException {
        nioSelector.selectNow(selectionKey -> {
            if (selectionKey.isValid()) {
                ChannelingSocket channelingSocket = (ChannelingSocket) selectionKey.attachment();

                if (channelingSocket == null) {
                    log.error("Unknown selection key invoke ...");
                    return;
                }

                try {
                    if (channelingSocket instanceof ChannelServerRunner) {
                        if (doServerIO(channelingSocket, selectionKey)) {
                        } else {
                            log.debug("pending channel");
                        }
                    } else {
                        if (doIO(channelingSocket, selectionKey)) {
//                    iter.remove();
                        } else {
                            log.debug("pending channel");
                        }
                    }
                } catch (Exception e) {
                    channelingSocket.close(csClosed -> {
                    });
                    channelingSocket.getErrorCallBack().error(channelingSocket, e);
                }
            }
        });


        Set<SelectionKey> allKeys = nioSelector.keys();


        for (ChannelingPlugin plugin : channeling.getChannelingPlugins()) {
            plugin.checkKeys(allKeys);
        }


/**
 *   Force Call back feature
 */
//        log.info("Size = " + allKeys.size());
//        for (SelectionKey key : allKeys) {
//            ChannelingSocket channelingSocket = (ChannelingSocket) key.attachment();
//            if (channelingSocket != null) {
//
//                if (channelingSocket.isForceCallback()) {
//                    // If it is force call back, set last process bytes to 0
//                    channelingSocket.setLastProcessedBytes(0);
//                    channelingSocket.getThen().callback(channelingSocket);
//                }
//            }
//        }
/**
 *   Force Call back feature End
 */


/**
 *   Time our feature
 */
//        if (bufferingCount++ > DEFAULT_BUFFER_TURNOVER) {
//            long currTime = new Date().getTime();
//            bufferingCount = 0;
//            for (SelectionKey key : allKeys) {
//                ChannelingSocket channelingSocket = (ChannelingSocket) key.attachment();
//                if (channelingSocket != null) {
//                    long actionTime = channelingSocket.getActionTime();
//                    long elapsedTimeInMs = currTime - actionTime;
//
//                    if (channelingSocket.getIoTask() == ChannelingTask.DO_CONNECT && elapsedTimeInMs >= channeling.connectionTimeoutInMs) {
//                        key.cancel();
//                        channelingSocket.close();
//                        TimeoutException timeout =
//                                new TimeoutException("Connecting timeout, IO has spent around" + channeling.connectionTimeoutInMs + "++ms");
//                        channelingSocket.getErrorCallBack().error(channelingSocket, timeout);
//                        throw timeout;
//                    }
//
//                    if (channelingSocket.getIoTask() != ChannelingTask.DO_CONNECT && elapsedTimeInMs >= channeling.readWriteTimeOutInMs) {
//                        key.cancel();
//                        channelingSocket.close();
//                        TimeoutException timeout =
//                                new TimeoutException("Read / Writing timeout, IO has spent around " + channeling.readWriteTimeOutInMs + "++ms");
//                        channelingSocket.getErrorCallBack().error(channelingSocket, timeout);
//                        throw timeout;
//                    }
//                }
//            }
//        }

/**
 *   Time out feature End
 */

    }

    private void idleTask(ChannelingSocket channelingSocket) {
        channelingSocket.setIoTask(ChannelingTask.DO_IDLE);
    }

    /**
     * @param socket ChannelingSocket
     * @return if IO done with call back
     * @throws IOException
     * @throws TimeoutException
     */
    private void registerIOTask(ChannelingSocket socket) throws IOException {
        SocketChannel $sc;
        if (socket.isSSL()) {
            $sc = ((SSLSocketChannel) socket.getSocketChannel()).getWrappedSocketChannel();
        } else {
            $sc = socket.getSocketChannel();
        }
        switch (socket.getIoTask()) {
            case DO_CONNECT:
                $sc.connect(socket.getRemoteAddress());
                doRegister(SelectionKey.OP_CONNECT, socket, $sc);
                break;
            case DO_WRITE:
            case DO_PROXY_SSL_CONNECT_WRITE:
                doRegister(SelectionKey.OP_WRITE, socket, $sc);
                break;
            case DO_CLOSE:
//                doRegister(SelectionKey.OP_WRITE, socket, $sc);
                if ($sc.isOpen()) {
                    if (socket.isSSL()) {
                        SSLSocketChannel sslSocketChannel = ((SSLSocketChannel) socket.getSocketChannel());
                        sslSocketChannel.getWrappedSocketChannel().keyFor(nioSelector).cancel();
                        sslSocketChannel.implCloseSelectableChannel();
                    } else {
                        SelectionKey targetKey = $sc.keyFor(nioSelector);
                        targetKey.cancel();
//                        targetKey.channel().close();
                        $sc.close();
                    }
                }
                idleTask(socket);
                socket.getThen().callback(socket);

                break;
            case DO_READ:
            case DO_PROXY_SSL_CONNECT_READ:
                if (socket.isEagerRead()) {
                    doRegister(SelectionKey.OP_READ | SelectionKey.OP_WRITE, socket, $sc);
                } else {
                    doRegister(SelectionKey.OP_READ, socket, $sc);
                }
                break;
//            case DO_REMOVE_EAGER_READ:
//                doRegister($sc.keyFor(nioSelector).interestOps() & ~SelectionKey.OP_WRITE, socket, $sc);
//                break;
            case DO_IDLE:
                break;
            default:
                throw new IOException("Ambiguous channeling action! ");
        }
    }

    private void registerServerIOTask(ChannelingSocket socket) throws IOException {
        ServerSocketChannel $ssc;
//        if (socket.isSSL()) {
            // TODO
//            $sc = ((ServerSocketChannel) socket.getServerSocketChannel()).getWrappedSocketChannel();
//        } else {
            $ssc = socket.getServerSocketChannel();
//        }
        switch (socket.getIoTask()) {
            case DO_ACCEPT:
                doRegister(SelectionKey.OP_ACCEPT, socket, $ssc);
                //                $sc.keyFor(nioSelector).cancel();
                break;
            case DO_IDLE:
                break;
            default:
                throw new IOException("Ambiguous channeling action! " + socket.getIoTask());
        }
    }

    private void doRegister(int interested, ChannelingSocket socket, SocketChannel $sc) throws ClosedChannelException {
        SelectionKey key = $sc.keyFor(this.nioSelector);
        if (key != null && key.isValid()) {
            key.interestOps(interested);
        } else {
            $sc.register(
                    nioSelector, interested, socket);
        }

    }

    private void doRegister(int interested, ChannelingSocket socket, ServerSocketChannel $sc) throws ClosedChannelException {
        SelectionKey key = $sc.keyFor(this.nioSelector);
        if (key != null && key.isValid()) {
            key.interestOps(interested);
        } else {
            $sc.register(
                    nioSelector, interested, socket);
        }

    }

    /**
     * @param socket ChannelingSocket
     * @param key
     * @return if IO done with call back
     * @throws IOException
     * @throws TimeoutException
     */
    private boolean doIO(ChannelingSocket socket, SelectionKey key) throws IOException, TimeoutException {
        SocketChannel $sc = socket.getSocketChannel();

        ChannelingTask ioTask = socket.getIoTask();


        if (socket.tryRemoveEagerRead()) {
            // If removed eager read but current IO is do write, then don't remove do write event
            if (ioTask != ChannelingTask.DO_WRITE) {
                if (socket.isSSL()) {
                    SocketChannel nativeSocketChannel = ((SSLSocketChannel) $sc).getWrappedSocketChannel();
                    doRegister(nativeSocketChannel.keyFor(nioSelector).interestOps() & ~SelectionKey.OP_WRITE, socket, nativeSocketChannel);
                } else {
                    doRegister($sc.keyFor(nioSelector).interestOps() & ~SelectionKey.OP_WRITE, socket, $sc);
                }
            }
        }

        switch (ioTask) {
            case DO_ACCEPT:
                if (key.isValid() && key.isAcceptable()) {
                    return doPredicateThenCallback(socket, 0, $sc, key);
                }
                return false;
            case DO_READ:
                if (key.isValid() && key.isReadable()) {
                    return doRead(socket, $sc, key);
                } else if (socket.isEagerRead()) {
                    if (socket.isSSL()) {
                        return doRead(socket, $sc, key);
                    }
                    return doPredicateThenCallback(socket, 0, $sc, key);
                }
                return false;
            case DO_WRITE:
                if (key.isValid() && key.isWritable()) {
                    return doWrite(socket, $sc, key);
                }
                return false;
//                else {
//                    throw new IOException("Socket been signaled to write but it is not ready for write");
//                }
            case DO_PROXY_SSL_CONNECT_READ:
                if (key.isValid() && key.isReadable()) {
                    return doSSLProxyConnectRead(socket, (SSLSocketChannel) $sc, key);
                }
                return false;
            case DO_PROXY_SSL_CONNECT_WRITE:
                if (key.isValid() && key.isWritable()) {
                    return doSSLProxyConnectWrite(socket, (SSLSocketChannel) $sc, key);
                }
                return false;
            case DO_CLOSE:
                if ($sc.isOpen()) {
                    key.cancel();
//                    key.channel().close();
                    $sc.close();
                }
                idleTask(socket);
//                socket.getThen().callback(socket);
                return doPredicateThenCallback(socket, 0, $sc, key);
            case DO_CONNECT:
                return doPredicateThenCallback(socket, 0, $sc, key);
            case DO_IDLE:
                return true;
            default:
                throw new IOException("Ambiguous channeling action! ");
        }
    }

    private boolean doServerIO(ChannelingSocket socket, SelectionKey key) throws IOException, TimeoutException {
        ServerSocketChannel $ssc = socket.getServerSocketChannel();

        ChannelingTask ioTask = socket.getIoTask();

        switch (ioTask) {
            case DO_ACCEPT:
                if (key.isValid() && key.isAcceptable()) {
                    return doPredicateThenCallback(socket, 0, null, key);
                }
                return false;
            case DO_IDLE:
                return true;
            default:
                throw new IOException("Ambiguous channeling action! " + ioTask);
        }
    }

    private boolean doWrite(ChannelingSocket socket, SocketChannel $sc, SelectionKey key) throws IOException, TimeoutException {
        ByteBuffer writeBuff = socket.getCurrWritingBuffer();
        if (writeBuff == null) {
            throw new NullPointerException("Buffer for writing is null ...");
        }
        return doPredicateThenCallback(socket, $sc.write(writeBuff), $sc, key);
    }

    private boolean doSSLProxyConnectWrite(ChannelingSocket socket, SSLSocketChannel $sc, SelectionKey key) throws IOException, TimeoutException {
        ByteBuffer writeBuff = socket.getCurrWritingBuffer();
        if (writeBuff == null) {
            throw new NullPointerException("Buffer for writing is null ...");
        }
        return doPredicateThenCallback(socket, $sc.getWrappedSocketChannel().write(writeBuff), $sc, key);
    }

    private boolean doRead(ChannelingSocket socket, SocketChannel $sc, SelectionKey key) throws IOException, TimeoutException {
        ByteBuffer readBuff = socket.getReadBuffer();
//        log.debug("is Eager Read ? {}", socket.isEagerRead() ? "YES" : "NO");
        int numRead = $sc.read(readBuff);
        if (numRead == -1) {
            log.debug("Socket closed by remote peer");
            socket.setLastProcessedBytes(numRead);
            if ($sc.isOpen()) {
                key.cancel();
                $sc.close();
            }
            idleTask(socket);
            socket.getThen().callback(socket);
            return true;
        } else {
            return doPredicateThenCallback(socket, numRead, $sc, key);
        }
    }

    private boolean doSSLProxyConnectRead(ChannelingSocket socket, SSLSocketChannel $sc, SelectionKey key) throws IOException, TimeoutException {
        ByteBuffer readBuff = socket.getReadBuffer();
//        log.debug("is Eager Read ? {}", socket.isEagerRead() ? "YES" : "NO");
        int numRead = $sc.getWrappedSocketChannel().read(readBuff);
        if (numRead == -1) {
            log.debug("Socket closed by remote peer");
            socket.setLastProcessedBytes(numRead);
            if ($sc.isOpen()) {
                key.cancel();
                $sc.close();
            }
            idleTask(socket);
            socket.getThen().callback(socket);
            return true;
        } else {
            return doPredicateThenCallback(socket, numRead, $sc, key);
        }
    }

    /**
     * @param socket ChannelingSocket
     * @param ret    result returned
     * @param $sc    nio SocketChannel
     * @param key    nio SelectionKey
     * @return If Predicate done
     * @throws IOException
     * @throws TimeoutException
     */
    private boolean doPredicateThenCallback(ChannelingSocket socket, int ret,
                                            SocketChannel $sc, SelectionKey key) throws IOException, TimeoutException {

        socket.setLastProcessedBytes(ret);
        if (socket.getCurrentPredicate() == null) {
            idleTask(socket);
            socket.getThen().callback(socket);
            return true;
        }


        switch (socket.getPredicateTask()) {
            case WHEN_READ_BYTEBUFFER:
                if (socket.getCurrentPredicate().test(socket.getReadBuffer())) {
                    socket.setPredicateTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }
                break;
            case WHEN_WRITE_BYTEBUFFER:
                if (socket.getCurrentPredicate().test(socket.getCurrWritingBuffer())) {
                    socket.setPredicateTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }
                break;
            case WHEN_SOCKETCHANNEL:
                if (socket.getCurrentPredicate().test($sc)) {
                    socket.setPredicateTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }
                break;
            case WHEN_CHANNELING_SOCKET:
                if (socket.getCurrentPredicate().test(socket)) {
                    socket.setPredicateTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }
                break;
            case WHEN_CONNECT_STATUS:
                if (socket.getCurrentPredicate().test(key.isConnectable() && $sc.finishConnect())) {
                    socket.setPredicateTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }
//                if (socket.retry() >= channeling.connectingWaitCount) {
//                    throw new TimeoutException("Connecting timeout");
//                }
                break;
            case WHEN_READWRITE_PROCESS:
                if (socket.getCurrentPredicate().test(ret)) {
                    socket.setPredicateTask(null);
                    socket.setIoTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }

//                if (socket.retry() >= channeling.readWriteWaitCount) {
//                    throw new TimeoutException("Read/Write timeout");
//                }
                break;
            case WHEN_CLOSING_PROCESS:
                if (socket.getCurrentPredicate().test(!$sc.isConnected())) {
                    socket.setPredicateTask(null);
                    idleTask(socket);
                    socket.getThen().callback(socket);
                    return true;
                }
//                if (socket.retry() >= channeling.connectingWaitCount) {
//                    throw new TimeoutException("Closing timeout");
//                }
                break;
            default:
                throw new IOException("Idle/unknown Channeling socket on pool");
        }

        /** put in retry list **/
//        if (!isRetry) {
//            retrySockets.add(socket);
//        }
        return false;
    }


}
