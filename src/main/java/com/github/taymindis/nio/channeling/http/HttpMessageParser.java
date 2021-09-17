package com.github.taymindis.nio.channeling.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HttpMessageParser {
    private String headerContent;
    private int bodyOffset = -1;

    private int currLen = 0, expectedLen = -1;
    private boolean doneParsed;
    private byte[] rawBytes;
    private String body;

    public HttpMessageParser() {
        doneParsed = false;
        rawBytes = null;
        body = null;
    }

    public String getHeaderContent() {
        return headerContent;
    }

    public void setHeaderContent(String headerContent) {
        this.headerContent = headerContent;
    }

    public String getBody(Charset charset) {
        if(body == null && bodyOffset > 0) {
           body = new String(rawBytes, charset).substring(bodyOffset);
        }
        return body;
    }
    public String getBody() {
       return getBody(StandardCharsets.UTF_8);
    }

    public int getBodyOffset() {
        return bodyOffset;
    }

    public void setBodyOffset(int bodyOffset) {
        this.bodyOffset = bodyOffset;
    }

    public int getCurrLen() {
        return currLen;
    }

    public int getExpectedLen() {
        return expectedLen;
    }

    public void setExpectedLen(int expectedLen) {
        this.expectedLen = expectedLen;
    }

    public void fillCurrLen(int length) {
        currLen += length;
    }

    public boolean isDoneParsed() {
        return doneParsed;
    }

    public void setDoneParsed(boolean doneParsed) {
        this.doneParsed = doneParsed;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

}
