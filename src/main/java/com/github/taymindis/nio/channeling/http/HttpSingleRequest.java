package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.BytesHelper;
import com.github.taymindis.nio.channeling.ChannelingSocket;
import com.github.taymindis.nio.channeling.WhenConnectingStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class HttpSingleRequest implements HttpRequest {
    private int totalRead = 0, totalWrite, requiredLength, bodyOffset;
    private ByteBuffer readBuffer;
    private String messageToSend;
    private String host;
    private int port;
    private ChannellingBaos response;
    private ChannelingSocket socket;
    private HttpSingleRequestCallback result;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
    private HttpResponse httpResponse;
    private final boolean enableGzipDecompression;
    private RedirectionSocket redirectionSocket;
    private String prevRedirectionLoc;

    public HttpSingleRequest(ChannelingSocket socket,
                             String host, int port,
                             String messageToSend) {
        this(socket, host, port, messageToSend, 1024);
    }

    public HttpSingleRequest(ChannelingSocket socket,
                             String host,
                             int port,
                             String messageToSend,
                             int minInputBufferSize) {
        this(socket, host, port, messageToSend, minInputBufferSize, false);
    }

    public HttpSingleRequest(ChannelingSocket socket,
                             String host,
                             int port,
                             String messageToSend,
                             int minInputBufferSize,
                             boolean enableGzipDecompression) {
        this(socket, host, port, messageToSend, minInputBufferSize, enableGzipDecompression, null);

    }

    protected HttpSingleRequest(ChannelingSocket socket,
                                String host,
                                int port,
                                String messageToSend,
                                int minInputBufferSize,
                                boolean enableGzipDecompression,
                                RedirectionSocket redirectionSocket) {
        this.readBuffer = ByteBuffer.allocate(socket.isSSL() ? socket.getSSLMinimumInputBufferSize() : minInputBufferSize);
        this.response = new ChannellingBaos();
        this.messageToSend = messageToSend;
        this.socket = socket;
        this.host = host;
        this.port = port;
        this.responseType = HttpResponseType.PENDING;
        this.contentEncodingType = ContentEncodingType.PENDING;
        this.bodyOffset = -1;
        this.enableGzipDecompression = enableGzipDecompression;
        this.redirectionSocket = redirectionSocket;
        this.prevRedirectionLoc = null;
    }

    public void execute(HttpSingleRequestCallback callback) {
        this.result = callback;
        socket.withConnect(host, port).when((WhenConnectingStatus) connectingStatus -> connectingStatus).then(this::connectAndThen, this::error);
    }

    @Override
    public void execute(HttpStreamRequestCallback callback) {
        throw new UnsupportedOperationException("HttpRequest un-support StreamResponse");
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
            httpResponse = new HttpResponse();
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
                response.write(b);
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
                    String contentLength = lowCaseHeaders.substring(lowCaseHeaders.indexOf("content-length:") + "content-length:".length()).split("\\r?\\n", 2)[0];
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
                requiredLength += bodyOffset;
            } else if ((bodyOffset = consumeMessage.indexOf("\n\n")) > 0) {
                bodyOffset += 2;
                requiredLength += bodyOffset;
            }
        }
    }

    private void transferEncodingResponse(ChannelingSocket channelingSocket) throws Exception {
        byte[] consumedBuffers = response.toByteArray();
        int len = response.size();

//        String last5Chars = new String(Arrays.copyOfRange(totalConsumedBytes, len - 5, len), StandardCharsets.UTF_8);
        if (len>=7 && BytesHelper.equals(consumedBuffers, "\r\n0\r\n\r\n".getBytes(), len - 7)) {
            channelingSocket.noEagerRead();
            httpResponse.setRawBytes(consumedBuffers);
            httpResponse.setBodyOffset(bodyOffset);
            updateResponseType(httpResponse);

            if(redirectionSocket != null) {
                Map<String, String> headers = httpResponse.getHeaderAsMap();
                if (headers.containsKey("Location")) {
                    String location = headers.get("Location");
                    if(!location.equals(prevRedirectionLoc)) {
                        channelingSocket.close(cs -> {});
                        redirectingRequest(location, channelingSocket);
                        execute(result);
                        return;
                    }
                }
            }
            result.accept(httpResponse, channelingSocket.getContext());
            channelingSocket.close(this::closeAndThen);
        } else {
            eagerRead(channelingSocket);
        }
    }

