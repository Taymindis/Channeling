package com.github.taymindis.nio.channeling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelingServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ChannelingProcessor.class);

    private boolean isActive = false;
    private final ChannelingSocket channelServerRunner;
    private int buffSize = 1024;
    private final Channeling channeling;

    public ChannelingServer(Channeling channeling, String host, int port) throws Exception {
        this(channeling, host, port, null);
    }
    public ChannelingServer(Channeling channeling, String host, int port, Object context) throws Exception {
        this.channeling = channeling;
        this.channelServerRunner = channeling.wrapServer(context, host, port);
    }
    public ChannelingServer(Channeling channeling, SSLContext sslContext, String host, int port) throws Exception {
        this(channeling, sslContext, host, port, null);
    }
    public ChannelingServer(Channeling channeling, SSLContext sslContext, String host, int port, Object context) throws Exception {
        this.channeling = channeling;
        this.channelServerRunner = channeling.wrapSSLServer(sslContext, context, host, port);
    }

    public void start() {
        isActive = true;
        AtomicBoolean waitForAccept = new AtomicBoolean(false);
        SSLContext sslContext = ((ChannelServerRunner) channelServerRunner).getSslContext();
        Object attachment = channelServerRunner.getContext();
//        SSLEngine sslEngine = ((ChannelServerRunner) channelServerRunner).getSslEngine();
//        SSLSession dummySession = sslEngine.getSession();
//        myAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
//        myNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
//        peerAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
//        peerNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
//        dummySession.invalidate();

        while (isActive) {

            if(!waitForAccept.compareAndSet(false, true)) {

//                try {
//                    Thread.sleep(16);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                continue;
            }


            channelServerRunner.withAccept().then(channelingSocket -> {
                SocketChannel socketChannel;

                try {
                    socketChannel = (channelingSocket.getServerSocketChannel()).accept();

                    while(!waitForAccept.compareAndSet(true, false)){

                    }

                    socketChannel.configureBlocking(false);


//                SSLEngine engine = sslContext.createSSLEngine();
//                engine.setUseClientMode(false);
//

                ChannelingSocket acceptedSock =
                        channeling.wrap(socketChannel, attachment, buffSize);



                    acceptedSock.withRead(buffSize).then(socketResp -> {

                        int numRead = socketResp.getLastProcessedBytes();
                        ByteBuffer readBuffer = socketResp.getReadBuffer();
                        /**
                         *
                         * This is transfer encoding method
                         */
                        try {
                            if (numRead > 0) {
                                readBuffer.flip();
                                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                                readBuffer.get(b);

//                                System.out.println(new String(b, StandardCharsets.UTF_8));
                                ByteBuffer writeBuffer = ByteBuffer.wrap("HTTP/1.1 200 OK\nDate: Mon, 27 Jul 2009 12:28:53 GMT\nServer: Apache/2.2.14 (Win32)\nLast-Modified: Wed, 22 Jul 2009 19:15:56 GMT\nContent-Length: 2\nContent-Type: text/plain\n\nOK".getBytes(StandardCharsets.UTF_8));
                                socketResp.write(writeBuffer, this::closeSocketSilently, ChannelingServer.this::closeErrorSocketSilently);
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            closeSocketSilently(socketResp);
                        }
                    },ChannelingServer.this::closeErrorSocketSilently);





//                engine.beginHandshake();
//
//                if (doHandshake(socketChannel, engine)) {
//                    socketChannel.register(selector, SelectionKey.OP_READ, engine);
//                } else {
//                    socketChannel.close();
//                    log.debug("Connection closed due to handshake failure.");
//                }
                } catch (Exception e) {
                    log.error("Error while trying to accepting socket ... ", e);
                }
            }, (s, e) -> {
                if(++i % 100 == 0) {
                    System.out.println(i + " released");
                }
            });

        }

    }
    
    int i = 0;

    private void closeErrorSocketSilently(ChannelingSocket channelingSocket, Exception e) {
        channelingSocket.close(s -> {
            if(++i % 100 == 0) {
                System.out.println(i + " released");
            }
        });
    }

    private void closeSocketSilently(ChannelingSocket socketResp) {
        socketResp.close(s -> {
            if(++i % 100 == 0) {
                System.out.println(i + " released");
            }
        });
    }

    public int getBuffSize() {
        return buffSize;
    }

    public void setBuffSize(int buffSize) {
        this.buffSize = buffSize;
    }

    public void stop() {
        this.isActive = false;
    }

    @Override
    public void close() throws Exception {
        this.stop();
    }
}
