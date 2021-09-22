package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.BytesHelper;
import com.github.taymindis.nio.channeling.ChannelingSocket;
import com.github.taymindis.nio.channeling.WhenConnectingStatus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

public class HttpStreamRequest  {
    private int totalRead = 0, totalWrite, requiredLength, bodyOffset;
    private ByteBuffer readBuffer;
    private String messageToSend;
    private String host;
    private int port;
    private ByteArrayOutputStream response;
    private ChannelingSocket socket;
    private HttpStreamRequestCallback streamChunked;
    private Consumer<Exception> error;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
    private byte[] currConsumedBytes;

    private final boolean enableGzipDecompression;

    public HttpStreamRequest(ChannelingSocket socket,
                             String host, int port,
                             String messageToSend) {
        this(socket, host, port, messageToSend, 1024);
    }

    public HttpStreamRequest(ChannelingSocket socket,
                             String host,
                             int port,
                             String messageToSend,
                             int minInputBufferSize) {
        this(socket, host, port, messageToSend, minInputBufferSize, false);
    }

    public HttpStreamRequest(ChannelingSocket socket,
                             String host,
                             int port,
                             String messageToSend,
                             int minInputBufferSize,
                             boolean enableGzipDecompression) {
        this(socket, host, port, messageToSend, minInputBufferSize, enableGzipDecompression, null);

    }

    protected HttpStreamRequest(ChannelingSocket socket,
                                String host,
                                int port,
                                String messageToSend,
                                int minInputBufferSize,
                                boolean enableGzipDecompression,
                                RedirectionSocket redirectionSocket) {
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

    public void execute(HttpStreamRequestCallback callback, Consumer<Exception> error) {
        this.streamChunked = callback;
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

    public void connectAndThen(ChannelingSocket channelingSocket) {
        ByteBuffer writeBuffer = ByteBuffer.wrap(messageToSend.getBytes(StandardCharsets.UTF_8));
        channelingSocket.write(writeBuffer, this::writeAndThen);
    }

    public void writeAndThen(ChannelingSocket channelingSocket) {
        ByteBuffer currWriteBuff = channelingSocket.getCurrWritingBuffer();
        totalWrite += channelingSocket.getLastProcessedBytes();
        if (currWriteBuff.hasRemaining()) {
            channelingSocket.write(currWriteBuff, this::writeAndThen);
        } else {
            readBuffer.clear();
            channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
        }
    }

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
                currConsumedBytes = b;
                response.write(b);
                streamChunked.accept(b, false);
                extractResponseAndEncodingType(response.toByteArray());
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
            if ((bodyOffset = BytesHelper.indexOf(bytes, "\r\n\r\n".getBytes())) > 0) {
                bodyOffset += 4;
            } else if ((bodyOffset = BytesHelper.indexOf(bytes, "\n\n".getBytes())) > 0) {
                bodyOffset += 2;
            }

            // Means done header bytes
            if (bodyOffset > 0) {
//                String headersContent = consumeMessage.substring(0, bodyOffset);
//                httpResponse.setHeaders(headersContent);
                String lowCaseHeaders = new String(bytes).toLowerCase();
                if (lowCaseHeaders.contains("transfer-encoding:")) {
                    responseType = HttpResponseType.TRANSFER_CHUNKED;
                } else if (lowCaseHeaders.contains("content-length:")) {
                    String contentLength = lowCaseHeaders.substring(lowCaseHeaders.indexOf("content-length:") + "content-length:".length()).split("\\r?\\n", 2)[0];
                    requiredLength = Integer.parseInt(contentLength.trim());
                    responseType = HttpResponseType.CONTENT_LENGTH;
                } else {
                    requiredLength = bytes.length - bodyOffset;
                    responseType = HttpResponseType.CONTENT_LENGTH;
                }

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
//        String consumeMessage = parseToString(bytes);
        if (bodyOffset == -1) {
            if ((bodyOffset = BytesHelper.indexOf(bytes, "\r\n\r\n".getBytes())) > 0) {
                bodyOffset += 4;
                requiredLength += bodyOffset;
            } else if ((bodyOffset = BytesHelper.indexOf(bytes, "\n\n".getBytes())) > 0) {
                bodyOffset += 2;
                requiredLength += bodyOffset;
            }
        }
    }


    private void transferEncodingResponse(ChannelingSocket channelingSocket) throws Exception {
        byte[] totalConsumedBytes = response.toByteArray();
        int len = totalConsumedBytes.length;

        byte[] last5Bytes = BytesHelper.subBytes(totalConsumedBytes, len-5);

        if (BytesHelper.equals(last5Bytes, "0\r\n\r\n".getBytes()) || BytesHelper.equals(last5Bytes, "0\n\n".getBytes(), 2)) {
            channelingSocket.noEagerRead();
            streamChunked.accept(currConsumedBytes,true);
            channelingSocket.close(this::closeAndThen);
        } else {
            eagerRead(channelingSocket);
        }
    }


    private void contentLengthResponse(ChannelingSocket channelingSocket) throws Exception {
        if (totalRead >= requiredLength) {
            channelingSocket.noEagerRead();
            streamChunked.accept(currConsumedBytes,true);
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


    public void closeAndThen(ChannelingSocket channelingSocket) {
        /** Do nothing **/
    }

    public void error(ChannelingSocket channelingSocket, Exception e) {
        error.accept(e);
        channelingSocket.close(this::closeAndThen);
    }
}
