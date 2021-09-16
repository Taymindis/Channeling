package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpRequest;
import com.github.taymindis.nio.channeling.http.HttpRequestMessage;
import com.github.taymindis.nio.channeling.http.HttpResponse;
import com.github.taymindis.nio.channeling.http.RequestListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
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
    private final boolean isSSLServer;
    private final AtomicBoolean waitForAccept = new AtomicBoolean(false);
    private SSLContext sslContext;
    private Object attachment;
    private RequestListener requestListener;


    public ChannelingServer(Channeling channeling, String host, int port) throws Exception {
        this(channeling, host, port, null);
    }

    public ChannelingServer(Channeling channeling, String host, int port, Object context) throws Exception {
        this.channeling = channeling;
        this.isSSLServer = false;
        this.channelServerRunner = channeling.wrapServer(context, host, port);
    }

    public ChannelingServer(Channeling channeling, SSLContext sslContext, String host, int port) throws Exception {
        this(channeling, sslContext, host, port, null);
    }

    public ChannelingServer(Channeling channeling, SSLContext sslContext, String host, int port, Object context) throws Exception {
        this.channeling = channeling;
        this.isSSLServer = sslContext != null;
        this.channelServerRunner = channeling.wrapSSLServer(sslContext, context, host, port);
    }

    public void listen(RequestListener requestListener) {
        isActive = true;
        this.requestListener = requestListener;
        sslContext = ((ChannelServerRunner) channelServerRunner).getSslContext();
        attachment = channelServerRunner.getContext();
//        SSLEngine sslEngine = ((ChannelServerRunner) channelServerRunner).getSslEngine();
//        SSLSession dummySession = sslEngine.getSession();
//        myAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
//        myNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
//        peerAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
//        peerNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
//        dummySession.invalidate();

        if (isSSLServer) {
            while (isActive) {
                if (!waitForAccept.compareAndSet(false, true)) {
                    continue;
                }
                channelServerRunner.withAccept().then(this::sslSocketProcessor, this::closeErrorSocketSilently);
            }
        } else {
            while (isActive) {
                if (!waitForAccept.compareAndSet(false, true)) {
                    continue;
                }
                channelServerRunner.withAccept().then(this::socketProcessor, this::closeErrorSocketSilently);
            }
        }

    }

    private void socketProcessor(ChannelingSocket channelingSocket) {
        SocketChannel socketChannel = null;
        try {
            socketChannel = (channelingSocket.getServerSocketChannel()).accept();
            //noinspection StatementWithEmptyBody
            while (!waitForAccept.compareAndSet(true, false)) ;
            socketChannel.configureBlocking(false);
//
            ChannelingSocket acceptedSock =
                    channeling.wrap(socketChannel, attachment, buffSize);

            acceptedSock.withEagerRead(buffSize).then(this::readAndThen, ChannelingServer.this::closeErrorSocketSilently);

        } catch (Exception e) {
            log.error("Error while trying to accepting socket ... ", e);
            if(socketChannel != null) {
                try {
                    socketChannel.close();
                } catch (IOException ioException) {
                    log.error(ioException.getMessage(), ioException);
                }
            }
        }
    }

    private void sslSocketProcessor(ChannelingSocket channelingSocket) {
        SocketChannel socketChannel = null;

        try {
            socketChannel = (channelingSocket.getServerSocketChannel()).accept();

            //noinspection StatementWithEmptyBody
            while (!waitForAccept.compareAndSet(true, false)) ;

            socketChannel.configureBlocking(false);


            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
//

            ChannelingSocket acceptedSock =
                    channeling.wrapSSL(engine, attachment, buffSize, socketChannel);


            acceptedSock.withEagerRead(acceptedSock.getSSLMinimumInputBufferSize()).then(this::readAndThen, ChannelingServer.this::closeErrorSocketSilently);
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
            if(socketChannel != null) {
                try {
                    socketChannel.close();
                } catch (IOException ioException) {
                    log.error(ioException.getMessage(), ioException);
                }
            }
        }

    }

    int i = 0;

    private void closeErrorSocketSilently(ChannelingSocket channelingSocket, Exception e) {
        if (e != null) {
            e.printStackTrace();
        }
//        if(channelingSocket instanceof ChannelSSLRunner) {
//          SSLSocketChannel sslSocketChannel = (SSLSocketChannel) channelingSocket.getSocketChannel();
//          sslSocketChannel.get
//        }
        channelingSocket.close(s -> {
            if (++i % 100 == 0) {
                System.out.println(i + " released");
            }
        });
    }


    private void eagerRead(ByteBuffer readBuffer, ChannelingSocket channelingSocket) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
    }

    private void readAndThen(ChannelingSocket socketRead) {
        int numRead = socketRead.getLastProcessedBytes();
        ByteBuffer readBuffer = socketRead.getReadBuffer();
        /**
         *
         * This is transfer encoding method
         */
        try {
            if (numRead > 0) {
                readBuffer.flip();
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);

                // TODO Handle Request

                HttpRequestMessage request = massageBytesToHttp(b);
                HttpResponse response = this.requestListener.handleRequest(request);


                ByteBuffer writeBuffer = ByteBuffer.wrap("HTTP/1.1 200 OK\nDate: Mon, 27 Jul 2009 12:28:53 GMT\nServer: Apache/2.2.14 (Win32)\nLast-Modified: Wed, 22 Jul 2009 19:15:56 GMT\nContent-Length: 2\nContent-Type: text/plain\n\nOK".getBytes(StandardCharsets.UTF_8));
                socketRead.write(writeBuffer, this::closeSocketSilently, ChannelingServer.this::closeErrorSocketSilently);
            } else if (numRead == 0) {
                eagerRead(readBuffer, socketRead);
            } else {
                closeSocketSilently(socketRead);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            closeSocketSilently(socketRead);
        }
    }

    private HttpRequestMessage massageBytesToHttp(byte[] b) {
        return new HttpRequestMessage(b);
    }


    private void closeSocketSilently(ChannelingSocket socketResp) {
        this.closeErrorSocketSilently(socketResp, null);
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
