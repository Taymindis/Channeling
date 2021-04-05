package com.github.taymindis.nio.channeling.http;

enum ContentEncodingType {
    PENDING,
    GZIP,
    OTHER,
    COMPRESS,// Not support YET
    DEFLATE,// Not support YET
    BR // Not support
}