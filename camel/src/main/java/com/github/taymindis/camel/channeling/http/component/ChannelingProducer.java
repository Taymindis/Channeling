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

import com.github.taymindis.nio.channeling.Channeling;
import com.github.taymindis.nio.channeling.ChannelingBytes;
import com.github.taymindis.nio.channeling.ChannelingSocket;
import com.github.taymindis.nio.channeling.http.*;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import static com.github.taymindis.camel.channeling.http.component.ChannelingConsumer.*;

/**
 *
 */
public class ChannelingProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelingProducer.class);
    private final Channeling client;
    private static final String REDIRECTION_CODES = "301,302";
    private RedirectionSocket redirectionSocket;
    final private FluentProducerTemplate fluentProducerTemplate;
    final private String callBackRoute;
    final private Boolean hasAfterProxied;

    public ChannelingProducer(ChannelingEndpoint endpoint) {
        super(endpoint);
        this.client = endpoint.getEngine();
        this.callBackRoute = endpoint.getCallbackRoute();
        this.fluentProducerTemplate = callBackRoute == null ? null : endpoint.getCamelContext().createFluentProducerTemplate();
        this.hasAfterProxied = callBackRoute != null;

        redirectionSocket = (host, port, isSSL, camelExchange) -> {
            if (isSSL) {
                if (endpoint.getChannelingProxy() != null) {
                    return client.wrapProxySSL(endpoint.getChannelingProxy(), endpoint.getSslContext(), host, port, camelExchange);
                } else {
                    return client.wrapSSL(endpoint.getSslContext(), host, port, camelExchange);
                }
            }

            if (endpoint.getChannelingProxy() != null) {
                return client.wrapProxy(endpoint.getChannelingProxy(), camelExchange);
            } else {
                return client.wrap(camelExchange);
            }
        };
    }

    @Override
    public ChannelingEndpoint getEndpoint() {
        return (ChannelingEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) {
        try {
            ChannelingEndpoint endpoint = getEndpoint();
            ChannelingBinding binding = endpoint.getBinding();
            HttpRequestBuilder requestBuilder = binding.preparingRequest(endpoint, exchange);
            HttpRequest httpRequest;
            ChannelingSocket cs;
            URI httpUri = endpoint.getHttpUri();
            String host = httpUri.getHost();
            int port = httpUri.getPort();
            if (endpoint.isSSL()) {
                if (port < 0) {
                    port = 443;
                }
                if (endpoint.getChannelingProxy() != null) {
                    cs = client.wrapProxySSL(endpoint.getChannelingProxy(), endpoint.getSslContext(), host, port, exchange);
                } else {
                    cs = client.wrapSSL(endpoint.getSslContext(), host, port, exchange);
                }
                if (endpoint.isUseStreaming()) {
                    httpRequest = new HttpStreamRequest(
                            cs,
                            host,
                            port,
                            requestBuilder.toString(),
                            cs.getSSLMinimumInputBufferSize()
                    );

                    processStreamRequest(exchange, httpRequest, requestBuilder, binding);
                } else if (endpoint.isFollowRedirect()) {
                    httpRequest = new HttpRedirectableRequest(
                            cs,
                            host, port,
                            requestBuilder.toString(),
                            cs.getSSLMinimumInputBufferSize(),
                            redirectionSocket);
                    processRequest(exchange, httpRequest, requestBuilder, binding);
                } else {
                    httpRequest = new HttpSingleRequest(
                            cs,
                            host, port,
                            requestBuilder.toString(),
                            cs.getSSLMinimumInputBufferSize()
                    );
                    processRequest(exchange, httpRequest, requestBuilder, binding);
                }
            } else {
                if (port < 0) {
                    port = 80;
                }
                if (endpoint.getChannelingProxy() != null) {
                    cs = client.wrapProxy(endpoint.getChannelingProxy(), exchange);
                } else {
                    cs = client.wrap(exchange);
                }

                LOG.debug("Executing request :::{}", requestBuilder.toString());


                if (endpoint.isUseStreaming()) {
                    httpRequest = new HttpStreamRequest(
                            cs,
                            host,
                            port,
                            requestBuilder.toString(),
                            1024
                    );

                    processStreamRequest(exchange, httpRequest, requestBuilder, binding);
                } else if (endpoint.isFollowRedirect()) {
                    httpRequest = new HttpRedirectableRequest(
                            cs,
                            host, port,
                            requestBuilder.toString(),
                            1024,
                            redirectionSocket);
                    processRequest(exchange, httpRequest, requestBuilder, binding);
                } else {
                    httpRequest = new HttpSingleRequest(
                            cs,
                            host, port,
                            requestBuilder.toString(),
                            1024
                    );
                    processRequest(exchange, httpRequest, requestBuilder, binding);
                }
            }


        } catch (Exception e) {
            exchange.setException(e);
            LOG.error(e.getMessage(), e);
        }
    }

    private void processStreamRequest(Exchange exchange, HttpRequest streamRequest, HttpRequestBuilder requestBuilder,
                                      ChannelingBinding binding)  {
        exchange.setProperty(CHANNELING_REVERSE_PROXIED, Boolean.TRUE);
        exchange.getMessage().setHeader(ChannelingConstant.CH_LAST_STREAM_CHUNKED, false);

        streamRequest.execute(new HttpStreamRequestCallback() {
            @Override
            public void headerAccept(byte[] chunked, int offset, int length, ChannelingSocket socket) throws Exception {
                Exchange $exchange = (Exchange) socket.getContext();
                Map<String, String> headerMap = HttpMessageHelper.massageHeaderContentToHeaderMap(
                        new String(chunked, offset, length)
                        , false);
                $exchange.getMessage().getHeaders().putAll(headerMap);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Response Headers: " + headerMap.toString());
                }
                tryProxyHeaderResponse(exchange);
            }

            @Override
            public void accept(byte[] chunked, int offset, int length, ChannelingSocket socket) {
                exchangeProxyStream(chunked, offset, length, socket, false);
            }

            @Override
            public void last(byte[] chunked, int offset, int length, ChannelingSocket socket) {
                exchangeProxyStream(chunked, offset, length,  socket, true);
            }

            @Override
            public void error(Exception e, ChannelingSocket socket) {
                exchange.getMessage().setHeader(ChannelingConstant.CH_LAST_STREAM_CHUNKED, true);
                doFailure(e, exchange, binding, requestBuilder);
            }
        });
    }

    private void exchangeProxyStream(byte[] chunked, int offset, int length, ChannelingSocket socket, boolean isLastStream) {
        Exchange $exchange = (Exchange) socket.getContext();
        $exchange.getMessage().setBody(new ChannelingBytes(chunked, offset, length), ChannelingBytes.class);
        $exchange.getMessage().setHeader(ChannelingConstant.CH_LAST_STREAM_CHUNKED, isLastStream);
        final Exchange finalExchange;
        if (hasAfterProxied) {
            finalExchange = fluentProducerTemplate
                    .to(callBackRoute)
                    .withExchange($exchange)
                    .send();
        } else {
            finalExchange = $exchange;
        }
        tryProxyStreamResponse(finalExchange, isLastStream);
    }

    private void processRequest(Exchange exchange, HttpRequest httpRequest, HttpRequestBuilder requestBuilder,
                                ChannelingBinding binding)  {
        exchange.setProperty(CHANNELING_REVERSE_PROXIED, Boolean.TRUE);

        httpRequest.execute(new HttpSingleRequestCallback() {
            @Override
            public void accept(HttpResponse httpResponse, Object attachment) {
                Exchange $exchange = (Exchange) attachment;
                try {
                    String[] headers = httpResponse.getHeaders().split("\\r?\\n");

                    if (headers.length < 2) {
                        throw new Exception("Invalid Response contain " + Arrays.toString(headers));
                    }

                    String[] statusText = headers[0].split("\\s", 3);

                    // TODO  whether is success or not dont touch the response
                    if (!isSuccess(statusText)) {
                        LOG.error(headers[0],
                                new IOException("Unexpected code " + Arrays.toString(statusText)));
                        if (getEndpoint().isThrowExceptionOnFailure()) {
                            $exchange.setException(binding.catchChannelingCallFailedException(
                                    getEndpoint(),
                                    $exchange,
                                    requestBuilder.getPath(),
                                    httpResponse,
                                    httpResponse.getBodyContent()));
                        }
                    }

                    binding.populateResponseHeaders($exchange, statusText, httpResponse,
                            getEndpoint().getHeaderFilterStrategy());
                    binding.populateResponseBody($exchange, httpResponse);

                    /** TODO CallbackRoute **/
                    final Exchange finalExchange;
                    if (hasAfterProxied) {
                        finalExchange = fluentProducerTemplate
                                .to(callBackRoute)
                                .withExchange($exchange)
                                .send();
                    } else {
                        finalExchange = $exchange;
                    }

                    tryProxyResponse(finalExchange);

                } catch (Exception e) {
                    doFailure(e, $exchange, binding, requestBuilder);
                }

            }

            @Override
            public void error(Exception e, ChannelingSocket socket) {
                doFailure(e, (Exchange) socket.getContext(), binding, requestBuilder);
            }
        });
    }

    private void doFailure(Exception e, Exchange exchange, ChannelingBinding binding, HttpRequestBuilder requestBuilder) {
        try {
            if (getEndpoint().isThrowExceptionOnFailure()) {
                exchange.setException(binding.catchChannelingCallFailedException(
                        getEndpoint(),
                        exchange,
                        requestBuilder.getPath(),
                        e.getMessage()));
            } else {
                exchange.setException(e);
            }
        } finally {
            tryProxyResponse(exchange);
        }
    }

    private void tryProxyResponse(Exchange exchange) {
        ResponseCallback callback = exchange.getProperty(CHANNELING_CALLBACK_KEY, ResponseCallback.class);
        ChannelingConsumer channelingConsumer = exchange.getProperty(CHANNELING_CONSUMER_KEY, ChannelingConsumer.class);

        if (callback != null && channelingConsumer != null) {
            channelingConsumer.finalizeResponse(callback, exchange);
        }
    }

    private void tryProxyHeaderResponse(Exchange exchange) {
        ResponseCallback callback = exchange.getProperty(CHANNELING_CALLBACK_KEY, ResponseCallback.class);
        ChannelingConsumer channelingConsumer = exchange.getProperty(CHANNELING_CONSUMER_KEY, ChannelingConsumer.class);

        if (callback != null && channelingConsumer != null) {
            channelingConsumer.streamHeadersResponse(callback, exchange);
        }
    }

    private void tryProxyStreamResponse(Exchange exchange, boolean isLastStream) {
        ResponseCallback callback = exchange.getProperty(CHANNELING_CALLBACK_KEY, ResponseCallback.class);
        ChannelingConsumer channelingConsumer = exchange.getProperty(CHANNELING_CONSUMER_KEY, ChannelingConsumer.class);

        if (callback != null && channelingConsumer != null) {
            channelingConsumer.streamChunkedResponse(callback, exchange,isLastStream);
        }
    }

    private boolean isSuccess(String[] statusText) {
        int code = Integer.parseInt(statusText[1]);
        return code >= 200 && code < 300;
    }

}
