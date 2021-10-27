package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingByteWriter;
import com.github.taymindis.nio.channeling.ChannelingBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HttpRequestParser {
    private String headerContent;
    private int bodyOffset = -1, expectedLen = -1;
    private boolean doneParsed;
    private ChannelingByteWriter byteWriter;

    private static final int DEFAULT_SIZE = 2048;

    public HttpRequestParser() {
        this(DEFAULT_SIZE);
    }

    public HttpRequestParser(int initSize) {
        doneParsed = false;
        byteWriter = new ChannelingByteWriter(initSize);
    }

    public String getHeaderContent() {
        return headerContent;
    }

    public void setHeaderContent(String headerContent) {
        this.headerContent = headerContent;
    }

//    public String getBody(Charset charset) throws IOException {
//        if (body == null && bodyOffset > 0) {
//            ChannelingBytes bytes = byteWriter.toChannelingBytes(bodyOffset);
//            body = new String(bytes.getBuff(), bytes.getOffset(), bytes.getLength(), charset);
//        }
//        return body;
//    }
//
//    public String getBody() throws IOException {
//        return getBody(StandardCharsets.UTF_8);
//    }

    public int getBodyOffset() {
        return bodyOffset;
    }

    public void setBodyOffset(int bodyOffset) {
        this.bodyOffset = bodyOffset;
    }

    public int getExpectedLen() {
        return expectedLen;
    }

    public void setExpectedLen(int expectedLen) {
        this.expectedLen = expectedLen;
    }

    public boolean isDoneParsed() {
        return doneParsed;
    }

    public void setDoneParsed(boolean doneParsed) {
        this.doneParsed = doneParsed;
    }

    public ChannelingBytes getRawBytes() throws IOException {
        return byteWriter.toChannelingBytes();
    }

    public void writeBytes(ByteBuffer buffer) throws IOException {
        byteWriter.write(buffer);
    }

    public ChannelingByteWriter getByteWriter() {
        return byteWriter;
    }
}
