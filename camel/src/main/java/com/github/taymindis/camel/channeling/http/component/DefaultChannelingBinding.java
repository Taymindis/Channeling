package com.github.taymindis.camel.channeling.http.component;

import com.github.taymindis.nio.channeling.http.HttpRequestBuilder;
import com.github.taymindis.nio.channeling.http.HttpRequestMessage;
import com.github.taymindis.nio.channeling.http.HttpResponse;
import com.github.taymindis.nio.channeling.http.HttpResponseMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.TypeConverter;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.*;

public class DefaultChannelingBinding implements ChannelingBinding {

    private static final List<String> noBodyMethod = Arrays.asList("GET", "OPTIONS", "HEAD", "CONNECT", "TRACE");
    private static final List<String> hasBodyMethod = Arrays.asList("POST", "PUT", "DELETE", "PATCH");
    private static final Logger LOG = LoggerFactory.getLogger(ChannelingConsumer.class);

    private static final String JSON_TYPE = "application/json; charset=utf-8";
    private static final String FORMENCODED = "application/x-www-form-urlencoded";
    private static final String TEXTPLAIN = "text/plain; charset=utf-8";

    private final boolean isBridgeEnpoint;
    private boolean isMuteException = false;

    public DefaultChannelingBinding(boolean isBridgeEnpoint) {
        this.isBridgeEnpoint = isBridgeEnpoint;
    }

    public DefaultChannelingBinding() {
        // Technically not using this field if camel component, it will use endpoint.isBridgeEndpoint instead
        this.isBridgeEnpoint = true;
    }

    /** Consumer Scope **/
    @Override
    public Message toCamelMessage(HttpRequestMessage requestMessage, Exchange exchange) {
        Message result = new DefaultMessage(exchange);

        populateCamelHeaders(requestMessage, result.getHeaders(), exchange);

        // Map form data which is parsed by undertow form parsers
//        result.setBody(requestMessage.getBody());
        return result;
    }

    @Override
    public void populateCamelHeaders(HttpRequestMessage requestMessage, Map<String, Object> headersMap, Exchange exchange) {
        LOG.trace("populateCamelHeaders: {}", exchange.getMessage().getHeaders());

        String path = requestMessage.getPath();
        ChannelingEndpoint endpoint = (ChannelingEndpoint) exchange.getFromEndpoint();
        if (endpoint.getHttpUri() != null) {
            // need to match by lower case as we want to ignore case on context-path
            String endpointPath = endpoint.getHttpUri().getPath();
            String matchPath = path.toLowerCase(Locale.US);
            String match = endpointPath.toLowerCase(Locale.US);
            if (matchPath.startsWith(match)) {
                path = path.substring(endpointPath.length());
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP-Method {}", requestMessage.getMethod());
            LOG.trace("HTTP-Uri {}", requestMessage.getPath());
        }

        //process uri parameters as headers
        String query = getQueryParameters(requestMessage);
        //continue if the map is not empty, otherwise there are no params
        if (!query.isEmpty() && query.contains("&")) {
           for(String kvs : query.split("&")) {
               String[] kv = kvs.split("=", 2);
               headersMap.put(kv[0], kv[1]);
           }
        }


        headersMap.put(Exchange.HTTP_PATH, path);
        // NOTE: these headers is applied using the same logic as camel-http/camel-jetty to be consistent
        headersMap.put(Exchange.HTTP_METHOD, requestMessage.getMethod());
        // strip query parameters from the uri
        headersMap.put(Exchange.HTTP_URL,  getHTTPUrl(requestMessage));
        // uri is without the host and port
        headersMap.put(Exchange.HTTP_URI, requestMessage.getPath());
        headersMap.put(Exchange.HTTP_QUERY, query);
        headersMap.put(Exchange.HTTP_RAW_QUERY, query);
    }

    @Override
    public HttpResponseMessage toHttpResponseHeader(Message message, HeaderFilterStrategy headerFilterStrategy)  {
        HttpResponseMessage responseMessage = new HttpResponseMessage();
        Exchange camelExchange = message.getExchange();
        Object body = message.getBody();
        Exception exception = camelExchange.getException();

        int code = determineResponseCode(camelExchange, body);
        message.getHeaders().put(Exchange.HTTP_RESPONSE_CODE, code);
        String statusText = (String) message.getHeaders().get(Exchange.HTTP_RESPONSE_TEXT);

        responseMessage.setCode(code);
        responseMessage.setStatusText(statusText);


        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);

        message.removeHeaders("Camel*");


        //copy headers from Message to Response
        TypeConverter tc = message.getExchange().getContext().getTypeConverter();
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = org.apache.camel.support.ObjectHelper.createIterator(value, null);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());
                if (headerValue != null && headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, camelExchange)) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    responseMessage.addHeader(key, headerValue);
                }
            }
        }

        if (exception != null && !isMuteException()) {
//            if (isTransferException()) {
//                // we failed due an exception, and transfer it as java serialized object
//                ByteArrayOutputStream bos = new ByteArrayOutputStream();
//                ObjectOutputStream oos = new ObjectOutputStream(bos);
//                oos.writeObject(exception);
//                oos.flush();
//                IOHelper.close(oos, bos);
//
//                // the body should be the serialized java object of the exception
//                body = ByteBuffer.wrap(bos.toByteArray());
//                // force content type to be serialized java object
//                message.setHeader(Exchange.CONTENT_TYPE, "application/x-java-serialized-object");
//            } else {
                // we failed due an exception so print it as plain text
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);

                // the body should then be the stacktrace
                body = ByteBuffer.wrap(sw.toString().getBytes());
                // force content type to be text/plain as that is what the stacktrace is
                message.setHeader(Exchange.CONTENT_TYPE, "text/plain");