//  This is to strips all the chunked len metrics and make it to real content
//    private void transferEncodingResponse(ChannelingSocket channelingSocket) throws Exception {
//        int len = response.size();
//
//        if (len >= 7) {
//            byte[] buffer = response.toByteArray();
//            byte[] last7Bytes = BytesHelper.subBytes(buffer, len-7, len);
////        String last5Chars = new String(Arrays.copyOfRange(totalConsumedBytes, len - 5, len), StandardCharsets.UTF_8);
//            if (BytesHelper.equals(last7Bytes, "\r\n0\r\n\r\n".getBytes())) {
//                channelingSocket.noEagerRead();
//                ByteArrayOutputStream respStream = new ByteArrayOutputStream();
//                int i=bodyOffset+1;
//                int nextOffSet = bodyOffset;
//                for(;i<len-1;i++) {
//                    if(buffer[i]=='\r' && buffer[i+1] == '\n') {
//                        String hex = new String(BytesHelper.subBytes(buffer, nextOffSet, i));
//                        int chunklen = HttpMessageHelper.hexToInt(hex);
//                        if(chunklen == 0){
//                            break;
//                        }
//                        respStream.write(buffer, i+2, chunklen);
//                        nextOffSet = i = i + 2 + chunklen + 2;
//                    }
//                }
//
//                httpResponse.setRawBytes(respStream.toByteArray());
//                httpResponse.setBodyOffset(bodyOffset);
//                updateResponseType(httpResponse);
//
//                if (redirectionSocket != null) {
//                    Map<String, String> headers = httpResponse.getHeaderAsMap();
//                    if (headers.containsKey("Location")) {
//                        String location = headers.get("Location");
//                        if (!location.equals(prevRedirectionLoc)) {
//                            channelingSocket.close(cs -> {
//                            });
//                            redirectingRequest(location, channelingSocket);
//                            execute(result);
//                            return;
//                        }
//                    }
//                }
//                result.accept(httpResponse, channelingSocket.getContext());
//                channelingSocket.close(this::closeAndThen);
//                return;
//            }
//        }
//        eagerRead(channelingSocket);
//
//    }

    private void redirectingRequest(String location, ChannelingSocket prevSocket) throws Exception {
        URI uri = new URI(location);
        host = uri.getHost();
        boolean isSSL = uri.getScheme().startsWith("https");

        port = uri.getPort();

        if(port < 0) {
            port = isSSL ? 443 : 80;
        }

        this.socket = redirectionSocket.request(host, port, isSSL, prevSocket);
        if(socket.isSSL()) {
            this.readBuffer = ByteBuffer.allocate(socket.getSSLMinimumInputBufferSize());
        } else {
            this.readBuffer.clear();
        }
        this.response.close();
        this.response = new ChannellingBaos();
        this.responseType = HttpResponseType.PENDING;
        this.contentEncodingType = ContentEncodingType.PENDING;
        this.bodyOffset = -1;
        this.prevRedirectionLoc = location;

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", host + ":"+port);
        requestBuilder.setPath( uri.getPath() + (uri.getRawQuery()!=null? "?"+uri.getRawQuery():""));


        this.messageToSend = requestBuilder.toString();
    }

    private void updateResponseType(HttpResponse httpResponse) throws IOException {
        if (contentEncodingType == ContentEncodingType.GZIP && enableGzipDecompression) {
            httpResponse.setContentEncodingType(ContentEncodingType.GZIP);
        } else {
            httpResponse.setContentEncodingType(contentEncodingType);
        }
    }

    private void contentLengthResponse(ChannelingSocket channelingSocket) throws Exception {
        if (totalRead >= requiredLength) {
            channelingSocket.noEagerRead();
            httpResponse.setRawBytes(response.toByteArray());
            httpResponse.setBodyOffset(bodyOffset);
            updateResponseType(httpResponse);

            if(redirectionSocket != null) {
                Map<String, String> headers = httpResponse.getHeaderAsMap();
                if (headers.containsKey("Location")) {
                    String location = headers.get("Location");
                    if(!location.equals(prevRedirectionLoc)) {
                        channelingSocket.close(cs -> {});
                        redirectingRequest(location, channelingSocket);
                        execute(result);
                        return;
                    }
                }
            }

            result.accept(httpResponse, channelingSocket.getContext());
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

    private void error(ChannelingSocket channelingSocket, Exception e) {
        result.error(e, channelingSocket);
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
