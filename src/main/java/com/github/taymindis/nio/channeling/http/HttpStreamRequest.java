package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.BytesHelper;
import com.github.taymindis.nio.channeling.ChannelingSocket;
import com.github.taymindis.nio.channeling.Then;
import com.github.taymindis.nio.channeling.WhenConnectingStatus;

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
    private byte[] currConsumedBytes;
    private boolean hasHeaderOut = false;
    private String reqHeaders = null;
    private static final int NEWLINE_BYTE_LENGTH = "\r\n".getBytes().length;

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
                    if(currBytes.length > bodyOffset ) {
                        currProcessingStream.write(currBytes, bodyOffset, currBytes.length - bodyOffset);
                    }
                    streamChunked.header(reqHeaders, channelingSocket);
                    eagerRead(channelingSocket, this::massageBody);
                    return;
                }
            }
            eagerRead(channelingSocket, this::massageHeader);
        } catch (Exception e) {
            error(channelingSocket, e);
        }
    }

    public void massageBody(ChannelingSocket channelingSocket) {
        int numRead = channelingSocket.getLastProcessedBytes();
        ByteBuffer readBuffer = channelingSocket.getReadBuffer();

        try {
            if (numRead > 0) {
                totalRead += numRead;
                readBuffer.flip();
                byte[] b = new byte[readBuffer.limit() - readBuffer.position()];
                readBuffer.get(b);
                currConsumedBytes = b;
                currProcessingStream.write(b);
                byte[] chunked = currProcessingStream.toByteArray();
                String[] chunkBodies = new String(chunked).split("\\r?\\n", 2);
                while(chunkBodies.length == 2) {
                    currChunkLength = HttpMessageHelper.hexToInt(chunkBodies[0]) + NEWLINE_BYTE_LENGTH;
                    byte[] chunkBody = chunkBodies[1].getBytes();

                    if (hasEnoughChunk(chunkBody, currChunkLength)) {
                        streamChunked.accept(BytesHelper.subBytes(chunkBody, 0, currChunkLength - NEWLINE_BYTE_LENGTH), channelingSocket);
                        currProcessingStream.reset();
                        if (chunkBody.length > currChunkLength) {
                            currProcessingStream.write(chunkBody, currChunkLength, chunkBody.length - currChunkLength);
                        }

                        chunked = currProcessingStream.toByteArray();
                        if(BytesHelper.equals(chunked, "\r\n0\r\n\r\n".getBytes())){
                            break;
                        }
                        chunkBodies = new String(chunked).split("\\r?\\n", 2);
                    } else {
                        break;
                    }
                }


//                if (responseType == HttpResponseType.TRANSFER_CHUNKED) {
//                    String[] chunkBodies = new String(firstBodyBytes).split("\\r?\\n", 2);
//                    currChunkLength = HttpMessageHelper.hexToInt(chunkBodies[0]) + "\r\n".getBytes().length;
//                    byte[] chunkBody = chunkBodies[1].getBytes();
//
//                    if (getEnoughChunk(chunkBody, currChunkLength)) {
//
//                    }
//
//
////                    int len = chunkBody.length;
////                    if (len > currChunkLength) {
////                        streamChunked.header(BytesHelper.subBytes(chunkBody, 0, currChunkLength), reqHeaders, channelingSocket);
////
////                        byte[] balanceBytes;
////                        while (true) {
////                            balanceBytes = BytesHelper.subBytes(chunkBody, currChunkLength, len);
////                            chunkBodies = new String(balanceBytes).split("\\r?\\n", 2);
////                            currChunkLength = HttpMessageHelper.hexToInt(chunkBodies[0]) + "\r\n".getBytes().length;
////                            chunkBody = chunkBodies[1].getBytes();
////                            len = chunkBody.length;
////                            if (len > currChunkLength) {
////                                streamChunked.accept(BytesHelper.subBytes(chunkBody, 0, currChunkLength), channelingSocket);
////                            } else {
////                                channelingSocket.withEagerRead(currChunkLength - len).then(this::readAndThen);
////                                break;
////                            }
////                        }
////
////                    } else {
////                        channelingSocket.withEagerRead(currChunkLength - chunkBody.length).then(this::readAndThen);
////                    }
//                } else {
//                    streamChunked.header(reqHeaders, channelingSocket);
//                    streamChunked.accept(firstBodyBytes, channelingSocket);
//                    response.reset();
//                    hasHeaderOut = true;
//                }
//                // Clear
//            } else if (hasHeaderOut) {
//                if (responseType == HttpResponseType.TRANSFER_CHUNKED) {
//                    String[] chunkBodies = new String(b).split("\\r?\\n", 2);
////                       currChunkLength = HttpMessageHelper.hexToInt(chunkBodies[0]);
//                    streamChunked.accept(chunkBodies[1].getBytes(), channelingSocket);
//                } else {
//                    streamChunked.accept(b, channelingSocket);
//                }
//            } else {
//                extractResponseAndEncodingType(response.toByteArray());
//                readBuffer.clear();
//                channelingSocket.withEagerRead(readBuffer).then(this::readAndThen);
//            }
//
            }

            tryFinalizez(channelingSocket);

        } catch (Exception e) {
            error(channelingSocket, e);
        }

    }


    private boolean hasEnoughChunk(byte[] chunkBody, int currChunkLength) {
        return chunkBody.length >= currChunkLength;
    }

    private String parseToString(byte[] consumedBytes) {
        return new String(consumedBytes, StandardCharsets.UTF_8);

    }

    private void extractResponseAndEncodingType(byte[] bytes)  {
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
//        String consumeMessage = parseToString(bytes);
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


    private void tryFinalizez(ChannelingSocket channelingSocket) {
        int len = currProcessingStream.size();
        if (len < 5) {
            eagerRead(channelingSocket, this::massageBody);
            return;
        }

        byte[] balanceBytes = currProcessingStream.toByteArray();

        byte[] last5Bytes = BytesHelper.subBytes(balanceBytes, len - 5);

        if (BytesHelper.equals(last5Bytes, "0\r\n\r\n".getBytes()) || BytesHelper.equals(last5Bytes, "0\n\n".getBytes(), 2)) {
            channelingSocket.noEagerRead();

//            if (responseType == HttpResponseType.TRANSFER_CHUNKED) {
//                String[] chunkBodies = new String(currConsumedBytes).split("\\r?\\n",2);
////                       currChunkLength = HttpMessageHelper.hexToInt(chunkBodies[0]);
//                streamChunked.last(chunkBodies[1].getBytes(), channelingSocket);
//            } else {
            streamChunked.last(currConsumedBytes, channelingSocket);
//            }

            channelingSocket.close(this::closeAndThen);
        } else {
            eagerRead(channelingSocket, this::massageBody);
        }
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
        this.streamChunked.error(e, channelingSocket);
        channelingSocket.close(this::closeAndThen);
    }


    @Override
    public void execute(HttpStreamRequestCallback callback) {
        this.streamChunked = callback;
        socket.withConnect(host, port).when((WhenConnectingStatus) connectingStatus -> connectingStatus).then(this::connectAndThen, this::error);
    }

    @Override
    public void execute(HttpResponseCallback result) {
        throw new UnsupportedOperationException("Stream Request un-support HttpResponse");
    }
}
