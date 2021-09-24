package com.github.taymindis.nio.channeling;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

class ChannelSSLRunner extends AbstractChannelRunner {

    private final SocketChannel sc;
    private final SSLSocketChannel sslsc;
    private Object context;
    private final Queue<ChannelingSocket> forRunners;
    private Predicate<?> currentPredicate = null;
    private ChannelingTask predicateTask;
    private ChannelingTask ioTask;
    private ByteBuffer readBuffer, currWritingBuffer;
    SocketAddress remoteAddress;
     Then then;
    ErrorCallback errorCallback = null;
    private long actionTime;
    private int lastProcessedBytes;

    static final Then DEFAULT_CALLBACK = sc -> {
        /** Do nothing **/
    };

    static final ErrorCallback DEFAULT_ERRORCALLBACK = (sc, e) -> e.printStackTrace();
    private boolean isEagerRead, removeEagerReadSignal;

    private static ThreadPoolExecutor sslThreadPool = null;

    /**
     * @param sslEngine sslEngine
     * @param context
     * @descr QSocketchannel is designed to for non block and async queue, client take turn to check result
     * if you want to configuring blocking way, AsynchronousSocketChannel is what you are looking for
     */
    ChannelSSLRunner(SSLEngine sslEngine, int sslWorker, Object context, int bufferSize,
                     Queue<ChannelingSocket> channelRunners) throws IOException {
        this(sslEngine, sslWorker, context, bufferSize, channelRunners, SocketChannel.open());
    }

