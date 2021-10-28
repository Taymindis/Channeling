package com.github.taymindis.nio.channeling.http;

import java.util.HashMap;
import java.util.Map;

public class HttpResponseMessage {
    private Map<String, String> headerMap = null;
    private Object content=null;
    private Integer code = null;
    private String statusText = null;
    private final String httpVersion;
    /** for Streaming only **/
    private boolean done = false;

    public HttpResponseMessage(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public HttpResponseMessage() {
        this.httpVersion = "HTTP/1.1";
    }


    public Map<String, String> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, String> headerMap) {
        this.headerMap = headerMap;
    }
    public void addHeader(Map<String, String> headerMap) {
        if(this.headerMap == null) {
            this.headerMap = headerMap;
        } else {
            this.headerMap.putAll(headerMap);
        }
    }
    public void addHeader(String key, String value) {
        if(this.headerMap == null) {
            this.headerMap = new HashMap<>();
        }
        this.headerMap.put(key, value);
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    static String toResponse() {
        return null;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
