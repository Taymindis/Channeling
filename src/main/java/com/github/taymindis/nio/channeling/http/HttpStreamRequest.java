package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HttpStreamRequest implements HttpRequest {
    private int totalRead = 0, totalWrite, requiredLength, currChunkLength = -1, bodyOffset;
    private ByteBuffer readBuffer;
    private String messageToSend;
    private String host;
    private int port;
    private ChannelingBytesStream channelingBytesStream;
    private ChannelingSocket socket;
    private HttpStreamRequestCallback streamChunked;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
//    private byte[] currConsumedBytes;
    private boolean hasHeaderOut = false;
    private String reqHeaders = null;
    private static final int NEWLINE_BYTE_LENGTH = "\r\n".getBytes().length;

    private final boolean enableGzipDecompression;
    private ChannelingBytes previousChunked = new ChannelingBytes(new byte[0], 0, 0);
    private ChannelingBytesResult headerResult;

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
        this.channelingBytesStream = new ChannelingBytesStream();
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
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);
                channelingBytesStream.write(b);
            } else if (totalRead == 0) {
                eagerRead(channelingSocket, this::massageHeader);
            } else if (contentEncodingType == ContentEncodingType.PENDING) {
                eagerRead(channelingSocket, this::massageHeader);
            } else {
                throw new IllegalStateException("Unknown action headers ....");
            }

            if (channelingBytesStream.size() > 0) {
//                byte[] currBytes = currProcessingStream.toByteArray();
                if ( findHeaders() ) {





                    ChannelingBytes bytes = new ChannelingBytes();
                    while(headerResult.read(bytes)) {
                        streamChunked.headerAccept(bytes, channelingSocket);
                    }

                    streamChunked.afterHeader(channelingSocket);

                    headerResult.flip(channelingBytesStream);

                    switch (responseType) {
                        case TRANSFER_CHUNKED:
                            while(headerResult.read(bytes)) {
                                processChunked(bytes, channelingSocket);
                            }
                            break;
                        case CONTENT_LENGTH:
                            if (totalRead >= requiredLength) {
                                streamChunked.last(channelingBytesStream.toByteArray(), channelingSocket);
                                channelingSocket.close(HttpStreamRequest.this::closeAndThen);
                                return;
                            } else {
                                streamChunked.accept(channelingBytesStream.toByteArray(), channelingSocket);
                                channelingBytesStream.reset();
                                eagerRead(channelingSocket, HttpStreamRequest.this::massageContentLengthBody);
                            }
                            break;
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
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);
//                currConsumedBytes = b;
                channelingBytesStream.write(b);
                ChannelingBytes bytes = new ChannelingBytes();
                while(headerResult.read(bytes)) {
                    processChunked(bytes, channelingSocket);
                }
            } else {
                eagerRead(channelingSocket, this::massageChunkedBody);
            }

        } catch (Exception e) {
            error(channelingSocket, e);
        }
    }

    /**
     *
     * @param channelingSocket
     * @throws IOException
     */
    private void processChunked(ChannelingBytes bytes, ChannelingSocket channelingSocket) throws IOException {
        if (isLastChunked(bytes)) {
            channelingSocket.noEagerRead();
            streamChunked.last(bytes, channelingSocket);
            channelingSocket.close(this::closeAndThen);
        } else {
            previousChunked = bytes;
            streamChunked.accept(bytes, channelingSocket);
            channelingBytesStream.reset();
            eagerRead(channelingSocket, this::massageChunkedBody);
        }

    }

    private boolean isLastChunked(ChannelingBytes bytes) throws IOException {
        int chunkLen = bytes.getLength();
        byte[] chunked = bytes.getBuff();
        if(chunkLen >= 7) {
           return BytesHelper.equals(chunked, "\r\n0\r\n\r\n".getBytes(), bytes.getOffset());
        } else {
            int prevLen = previousChunked.getLength();
            if (prevLen > 0) {
                byte[] lastAttemptedChunk;
                if (prevLen <= 7) {
                    lastAttemptedChunk = new byte[previousChunked.getLength() + chunkLen];
                    System.arraycopy(previousChunked.getBuff(), previousChunked.getOffset(), lastAttemptedChunk, 0, previousChunked.getLength());
                    System.arraycopy(chunked, bytes.getOffset(), lastAttemptedChunk, previousChunked.getLength() , chunkLen);
                } else {
                    lastAttemptedChunk = new byte[7 + chunkLen];
                    System.arraycopy(previousChunked.getBuff(),prevLen-7, lastAttemptedChunk, 0, 7);
                    System.arraycopy(chunked, bytes.getOffset(), lastAttemptedChunk,7 , chunkLen);
                }
                return BytesHelper.equals(lastAttemptedChunk, "\r\n0\r\n\r\n".getBytes(), lastAttemptedChunk.length - 7);
            }
        }
        return false;
    }

