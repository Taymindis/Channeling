package com.github.taymindis.camel.channeling.http.component;

import com.github.taymindis.nio.channeling.http.HttpRequestBuilder;
import com.github.taymindis.nio.channeling.http.HttpRequestMessage;
import com.github.taymindis.nio.channeling.http.HttpResponse;
import com.github.taymindis.nio.channeling.http.HttpResponseMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;

import java.io.IOException;
import java.util.Map;

public interface ChannelingBinding {

    /**
     * Prepare from channeling request to camel request Message
     * @param requestMessage Consumer request
     * @param exchange Camel Exchange
     * @return New Message
     */
    Message toCamelMessage(HttpRequestMessage requestMessage, Exchange exchange);

    /**
     * Prepare from channeling request to camel request header
     */
    void populateCamelHeaders(HttpRequestMessage requestMessage, Map<String, Object> headersMap, Exchange exchange);
    /**
     * Prepares a {@link HttpRequestBuilder} by setting up the required host, proxy details specified on the endpoint configuration
     */
    HttpRequestBuilder preparingRequest(ChannelingEndpoint endpoint, Exchange exchange) throws IOException;

    /**
     * Populates response headers on the exchange from the {@link HttpResponse} using the supplied
     * {@link HeaderFilterStrategy}
     */
    void populateResponseHeaders(Exchange exchange, String[] statusText, HttpResponse response, HeaderFilterStrategy strategy);

    /**
     * Processes the received {@link HttpResponse}
     */
    void populateResponseBody(Exchange exchange, HttpResponse httpResponse) throws IOException;

    /**
     * Prepares a {@link HttpRequestBuilder} customize body payload if not meet the payload type
     */
    void customBodyPayload(String method, Exchange exchange, HttpRequestBuilder builder) throws IOException;

    /**
     * Handles failures returned in the {@link HttpRequestBuilder}
     */
    Exception catchChannelingCallFailedException(
            ChannelingEndpoint endpoint, Exchange exchange, String url, HttpResponse response, String responseBody);

    /**
     * Handles Connectivity failures returned in the {@link HttpRequestBuilder}
     */
    Exception catchChannelingCallFailedException(
            ChannelingEndpoint endpoint, Exchange exchange, String url, String errorMessage);

    HttpResponseMessage toHttpResponseHeader(Message message, HeaderFilterStrategy headerFilterStrategy);

    /**
     *  @param message to filter response Header
     * @param headerFilterStrategy headerFilterStrategy
     */
    void filterResponseHeader(Message message, HeaderFilterStrategy headerFilterStrategy);
}
