package com.github.taymindis.camel.channeling.http.component;


import com.github.taymindis.nio.channeling.*;
import com.github.taymindis.nio.channeling.http.*;
import org.apache.camel.*;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static com.github.taymindis.camel.channeling.http.component.ChannelingConstant.CH_LAST_STREAM_CHUNKED;
import static com.github.taymindis.nio.channeling.Channeling.CHANNELING_VERSION;

public class ChannelingConsumer extends DefaultConsumer implements Suspendable, RequestListener, ErrorCallback {
    //    private final UndertowNioEndpoint endpoint;
    //    private final EventBusHelper eventBusHelper;
    private static final Logger LOG = LoggerFactory.getLogger(ChannelingConsumer.class);

    static final String CHANNELING_CALLBACK_KEY = "CHANNELING_CALLBACK_KEY";
    static final String CHANNELING_REVERSE_PROXIED = "CHANNELING_REVERSE_PROXIED";
    static final String CHANNELING_CONSUMER_KEY = "CHANNELING_CONSUMER_KEY";


    private volatile boolean suspended;
    private Channeling engine;
    private ChannelingServer channelingServer;
    private FluentProducerTemplate vhostProducerTemplate;
    private boolean hasVhost = false;
    private boolean shouldReadBody;
    private static SimpleDateFormat dateFormat;

    public ChannelingConsumer(ChannelingEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
//        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.suspended = false;
        SSLContext sslContext = null;

        ChannelingEndpoint channelingEndpoint = getEndpoint();
        this.engine = channelingEndpoint.getEngine();

        URI uri = channelingEndpoint.getHttpUri();

        String host = uri.getHost();
        boolean isSSL = uri.getScheme().startsWith("https");

        int port = uri.getPort();

        if (port < 0) {
            port = isSSL ? 443 : 80;
        }

        if (isSSL) {
            sslContext = getEndpoint().getSslContext();
            if (!engine.hasSSL()) {
                engine.enableSSL(channelingEndpoint.getNumSSLWorker());
            }
        }
        if (sslContext != null) {
            channelingServer = new ChannelingServer(engine, sslContext, host, port);
        } else {
            channelingServer = new ChannelingServer(engine, host, port);
        }

        if (channelingEndpoint.getTimeZone() != null) {
            TimeZone.setDefault(TimeZone.getTimeZone(channelingEndpoint.getTimeZone()));
        }

        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");


        channelingServer.setOnAcceptError(this);
        channelingServer.setOnReadError(this);
        channelingServer.setOnWriteError(this);

        channelingServer.setReadBody(channelingEndpoint.isReadBody());

        if (channelingEndpoint.getComponent().getVHostRequestListener() != null) {
            vhostProducerTemplate = channelingEndpoint.getCamelContext().createFluentProducerTemplate();
            hasVhost = true;
            String[] vHost = channelingEndpoint.getvHost().split(",");

            // TODO VHOST Based


        } else {

//            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
//            dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
            new Thread(() -> channelingServer.listen(this)).start();

        }


//        // start a single threaded pool to monitor events
//        executorService = endpoint.createExecutor();
//
//        // submit task to the thread pool
//        executorService.submit(() -> {
//            // subscribe to an event
//            eventBusHelper.subscribe(this::onEventListener);
//        });


    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (channelingServer != null) {
            channelingServer.stop();
        }
    }


    /**
     * Derived from camel-channeling
     **/
    @Override
    public void handleRequest(HttpRequestMessage requestMessage, ResponseCallback callback) {

        if (this.isSuspended()) {
            serviceUnavailable(callback, 503, "Service Unavailable", "");
        } else {
            handleRequest(requestMessage, callback, requestMessage.getBody());
        }
    }

    private void handleRequest(HttpRequestMessage requestMessage, ResponseCallback callback, ChannelingBytes bytes) {
        Exchange camelExchange = createExchange(requestMessage);

        /** BINDING **/
//            httpExchange.putAttachment(CAMEL_EXCHANGE_KEY, camelExchange);

//            ProxyBean proxyBean = new ProxyBean(httpExchange, this);
//            camelExchange.getMessage().setHeader("under_proxy_bean", proxyBean);

        try {
            camelExchange.setProperty(CHANNELING_CALLBACK_KEY, callback);
            camelExchange.setProperty(CHANNELING_CONSUMER_KEY, this);

            if (bytes != null) {
                camelExchange.getMessage().setBody(bytes, ChannelingBytes.class);
            }

            this.createUoW(camelExchange);
            this.getProcessor().process(camelExchange);
            Boolean hasProxied = camelExchange.getProperty(CHANNELING_REVERSE_PROXIED, Boolean.class);
            if (hasProxied == null || !hasProxied) {
                this.finalizeResponse(callback, camelExchange);
            }
        } catch (Exception e) {
            this.getExceptionHandler().handleException(e);
            serviceUnavailable(callback, 500, "Internal Server Error", e.getMessage());
        }
    }