//    public void eagerChunkBodyLen(ChannelingSocket channelingSocket) {
//        int numRead = channelingSocket.getLastProcessedBytes();
//        ByteBuffer readBuffer = channelingSocket.getReadBuffer();
//        try {
//            if (numRead > 0) {
//                totalRead += numRead;
//                readBuffer.flip();
//                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
//                readBuffer.get(b);
////                currConsumedBytes = b;
//                channelingBytesStream.write(b);
//                if (channelingBytesStream.size() < currChunkLength) {
//                    eagerRead(channelingSocket, this::eagerChunkBodyLen);
//                    return;
//                }
//
//                byte[] chunkBody = channelingBytesStream.toByteArray();
//
//                if (BytesHelper.equals(chunkBody, "\r\n0\r\n\r\n".getBytes(), chunkBody.length - 7)) {
//                    channelingSocket.noEagerRead();
//                    streamChunked.last(chunkBody, channelingSocket);
//                    channelingSocket.close(this::closeAndThen);
//                } else {
//                    streamChunked.accept(BytesHelper.subBytes(chunkBody, 0, currChunkLength - NEWLINE_BYTE_LENGTH), channelingSocket);
//                    channelingBytesStream.reset();
//                    byte[] chunked = BytesHelper.subBytes(chunkBody, currChunkLength, chunkBody.length);
//                    int len = chunked.length;
//                    if (len == 0) {
//                        eagerRead(channelingSocket, this::massageChunkedBody);
//                    } else if (len >= 5 && BytesHelper.equals(chunked, "0\r\n\r\n".getBytes(), len - 5)) {
//                        channelingSocket.noEagerRead();
//                        streamChunked.last("".getBytes(), channelingSocket);
//                        channelingSocket.close(this::closeAndThen);
//                    } else {
//                        processChunked(chunked, channelingSocket);
//                    }
//                }
//            } else {
//                eagerRead(channelingSocket, this::eagerChunkBodyLen);
//            }
//        } catch (Exception e) {
//            error(channelingSocket, e);
//        }
//    }

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
                    streamChunked.last(b, channelingSocket);
                    channelingSocket.close(this::closeAndThen);
                    return;
                } else {
                    streamChunked.accept(b, channelingSocket);
                }
            }

            eagerRead(channelingSocket, this::massageContentLengthBody);

        } catch (Exception e) {
            error(channelingSocket, e);
        }

    }

    private boolean findHeaders() {
        if (bodyOffset == -1) {

            headerResult = channelingBytesStream.searchBytesBefore("\r\n\r\n".getBytes(), false);

            if(headerResult == null) {
                return false;
            }
            bodyOffset = headerResult.getTotalBytes() + "\r\n\r\n".getBytes().length;

            if (responseType == HttpResponseType.PENDING || contentEncodingType == ContentEncodingType.PENDING) {
                ChannelingBytesResult contentLengthResult;

                if(channelingBytesStream.searchBytesAfter("transfer-encoding:".getBytes(), false, headerResult) != null) {
                    responseType = HttpResponseType.TRANSFER_CHUNKED;
                } else if((contentLengthResult = channelingBytesStream.searchBytesAfter("content-length:".getBytes(), false, headerResult)) != null) {
                    contentLengthResult = channelingBytesStream.searchBytesBefore("\r\n".getBytes(), false, contentLengthResult);
                    String contentLength = new String(contentLengthResult.dupBytes());
                    requiredLength = Integer.parseInt(contentLength.trim());
                    requiredLength += bodyOffset;
                    responseType = HttpResponseType.CONTENT_LENGTH;
                } else {
                    requiredLength = headerResult.getTotalBytes();
                    responseType = HttpResponseType.CONTENT_LENGTH;
                }

                if (channelingBytesStream.searchBytesAfter("content-encoding: gzip".getBytes(), false, headerResult) != null) {
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
}
