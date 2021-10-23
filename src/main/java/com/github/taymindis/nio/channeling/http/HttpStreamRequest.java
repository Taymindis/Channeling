package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HttpStreamRequest implements HttpRequest {
    private int totalRead = 0, totalWrite, requiredLength, currChunkLength = -1, bodyOffset;
    private ByteBuffer readBuffer;
    private String messageToSend;
    private String host;
    private int port;
    private ChannelingBytesStream2 channelingBytesStream;
    private ChannelingSocket socket;
    private HttpStreamRequestCallback streamChunked;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
    //    private byte[] currConsumedBytes;
    private boolean hasHeaderOut = false;
    private String reqHeaders = null;
    private static final int NEWLINE_BYTE_LENGTH = "\r\n".getBytes().length;
    private static final byte[] LAST_CHUNKED_PATTERN = "\r\n0\r\n\r\n".getBytes();


    private final boolean enableGzipDecompression;
//    private ChannelingBytes previousChunked = new ChannelingBytes(new byte[0], 0, 0);

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

    protected HttpStreamRequest(ChannelingSocket socket,
                                String host,
                                int port,
                                String messageToSend,
                                int minInputBufferSize,
                                boolean enableGzipDecompression) {
        this.readBuffer = ByteBuffer.allocate(socket.isSSL() ? socket.getSSLMinimumInputBufferSize() : minInputBufferSize);
        this.channelingBytesStream = new ChannelingBytesStream2(1024 * 10);
        this.messageToSend = messageToSend;
        this.socket = socket;
        this.host = host;
        this.port = port;
        this.responseType = HttpResponseType.PENDING;
        this.contentEncodingType = ContentEncodingType.PENDING;
        this.bodyOffset = -1;
        this.enableGzipDecompression = enableGzipDecompression;
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
            channelingSocket.withEagerRead(readBuffer).then(this::massageHeader);
        }
    }

    public void massageHeader(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();

        try {
            if (numRead > 0) {
                totalRead += numRead;
                readBuffer.flip();
                channelingBytesStream.write(readBuffer);
            } else if (totalRead == 0) {
                eagerRead(channelingSocket, this::massageHeader);
            } else if (contentEncodingType == ContentEncodingType.PENDING) {
                eagerRead(channelingSocket, this::massageHeader);
            } else {
                throw new IllegalStateException("Unknown action headers ....");
            }

            if (channelingBytesStream.size() > 0) {
//                byte[] currBytes = currProcessingStream.toByteArray();
                if (findHeaders()) {
                    ChannelingBytes bytes = channelingBytesStream.readToChannelingBytes(bodyOffset);

                    streamChunked.headerAccept(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);

                    /** Read the left over bytes **/
                    bytes = channelingBytesStream.readToChannelingBytes();

                    switch (responseType) {
                        case TRANSFER_CHUNKED:
                            if (isLastChunked()) {
                                channelingSocket.noEagerRead();
                                streamChunked.last(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);
                                channelingSocket.close(this::closeAndThen);
                            } else {
                                streamChunked.accept(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);
                                eagerRead(channelingSocket, this::massageChunkedBody);
                            }
                            return;
                        case CONTENT_LENGTH:
                            if (totalRead >= requiredLength) {
                                channelingSocket.noEagerRead();
                                streamChunked.last(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);
                                channelingSocket.close(HttpStreamRequest.this::closeAndThen);
                            } else {
                                streamChunked.accept(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);
                                eagerRead(channelingSocket, this::massageContentLengthBody);
                            }
                            return;
                        case PENDING:
                        case PARTIAL_CONTENT:
                            break;
                    }
                }
            }
            eagerRead(channelingSocket, this::massageHeader);
        } catch (Exception e) {
            error(channelingSocket, e);
        }
    }

    public void massageChunkedBody(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();

        try {
            if (numRead > 0) {
                totalRead += numRead;
                readBuffer.flip();
//                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
//                readBuffer.get(b);
//                currConsumedBytes = b;
                channelingBytesStream.write(readBuffer);
                ChannelingBytes bytes = channelingBytesStream.readToChannelingBytes();
                if (isLastChunked()) {
                    channelingSocket.noEagerRead();
                    streamChunked.last(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);
                    channelingSocket.close(this::closeAndThen);
                } else {
                    streamChunked.accept(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), channelingSocket);
                    eagerRead(channelingSocket, this::massageChunkedBody);
                }
            } else {
                eagerRead(channelingSocket, this::massageChunkedBody);
            }

        } catch (Exception e) {
            error(channelingSocket, e);
        }
    }

    private boolean isLastChunked() {
        return channelingBytesStream.endsWith(LAST_CHUNKED_PATTERN);
    }

    public void massageContentLengthBody(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();

        try {
            if (numRead > 0) {
                totalRead += numRead;
                readBuffer.flip();
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);
                if (totalRead >= requiredLength) {
                    streamChunked.last(b, 0, b.length, channelingSocket);
                    channelingSocket.close(this::closeAndThen);
                    return;
                } else {
                    streamChunked.accept(b, 0, b.length, channelingSocket);
                }
            }

            eagerRead(channelingSocket, this::massageContentLengthBody);

        } catch (Exception e) {
            error(channelingSocket, e);
        }

    }

    private boolean findHeaders() throws IOException {
        if (bodyOffset == -1) {
            bodyOffset = channelingBytesStream.indexOf("\r\n\r\n".getBytes());
            if (bodyOffset == -1) {
                return false;
            }
            bodyOffset += "\r\n\r\n".getBytes().length;
            if (responseType == HttpResponseType.PENDING ||
                    contentEncodingType == ContentEncodingType.PENDING) {
                reqHeaders = channelingBytesStream.toString(bodyOffset, StandardCharsets.UTF_8).toUpperCase();
                if (reqHeaders.contains("TRANSFER-ENCODING:")) {
                    responseType = HttpResponseType.TRANSFER_CHUNKED;
                } else if (reqHeaders.contains("CONTENT-LENGTH:")) {
                    String contentLength = reqHeaders.split("CONTENT-LENGTH:", 2)[1].split("\\r?\\n")[0];
                    requiredLength = Integer.parseInt(contentLength.trim());
                    requiredLength += bodyOffset;
                    responseType = HttpResponseType.CONTENT_LENGTH;
                } else {
                    requiredLength = channelingBytesStream.size();
                    responseType = HttpResponseType.CONTENT_LENGTH;
                }

                if (reqHeaders.contains("CONTENT-ENCODING: GZIP")) {
                    contentEncodingType = ContentEncodingType.GZIP;
                } else if (contentEncodingType == ContentEncodingType.PENDING) {
                    contentEncodingType = ContentEncodingType.OTHER;
                }
            }
            return true;
        }
        return true;
    }

    private void eagerRead(ChannelingSocket channelingSocket, Then $then) {
        if (!readBuffer.hasRemaining()) {
            readBuffer.clear();
        }
        channelingSocket.withEagerRead(readBuffer).then($then);
    }


    public void closeAndThen(ChannelingSocket channelingSocket) {
        /** Do nothing **/
    }

    private void error(ChannelingSocket channelingSocket, Exception e) {
        channelingSocket.close(this::closeAndThen);
        this.streamChunked.error(e, channelingSocket);
    }


    @Override
    public void execute(HttpStreamRequestCallback callback) {
        this.streamChunked = callback;
        socket.withConnect(host, port).when((WhenConnectingStatus) connectingStatus -> connectingStatus).then(this::connectAndThen, this::error);
    }

    @Override
    public void execute(HttpSingleRequestCallback result) {
        throw new UnsupportedOperationException("Stream Request un-support HttpResponse");
    }

    private boolean hasEnoughChunk(byte[] chunkBody, int currChunkLength) {
        return chunkBody.length >= currChunkLength;
    }

    private boolean hasEnoughChunk(int chunkBodyLen, int currChunkLength) {
        return chunkBodyLen >= currChunkLength;
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

    private String getData(ChannelingBytesResult result) {
        StringBuilder a = new StringBuilder();
        result.forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                a.append(new String(bytes, offset, length));
                return true;
            }
        });
        return a.toString();
    }
}