    void finalizeResponse(ResponseCallback callback, Exchange camelExchange) {
        try {
            this.sendResponse(callback, camelExchange);
        } catch (Exception var12) {
            this.getExceptionHandler().handleException(var12);
        } finally {
            this.releaseExchangeAndUoW(camelExchange);
        }
    }

    void streamHeadersResponse(ResponseCallback callback, Exchange exchange) {
        ChannelingEndpoint endpoint = getEndpoint();
        Message message = exchange.getMessage();

        endpoint.getBinding().filterResponseHeader(message, endpoint.getHeaderFilterStrategy());

//        message.setHeader("Transfer-Encoding", "chunked");
        message.setHeader("Proxy-By", CHANNELING_VERSION);
//        message.removeHeader("Content-Length");
        message.removeHeaders("CamelHttp*");
        message.removeHeader(CH_LAST_STREAM_CHUNKED);
//                    headerMap.remove("Content-Encoding");
        String statusLine = message.getHeader("status", String.class);

        if (statusLine == null) {
            statusLine = message.getHeader(Exchange.HTTP_RESPONSE_TEXT, String.class);
            String code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, String.class);
            if (code == null) {
                code = "200";
            }
            if (statusLine == null) {
                statusLine = String.format("HTTP/1.1 %s UNKNOWN", code);
            } else {
                statusLine = String.format("HTTP/1.1 %s %s", code, statusLine);
            }
        }

        byte[] headerBytes = HttpMessageHelper.headerToString2(message.getHeaders(), statusLine).getBytes();
        callback.streamWrite(ByteBuffer.wrap(headerBytes), this::channelIdle);
    }

    void streamChunkedResponse(ResponseCallback callback, Exchange camelExchange, boolean isLastStream) {
        try {
            Message message = camelExchange.getMessage();
            Object obj = message.getBody();
            if (obj instanceof ChannelingBytes) {
                ChannelingBytes bytes = (ChannelingBytes) obj;
                if (isLastStream) {
                    callback.streamWrite(ByteBuffer.wrap(bytes.getBuff(), bytes.getOffset(), bytes.getLength()), this::channelClose);
                } else {
                    callback.streamWrite(ByteBuffer.wrap(bytes.getBuff(), bytes.getOffset(), bytes.getLength()), this::channelIdle);
                }
            } else if (obj instanceof byte[]) {
                byte[] chunked = (byte[]) obj;
                if (isLastStream) {
                    callback.streamWrite(ByteBuffer.wrap(chunked), this::channelClose);
                } else {
                    callback.streamWrite(ByteBuffer.wrap(chunked), this::channelIdle);
                }
            }
        } catch (Exception var12) {
            this.getExceptionHandler().handleException(var12);
        } finally {
            this.releaseExchangeAndUoW(camelExchange);
        }
    }

    private void channelIdle(ChannelingSocket channelingSocket) {
        // Do Nothing
    }

    private void channelClose(ChannelingSocket socket) {
        socket.close(s -> {
        });
    }

    private void serviceUnavailable(ResponseCallback callback, int code, String statusText, String content) {
        HttpResponseMessage responseMessage = new HttpResponseMessage();
        responseMessage.setStatusText(statusText);
        responseMessage.setCode(code);
        responseMessage.addHeader("Server", CHANNELING_VERSION);
        if (content != null && content.length() > 0) {
            responseMessage.setContent(content);
            responseMessage.addHeader("Server", CHANNELING_VERSION);
            responseMessage.addHeader("Content-Length", String.valueOf(content.length()));
        }
        callback.write(responseMessage, null, this::closeSocket);
    }

    private void sendResponse(ResponseCallback callBack, Exchange camelExchange) {
        ChannelingBinding binding = this.getEndpoint().getBinding();
        HttpResponseMessage responseMessage = binding.toHttpResponseHeader(camelExchange.getMessage(), getEndpoint().getHeaderFilterStrategy());
        Object body = responseMessage.getContent();

        /** Remove Transfer Chunked header **/
//        responseMessage.getHeaderMap().remove("Transfer-Encoding");

        if (body == null) {
            String message = responseMessage.getCode() == 500 ? "Exception" : "No response available";

            responseMessage.setCode(500);
            responseMessage.setStatusText(message);
            LOG.trace("No payload to send as reply for exchange: {}", camelExchange);
//            String contentType = (String) camelExchange.getIn().getHeader("Content-Type", MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"), String.class);
//            responseMessage.addHeader(Exchange.CONTENT_TYPE, contentType);
            callBack.write(responseMessage, null, this::closeSocket);
        } else {
            Charset charset = camelExchange.getProperty(Exchange.HTTP_CHARACTER_ENCODING, Charset.class);


//            if (false) {
//                // TODO Streaming need to in sync with producer read and write to client,
//                //  this is the benefit when you want to reduce memory
//                responseMessage.addHeader(Exchange.TRANSFER_ENCODING, "chunked");
//                responseMessage.getHeaderMap().remove(Exchange.CONTENT_LENGTH);
//                if (body instanceof byte[]) {
//                    String content = new String((byte[]) body);
//                    if (responseMessage.isDone()) {
//                        responseMessage.setContent(String.format("%d\r\n%s\r\n0\r\n\r\n", content.length(), content));
//                    } else {
//                        responseMessage.setContent(String.format("%d\r\n%s\r\n", content.length(), content));
//                    }
//                } else if (body instanceof String) {
//                    String content = (String) body;
//                    if (responseMessage.isDone()) {
//                        responseMessage.setContent(String.format("%d\r\n%s\r\n0\r\n\r\n", content.length(), content));
//                    } else {
//                        responseMessage.setContent(String.format("%d\r\n%s\r\n", content.length(), content));
//                    }
//                }
//
//                callBack.write(responseMessage, charset, sc -> {
//                    if (responseMessage.isDone()) {
//                        closeSocket(sc);
//                    }
//                });
//            } else {
            responseMessage.getHeaderMap().remove(Exchange.TRANSFER_ENCODING);
            // It's already decoded
            responseMessage.getHeaderMap().remove(Exchange.CONTENT_ENCODING);
            responseMessage.addHeader("Date", dateFormat.format(new Date()));
            responseMessage.addHeader("Proxy-Server", CHANNELING_VERSION);

            if (body instanceof byte[]) {
                String content = new String((byte[]) body);
                responseMessage.addHeader(Exchange.CONTENT_LENGTH, String.valueOf(((byte[]) body).length));
                responseMessage.setContent(content);
                responseMessage.setDone(true);
            } else if (body instanceof String) {
                String content = (String) body;
                responseMessage.setContent(content);
                responseMessage.getHeaderMap().put(Exchange.CONTENT_LENGTH, String.valueOf(content.getBytes().length));
                responseMessage.setDone(true);
            }

            callBack.write(responseMessage, charset, sc -> {
                if (responseMessage.isDone()) {
                    closeSocket(sc);
                }
            });
//            }

        }
    }

    private void closeSocket(ChannelingSocket socket) {
        socket.close(sc -> {
        });
    }


