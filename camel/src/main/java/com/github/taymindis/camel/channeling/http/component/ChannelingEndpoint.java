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
import com.github.taymindis.nio.channeling.ChannelingPlugin;
import com.github.taymindis.nio.channeling.ChannelingProxy;
import org.apache.camel.*;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

import javax.net.ssl.SSLContext;
import java.net.CookieHandler;
import java.net.URI;
import java.util.List;

/**
 * Call external HTTP services using <a href="https://github.com/taymindis/channeling">Channeling Client</a>.
 */
@UriEndpoint(firstVersion = "3.11.1", scheme = "channeling", title = "Channeling Client (channeling)", syntax = "channeling:httpUri",
        producerOnly = true, category = {Category.HTTP}, lenientProperties = true)
public class ChannelingEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {

    private Channeling engine = null;

    @UriPath
    @Metadata(required = true)
    private URI httpUri;
    @UriParam(defaultValue = "false")
    private boolean bridgeEndpoint;
//    @UriParam
//    private boolean preserveHost;
    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure;
    @UriParam
    private Integer maxRequest = -1;
    @UriParam
    private Integer maxRequestPerHost = -1;
    @UriParam
    private Integer worker = 1;

    @UriParam
    private Integer numSSLWorker = 1;

    @UriParam(defaultValue = "false")
    private boolean shutdownImmediately;

