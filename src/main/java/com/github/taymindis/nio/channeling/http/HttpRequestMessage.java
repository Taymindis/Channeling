package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingSocket;

import java.net.SocketAddress;
import java.util.Map;

public class HttpRequestMessage {
    private SocketAddress remoteAddress;
    private Map<String, String> headerMap = null;
    private String body;
    private String method;
    private String path;
    private String httpVersion;
    private ChannelingSocket clientSocket;

    public HttpRequestMessage(ChannelingSocket sc) {
        this.clientSocket = sc;
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

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    // For client later use
    public ChannelingSocket getClientSocket() {
        return clientSocket;
    }

    public void setClientSocket(ChannelingSocket $sc) {
        this.clientSocket = $sc;
    }

    public Object getContext() {
        return clientSocket.getContext();
    }

    public void setContext(Object context) {
        this.clientSocket.setContext(context);
    }
}