//    private HttpHandler wrapHandler(HttpHandler handler, UndertowEndpoint endpoint) {
//        HttpHandler nextHandler = handler;
//        String[] handlders = endpoint.getHandlers().split(",");
//        String[] var5 = handlders;
//        int var6 = handlders.length;
//
//        for(int var7 = 0; var7 < var6; ++var7) {
//            String obj = var5[var7];
//            if (EndpointHelper.isReferenceParameter(obj)) {
//                obj = obj.substring(1);
//            }
//
//            CamelUndertowHttpHandler h = (CamelUndertowHttpHandler) CamelContextHelper.mandatoryLookup(endpoint.getCamelContext(), obj, CamelUndertowHttpHandler.class);
//            h.setNext((HttpHandler)nextHandler);
//            nextHandler = h;
//        }
//
//        return (HttpHandler)nextHandler;
//    }

    private Exchange createExchange(HttpRequestMessage requestMessage) {
        Exchange exchange = this.createExchange(false);
        exchange.setPattern(ExchangePattern.InOut);
        Message in = this.getEndpoint().getBinding().toCamelMessage(requestMessage, exchange);
//        if (this.getEndpoint().getSecurityProvider() != null) {
//            this.getEndpoint().getSecurityProvider().addHeader((key, value) -> {
//                in.setHeader(key, value);
//            }, httpExchange);
//        }

        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, StandardCharsets.UTF_8);
        in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, StandardCharsets.UTF_8);
        exchange.setIn(in);
        requestMessage.setContext(exchange);
        return exchange;
    }

    public ChannelingEndpoint getEndpoint() {
        return (ChannelingEndpoint) super.getEndpoint();
    }

    protected void doResume() throws Exception {
        this.suspended = false;
        super.doResume();
    }

    public boolean isSuspended() {
        return this.suspended;
    }

    @Override
    public void error(ChannelingSocket sc, Exception e) {
        Exchange exchange = (Exchange) sc.getContext();
//        sc.close(_sc->{});
        releaseExchangeAndUoW(exchange);
    }


    private void releaseExchangeAndUoW(Exchange exchange) {
        this.doneUoW(exchange);
        this.releaseExchange(exchange, false);
    }
}
