package com.github.taymindis.nio.channeling.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class HttpResponse {
    private static Logger log = LoggerFactory.getLogger(HttpResponse.class);
    private String headers;
    private byte[] rawBytes;
    private int bodyOffset;
    private HttpResponseType responseType;
    private ContentEncodingType contentEncodingType;
    private Integer code = null;
    private String statusText = null;
    private Map<String,String> headerMap = null;

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getBodyContent() {
        return this.getBodyContent(StandardCharsets.UTF_8);
    }

    public String getBodyContent(Charset charset) {
        if (contentEncodingType == ContentEncodingType.GZIP) {
            try {
                return decompress(getBodyBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Error while decompressing the GZIP", e);
                e.printStackTrace();
            }
        }

        return new String(getBodyBytes(), charset);
    }

    public byte[] getBodyBytes() {
        if (responseType == HttpResponseType.TRANSFER_CHUNKED) {
            return toChunkedBytes2();
        }
        return Arrays.copyOfRange(rawBytes, bodyOffset, rawBytes.length);
    }

    @Deprecated
    private byte[] toChunkedBytes() {
        StringBuilder clearedHexaResponse = new StringBuilder();
        String respBody = new String(rawBytes, StandardCharsets.UTF_8).substring(bodyOffset);

        String[] hexaAndContent = respBody.split("\\r?\\n", 2);

        long lengthOfContent;

        String hexa = hexaAndContent[0];
        String body = hexaAndContent[1];

        while ((lengthOfContent = Long.parseLong(hexa, 16)) > 0) {
            int subStrLen = Math.toIntExact(lengthOfContent);

            clearedHexaResponse.append(body, 0, subStrLen);
            hexaAndContent = body.substring(subStrLen).split("\\r?\\n", 3);
            hexa = hexaAndContent[1];
            body = hexaAndContent[2];
        }

//        clearedHexaResponse.flip();
//        byte[] rs = new byte[clearedHexaResponse.remaining()];
//        clearedHexaResponse.get(rs);
        return clearedHexaResponse.toString().getBytes();
    }


    private byte[] toChunkedBytes2() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(rawBytes.length);
        ByteBuffer hextBytes = ByteBuffer.allocate(128);
        ByteBuffer recleanByteBuff = ByteBuffer.allocate(rawBytes.length);
        byteBuffer.put(rawBytes);
        byteBuffer.flip().position(bodyOffset);

        char c;
        while (byteBuffer.hasRemaining()) {
            while ((c = (char) byteBuffer.get()) != '\r' && c != '\n') {
                hextBytes.put((byte) c);
            }

            if (c == '\r') {
                c =(char) byteBuffer.get();
            }

            if (c == '\n') {
                // Get Hexa
                hextBytes.flip();
                if(!hextBytes.hasRemaining()) {
                    hextBytes.compact();
                    continue;
//                    throw new IllegalStateException("Invalid Chunk Response Message, hexadecimal not found");
                }
                byte[] hexLen = new byte[hextBytes.remaining()];
                hextBytes.get(hexLen);
                long chunkedLen = Long.parseLong(new String(hexLen), 16);

                if(chunkedLen == 0) {
                    break;
                }

                while (chunkedLen-- > 0) {
                    recleanByteBuff.put(byteBuffer.get());
                }

                hextBytes.clear();
            } else {
                throw new IllegalStateException("Invalid Chunk Response Message");
            }
        }

        recleanByteBuff.flip();
        byte[] recleanBytes = new byte[recleanByteBuff.remaining()];
        recleanByteBuff.get(recleanBytes);
        return recleanBytes;


//        String[] hexaAndContent = respBody.split("\\r?\\n", 2);
//
//        long lengthOfContent;
//
//        String hexa = hexaAndContent[0];
//        String body = hexaAndContent[1];
//
//        while ((lengthOfContent = Long.parseLong(hexa, 16)) > 0) {
//            int subStrLen = Math.toIntExact(lengthOfContent);
//
//            clearedHexaResponse.append(body, 0, subStrLen);
//            hexaAndContent = body.substring(subStrLen).split("\\r?\\n", 3);
//            hexa = hexaAndContent[1];
//            body = hexaAndContent[2];
//        }
//        return clearedHexaResponse.toString().getBytes();
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public int getBodyOffset() {
        return bodyOffset;
    }

    public void setBodyOffset(int bodyOffset) {
        this.bodyOffset = bodyOffset;
    }

    public HttpResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(HttpResponseType responseType) {
        this.responseType = responseType;
    }

    public int getCode() {
        if(headerMap == null) {
            parseHeaders(this);
        }
        return code;
    }

    public String getStatusText() {
        if(headerMap == null) {
            parseHeaders(this);
        }
        return statusText;
    }

    public String getHeader(String key) {
        if(headerMap == null) {
            parseHeaders(this);
        }
        return headerMap.get(key);
    }

    public Map<String,String> getHeaderAsMap() {
        if(headerMap == null) {
            parseHeaders(this);
        }
        return headerMap;
    }

    private static void parseHeaders(HttpResponse response) {
        String headers = response.getHeaders();

        if(headers != null) {

            Map<String, String> headerMap = new HashMap();

            String[] keyValues = headers.split("\\r?\\n");

            if(keyValues.length < 2) {
                return;
            }

            String[] statusText = keyValues[0].split("\\s", 3);

            response.code = Integer.parseInt(statusText[1]);
            response.statusText = statusText[2];

            for (int i = 1, size = keyValues.length; i < size; i++) {
                String header = keyValues[i];
                String[] keyPair = header.split(":\\s?", 2);
                headerMap.putIfAbsent(keyPair[0], keyPair[1]);
            }
            response.headerMap = headerMap;
        }

    }

    public static boolean isCompressed(final byte[] compressed) {
        return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }

    public static String decompress(final byte[] compressed, Charset charset) throws IOException {
        final StringBuilder outStr = new StringBuilder();
        final CharBuffer outputBuffer = CharBuffer.allocate(1024);
        outputBuffer.clear();
        if ((compressed == null) || (compressed.length == 0)) {
            return "";
        }

        if (isCompressed(compressed)) {
            try (final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 final InputStreamReader inputStreamReader = new InputStreamReader(gis, charset)
            ) {
                while (inputStreamReader.read(outputBuffer) > 0) {
                    outputBuffer.flip();
                    char[] ca = new char[outputBuffer.limit() - outputBuffer.position()];
                    outputBuffer.get(ca);
                    outStr.append(ca);
                    if (!outputBuffer.hasRemaining()) {
                        outputBuffer.clear();
                    }
                }
            }
        } else {
            outStr.append(Arrays.toString(compressed));
        }
        return outStr.toString();
    }

    public void setContentEncodingType(ContentEncodingType contentEncodingType) {
        this.contentEncodingType = contentEncodingType;
    }
}
