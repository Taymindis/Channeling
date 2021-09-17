package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.taymindis.nio.channeling.http.HttpMessageHelper.*;

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
    private Map<String, RequestListener> vHostRequestListener;
    private RequestListener defaultRequestListener;
    private boolean readBody = true;


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
        defaultRequestListener = requestListener;
        listen(Map.of("_", requestListener));
    }

    public void listen(Map<String, RequestListener> vHostRequestListener) {
        if(isActive) {
            throw new IllegalStateException("Service has already running ... ");
        }
        isActive = true;

        // Retrieve first as Default Listener
        for(Map.Entry<String, RequestListener> entry: vHostRequestListener.entrySet()) {
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

            acceptedSock.withEagerRead(buffSize).then(this::readAndThen, ChannelingServer.this::closeErrorSocketSilently);

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
            if (socketChannel != null) {
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

                HttpRequestParser messageParser = parsingMessage(socketRead, b);

                if (!messageParser.isDoneParsed()) {
                    socketRead.setContext(messageParser);
                    eagerRead(readBuffer, socketRead);
                } else {
                    // TODO Handle Request
                    HttpRequestMessage request = convertMessageToHttpRequestMessage(socketRead, messageParser);
                    HttpResponseMessage response;
                    String vHost = request.getHeaderMap().get("Host");
                    if(vHost != null) {
                        response = this.vHostRequestListener.getOrDefault(vHost, defaultRequestListener).handleRequest(request);
                    } else {
                        response = defaultRequestListener.handleRequest(request);
                    }

                    String responseMsg = massageResponseToString(response);

                    ByteBuffer writeBuffer = ByteBuffer.wrap(responseMsg.getBytes(StandardCharsets.UTF_8));
                    socketRead.write(writeBuffer, this::closeSocketSilently, ChannelingServer.this::closeErrorSocketSilently);

                }

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

    private HttpRequestMessage convertMessageToHttpRequestMessage(ChannelingSocket socketRead, HttpRequestParser messageParser) throws Exception {
        HttpRequestMessage request = new HttpRequestMessage();
        request.setRemoteAddress(socketRead.getRemoteAddress());

        massageRequestHeader(request, messageParser.getHeaderContent());

        request.setBody(messageParser.getBody());

        return request;
    }

    private HttpRequestParser parsingMessage(ChannelingSocket socketRead, byte[] b) throws IOException {
        HttpRequestParser message = (HttpRequestParser) socketRead.getContext();
        if (message == null) {
            message = new HttpRequestParser();
//            message.setRemoteAddress(socketRead.getSocketChannel().getRemoteAddress());
        }
        int requiredLength;
        int bodyOffset = message.getBodyOffset();

        if (isReadBody()) {
            byte[] currBytes = message.getRawBytes();
            if (currBytes == null) {
                message.setRawBytes(b);
            } else {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    outputStream.write(currBytes);
                    outputStream.write(b);

                    message.setRawBytes(outputStream.toByteArray());
                }
            }
        }


        String consumeMessage = parseToString(b);

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
                if (lowCaseHeaders.contains("content-length: ")) {
                    String contentLength = lowCaseHeaders.substring(lowCaseHeaders.indexOf("content-length:") + "content-length:".length()).split("\r\n", 2)[0];
                    requiredLength = Integer.parseInt(contentLength.trim());
                } else {
                    requiredLength = consumeMessage.length() - bodyOffset;
                    message.setDoneParsed(true);
                }

                requiredLength += bodyOffset;

                message.setExpectedLen(requiredLength);
            }
        }

        if (message.getExpectedLen() > 0) {
            message.setDoneParsed(message.getCurrLen() >= message.getExpectedLen());
        }

        return message;


    }


    private void closeSocketSilently(ChannelingSocket socketResp) {
        this.closeErrorSocketSilently(socketResp, null);
    }

    public boolean isReadBody() {
        return readBody;
    }

    public void setReadBody(boolean readBody) {
        this.readBody = readBody;
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
