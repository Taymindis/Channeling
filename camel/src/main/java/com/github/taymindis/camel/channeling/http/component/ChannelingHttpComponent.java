package com.github.taymindis.camel.channeling.http.component;

import com.github.taymindis.nio.channeling.Channeling;
import com.github.taymindis.nio.channeling.ChannelingPlugin;
import com.github.taymindis.nio.channeling.ChannelingProxy;
import com.github.taymindis.nio.channeling.http.RequestListener;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Component("channeling")
public class ChannelingHttpComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelingHttpComponent.class);

    @Metadata(label = "advanced")
    private List<ChannelingPlugin> channelingPluginList;
    @Metadata(label = "advanced")
    private ChannelingBinding binding;
    @Metadata(label = "security")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced")
    private ChannelingProxy channelingProxy;
    @Metadata(label = "advanced", description = "Number of worker to consuming the event")
    private Integer worker = 1;
    @Metadata(label = "advanced", description = "Number of worker processing SSL Delegated Task")
    private Integer numSSLWorker = 1;

    @Metadata(label = "advanced")
    private Integer connectionTimeout = 3000;

    @Metadata(label = "advanced")
    private Integer readTimeout = 15000;

    @Metadata(label = "common", defaultValue = "false")
    private boolean shutdownImmediately;

    private Channeling sharedChanneling;

    private boolean sharedEngine = true;

    private Map<String, RequestListener> vHostRequestListener = null;


    public ChannelingHttpComponent() {
    }

    public ChannelingHttpComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean isSSLEndpoint;
        if (uri.startsWith("channeling://https")) {
            isSSLEndpoint = true;
        } else if (uri.startsWith("channeling://http")) {
            isSSLEndpoint = false;
        } else {
            throw new IllegalStateException("Invalid uri argument " + uri);
        }

        String addressUri = createAddressUri(uri, remaining);
        SSLContextParameters ssl = getSslContextParameters();
        if (ssl == null) {
            ssl = retrieveGlobalSslContextParameters();
        }

        // Do not set the HTTP URI because we still have all of the Camel internal
        // parameters in the URI at this point.
        ChannelingEndpoint endpoint = createChannelingHttpEndpoint(uri, this, null);
        setEndpointHeaderFilterStrategy(endpoint);
        endpoint.setBinding(getBinding());
        endpoint.setWorker(getWorker());
        endpoint.setConnectionTimeout(getConnectionTimeout());
        endpoint.setReadTimeout(getReadTimeout());
        endpoint.setChannelingProxy(getChannelingProxy());
        endpoint.setSslContextParameters(ssl);
        endpoint.setSSL(isSSLEndpoint);
        endpoint.setNumSSLWorker(numSSLWorker);


        setProperties(endpoint, parameters);

        managedSSLPlugin(endpoint);
        managedCookiePlugin(endpoint);
        managedCachePlugin(endpoint);


        if(this.sharedChanneling != null) {
            endpoint.setEngine(this.sharedChanneling);

            if(isSSLEndpoint) {
                this.sharedChanneling.enableSSL(numSSLWorker);
            }

        } else {
            endpoint.setEngine(createChanneling(endpoint, isSSLEndpoint));
        }

        if(this.sharedEngine) {
            this.sharedChanneling = endpoint.getEngine();
        }


        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        addressUri = UnsafeUriCharactersEncoder.encodeHttpURI(addressUri);
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), parameters);

//        if(httpUri.getPort() == -1) {
//            throw new IllegalArgumentException("Please specified the port ");
//        }

        endpoint.setHttpUri(httpUri);

