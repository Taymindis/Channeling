package com.github.taymindis.nio.channeling;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Channeling {
    private static final int DEFAULT_PEEK_TIME = 1;
    private final Queue<ChannelingSocket>[] channelQueues;
    private final Map<String, Integer> sslEnginesOrigin;
    private final ExecutorService eventRunner;
    final long connectionTimeoutInMs, readWriteTimeOutInMs;
    final private List<ChannelingPlugin> channelingPlugins;
    boolean noEagerSocket = false;

    boolean active = true;
    private final int nWorker;
    private int numOfSSLWoker = -1;

    public static Channeling startNewChanneling() throws IOException {
        return startNewChanneling(1, DEFAULT_PEEK_TIME);
    }

    public static Channeling startNewChanneling(int workers) throws IOException {
        return startNewChanneling(workers, DEFAULT_PEEK_TIME);
    }

    public static Channeling startNewChanneling(int workers, int peekPerMs) throws IOException {
        return startNewChanneling(workers, peekPerMs, 1500, 15000);
    }

    public static Channeling startNewChanneling(int workers, long connectionTimeoutInMs, long readWriteTimeOutInMs) throws IOException {
        return startNewChanneling(workers, DEFAULT_PEEK_TIME, connectionTimeoutInMs, readWriteTimeOutInMs);
    }

    /**
     * @param workers               number of worker should have run in background
     * @param peekPerNano           peekPerms per count per socket, default is 16ms
     * @param connectionTimeoutInMs time in milisecond to get timeoutException
     * @param readWriteTimeOutInMs  time in milisecond to get timeoutException
     * @return Channeling
     * @throws IOException  Throws IO Exception
     */
    public static Channeling startNewChanneling(int workers, int peekPerNano, long connectionTimeoutInMs, long readWriteTimeOutInMs) throws IOException {
        return startNewChanneling(workers, peekPerNano,
                connectionTimeoutInMs, readWriteTimeOutInMs, null);
    }

    public static Channeling startNewChanneling(int workers, int peekPerNano, long connectionTimeoutInMs,
                                                long readWriteTimeOutInMs, List<ChannelingPlugin> channelingPlugins) throws IOException {
        return new Channeling(workers, peekPerNano, connectionTimeoutInMs, readWriteTimeOutInMs, channelingPlugins);
    }

    public void setNoEagerSocket(boolean noEagerSocket) {
        this.noEagerSocket = noEagerSocket;
    }

    /**
     *
     * @param numOfWorker for sslengine delegating task worker
     */
    public void enableSSL(int numOfWorker) {
        this.numOfSSLWoker = numOfWorker;
    }

    private Channeling(int workers, int peekPerNano, long connectionTimeoutInMs, long readWriteTimeOutInMs, List<ChannelingPlugin> channelingPlugins) throws IOException {
        this.connectionTimeoutInMs = connectionTimeoutInMs;
        this.readWriteTimeOutInMs = readWriteTimeOutInMs;
        this.channelQueues = new ConcurrentLinkedQueue[workers];
        this.sslEnginesOrigin = new HashMap<>();

        this.channelingPlugins = new ArrayList<>();
        this.channelingPlugins.add(new ChannelingTimeoutFeature(connectionTimeoutInMs, readWriteTimeOutInMs));

        if (channelingPlugins != null) {
            this.channelingPlugins.addAll(channelingPlugins);
        }
        this.nWorker = workers;
        if (workers == 1) {
            eventRunner = Executors.newSingleThreadExecutor();
        } else {
            eventRunner = Executors.newFixedThreadPool(workers);
        }

        for (int i = 0; i < workers; i++) {
            channelQueues[i] = new ConcurrentLinkedQueue<>();
            eventRunner.execute(new ChannelingProcessor(channelQueues[i], this, peekPerNano));
        }
    }

    public List<ChannelingPlugin> getChannelingPlugins() {
        return channelingPlugins;
    }

    public ChannelingSocket wrap(SocketChannel socketChannel, Object attachment) {
        return wrap(socketChannel, attachment, 1024);
    }

    public ChannelingSocket wrap(SocketChannel socketChannel, Object attachment, int bufferSize) {
        int tix = (int) (Thread.currentThread().getId() % this.nWorker);
        return new ChannelRunner(socketChannel, attachment, bufferSize, channelQueues[tix]);
    }


    public ChannelingSocket wrap(Object attachment) throws IOException {
        return wrap(attachment, 1024);
    }

    public ChannelingSocket wrap(Object attachment, int bufferSize) throws IOException {
        return wrap(SocketChannel.open(), attachment, bufferSize);
    }

    public ChannelingSocket wrapSSL(SSLEngine sslEngine, Object attachment) throws Exception {
        return wrapSSL(sslEngine, attachment, 1024);
    }

    public ChannelingSocket wrapSSL(String protocols,
                                    String remoteHandshakeAddress,
                                    int remoteHandshakePort,
                                    Object attachment) throws Exception {
        SSLContext sslContext = getDefaultSSLContext(protocols); //SSLContext.getInstance("TLSv1.2");
        return wrapSSL(sslContext,
                remoteHandshakeAddress,
                remoteHandshakePort, attachment);
    }

    public ChannelingSocket wrapSSL(SSLContext sslContext,
                                    String remoteHandshakeAddress,
                                    int remoteHandshakePort, Object attachment) throws Exception {

        SSLEngine engine = sslContext.createSSLEngine(remoteHandshakeAddress, remoteHandshakePort);
        engine.setUseClientMode(true);
        return wrapSSL(engine, attachment, /*1024 not in use in ssl, ssl using session buffer*/ 1024);
    }

    public ChannelingSocket wrapSSL(SSLEngine sslEngine, Object attachment, int buffSize) throws Exception {

        if(this.numOfSSLWoker < 0) {
            throw new Exception("enableSSL is required ...");
        }

        int tix = (int) (Thread.currentThread().getId() % this.nWorker);
        // Try resize SSL Engine same tix with the same engine to prevent concurrent issue
        // TODO still apply resize SSL Engine? since it's one for one socket
//        tix = resideSSLEngine(sslEngine, tix);
        return new ChannelSSLRunner(sslEngine, this.numOfSSLWoker, attachment, buffSize, channelQueues[tix]);
    }


    /***
     Proxy Scope
     *
     *
     * @param proxy the ChannelingProxy proxy config
     * @param socketChannel SocketChannel reference
     * @param attachment context attachment
     * @return ChannelingSocket
     */
    public ChannelingSocket wrapProxy(ChannelingProxy proxy, SocketChannel socketChannel, Object attachment) {
        return wrapProxy(proxy, socketChannel, attachment, 1024);
    }

    public ChannelingSocket wrapProxy(ChannelingProxy proxy, SocketChannel socketChannel, Object attachment, int bufferSize) {
        int tix = (int) (Thread.currentThread().getId() % this.nWorker);
        return new ChannelProxyRunner(proxy, socketChannel, attachment, bufferSize, channelQueues[tix]);
    }


    public ChannelingSocket wrapProxy(ChannelingProxy proxy, Object attachment) throws IOException {
        return wrapProxy(proxy, attachment, 1024);
    }

    public ChannelingSocket wrapProxy(ChannelingProxy proxy, Object attachment, int bufferSize) throws IOException {
        return wrapProxy(proxy, SocketChannel.open(), attachment, bufferSize);
    }

    public ChannelingSocket wrapProxySSL(ChannelingProxy proxy, SSLEngine sslEngine, Object attachment) throws Exception {
        return wrapProxySSL(proxy, sslEngine, attachment, sslEngine.getSession().getApplicationBufferSize());
    }

    public ChannelingSocket wrapProxySSL(ChannelingProxy proxy, String protocols,
                                    String remoteHandshakeAddress,
                                    int remoteHandshakePort,
                                    Object attachment) throws Exception {
        SSLContext sslContext = getDefaultSSLContext(protocols); //SSLContext.getInstance("TLSv1.2");
        return wrapProxySSL(proxy, sslContext,
                remoteHandshakeAddress,
                remoteHandshakePort, attachment);
    }

    public ChannelingSocket wrapProxySSL(ChannelingProxy proxy, SSLContext sslContext,
                                    String remoteHandshakeAddress,
                                    int remoteHandshakePort, Object attachment) throws Exception {

        SSLEngine engine = sslContext.createSSLEngine(remoteHandshakeAddress, remoteHandshakePort);
        engine.setUseClientMode(true);
        return wrapProxySSL(proxy, engine, attachment, /*1024 not in use in ssl, ssl using session buffer*/ engine.getSession().getApplicationBufferSize());
    }

    public ChannelingSocket wrapProxySSL(ChannelingProxy proxy, SSLEngine sslEngine, Object attachment, int buffSize) throws Exception {

        if(this.numOfSSLWoker < 0) {
            throw new Exception("enableSSL is required ...");
        }

        int tix = (int) (Thread.currentThread().getId() % this.nWorker);
        // Try resize SSL Engine same tix with the same engine to prevent concurrent issue
        // TODO still apply resize SSL Engine? since it's one for one socket
//        tix = resideSSLEngine(sslEngine, tix);
        return new ChannelProxySSLRunner(proxy, sslEngine, this.numOfSSLWoker, attachment, buffSize, channelQueues[tix]);
    }



















    private synchronized int resideSSLEngine(SSLEngine sslEngine, int tix) {
        String sslEngineKey = getSSLEngineKey(sslEngine);
        if (sslEnginesOrigin.containsKey(sslEngineKey)) {
            return sslEnginesOrigin.get(sslEngineKey);
        }
        sslEnginesOrigin.put(sslEngineKey, tix);
        return tix;
    }

    private String getSSLEngineKey(SSLEngine sslEngine) {
        return String.format("%s_%d", sslEngine.getPeerHost(), sslEngine.getPeerPort());
    }

    /**
     * When shutdown, eventRunner service thread will be shutdown
     */
    public void shutdown() {
        active = false;
        eventRunner.shutdown();
    }


    public static WhenConnectingStatus whenConnected = connectingStatus -> connectingStatus;
    public static WhenClosingStatus whenClosed = closingStatus -> closingStatus;
    public static WhenReadWriteProcess whenByteConsumed = bytesProcessed -> bytesProcessed > 0;
    public static WhenReadWriteProcess whenBytesWritten = bytesProcessed -> bytesProcessed > 0;
    public static WhenWritingByteBuffer whenNoMoreToWrite = byteBuffer -> !byteBuffer.hasRemaining();


    protected static TrustManager[] createTrustManagers(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        InputStream trustStoreIS = new FileInputStream(filepath);
        try {
            trustStore.load(trustStoreIS, keystorePassword.toCharArray());
        } finally {
            if (trustStoreIS != null) {
                trustStoreIS.close();
            }
        }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);
        return trustFactory.getTrustManagers();
    }

    protected static String getDefaultKeyStore() {
        return System.getProperty("java.home") + "/lib/security/cacerts".replace('/', File.separatorChar);
    }

    protected static SSLContext getDefaultSSLContext(String protocol) throws Exception {
        SSLContext context = SSLContext.getInstance(protocol);
        context.init(null,
                createTrustManagers(getDefaultKeyStore(), "changeit"), new SecureRandom());
        return context;
    }
}
