package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingByteWriter;
import com.github.taymindis.nio.channeling.ChannelingBytes;
import com.github.taymindis.nio.channeling.ChannelingSocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Consumer;

public class HttpRequestMessage {
    private SocketAddress remoteAddress;
    private Map<String, String> headerMap = null;
    private String method;
    private String path;
    private String httpVersion;
    private ChannelingSocket clientSocket;
    private int expectedLen, bodyOffset;
    private ChannelingByteWriter clientReadWriter;
    private Consumer<ChannelingBytes> bodyConsumer;
    private int numOfReadTry = 2;

    public HttpRequestMessage(ChannelingSocket sc) {
        this.clientSocket = sc;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public Map<String, String> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, String> headerMap) {
        this.headerMap = headerMap;
    }
    public void addHeader(String key, String value) {
        this.headerMap.put(key, value);
    }

    public void readBody(Consumer<ChannelingBytes> bodyConsumer) throws IllegalStateException {

        if("GET".equals(method) || "HEAD".equals(method)) {
            try {
                bodyConsumer.accept(clientReadWriter.toChannelingBytes(bodyOffset, 0));
            } catch (IOException e) {
                e.printStackTrace();
                bodyConsumer.accept(null);
            }
            clientSocket.noEagerRead();
            return;
        }


        this.bodyConsumer = bodyConsumer;
        readBody(clientSocket.getReadBuffer(), clientSocket);
    }

    private void readBody(ByteBuffer readBuffer, ChannelingSocket clientSocket) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        clientSocket.withEagerRead(readBuffer).then(this::bodyAfterRead);
    }

    private void bodyAfterRead(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();
        try {
            if(numRead == -1) {
                channelingSocket.noEagerRead();
                this.bodyConsumer.accept(clientReadWriter.toChannelingBytes(bodyOffset));
                return;
            }

            readBuffer.flip();
            clientReadWriter.write(readBuffer);

            if(clientReadWriter.size() >= expectedLen) {
                channelingSocket.noEagerRead();
                this.bodyConsumer.accept(clientReadWriter.toChannelingBytes(bodyOffset));
                return;
            }

            if(numRead == 0) {
                if(numOfReadTry-- == 0) {
                    channelingSocket.noEagerRead();
                    bodyConsumer.accept(clientReadWriter.toChannelingBytes(bodyOffset, 0));
                    throw new IllegalStateException("Keep alive connection should present Content-length header");
                }
            } else {
                numOfReadTry = 2;
            }


            readBody(readBuffer, channelingSocket);
        } catch (IOException e) {
            channelingSocket.noEagerRead();
            e.printStackTrace();
        }
    }

    public void setExpectedLen(int expectedLen) {
        this.expectedLen = expectedLen;
    }

    public void setBodyOffset(int bodyOffset) {
        this.bodyOffset = bodyOffset;
    }

    public void setClientReadWriter(ChannelingByteWriter clientReadWriter) {
        this.clientReadWriter = clientReadWriter;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    // For client later use
    public ChannelingSocket getClientSocket() {
        return clientSocket;
    }

    public void setClientSocket(ChannelingSocket $sc) {
        this.clientSocket = $sc;
    }

    public Object getContext() {
        return clientSocket.getContext();
    }

    public void setContext(Object context) {
        this.clientSocket.setContext(context);
    }
}
