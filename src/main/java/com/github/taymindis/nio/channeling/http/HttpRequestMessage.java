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
    private int numOfReadTry = 1;
    private boolean hasBody;

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

    public void setHasBody(boolean hasBody) {
        this.hasBody = hasBody;
    }

    public void readBody(Consumer<ChannelingBytes> bodyConsumer) throws IllegalStateException {
        try {
            this.bodyConsumer = bodyConsumer;
            if (this.hasBody) {
                this.acceptBody(clientReadWriter.toChannelingBytes(bodyOffset));
                return;
            }

            if ("GET".equals(method) || "HEAD".equals(method)) {
                acceptBody(clientReadWriter.toChannelingBytes(bodyOffset, 0));
                return;
            }
            if (expectedLen != -1) {
                readBodyWithContentLength(clientSocket.getReadBuffer());
            } else {
                readBodyTillClose(clientSocket.getReadBuffer());

            }
        } catch (IOException e) {
            e.printStackTrace();
            acceptBody(null);
        }
    }

    private void readBodyWithContentLength(ByteBuffer readBuffer) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        clientSocket.withEagerRead(readBuffer).then(channelingSocket -> {
            int numRead = channelingSocket.getLastProcessedBytes();
            try {
                if (numRead == -1) {
                    throw new IOException("Connection reset by peer");
                }
                ByteBuffer byteBuffer = channelingSocket.getReadBuffer();
                byteBuffer.flip();
                clientReadWriter.write(byteBuffer);
                if (clientReadWriter.size() >= expectedLen) {
                    this.acceptBody(clientReadWriter.toChannelingBytes(bodyOffset));
                    return;
                }
                HttpRequestMessage.this.readBodyWithContentLength(byteBuffer);
            } catch (IOException e) {
                clientSocket.close(cs->{});
                e.printStackTrace();
            }
        });
    }

    private void readBodyTillClose(ByteBuffer readBuffer) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        clientSocket.withEagerRead(readBuffer).then(channelingSocket -> {
            int numRead = channelingSocket.getLastProcessedBytes();
            try {
                if (numRead == -1) {
                    this.acceptBody(clientReadWriter.toChannelingBytes(bodyOffset));
                    return;
                }
                ByteBuffer byteBuffer = channelingSocket.getReadBuffer();
                byteBuffer.flip();
                clientReadWriter.write(byteBuffer);

                if (numRead == 0) {
                    if (numOfReadTry-- == 0) {
                        acceptBody(clientReadWriter.toChannelingBytes(bodyOffset, 0));
                        return;
                    }
                } else {
                    numOfReadTry = 1;
                }
                HttpRequestMessage.this.readBodyTillClose(byteBuffer);
            } catch (IOException e) {
                this.acceptBody(null);
                e.printStackTrace();
            }
        });
    }

    private void acceptBody(ChannelingBytes bytes) {
        this.hasBody = true;
        this.clientSocket.noEagerRead();
        this.bodyConsumer.accept(bytes);
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
