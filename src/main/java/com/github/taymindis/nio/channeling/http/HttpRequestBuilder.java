package com.github.taymindis.nio.channeling.http;

public class HttpRequestBuilder {

    private String method = "";
    private String path = "";
    private String args = "";
    private StringBuilder headersBuilder = new StringBuilder();
    private String body = null;


    public HttpRequestBuilder() {

    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setArgs(String args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        if (!args.startsWith("?")) {
            this.args = '?' + args;
        } else {
            this.args = args;
        }
    }

    public void addHeader(String key, String value) {
        if (value.contains("\r\n")) {
            value = value.replace("\r\n", "");
        } else if (value.contains("\n")) {
            value = value.replace("\n", "");
        }
        this.headersBuilder.append(key).append(": ").append(value).append("\r\n");
    }

    public void setBody(String body) {
        this.body = body;
    }


    @Override
    public String toString() {
        StringBuilder buildRequest = new StringBuilder();
        if (method == null) {
            method = "GET";
        }

        if (args == null) {
            args = "";
        }

        buildRequest
                .append(method.toUpperCase()).append(' ')
                .append(path).append(args).append(' ')
                .append("HTTP/1.1\r\n")
                .append(headersBuilder.toString()).append("\r\n")
        ;

        if (body != null) {
            buildRequest.append(body);
        }

        return buildRequest.toString();
    }


    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getArgs() {
        return args;
    }

    public StringBuilder getHeadersBuilder() {
        return headersBuilder;
    }

    public String getBody() {
        return body;
    }
}
