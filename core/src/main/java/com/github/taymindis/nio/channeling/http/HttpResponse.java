package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.github.taymindis.nio.channeling.http.HttpMessageHelper.decompress;


public class HttpResponse {
    private static Logger log = LoggerFactory.getLogger(HttpResponse.class);
    private String headers;
    private ChannelingBytes rawBytes;
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
        ChannelingBytes bodyBytes = getBodyBytes();

        if (contentEncodingType == ContentEncodingType.GZIP) {
            try {
                return decompress(bodyBytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Error while decompressing the GZIP", e);
                e.printStackTrace();
            }
        }

        return new String(bodyBytes.getBuff(), bodyBytes.getOffset(), bodyBytes.getLength(), charset);
    }

    public ChannelingBytes getBodyBytes() {
        if (responseType == HttpResponseType.TRANSFER_CHUNKED) {
            return toChunkedBytes2();
        }
        return rawBytes; // Arrays.copyOfRange(rawBytes.getBuff(), bodyOffset, rawBytes.getLength());
    }

    @Deprecated
    private byte[] toChunkedBytes() {
        StringBuilder clearedHexaResponse = new StringBuilder();
        String respBody = new String(rawBytes.getBuff(), rawBytes.getOffset(), rawBytes.getLength(), StandardCharsets.UTF_8).substring(bodyOffset);

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


    private ChannelingBytes toChunkedBytes2() {
        ByteBuffer byteBuffer = ByteBuffer.wrap(rawBytes.getBuff(), rawBytes.getOffset(), rawBytes.getLength());  // ByteBuffer.allocate(rawBytes.getLength());
        ByteBuffer hextBytes = ByteBuffer.allocate(128);
        ByteBuffer recleanByteBuff = ByteBuffer.allocate(rawBytes.getLength());
//        byteBuffer.put(rawBytes.getBuff(), rawBytes.getOffset(), rawBytes.getLength());
        byteBuffer.position(bodyOffset);

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
//        byte[] recleanBytes = new byte[recleanByteBuff.remaining()];
//        recleanByteBuff.get(recleanBytes);
        return new ChannelingBytes(recleanByteBuff.array(),
                recleanByteBuff.position(), recleanByteBuff.remaining());


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

    public ChannelingBytes getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(ChannelingBytes rawBytes) {
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

            if(statusText.length == 3) {
                response.code = Integer.parseInt(statusText[1]);
                response.statusText = statusText[2];
            } else {
                response.code = 200;
                response.statusText = "OK";
            }

            for (int i = 1, size = keyValues.length; i < size; i++) {
                String header = keyValues[i];
                String[] keyPair = header.split(":\\s?", 2);
                headerMap.putIfAbsent(keyPair[0], keyPair[1]);
            }
            response.headerMap = headerMap;
        }

    }

    public void setContentEncodingType(ContentEncodingType contentEncodingType) {
        this.contentEncodingType = contentEncodingType;
    }

    public ContentEncodingType getContentEncodingType() {
        return contentEncodingType;
    }
}
