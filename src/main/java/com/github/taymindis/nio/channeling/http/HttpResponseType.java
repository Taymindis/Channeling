package com.github.taymindis.nio.channeling.http;

public enum HttpResponseType {
    PENDING,
    TRANSFER_CHUNKED,
    CONTENT_LENGTH,
    PARTIAL_CONTENT // Not supporting yet
}