    ChannelSSLRunner(SSLEngine sslEngine, int sslWorker, Object context, int bufferSize,
                     Queue<ChannelingSocket> channelRunners, SocketChannel socketChannel) throws IOException {
        if (sslThreadPool == null) {
            sslThreadPool = new ThreadPoolExecutor(sslWorker, sslWorker, 25, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        this.sslsc = new SSLSocketChannel(socketChannel, sslEngine, sslThreadPool);
        this.sc = sslsc.getWrappedSocketChannel();
        this.context = context;
        this.readBuffer = ByteBuffer.allocate(bufferSize);
        this.forRunners = channelRunners;
        this.isEagerRead = false;
        this.removeEagerReadSignal = false;
        this.lastProcessedBytes = 0;
//        this.actionTime = 0;
        try {
            sc.configureBlocking(false);
            sc.socket().setKeepAlive(false);
            sc.setOption(StandardSocketOptions.SO_KEEPALIVE, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Object getContext() {
        return context;
    }

    @Override
    public void setContext(Object context) {
        this.context = context;
    }

    @Override
    public SocketChannel getSocketChannel() {
        return sslsc;
    }

    @Override
    public long getActionTime() {
        return actionTime;
    }

    @Override
    public int getLastProcessedBytes() {
        return lastProcessedBytes;
    }

    @Override
    public void noEagerRead() {
        this.removeEagerReadSignal = true;
    }

    @Override
    public boolean isEagerRead() {
        return isEagerRead;
    }

    @Override
    public void setLastProcessedBytes(int lastProcessedBytes) {
        this.lastProcessedBytes = lastProcessedBytes;
    }

    @Override
    public void setPredicateTask(Predicate<?> predicateTask) {
        this.currentPredicate = predicateTask;
    }

    @Override
    public void setIoTask(ChannelingTask nextIoTask) {
        this.ioTask = nextIoTask;
    }

    @Override
    public boolean tryRemoveEagerRead() {
        if (removeEagerReadSignal && isEagerRead) {
            isEagerRead = false;
            removeEagerReadSignal = false;
            // Return true to remove successfully
            return true;
        }

        return false;
    }

    @Override
    public boolean isSSL() {
        return true;
    }

    @Override
    public ChannelingSocket withConnect(SocketAddress remote) {
        then = DEFAULT_CALLBACK;
        if (errorCallback == null) {
            errorCallback = DEFAULT_ERRORCALLBACK;
        }
        this.remoteAddress = remote;
        this.setIoTask(ChannelingTask.DO_CONNECT);

        return this;
    }

    @Override
    public ChannelingSocket withConnect(String host, int port) {
        return withConnect(new InetSocketAddress(host, port));
    }

    private ChannelingSocket withRead(boolean eager) {
        return withRead(-1, eager);
    }

    private ChannelingSocket withRead(int length, boolean eager) {
        then = DEFAULT_CALLBACK;
        if (errorCallback == null) {
            errorCallback = DEFAULT_ERRORCALLBACK;
        }
        // set to 0 position to reread everything
        if (length > readBuffer.capacity()) {
            readBuffer = increaseCapacity(readBuffer, length);
        }
        readBuffer.clear();
        if (length >= 0) {
            readBuffer.limit(length);
        }
        this.isEagerRead = eager;
        this.setIoTask(ChannelingTask.DO_READ);
        return this;
    }

    private ChannelingSocket withRead(ByteBuffer readBuffer, boolean eager) {
        then = DEFAULT_CALLBACK;
        if (errorCallback == null) {
            errorCallback = DEFAULT_ERRORCALLBACK;
        }
        this.readBuffer = readBuffer;
        this.isEagerRead = eager;
        this.setIoTask(ChannelingTask.DO_READ);
        return this;
    }

    @Override
    public ChannelingSocket withRead() {
        return withRead(-1, false);
    }

    @Override
    public ChannelingSocket withRead(int length) {
        return withRead(length, false);
    }

    @Override
    public ChannelingSocket withRead(ByteBuffer readBuffer) {
        return withRead(readBuffer, false);
    }

    @Override
    public ChannelingSocket withEagerRead() {
        return withRead(-1, true);
    }

    @Override
    public ChannelingSocket withEagerRead(int length) {
        return withRead(length, true);
    }

    @Override
    public ChannelingSocket withEagerRead(ByteBuffer readBuffer) {
        return withRead(readBuffer, true);
    }


    @Override
    public ChannelingSocket withWrite(ByteBuffer byteBuffer) {
        then = DEFAULT_CALLBACK;
        if (errorCallback == null) {
            errorCallback = DEFAULT_ERRORCALLBACK;
        }
        // Change back to flip mode, later channeling need to READ from buffer and write to server side
        this.currWritingBuffer = byteBuffer;
        this.setIoTask(ChannelingTask.DO_WRITE);
        return this;
    }

    @Override
    public ChannelingSocket withClose() {
        then = DEFAULT_CALLBACK;
        if (errorCallback == null) {
            errorCallback = DEFAULT_ERRORCALLBACK;
        }
        this.setIoTask(ChannelingTask.DO_CLOSE);

        return this;
    }

    @Override
    public void connect(SocketAddress remote, Then then) {
        withConnect(remote).then(then);
    }

    @Override
    public void connect(String host, int port, Then then) {
        withConnect(host, port).then(then);
    }

    @Override
    public void connect(SocketAddress remote, WhenConnectingStatus when, Then then) {
        withConnect(remote).when(when).then(then);
    }

    @Override
    public void connect(String host, int port, WhenConnectingStatus when, Then then) {
        withConnect(host, port).when(when).then(then);
    }

    @Override
    public void read(Then then) {
        withRead().then(then);
    }

    @Override
    public void read(WhenChannelingSocket when, Then then) {
        withRead().when(when).then(then);
    }

    @Override
    public void read(int length, Then then) {
        withRead(length).then(then);
    }

    @Override
    public void read(int length, WhenChannelingSocket when, Then then) {
        withRead(length).when(when).then(then);
    }

    @Override
    public void read(ByteBuffer readBufferHolder, Then then) {
        withRead(readBufferHolder).then(then);
    }

    @Override
    public void read(ByteBuffer readBufferHolder, WhenChannelingSocket when, Then then) {
        withRead(readBufferHolder).when(when).then(then);
    }

    @Override
    public void write(ByteBuffer messageBuffer, Then then) {
        withWrite(messageBuffer).then(then);
    }

    @Override
    public void write(ByteBuffer messageBuffer, WhenChannelingSocket when, Then then) {
        withWrite(messageBuffer).when(when).then(then);
    }

    @Override
    public void close(Then then) {
        withClose().then(then);
    }

    @Override
    public void close(WhenChannelingSocket when, Then then) {
        withClose().when(when).then(then);
    }

    @Override
    public void connect(SocketAddress remote, Then then, ErrorCallback errorCallback) {
        withConnect(remote).then(then, errorCallback);
    }

    @Override
    public void connect(String host, int port, Then then, ErrorCallback errorCallback) {
        withConnect(host, port).then(then, errorCallback);
    }

    @Override
    public void connect(SocketAddress remote, WhenConnectingStatus when, Then then, ErrorCallback errorCallback) {
        withConnect(remote).when(when).then(then, errorCallback);
    }

    @Override
    public void connect(String host, int port, WhenConnectingStatus when, Then then, ErrorCallback errorCallback) {
        withConnect(host, port).when(when).then(then, errorCallback);
    }

    @Override
    public void read(Then then, ErrorCallback errorCallback) {
        withRead().then(then, errorCallback);
    }

    @Override
    public void read(WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {
        withRead().when(when).then(then, errorCallback);
    }

    @Override
    public void read(int length, Then then, ErrorCallback errorCallback) {
        withRead(length).then(then, errorCallback);
    }

    @Override
    public void read(int length, WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {
        withRead(length).when(when).then(then, errorCallback);
    }

    @Override
    public void read(ByteBuffer readBufferHolder, Then then, ErrorCallback errorCallback) {
        withRead(readBufferHolder).then(then, errorCallback);
    }

    @Override
    public void read(ByteBuffer readBufferHolder, WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {
        withRead(readBufferHolder).when(when).then(then, errorCallback);
    }

    @Override
    public void write(ByteBuffer messageBuffer, Then then, ErrorCallback errorCallback) {
        withWrite(messageBuffer).then(then, errorCallback);
    }

    @Override
    public void write(ByteBuffer messageBuffer, WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {
        withWrite(messageBuffer).when(when).then(then, errorCallback);
    }

    @Override
    public void close(Then then, ErrorCallback errorCallback) {
        withClose().then(then, errorCallback);
    }

    @Override
    public void close(WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {
        withClose().when(when).then(then, errorCallback);
    }

    @Override
    public ChannelingSocket when(WhenReadingByteBuffer whenPredicate) {
        predicateTask = ChannelingTask.WHEN_READ_BYTEBUFFER;
        currentPredicate = whenPredicate;
        return this;
    }

    @Override
    public ChannelingSocket when(WhenWritingByteBuffer whenPredicate) {
        predicateTask = ChannelingTask.WHEN_WRITE_BYTEBUFFER;
        currentPredicate = whenPredicate;
        return this;
    }


    @Override
    public ChannelingSocket when(WhenSocketChannel whenPredicate) {
        predicateTask = ChannelingTask.WHEN_SOCKETCHANNEL;
        currentPredicate = whenPredicate;
        return this;
    }


    @Override
    public ChannelingSocket when(WhenChannelingSocket whenPredicate) {
        predicateTask = ChannelingTask.WHEN_CHANNELING_SOCKET;
        currentPredicate = whenPredicate;
        return this;
    }

    @Override
    public ChannelingSocket when(WhenConnectingStatus whenPredicate) {
        predicateTask = ChannelingTask.WHEN_CONNECT_STATUS;
        currentPredicate = whenPredicate;
        return this;
    }

    @Override
    public ChannelingSocket when(WhenReadWriteProcess whenPredicate) {
        predicateTask = ChannelingTask.WHEN_READWRITE_PROCESS;
        currentPredicate = whenPredicate;
        return this;
    }

    @Override
    public ChannelingSocket when(WhenClosingStatus whenPredicate) {
        predicateTask = ChannelingTask.WHEN_CLOSING_PROCESS;
        currentPredicate = whenPredicate;
        return this;
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
    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public ByteBuffer getCurrWritingBuffer() {
        return currWritingBuffer;
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
        return sslsc.getMinApplicationBufferSize();
    }

    protected static ByteBuffer doubleTheBuffer(ByteBuffer buffer) {
        return increaseCapacity(buffer, buffer.capacity() * 2);
    }


    /**
     * ref http://www.java2s.com/example/java/java.nio/increase-bytebuffers-capacity.html
     * Increase ByteBuffer's capacity.
     *
     * @param buffer the ByteBuffer want to increase capacity
     * @param size   increased size
     * @return increased capacity ByteBuffer
     * @throws IllegalArgumentException if size less than 0 or buffer is null
     */
    public static ByteBuffer increaseCapacity(ByteBuffer buffer, int size)
            throws IllegalArgumentException {
        if (buffer == null)
            throw new IllegalArgumentException("buffer is null");
        if (size < 0)
            throw new IllegalArgumentException("size less than 0");

        int capacity = buffer.capacity() + size;
        ByteBuffer result = allocate(capacity, buffer.isDirect());
        buffer.flip();
        result.put(buffer);
        return result;
    }

    /**
     * Allocate ByteBuffer.
     *
     * @param capacity ByteBuffer capacity
     * @param direct   allocate DirectByteBuffer
     * @return allocated ByteBuffer
     * @throws IllegalArgumentException if capacity is negative
     */
    public static ByteBuffer allocate(int capacity, boolean direct)
            throws IllegalArgumentException {
        if (capacity < 0)
            throw new IllegalArgumentException("capacity can't be negative");
        return direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer
                .allocate(capacity);
    }


    private static int checkHowmuchBytesStillCanWriteToBuffer(ByteBuffer byteBuffer) {
        // change to flip(reading mode) to check how much capacity left
        byteBuffer.flip();

        int leftOverSpace = (byteBuffer.capacity() - byteBuffer.limit());

        // Done and change to write mode, continue to write
        byteBuffer.compact();

        return leftOverSpace;
    }

    private static int checkHowmuchBytesStillCanReadFromBuffer(ByteBuffer byteBuffer) {
        // change to flip(reading mode) to check how much can read
        byteBuffer.flip();

        int bytesCanRead = byteBuffer.limit() - byteBuffer.position();

        return bytesCanRead;
    }

    protected static void shutdownSSLService() {
        if(sslThreadPool != null) {
            sslThreadPool.shutdownNow();
        }
    }
}