//        if (endpoint.isUseStreaming() && endpoint.getCallbackRoute() != null) {
//            throw new Exception("UseStreaming and callbackRoute cannot be using at the same time");
//        }

        return endpoint;
    }

    private Channeling createChanneling(ChannelingEndpoint endpoint, boolean isSSLEndpoint) throws IOException {
        Channeling channeling = Channeling.startNewChanneling(endpoint.getWorker(), 1,
                endpoint.getConnectionTimeout(), endpoint.getReadTimeout(), endpoint.getPlugins());

        if(isSSLEndpoint) {
            channeling.enableSSL(numSSLWorker);
        }

        return channeling;
    }

    private void managedCachePlugin(ChannelingEndpoint endpoint) {
    }

    private void managedCookiePlugin(ChannelingEndpoint endpoint) {
//        CookieHandler cookieHandler = endpoint.getCookieHandler();
//        if (cookieHandler != null) {
//            builder = builder.cookieJar(new JavaNetCookieJar(cookieHandler));
//        }
    }

    private void managedSSLPlugin(ChannelingEndpoint endpoint) throws Exception {
        SSLContextParameters sslContextParameters = endpoint.getSslContextParameters();
        try {
            if (sslContextParameters != null) {
                SSLContext sslContext = SSLContext.getInstance(sslContextParameters.getSecureSocketProtocol());
                TrustManager[] trustManagers = sslContextParameters.getTrustManagers().createTrustManagers();
                sslContext.init(
                        sslContextParameters.getKeyManagers() == null
                                ? null : sslContextParameters.getKeyManagers().createKeyManagers(),
                        trustManagers,
                        sslContextParameters.getSecureRandom() == null
                                ? null : sslContextParameters.getSecureRandom().createSecureRandom());
//                SSLSocketFactory f = sslContext.getSocketFactory();
//                builder = builder.sslSocketFactory(f, (X509TrustManager) trustManagers[0]);
                endpoint.setSslContext(sslContext);
            } else {
                endpoint.setSslContext(getDefaultSSLContext("TLSv1.2"));
            }

        } catch (Exception e) {
            throw e;
        }
    }

    public ChannelingBinding getBinding() {
        if (binding == null) {
            binding = new DefaultChannelingBinding();
        }
        return binding;
    }

    /**
     * To use a custom {@link ChannelingBinding} which allows to control how to bind between channeling http and Camel.
     */
    public void setBinding(ChannelingBinding binding) {
        this.binding = binding;
    }

    public List<ChannelingPlugin> getChannelingPluginList() {
        return channelingPluginList;
    }

    public void setChannelingPluginList(List<ChannelingPlugin> channelingPluginList) {
        this.channelingPluginList = channelingPluginList;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Reference to a org.apache.camel.support.jsse.SSLContextParameters in the Registry. Note that configuring this
     * option will override any SSL/TLS configuration options provided through the clientConfig option at the endpoint
     * or component level.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isSharedEngine() {
        return sharedEngine;
    }

    public void setSharedEngine(boolean sharedEngine) {
        this.sharedEngine = sharedEngine;
    }

    //    public boolean isAllowJavaSerializedObject() {
    //        return allowJavaSerializedObject;
    //    }
    //
    //    /**
    //     * Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object
    //     * <p/>
    //     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming data from
    //     * the request to Java and that can be a potential security risk.
    //     */
    //    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
    //        this.allowJavaSerializedObject = allowJavaSerializedObject;
    //    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    protected String createAddressUri(String uri, String remaining) {
        return remaining;
    }

    protected ChannelingEndpoint createChannelingHttpEndpoint(String endpointUri,
                                                              ChannelingHttpComponent component,
                                                              URI httpUri) {
        return new ChannelingEndpoint(endpointUri, component, httpUri);
    }

    public Integer getWorker() {
        return worker;
    }

    public void setWorker(Integer worker) {
        this.worker = worker;
    }

    public boolean isShutdownImmediately() {
        return shutdownImmediately;
    }

    public void setShutdownImmediately(boolean shutdownImmediately) {
        this.shutdownImmediately = shutdownImmediately;
    }

    public Integer getNumSSLWorker() {
        return numSSLWorker;
    }

    public void setNumSSLWorker(Integer numSSLWorker) {
        this.numSSLWorker = numSSLWorker;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    public ChannelingProxy getChannelingProxy() {
        return channelingProxy;
    }

    public void setChannelingProxy(ChannelingProxy channelingProxy) {
        this.channelingProxy = channelingProxy;
    }

    protected static TrustManager[] createTrustManagers(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        InputStream trustStoreIS = new FileInputStream(filepath);
        try {
            trustStore.load(trustStoreIS, keystorePassword.toCharArray());
        } finally {
            if (trustStoreIS != null) {
                trustStoreIS.close();
            }
        }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);
        return trustFactory.getTrustManagers();
    }

    protected static String getDefaultKeyStore() {
        return System.getProperty("java.home") + "/lib/security/cacerts".replace('/', File.separatorChar);
    }

    protected static SSLContext getDefaultSSLContext(String protocol) throws Exception {
        SSLContext context = SSLContext.getInstance(protocol);
        context.init(null,
                createTrustManagers(getDefaultKeyStore(), "changeit"), new SecureRandom());
        return context;
    }

    public Map<String, RequestListener> getVHostRequestListener() {
        return vHostRequestListener;
    }

    public void setVHostRequestListener(Map<String, RequestListener> vHostRequestListener) {
        this.vHostRequestListener = vHostRequestListener;
    }
}