    @UriParam(defaultValue = "false")
    private boolean followRedirect;

    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();
    @UriParam(label = "advanced")
    private ChannelingBinding binding;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "advanced")
    private List<ChannelingPlugin> plugins;
    @UriParam(label = "advanced")
    private ChannelingProxy channelingProxy;

    @UriParam(label = "producer", defaultValue = "false")
    private boolean connectionClose;
    @UriParam(label = "producer", defaultValue = "3000")
    private long connectionTimeout;
    @UriParam(label = "producer", defaultValue = "15000")
    private long readTimeout;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;


    @UriParam(
            label = "producer",
            defaultValue = "false",
            description = "If true, the producer will use streaming out to raw bytes " +
                    "into message body without waiting the whole response, CH_CONTEXT, " +
                    "CH_LAST_STREAM will be given in exchange, if true, FOLLOW_REDIRECT will be ignore"
    )
    private boolean useStreaming;

    @UriParam(
            label = "producer", description = "For Non block callback route"
    )
    private String callbackRoute = null;

    @UriParam(label = "consumer", name = "vHost")
    private String vHost = null;

    private SSLContext sslContext;
    private boolean isSSL;

    @UriParam(label = "consumer", name = "timezone")
    private String timezone;


    @UriParam(label = "consumer", defaultValue = "true")
    private boolean readBody;

    public ChannelingEndpoint(String endpointUri, ChannelingHttpComponent component, URI httpUri) {
        super(endpointUri, component);
        this.httpUri = httpUri;
    }

    @Override
    public ChannelingHttpComponent getComponent() {
        return (ChannelingHttpComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(engine, "Channeling client", this);
        ObjectHelper.notNull(httpUri, "HttpUri", this);
        ObjectHelper.notNull(binding, "ChannelingBinding", this);

        return new ChannelingProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new ChannelingConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the HttpProducer
        return true;
    }

    public Channeling getEngine() {
        return engine;
    }

    public void setEngine(Channeling engine) {
        this.engine = engine;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * The URI to use such as http://hostname:port/path
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public ChannelingBinding getBinding() {
        return binding;
    }

    /**
     * To use a custom {@link ChannelingBinding} which allows to control how to bind between Channeling and Camel.
     */
    public void setBinding(ChannelingBinding binding) {
        this.binding = binding;
    }

    public Integer getMaxRequest() {
        return maxRequest;
    }

    /**
     * Total Max Request can call for the endpoint
     */
    public void setMaxRequest(Integer maxRequest) {
        this.maxRequest = maxRequest;
    }

    public Integer getMaxRequestPerHost() {
        return maxRequestPerHost;
    }

    /**
     * Max Request can call per host for the endpoint
     */
    public void setMaxRequestPerHost(Integer maxRequestPerHost) {
        this.maxRequestPerHost = maxRequestPerHost;
    }


    public Integer getWorker() {
        return worker;
    }

    /**
     * Set how many worker for consuming the event
     */
    public void setWorker(Integer worker) {
        this.worker = worker;
    }


    public Integer getNumSSLWorker() {
        return numSSLWorker;
    }

    /**
     * Set how many SSL worker for handshake event
     */
    public void setNumSSLWorker(Integer numSSLWorker) {
        this.numSSLWorker = numSSLWorker;
    }

    public List<ChannelingPlugin> getPlugins() {
        return plugins;
    }

    /**
     * Extra Plugin filter when event consumed
     */
    public void setPlugins(List<ChannelingPlugin> plugins) {
        this.plugins = plugins;
    }

    public ChannelingProxy getChannelingProxy() {
        return channelingProxy;
    }

    /**
     * For proxy Nio, only support HTTP/HTTPS proxy
     */
    public void setChannelingProxy(ChannelingProxy channelingProxy) {
        this.channelingProxy = channelingProxy;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * connection timeout in MS
     */
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    /**
     * read timeout in MS
     */
    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, then the Exchange.HTTP_URI header is ignored, and use the endpoint's URI for request. You
     * may also set the throwExceptionOnFailure to be false to let the ChannelingProducer send all the fault response back.
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the ChannelingHttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isShutdownImmediately() {
        return shutdownImmediately;
    }

    /**
     * Option to toggle when camel context shutting down, should channeling engine also shutting down immediately
     * false for gracefully shutdown
     * true for shutdown now
     * default is false
     */
    public void setShutdownImmediately(boolean shutdownImmediately) {
        this.shutdownImmediately = shutdownImmediately;
    }


    public boolean isFollowRedirect() {
        return followRedirect;
    }

    /**
     * Option to toggle when you are using as a backend service instead of browser mode
     * false, it will response redirection response
     * true, it will handle redirection response before it response the result
     * default is false
     */
    public void setFollowRedirect(boolean followRedirect) {
        this.followRedirect = followRedirect;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Reference to a org.apache.camel.support.jsse.SSLContextParameters in the Registry. This reference overrides any
     * configured SSLContextParameters at the component level. See Using the JSSE Configuration Utility. Note that
     * configuring this option will override any SSL/TLS configuration options provided through the clientConfig option
     * at the endpoint or component level.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isConnectionClose() {
        return connectionClose;
    }

    /**
     * Define if the Connection Close header has to be added to HTTP Request. This parameter is false by default
     */
    public void setConnectionClose(boolean connectionClose) {
        this.connectionClose = connectionClose;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    /**
     * Configure a cookie handler to maintain a HTTP session
     */
    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
    }

    public String getvHost() {
        return vHost;
    }

    public void setvHost(String vHost) {
        this.vHost = vHost;
    }

    public String getCallbackRoute() {
        return callbackRoute;
    }

    public void setCallbackRoute(String callbackRoute) {
        this.callbackRoute = callbackRoute;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public boolean isSSL() {
        return isSSL;
    }

    public void setSSL(boolean SSL) {
        isSSL = SSL;
    }

    public boolean isUseStreaming() {
        return useStreaming;
    }

    public void setUseStreaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (engine == null) {
            throw new IllegalStateException("engine should not be null ... ");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // ensure client is closed when stopping
        if (engine != null) {
           if(shutdownImmediately) {
               engine.shutdownNow();
           } else {
               engine.shutdown();
           }
        }
        engine = null;
    }

    public String getTimeZone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isReadBody() {
        return this.readBody;
    }

    public void setReadBody(boolean readBody) {
        this.readBody = readBody;
    }
}