//            }

            // and mark the exception as failure handled, as we handled it by returning it as the response
            ExchangeHelper.setFailureHandled(camelExchange);
        } else if (exception != null && isMuteException()) {
            // mark the exception as failure handled, as we handled it by actively muting it
            ExchangeHelper.setFailureHandled(camelExchange);
        }

//        String bodyTypeName = MessageHelper.getBodyTypeName(message);
//        String contentEncoding = MessageHelper.getContentEncoding(message);
        if (contentType == null) {
            contentType = "text/plain";
        }

        responseMessage.addHeader(Exchange.CONTENT_TYPE, contentType);
        LOG.trace("Content-Type: {}", contentType);

        responseMessage.setContent(body);

        return responseMessage;
    }

    @Override
    public void filterResponseHeader(Message message, HeaderFilterStrategy headerFilterStrategy) {
        //copy headers from Message to Response
        Exchange camelExchange = message.getExchange();
        TypeConverter tc = camelExchange.getContext().getTypeConverter();
        Map<String,Object> filteredHeaders = new HashMap<String,Object>();
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = org.apache.camel.support.ObjectHelper.createIterator(value, null);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());
                if (headerValue != null && headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, camelExchange)) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    filteredHeaders.put(key, headerValue);
                }
            }
        }
        message.setHeaders(filteredHeaders);
    }

    /*
     * set the HTTP status code
     */
    private int determineResponseCode(Exchange camelExchange, Object body) {
        boolean failed = camelExchange.isFailed();
        int defaultCode = failed ? 500 : 200;

        Message message = camelExchange.getMessage();
        Integer currentCode = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        int codeToUse = currentCode == null ? defaultCode : currentCode;

        if (codeToUse != 500) {
            if (body == null || body instanceof String && ((String) body).trim().isEmpty()) {
                // no content
                codeToUse = currentCode == null ? 204 : currentCode;
            }
        }

        return codeToUse;
    }

    private String getHTTPUrl(HttpRequestMessage requestMessage) {
        String path = requestMessage.getPath();
        int qpIndex = path.indexOf("?");
        if(qpIndex < 0) {
            return path;
        }
        return path.substring(0, qpIndex);
    }

    private String getQueryParameters(HttpRequestMessage requestMessage) {
      String path = requestMessage.getPath();

      int qpIndex = path.indexOf("?") + 1;

      if(qpIndex <= 0){
          return "";
      }

      return path.substring(qpIndex);
    }


    @Override
    public HttpRequestBuilder preparingRequest(ChannelingEndpoint endpoint, Exchange exchange) throws IOException {
        Message message = exchange.getIn();

        String queryString = message.getHeader(Exchange.HTTP_QUERY, String.class);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        if (endpoint.isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);

            exchange.getIn().getHeaders().remove("host");
        }

        URI httpUri = endpoint.getHttpUri();

        String uri = httpUri.getPath();
        if (isBridgeEnpoint) {
            String bridgePath = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
            if(bridgePath != null) {
               uri = uri.concat(bridgePath);
            }
        }

        // resolve placeholders in uri
        try {
            uri = exchange.getContext().resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uri, exchange, e);
        }

        if (uri == null) {
            uri = "/";
        } else if (!uri.startsWith("/")) {
            uri = "/".concat(uri);
        }

        // append HTTP_PATH to HTTP_URI if it is provided in the header
