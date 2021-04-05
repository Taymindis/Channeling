package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;
import com.github.taymindis.nio.channeling.WhenConnectingStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public class HttpRequest implements SingleRequest {
    private int totalRead = 0, totalWrite, requiredLength, bodyOffset;
    private final ByteBuffer readBuffer;
    private final String messageToSend;
    private final String host;
    private final int port;
    private final ByteArrayOutputStream response;
    private final ChannelingSocket socket;
    private Consumer<HttpResponse> result;
    private Consumer<Exception> error;
    private byte[] lastConsumedBytes;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
    private HttpResponse httpResponse;
    private final boolean enableGzipDecompression;

    public HttpRequest(ChannelingSocket socket,
                       String host, int port,
                       String messageToSend) {
        this(socket, host, port, messageToSend, 1024);
    }

    public HttpRequest(ChannelingSocket socket,
                       String host,
                       int port,
                       String messageToSend,
                       int minInputBufferSize) {
        this(socket, host, port, messageToSend, minInputBufferSize, false);
    }

    public HttpRequest(ChannelingSocket socket,
                       String host,
                       int port,
                       String messageToSend,
                       int minInputBufferSize,
                       boolean enableGzipDecompression) {
        this.readBuffer = ByteBuffer.allocate(socket.isSSL() ? socket.getSSLMinimumInputBufferSize() : minInputBufferSize);
        this.response = new ByteArrayOutputStream();
        this.messageToSend = messageToSend;
        this.socket = socket;
        this.host = host;
        this.port = port;
        this.responseType = HttpResponseType.PENDING;
        this.contentEncodingType = ContentEncodingType.PENDING;
        this.bodyOffset = -1;
        this.enableGzipDecompression = enableGzipDecompression;
    }

    public void get(Consumer<HttpResponse> result, Consumer<Exception> error) {
        this.result = result;
        this.error = error;
        socket.withConnect(host, port).when((WhenConnectingStatus) connectingStatus -> connectingStatus).then(this::connectAndThen, this::error);
    }

    public int getTotalRead() {
        return totalRead;
    }

    public void setTotalRead(int totalRead) {
        this.totalRead = totalRead;
    }

    public int getTotalWrite() {
        return totalWrite;
    }

    public void setTotalWrite(int totalWrite) {
        this.totalWrite = totalWrite;
    }


    @Override
    public void connectAndThen(ChannelingSocket channelingSocket) {
        ByteBuffer writeBuffer = ByteBuffer.wrap(messageToSend.getBytes(StandardCharsets.UTF_8));
        channelingSocket.write(writeBuffer, this::writeAndThen);
    }

    @Override
    public void writeAndThen(ChannelingSocket channelingSocket) {
        ByteBuffer currWriteBuff = channelingSocket.getCurrWritingBuffer();
        totalWrite += channelingSocket.getLastProcessedBytes();
        if (currWriteBuff.hasRemaining()) {
            channelingSocket.write(currWriteBuff, this::writeAndThen);
        } else {
            readBuffer.clear();
            httpResponse = new HttpResponse();
            channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
        }
    }

    @Override
    public void readAndThen(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();
        /**
         *
         * This is transfer encoding method
         */
        try {
            if (numRead > 0) {
                totalRead += numRead;
                readBuffer.flip();
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);
                lastConsumedBytes = b;
                extractResponseAndEncodingType(b);
                response.write(b);
                readBuffer.clear();
                channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
            } else if (totalRead == 0) {
                eagerRead(channelingSocket);
            } else if (contentEncodingType == ContentEncodingType.PENDING) {
                eagerRead(channelingSocket);
            } else {
                if (bodyOffset == -1) {
                    extraBodyOffsetOnly(response.toByteArray());
                }
                switch (responseType) {
                    case PENDING:
                        eagerRead(channelingSocket);
                        break;
                    case TRANSFER_CHUNKED:
                        transferEncodingResponse(channelingSocket);
                        break;
                    case CONTENT_LENGTH:
                        contentLengthResponse(channelingSocket);
                        break;
                    case PARTIAL_CONTENT:
                    default:
                        error(channelingSocket, new IllegalStateException("It shouldn't be here"));
                }
            }
        } catch (Exception e) {
            error(channelingSocket, e);
        }
    }

    private String parseToString(byte[] consumedBytes) {
        return new String(consumedBytes, StandardCharsets.UTF_8);

    }

    private void extractResponseAndEncodingType(byte[] bytes) throws Exception {
        if (responseType == HttpResponseType.PENDING || contentEncodingType == ContentEncodingType.PENDING) {
            String consumeMessage = parseToString(bytes);
            if ((bodyOffset = consumeMessage.indexOf("\r\n\r\n")) > 0) {
                bodyOffset += 4;
            } else if ((bodyOffset = consumeMessage.indexOf("\n\n")) > 0) {
                bodyOffset += 2;
            }

            // Means done header rendered
            if (bodyOffset > 0) {
                String headersContent = consumeMessage.substring(0, bodyOffset);
                httpResponse.setHeaders(headersContent);
                String lowCaseHeaders = headersContent.toLowerCase();
                if (lowCaseHeaders.contains("transfer-encoding:")) {
                    responseType = HttpResponseType.TRANSFER_CHUNKED;
                } else if (lowCaseHeaders.contains("content-length: ")) {
                    String contentLength = lowCaseHeaders.substring(lowCaseHeaders.indexOf("content-length:") + "content-length:".length()).split("\r\n", 2)[0];
                    requiredLength = Integer.parseInt(contentLength.trim());
                    responseType = HttpResponseType.CONTENT_LENGTH;
                } else {
                    requiredLength = consumeMessage.length() - bodyOffset;
                    responseType = HttpResponseType.CONTENT_LENGTH;
                }

                httpResponse.setResponseType(responseType);

                if (lowCaseHeaders.contains("content-encoding: gzip")) {
                    contentEncodingType = ContentEncodingType.GZIP;
                } else if (contentEncodingType == ContentEncodingType.PENDING) {
                    contentEncodingType = ContentEncodingType.OTHER;
                }
                requiredLength += bodyOffset;
            }
        }

    }

    private void extraBodyOffsetOnly(byte[] bytes) {
        String consumeMessage = parseToString(bytes);
        if (bodyOffset == -1) {
            if ((bodyOffset = consumeMessage.indexOf("\r\n\r\n")) > 0) {
                bodyOffset += 4;
            } else if ((bodyOffset = consumeMessage.indexOf("\n\n")) > 0) {
                bodyOffset += 2;
            }
        }
        requiredLength += bodyOffset;
    }


    private void transferEncodingResponse(ChannelingSocket channelingSocket) throws IOException {
        int len = lastConsumedBytes.length;

        String last5Chars = new String(Arrays.copyOfRange(lastConsumedBytes, len - 5, len), StandardCharsets.UTF_8);

        if (last5Chars.equals("0\r\n\r\n") || last5Chars.substring(2).equals("0\n\n")) {
            channelingSocket.noEagerRead();
            httpResponse.setRawBytes(response.toByteArray());
            httpResponse.setBodyOffset(bodyOffset);
            updateResponseType(httpResponse);
            result.accept(httpResponse);
            channelingSocket.close(this::closeAndThen);
        } else {
            eagerRead(channelingSocket);
        }
    }

    private void updateResponseType(HttpResponse httpResponse) throws IOException {
        if (contentEncodingType == ContentEncodingType.GZIP && enableGzipDecompression) {
            httpResponse.setContentEncodingType(ContentEncodingType.GZIP);
        } else {
            httpResponse.setContentEncodingType(contentEncodingType);
        }
    }

    private void contentLengthResponse(ChannelingSocket channelingSocket) throws IOException {
        if (totalRead >= requiredLength) {
            channelingSocket.noEagerRead();
            httpResponse.setRawBytes(response.toByteArray());
            httpResponse.setBodyOffset(bodyOffset);
            updateResponseType(httpResponse);
            result.accept(httpResponse);
            channelingSocket.close(this::closeAndThen);
        } else {
            eagerRead(channelingSocket);
        }
    }

    private void eagerRead(ChannelingSocket channelingSocket) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
    }


    @Override
    public void closeAndThen(ChannelingSocket channelingSocket) {
        /** Do nothing **/
    }

    @Override
    public void error(ChannelingSocket channelingSocket, Exception e) {
        error.accept(e);
        channelingSocket.close(this::closeAndThen);
    }

    /**
     This is derived from
     https://github.com/patrickfav/bytes-java
     */
    static int indexOf(byte[] array, byte[] target, int start, int end) {
        Objects.requireNonNull(array, "array must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (target.length == 0 || start < 0) {
            return -1;
        }

        outer:
        for (int i = start; i < Math.min(end, array.length - target.length + 1); i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
