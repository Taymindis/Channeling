package com.github.taymindis.nio.channeling.http;

import java.net.SocketAddress;
import java.util.Map;

public class HttpRequestMessage {
    private final byte[] rawBytes;
    private SocketAddress remoteAddress;
    private Map<String, String> headerMap = null;
    private String body;
    private String method;
    private String path;

    public HttpRequestMessage(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public HttpRequestMessage() {
        this.rawBytes = null;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public Map<String, String> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, String> headerMap) {
        this.headerMap = headerMap;
    }
    public void addHeader(String key, String value) {
        this.headerMap.put(key, value);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