//        String path = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
//        if (path != null) {
//            if (path.startsWith("/")) {
//                URI baseURI;
//                String baseURIString = exchange.getIn().getHeader(Exchange.HTTP_BASE_URI, String.class);
//                try {
//                    if (baseURIString == null) {
//                        if (exchange.getFromEndpoint() != null) {
//                            baseURIString = exchange.getFromEndpoint().getEndpointUri();
//                        } else {
//                            // will set a default one for it
//                            baseURIString = "/";
//                        }
//                    }
//                    baseURI = new URI(baseURIString);
//                    String basePath = baseURI.getPath();
//                    if (path.startsWith(basePath)) {
//                        path = path.substring(basePath.length());
//                        if (path.startsWith("/")) {
//                            path = path.substring(1);
//                        }
//                    } else {
//                        throw new RuntimeExchangeException(
//                                "Cannot analyze the Exchange.HTTP_PATH header, due to: cannot find the right HTTP_BASE_URI",
//                                exchange);
//                    }
//                } catch (Exception t) {
//                    throw new RuntimeExchangeException(
//                            "Cannot analyze the Exchange.HTTP_PATH header, due to: " + t.getMessage(), exchange, t);
//                }
//            }
//            if (path.length() > 0) {
//                // make sure that there is exactly one "/" between HTTP_URI and
//                // HTTP_PATH
//                if (!uri.endsWith("/")) {
//                    uri = uri + "/";
//                }
//                uri = uri.concat(path);
//            }
//        }


        requestBuilder.setPath(uri);
        if (queryString != null) {
            requestBuilder.setArgs(queryString);
        }

