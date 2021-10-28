/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.taymindis.camel.channeling.http.component;

import com.github.taymindis.nio.channeling.http.HttpResponse;
import org.apache.camel.CamelException;
import org.apache.camel.util.URISupport;

public class ChannelingCallFailedException extends CamelException {
    private static final long serialVersionUID = -6731281444593522633L;
    private final String url;
    private final String headers;
    private final String body;

    public ChannelingCallFailedException(String url,
                                         HttpResponse httpResponse) {
        // sanitize url so we do not show sensitive information such as passwords
        super(new StringBuilder("HTTP Call failed invoking ").append(
                URISupport.sanitizeUri(url)).append(
                        ", with header line: ")
                .append(httpResponse.getHeaders()).append(", message: ").append(httpResponse.getBodyContent()).toString());
        this.url = URISupport.sanitizeUri(url);
        this.headers = httpResponse.getHeaders();
        this.body = httpResponse.getBodyContent();
    }
    public ChannelingCallFailedException(String url, String responseBody) {
        // sanitize url so we do not show sensitive information such as passwords
        super(new StringBuilder("HTTP Call failed invoking ").append(
                URISupport.sanitizeUri(url))
                .append(", Error Cause: ").append(responseBody)
                .toString());
        this.url = URISupport.sanitizeUri(url);
        this.headers = null;
        this.body = responseBody;
    }

    public String getUrl() {
        return url;
    }

    public String getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
