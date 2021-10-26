package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.taymindis.nio.channeling.http.HttpMessageHelper.*;

public class ChannelingServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ChannelingProcessor.class);
    private static final String DEFAULT_VHOST_NAME = "_";

    private boolean isActive = false;
    private final ChannelingSocket channelServerRunner;
    private int buffSize = 1024;
    private final Channeling channeling;
    private final boolean isSSLServer;
    private final AtomicBoolean waitForAccept = new AtomicBoolean(false);
    private SSLContext sslContext;
    private Object attachment;
    private Map<String, RequestListener> vHostRequestListener;
    private RequestListener defaultRequestListener;
    private boolean readBody = true, keepAlive = false;
    private ErrorCallback onReadError, onWriteError, onAcceptError;
    private static final ErrorCallback ON_READ_ERROR = (sc, e) -> {
        e.printStackTrace();
        sc.close(s->{});
    };
    private ErrorCallback ON_WRITE_ERROR = (sc, e) -> {
        e.printStackTrace();
        sc.close(s->{});
    };
    private ErrorCallback ON_ACCEPT_ERROR = (sc, e) -> {
        e.printStackTrace();
        sc.close(s->{});
    };
    private int waitPerNano = -1;


    public ChannelingServer(Channeling channeling, String host, int port) throws Exception {
        this(channeling, host, port, null);
    }

    public ChannelingServer(Channeling channeling, String host, int port, Object context) throws Exception {
        this.channeling = channeling;
        this.isSSLServer = false;
        this.channelServerRunner = channeling.wrapServer(context, host, port);
        this.onAcceptError = ON_ACCEPT_ERROR;
        this.onReadError = ON_READ_ERROR;
        this.onWriteError = ON_WRITE_ERROR;
    }

    public ChannelingServer(Channeling channeling, SSLContext sslContext, String host, int port) throws Exception {
        this(channeling, sslContext, host, port, null);
    }

    public ChannelingServer(Channeling channeling, SSLContext sslContext, String host, int port, Object context) throws Exception {
        this.channeling = channeling;
        this.isSSLServer = sslContext != null;
        this.channelServerRunner = channeling.wrapSSLServer(sslContext, context, host, port);
        this.onAcceptError = ON_ACCEPT_ERROR;
        this.onReadError = ON_READ_ERROR;
        this.onWriteError = ON_WRITE_ERROR;
    }

    public void listen(RequestListener requestListener) {
        defaultRequestListener = requestListener;
        listen(Map.of(DEFAULT_VHOST_NAME, requestListener));
    }

    public void listen(Map<String, RequestListener> vHostRequestListener) {
        if (isActive) {
            throw new IllegalStateException("Service has already running ... ");
        }
        isActive = true;

        // Retrieve first as Default Listener
        for (Map.Entry<String, RequestListener> entry : vHostRequestListener.entrySet()) {
            this.defaultRequestListener = entry.getValue();
            break;
        }

        this.vHostRequestListener = vHostRequestListener;
        sslContext = ((ChannelServerRunner) channelServerRunner).getSslContext();
        attachment = channelServerRunner.getContext();
//        SSLEngine sslEngine = ((ChannelServerRunner) channelServerRunner).getSslEngine();
//        SSLSession dummySession = sslEngine.getSession();
//        myAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
//        myNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
//        peerAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
//        peerNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
//        dummySession.invalidate();

        try {
            if (isSSLServer) {
                while (isActive) {
                    if (!waitForAccept.compareAndSet(false, true)) {
                        if (waitPerNano > 0) {
                            Thread.sleep(0, waitPerNano);
                        }
                        continue;
                    }
                    channelServerRunner.withAccept().then(this::sslSocketProcessor, onAcceptError);
                }
            } else {
                while (isActive) {
                    if (!waitForAccept.compareAndSet(false, true)) {
                        if (waitPerNano > 0) {
                            Thread.sleep(0, waitPerNano);
                        }
                        continue;
                    }
                    channelServerRunner.withAccept().then(this::socketProcessor, onAcceptError);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void socketProcessor(ChannelingSocket serverSocket) {
        SocketChannel socketChannel = null;
        try {
            socketChannel = (serverSocket.getServerSocketChannel()).accept();
            //noinspection StatementWithEmptyBody
            while (!waitForAccept.compareAndSet(true, false)) ;
            socketChannel.configureBlocking(false);
//
            ChannelingSocket acceptedSock =
                    channeling.wrap(socketChannel, attachment, buffSize);

            Channeling.KeepAlive(acceptedSock, isKeepAlive());

            acceptedSock.withEagerRead(buffSize)
                    .then(this::readAndThen, this.onReadError);

        } catch (Exception e) {
            log.error("Error while trying to accepting socket ... ", e);
            if (socketChannel != null) {
                try {
                    socketChannel.close();
                } catch (IOException ioException) {
                    log.error(ioException.getMessage(), ioException);
                }
            }
        }
    }

    private void sslSocketProcessor(ChannelingSocket serverSocket) {
        SocketChannel socketChannel = null;

        try {
            socketChannel = (serverSocket.getServerSocketChannel()).accept();

            //noinspection StatementWithEmptyBody
            while (!waitForAccept.compareAndSet(true, false)) ;

            socketChannel.configureBlocking(false);


            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
//

            ChannelingSocket acceptedSock =
                    channeling.wrapSSL(engine, attachment, buffSize, socketChannel);

            Channeling.KeepAlive(acceptedSock, isKeepAlive());

            acceptedSock.withEagerRead(acceptedSock.getSSLMinimumInputBufferSize())
                    .then(this::readAndThen, onReadError);
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
            if (socketChannel != null) {
                try {
                    socketChannel.close();
                } catch (IOException ioException) {
                    log.error(ioException.getMessage(), ioException);
                }
            }
        }

    }
    
    private void eagerRead(ByteBuffer readBuffer, ChannelingSocket channelingSocket) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
    }

    /**
     Request BODY RULE
     * 1. If it is a GET or HEAD request, you only need to read the HTTP headers,
     *    request body is normally ignored if it exists, so when you encounter \r\n\r\n,
     *    you reach the end of the request(actually the request headers).
     * 2. If it is a POST method, read the Content-Length in the header and read up to
     *    Content-Length bytes.
     * 3. If it is a POST method and the Content-Length header is absent,
     *    which is most likely to happen, read until -1 is returned, which is the signal of EOF.
     * 4. If it is a POST method and the Content-Length header is absent but keep alived, throw illegal state exception,
     *    keep alive must have content length header.
     *
     * @param socketRead
     */

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
//                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
//                readBuffer.get(b);
                HttpRequestParser messageParser = parsingMessage(socketRead, readBuffer);

                if (!messageParser.isDoneParsed()) {
                    socketRead.setContext(messageParser);
                    eagerRead(readBuffer, socketRead);
                } else {
//                    socketRead.noEagerRead();
                    HttpRequestMessage request = convertMessageToHttpRequestMessage(socketRead, messageParser);
                    String vHost = request.getHeaderMap().get("Host");
                    if (vHost == null) {
                        vHost = DEFAULT_VHOST_NAME;
                    }
                    this.vHostRequestListener
                            .getOrDefault(vHost, defaultRequestListener)
//                    defaultRequestListener
                            .handleRequest(request, new ResponseCallback() {
                                private Deque<QueueWriteBuffer> buffQueue = new ArrayDeque<>();

                                @Override
                                public void write(HttpResponseMessage responseMessage, Charset charset, Then $then) {
                                    if(charset == null) {
                                        charset = StandardCharsets.UTF_8;
                                    }
                                    try {
                                        String responseMsg = massageResponseToString(responseMessage);
                                        ByteBuffer writeBuffer = ByteBuffer.wrap(responseMsg.getBytes(charset));
                                        socketRead.write(writeBuffer, socket-> {
                                                    this.flush(socket, $then);
                                                },
                                                ChannelingServer.this.onWriteError);
                                    } catch (Exception e) {
                                        ChannelingServer.this.onWriteError.error(socketRead, e);
                                    }
                                }

                                @Override
                                public void streamWrite(ByteBuffer b, Then $then) {
                                    if ( queueForWrite(b, $then) != null) {
                                        socketRead.write(b, socket -> {
                                                    this.flush(socket, $then);
                                                },
                                                ChannelingServer.this.onWriteError);
                                    }
                                }

                                private void flush(ChannelingSocket channelingSocket, Then callback) {
                                     ByteBuffer currWriteBuff = channelingSocket.getCurrWritingBuffer();
                                    if (currWriteBuff.hasRemaining()) {
                                        socketRead.write(currWriteBuff, s -> this.flush(s, callback));
                                    } else {
                                        callback.callback(socketRead);
                                        QueueWriteBuffer qwb;
                                        if((qwb = queueForWrite(null, null)) != null) {
                                            socketRead.write(qwb.getNb(), socket -> {
                                                        this.flush(socket, qwb.get$then());
                                                    },
                                                    ChannelingServer.this.onWriteError);
                                        }
                                    }
                                }
                                private synchronized QueueWriteBuffer queueForWrite(ByteBuffer nb, Then $then) {
                                    QueueWriteBuffer qwb;
                                    if(nb == null) { // Means remove first one and take second one
                                        buffQueue.poll();
                                        if(buffQueue.isEmpty())
                                            return null;
                                        return buffQueue.peek();
                                    } else {
                                        qwb = new QueueWriteBuffer(nb, $then);
                                        if(buffQueue.isEmpty()) {
                                            buffQueue.offer(qwb);
                                            return qwb;
                                        } else {
                                            buffQueue.offer(qwb);
                                            return null;
                                        }
                                    }
                                }
                            });
                }

            } else if (numRead == 0) {
                eagerRead(readBuffer, socketRead);
            } else {
                closeSocketSilently(socketRead);
            }
        } catch (Exception e) {
            this.onReadError.error(socketRead, e);
        }
    }

    private HttpRequestMessage convertMessageToHttpRequestMessage(ChannelingSocket socketRead, HttpRequestParser messageParser) throws Exception {
        HttpRequestMessage request = new HttpRequestMessage(socketRead);
        request.setRemoteAddress(socketRead.getRemoteAddress());

        massageRequestHeader(request, messageParser.getHeaderContent());

        request.setBodyOffset(messageParser.getBodyOffset());
        request.setClientReadWriter(messageParser.getByteWriter());
        request.setExpectedLen(messageParser.getExpectedLen());

        return request;
    }

    private HttpRequestParser parsingMessage(ChannelingSocket socketRead, ByteBuffer bb) throws IOException {
        HttpRequestParser message = (HttpRequestParser) socketRead.getContext();
        if (message == null) {
            message = new HttpRequestParser();
//            message.setRemoteAddress(socketRead.getSocketChannel().getRemoteAddress());
        }
        int requiredLength;
        int bodyOffset = message.getBodyOffset();

        if (isReadBody()) {
            message.writeBytes(bb);
        }

        String consumeMessage = parseToString(message.getRawBytes());

        message.fillCurrLen(consumeMessage.length());

        if (bodyOffset == -1) {
            if ((bodyOffset = consumeMessage.indexOf("\r\n\r\n")) > 0) {
                bodyOffset += 4;
            } else if ((bodyOffset = consumeMessage.indexOf("\n\n")) > 0) {
                bodyOffset += 2;
            }
            message.setBodyOffset(bodyOffset);
            if (bodyOffset > 0) {
                String headersContent = consumeMessage.substring(0, bodyOffset);

                message.setHeaderContent(headersContent);
                String lowCaseHeaders = headersContent.toLowerCase();
                // If not sure why this, see the "Request BODY RULE" in this file comment
                if (lowCaseHeaders.contains("content-length:")) {
                    String contentLength = lowCaseHeaders.substring(lowCaseHeaders.indexOf("content-length:") + "content-length:".length()).split("\\r?\\n", 2)[0];
                    requiredLength = Integer.parseInt(contentLength.trim());
                    requiredLength += bodyOffset;
                    message.setExpectedLen(requiredLength);
                    message.setDoneParsed(true);
                } else {
                    message.setExpectedLen(consumeMessage.length());
                    message.setDoneParsed(true);
                }

            }
        }

//        if (message.getExpectedLen() > 0) {
//            message.setDoneParsed(message.getCurrLen() >= message.getExpectedLen());
//        }

        return message;


    }

    public Channeling getChanneling() {
        return channeling;
    }

    private void closeSocketSilently(ChannelingSocket socketResp) {
        socketResp.close(s->{});
    }

    public boolean isReadBody() {
        return readBody;
    }

    public void setReadBody(boolean readBody) {
        this.readBody = readBody;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
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



    public ErrorCallback getOnReadError() {
        return onReadError;
    }

    public void setOnReadError(ErrorCallback onReadError) {
        this.onReadError = onReadError;
    }

    public ErrorCallback getOnWriteError() {
        return onWriteError;
    }

    public void setOnWriteError(ErrorCallback onWriteError) {
        this.onWriteError = onWriteError;
    }

    public ErrorCallback getOnAcceptError() {
        return onAcceptError;
    }

    public void setOnAcceptError(ErrorCallback onAcceptError) {
        this.onAcceptError = onAcceptError;
    }

    public int getWaitPerNano() {
        return waitPerNano;
    }

    public void setWaitPerNano(int waitPerNano) {
        this.waitPerNano = waitPerNano;
    }
}