//        requestBuilder.addHeader("Host", String.format("%s:%d", httpUri.getHost(), httpUri.getPort()));
        requestBuilder.addHeader("Host", httpUri.getHost());

        populateRequestHeadersBody(exchange, requestBuilder, endpoint.getHeaderFilterStrategy());

        if (endpoint.isConnectionClose()) {
            requestBuilder.addHeader("Connection", "close");
        }

        return requestBuilder;

    }

    private static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }

    @Override
    public void customBodyPayload(String method, Exchange exchange, HttpRequestBuilder builder) {
        // TODO do nothing, customize it yourself
    }

    @Override
    public Exception catchChannelingCallFailedException(
            ChannelingEndpoint endpoint, Exchange exchange,
            String url, HttpResponse response, String body) {
        return new ChannelingCallFailedException(url, response);
    }

    @Override
    public Exception catchChannelingCallFailedException(
            ChannelingEndpoint endpoint, Exchange exchange, String url, String errorMessage) {
        return new ChannelingCallFailedException(url, errorMessage);
    }

    private void populateRequestHeadersBody(Exchange exchange, HttpRequestBuilder builder, HeaderFilterStrategy strategy)
            throws IOException {
        // Ensure the Content-Type header is always added if the corresponding exchange header is present
        String contentType = ExchangeHelper.getContentType(exchange);
        if (ObjectHelper.isNotEmpty(contentType)) {
            builder.addHeader(Exchange.CONTENT_TYPE, contentType);
        }

        // Transfer exchange headers to the HTTP request while applying the filter strategy
        Message message = exchange.getMessage();
        String queryString = message.getHeader(Exchange.HTTP_QUERY, String.class);
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String headerValue = message.getHeader(entry.getKey(), String.class);
            String key = entry.getKey();
            if (strategy != null && !strategy.applyFilterToExternalHeaders(key, headerValue, exchange)) {
                if (!key.isEmpty() && !key.startsWith("CamelHttp") && !key.equalsIgnoreCase("Accept-Encoding") && (queryString == null || !queryString.contains(key + "="))) {
                    builder.addHeader(key, headerValue);
                }
            }
        }

        String method = message.getHeader(Exchange.HTTP_METHOD, String.class);
        builder.setMethod(method);
        if (hasBodyMethod.contains(method)) {
            byte[] bodyBytes;
            Object body = message.getBody();

            if (body instanceof Map) {
                Map bodyMap = (Map) body;
                StringBuilder bodyBuilder = (StringBuilder) bodyMap.entrySet().stream().reduce(new StringBuilder(),
                        DefaultChannelingBinding::reduceMapToString);
                if (bodyBuilder.length() > 0) {
                    bodyBuilder.setLength(bodyBuilder.length() - 1);
                }

                builder.setBody(bodyBuilder.toString());

//                sendBodyString(builder, method, contentType, bodyBuilder.toString());
//            } else if (body instanceof NettyChannelBufferStreamCache) {
//                NettyChannelBufferStreamCache nettyChannelBufferStreamCache
//                        = message.getBody(NettyChannelBufferStreamCache.class);
//
//                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//                    nettyChannelBufferStreamCache.writeTo(baos);
//                    bodyBytes = baos.toByteArray();
//                } catch (IOException e) {
//                    exchange.setException(e);
//                    throw e;
//                }
//                builder.setBody(new String(bodyBytes, StandardCharsets.UTF_8));
////                builder.method(method, RequestBody.create(bodyBytes));
            } else if (body instanceof String) {
                builder.setBody((String) body);
//                sendBodyString(builder, method, contentType, (String) body);
            } else {
                customBodyPayload(method, exchange, builder);
            }


            String builtBody = builder.getBody();
            if(builtBody != null) {
                builder.addHeader("Content-Length", String.valueOf(builtBody.length()));
            }
        }
    }

//    private void sendBodyString(HttpRequestBuilder builder, String method, String contentType, String body) {
//        if (contentType != null) {
//            if (contentType.contains("application/json")) {
//                builder.method(method, RequestBody.create(body, JSON));
//            } else if (contentType.contains("application/x-www-form-urlencoded")) {
//                builder.method(method, RequestBody.create(body, FORMENCODED));
//            } else {
//                builder.method(method, RequestBody.create(body, TEXTPLAIN));
//            }
//        }
//    }

    @Override
    public void populateResponseHeaders(Exchange exchange, String[] statusText, HttpResponse response, HeaderFilterStrategy strategy) {
        Message message = exchange.getMessage();

        String[] headers = response.getHeaders().split("\\r?\\n");

        message.setHeader(Exchange.HTTP_RESPONSE_CODE, Integer.parseInt(statusText[1]));
        message.setHeader(Exchange.HTTP_RESPONSE_TEXT, statusText[2]);

        for (int i = 1, size = headers.length; i < size; i++) {
            String header = headers[i];
            String[] keyPair = header.split(":\\s?");
            String name = keyPair[0];
            String value = keyPair[1];

            if (LOG.isDebugEnabled()) {
                LOG.debug("Response headers {}, {}", name, value);
            }

            if (name.equalsIgnoreCase("content-type")) {
                name = Exchange.CONTENT_TYPE;
                exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
            }
            Object extracted = HttpHelper.extractHttpParameterValue(value);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(name, extracted, exchange)) {
                HttpHelper.appendHeader(message.getHeaders(), name, extracted);
            }
        }
    }


    @Override
    public void populateResponseBody(Exchange exchange, HttpResponse response) {
        Object content = response.getBodyContent();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Response body {}", content);
        }
        exchange.getMessage().setBody(content);
    }

    private static Object reduceMapToString(Object o, Object o1) {
        StringBuilder b = (StringBuilder) o;
        Map.Entry<String, String> m = (Map.Entry) o1;
        if (m.getKey().isEmpty()) {
            return b;
        }
        return b.append(m.getKey()).append('=').append(m.getValue()).append('&');
    }

    public boolean isMuteException() {
        return isMuteException;
    }

    public void setMuteException(boolean muteException) {
        isMuteException = muteException;
    }
}
