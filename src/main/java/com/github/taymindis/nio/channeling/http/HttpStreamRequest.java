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
    private ByteArrayOutputStream currProcessingStream;
    private ChannelingSocket socket;
    private HttpStreamRequestCallback streamChunked;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
//    private byte[] currConsumedBytes;
    private boolean hasHeaderOut = false;
    private String reqHeaders = null;
    private static final int NEWLINE_BYTE_LENGTH = "\r\n".getBytes().length;

    private final boolean enableGzipDecompression;
    private byte[] previousChunked = new byte[0];

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
        this.currProcessingStream = new ByteArrayOutputStream();
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
                currProcessingStream.write(b);
            } else if (totalRead == 0) {
                eagerRead(channelingSocket, this::massageHeader);
            } else if (contentEncodingType == ContentEncodingType.PENDING) {
                eagerRead(channelingSocket, this::massageHeader);
            } else {
                throw new IllegalStateException("Unknown action headers ....");
            }

            if (currProcessingStream.size() > 0) {
                byte[] currBytes = currProcessingStream.toByteArray();
                if (tryFindingBodyOffset(currBytes)) {
                    extractResponseAndEncodingType(currBytes);
                    currProcessingStream.reset();
                    if (currBytes.length > bodyOffset) {
                        // TODO check this part please, is this causing number format issue?
                        currProcessingStream.write(currBytes, bodyOffset, currBytes.length - bodyOffset);
                    }
                    streamChunked.header(reqHeaders, channelingSocket);
                    switch (responseType) {
                        case TRANSFER_CHUNKED:
                            processChunked(currProcessingStream.toByteArray(), channelingSocket);
                            break;
                        case CONTENT_LENGTH:
                            if (totalRead >= requiredLength) {
                                streamChunked.last(currProcessingStream.toByteArray(), channelingSocket);
                                channelingSocket.close(this::closeAndThen);
                                return;
                            } else {
                                streamChunked.accept(currProcessingStream.toByteArray(), channelingSocket);
                                currProcessingStream.reset();
                                eagerRead(channelingSocket, this::massageContentLengthBody);
                            }
                            break;
                        case PENDING:
                        case PARTIAL_CONTENT:
                            break;
                    }

                    return;
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
                currProcessingStream.write(b);
                processChunked(currProcessingStream.toByteArray(), channelingSocket);
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
    private void processChunked(byte[] chunked, ChannelingSocket channelingSocket) throws IOException {
        int chunkLen = chunked.length;
        if (isLastChunked(chunked, chunkLen)) {
            channelingSocket.noEagerRead();
            streamChunked.last(chunked, channelingSocket);
            channelingSocket.close(this::closeAndThen);
        } else {
            previousChunked = chunked;
            streamChunked.accept(chunked, channelingSocket);
            currProcessingStream.reset();
            eagerRead(channelingSocket, this::massageChunkedBody);
        }

    }

    private boolean isLastChunked(byte[] chunked, int chunkLen) throws IOException {
        if(chunkLen >= 7) {
           return BytesHelper.equals(chunked, "\r\n0\r\n\r\n".getBytes(), chunkLen - 7);
        } else {
            int prevLen = previousChunked.length;
            if (prevLen > 0) {

                byte[] lastAttemptedChunk;
                if (prevLen <= 7) {
                    lastAttemptedChunk = BytesHelper.concat(previousChunked, chunked);
                } else {
                    lastAttemptedChunk = BytesHelper.concat(BytesHelper.subBytes(previousChunked, prevLen-7), chunked);
                }
                return BytesHelper.equals(lastAttemptedChunk, "\r\n0\r\n\r\n".getBytes(), lastAttemptedChunk.length - 7);
            }
        }
        return false;
    }

    public void eagerChunkBodyLen(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();
        try {
            if (numRead > 0) {
                totalRead += numRead;
                readBuffer.flip();
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);
//                currConsumedBytes = b;
                currProcessingStream.write(b);
                if (currProcessingStream.size() < currChunkLength) {
                    eagerRead(channelingSocket, this::eagerChunkBodyLen);
                    return;
                }

                byte[] chunkBody = currProcessingStream.toByteArray();

                if (BytesHelper.equals(chunkBody, "\r\n0\r\n\r\n".getBytes(), chunkBody.length - 7)) {
                    channelingSocket.noEagerRead();
                    streamChunked.last(chunkBody, channelingSocket);
                    channelingSocket.close(this::closeAndThen);
                } else {
                    streamChunked.accept(BytesHelper.subBytes(chunkBody, 0, currChunkLength - NEWLINE_BYTE_LENGTH), channelingSocket);
                    currProcessingStream.reset();
                    byte[] chunked = BytesHelper.subBytes(chunkBody, currChunkLength, chunkBody.length);
                    int len = chunked.length;
                    if (len == 0) {
                        eagerRead(channelingSocket, this::massageChunkedBody);
                    } else if (len >= 5 && BytesHelper.equals(chunked, "0\r\n\r\n".getBytes(), len - 5)) {
                        channelingSocket.noEagerRead();
                        streamChunked.last("".getBytes(), channelingSocket);
                        channelingSocket.close(this::closeAndThen);
                    } else {
                        processChunked(chunked, channelingSocket);
                    }
                }
            } else {
                eagerRead(channelingSocket, this::eagerChunkBodyLen);
            }
        } catch (Exception e) {
            error(channelingSocket, e);
        }

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

    private void extractResponseAndEncodingType(byte[] bytes) {
        if (responseType == HttpResponseType.PENDING || contentEncodingType == ContentEncodingType.PENDING) {
            // Means done header bytes
//                String headersContent = consumeMessage.substring(0, bodyOffset);
//                httpResponse.setHeaders(headersContent);
            reqHeaders = new String(BytesHelper.subBytes(bytes, 0, bodyOffset));
            String lowCaseHeaders = reqHeaders.toLowerCase();
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

    private boolean tryFindingBodyOffset(byte[] bytes) {
        if (bodyOffset == -1) {
            if ((bodyOffset = BytesHelper.indexOf(bytes, "\r\n\r\n".getBytes())) > 0) {
                bodyOffset += 4;
                requiredLength += bodyOffset;
                return true;
            } else if ((bodyOffset = BytesHelper.indexOf(bytes, "\n\n".getBytes())) > 0) {
                bodyOffset += 2;
                requiredLength += bodyOffset;
                return true;
            }
            return false;
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
