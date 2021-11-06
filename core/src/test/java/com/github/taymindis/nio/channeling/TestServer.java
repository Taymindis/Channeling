package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.taymindis.nio.channeling.Channeling.CHANNELING_VERSION;


public class TestServer {

    private static Channeling channeling;
    private static Logger logger = LoggerFactory.getLogger(TestServer.class);
    private static SimpleDateFormat dateFormat;

    AtomicInteger totalDone;

    List<String> proxiedTest = List.of(
//                "http://mygab.crmxs.com/"
//                ,
            "http://localhost/"
            ,
            "https://www.google.com.sg/"
            ,
            "https://sg.yahoo.com/?p=us"
    );

    @BeforeEach
    public void beforeEach() throws Exception {
        totalDone = new AtomicInteger(0);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        channeling = Channeling.startNewChanneling(4, 1, 100 * 1000, 1000 * 1000);
        channeling.enableSSL(2);
    }

    @Test
    public void testServer() throws Exception {
        ChannelingServer channelingServer = new ChannelingServer(channeling, "localhost", 8080);

        channelingServer.setBuffSize(1024);
        channelingServer.setKeepAlive(true);


        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");

        new Thread(() -> channelingServer.listen(this::localHostHandler)).start();

        int tick = 1000;


        new TestKits(channeling).multiThreadTestLocalhost("localhost", 8080, 4, 1000);
        while (tick-- > 0) {
            Thread.sleep(999);
            System.out.printf("tick %d\n", tick);
        }

        channelingServer.stop();
    }

    @Test
    public void testProxyServer() throws Exception {
        ChannelingServer channelingServer = new ChannelingServer(channeling, "localhost", 8080);

        channelingServer.setBuffSize(1024);


        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
        AtomicInteger j = new AtomicInteger();


        new Thread(() -> channelingServer.listen((requestMessage, callback) -> {
            this.proxyHandler(requestMessage, callback, proxiedTest.get(j.getAndIncrement() % proxiedTest.size()));
        })).start();

        int tick = 1000;

        while (tick-- > 0) {
            Thread.sleep(999);
//            System.out.printf("tick %d\n", tick);
        }

        channelingServer.stop();
    }

    @Test
    public void testProxyServerViaStreaming() throws Exception {
        ChannelingServer channelingServer = new ChannelingServer(channeling, "localhost", 8080);

        channelingServer.setBuffSize(1024);
        channelingServer.setWaitPerNano(1);


        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
        AtomicInteger j = new AtomicInteger();

        new Thread(() -> channelingServer.listen((requestMessage, callback) -> {
            this.proxyStreamHandler(requestMessage, callback, proxiedTest.get(j.getAndIncrement() % proxiedTest.size()));
        })).start();

        int tick = 1000;

        while (tick-- > 0) {
            Thread.sleep(999);
//            System.out.printf("tick %d\n", tick);
        }

        channelingServer.stop();
    }

    private void proxyHandler(HttpRequestMessage requestMessage, ResponseCallback callback, String proxyTo) {
        try {

            URI uri = new URI(proxyTo);
            String host = uri.getHost();
            boolean isSSL = uri.getScheme().startsWith("https");
            String path = uri.getPath();
            String args = uri.getQuery();

            int port = uri.getPort();

            if (port < 0) {
                port = isSSL ? 443 : 80;
            }
            ChannelingSocket cs = isSSL ? channeling.wrapSSL("TLSv1.2", host, port, null) : channeling.wrap(null);

            HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

            requestBuilder.setMethod("GET");
            requestBuilder.addHeader("Host", host);
            requestBuilder.setPath(path);
            requestBuilder.setArgs(args);

            HttpRequest httpRequest = new HttpSingleRequest(
                    cs,
                    host,
                    port,
                    requestBuilder.toString(),
                    isSSL ? cs.getSSLMinimumInputBufferSize() : 1024
            );
            HttpResponseMessage res = new HttpResponseMessage();

            httpRequest.execute(new HttpSingleRequestCallback() {
                @Override
                public void accept(HttpResponse httpResponse, Object attachment) {
                    res.setCode(httpResponse.getCode());
                    res.setStatusText(httpResponse.getStatusText());
                    res.setContent(httpResponse.getBodyContent());

                    Map<String, String> responHeaders = httpResponse.getHeaderAsMap();

                    String content = (String) res.getContent();

                    if (responHeaders.containsKey("Content-Encoding")) {
                        responHeaders.remove("Content-Encoding");
                    } else {
//                        DEBUG_INFO("asd");

                    }

                    responHeaders.remove("Content-Length");
                    responHeaders.remove("Transfer-Encoding");
                    res.addHeader(responHeaders);
                    res.addHeader("Content-Length", String.valueOf(content.getBytes().length));
                    res.addHeader("Date", dateFormat.format(new Date()));
                    res.addHeader("Proxy-Server", CHANNELING_VERSION);


                    callback.write(res, null, TestServer.this::close);

                }

                @Override
                public void error(Exception e, ChannelingSocket socket) {
                    requestMessage.getClientSocket().close(sc -> {
                    });
                    socket.close(sc -> {
                    });
//                    Assertions.fail(e.getMessage(), e);
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void proxyStreamHandler(HttpRequestMessage requestMessage, ResponseCallback callback, String proxyTo) {
        requestMessage.readBody(bytes -> {
            try {
                String body = bytes.toString();

                URI uri = new URI(proxyTo);
                String host = uri.getHost();
                boolean isSSL = uri.getScheme().startsWith("https");

                int port = uri.getPort();
                String path = uri.getPath();
                String args = uri.getQuery();

                if (port < 0) {
                    port = isSSL ? 443 : 80;
                }
                ChannelingSocket cs = isSSL ? channeling.wrapSSL("TLSv1.2", host, port, null) : channeling.wrap(null);

                HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

                requestBuilder.setMethod("GET");
                requestBuilder.addHeader("Host", String.format("%s", host));
                requestBuilder.setPath(path);
                requestBuilder.setArgs(args);
                AtomicInteger loading = new AtomicInteger(0);

                HttpRequest httpRequest = new HttpStreamRequest(
                        cs,
                        host,
                        port,
                        requestBuilder.toString(),
                        isSSL ? cs.getSSLMinimumInputBufferSize() : 1024
                );

                // TODO Please do the next write after write and then, if not sure, follow transfer chunked mock result
                httpRequest.execute(new HttpStreamRequestCallback() {

                    @Override
                    public void headerAccept(byte[] chunked, int offset, int length, ChannelingSocket socket) throws Exception {
                        callback.streamWrite(ByteBuffer.wrap(chunked, offset, length), clientSocket -> {
                            // TODO Continue
                        });
                        //                    DEBUG_INFO("HEADER========\n" + new String(chunked, offset, length));

                    }
                    //                @Override
                    //                public void headerEnd(byte[] chunked, int offset, int length, ChannelingSocket socket) throws Exception {
                    //
                    //                    Map<String, String> headerMap = new HashMap<>();
                    //
                    //                    headerMap.put("Proxy-By", CHANNELING_VERSION);
                    ////
                    //////                    String statusLine = headerMap.getOrDefault("status", "HTTP/1.1 200 OK");
                    //////                    DEBUG_INFO(HttpMessageHelper.headerToString(headerMap, statusLine));
                    //////                    byte[] headerBytes = HttpMessageHelper.headerToBytes(headerMap, statusLine);
                    //////                    debugStream.write(headerBytes);
                    ////
                    //                    byte[] addedOnHeaders = HttpMessageHelper.headerToBytes(headerMap);
                    ////
                    //                    callback.streamWrite(ByteBuffer.wrap(addedOnHeaders), clientSocket -> {
                    //                        // TODO Continue
                    //                    });
                    //
                    ////                    callback.streamWrite(ByteBuffer.wrap(chunked, offset, length), clientSocket -> {
                    ////                        // TODO Continue
                    ////                    });
                    ////                    DEBUG_INFO("HEADER LAST========\n" + new String(chunked, offset, length));
                    //
                    //                }

                    @Override
                    public void accept(byte[] chunked, int offset, int length, ChannelingSocket socket) {

                        try {
                            //                        DEBUG_INFO(new String(chunked));
                            //                        DEBUG_INFO(loading.incrementAndGet());
                            //                        debugStream.write(chunked);
                            //                        if (nextChunkedLen == -1) {
                            //                            String[] lenAndBody = new String(chunked).split("\\r?\\n", 2);
                            //                            String hex = lenAndBody[0];
                            //                            nextChunkedLen = HttpMessageHelper.hexToInt(hex) + 2;
                            //                            chunked = lenAndBody[1].getBytes();
                            //                        }
                            //                        if ((accLen + chunked.length) >= nextChunkedLen) {
                            //                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            //                            int lastLenOfTheChunk = nextChunkedLen - accLen;
                            //                            byte[] streamChunk, lastBufferOfTheChunk = BytesHelper.subBytes(chunked, 0, lastLenOfTheChunk - 2);
                            //
                            //                            while ((streamChunk = chunkQueue.poll()) != null) {
                            //                                outputStream.write(streamChunk);
                            //                            }
                            //                            outputStream.write(lastBufferOfTheChunk);
                            //                            responseHandler.accept(outputStream.toByteArray(), socket);
                            //                            byte[] carrForwardedChunk = BytesHelper.subBytes(chunked, lastLenOfTheChunk);
                            //                            accLen = carrForwardedChunk.length;
                            //
                            //                            String[] lenAndBody = new String(carrForwardedChunk).split("\\r?\\n", 2);
                            //                            nextChunkedLen = HttpMessageHelper.hexToInt(lenAndBody[0]) + 2;
                            //                            chunkQueue.add(lenAndBody[1].getBytes());
                            //                        } else {
                            //                            chunkQueue.add(chunked);
                            //                            accLen += chunked.length;
                            ////                            responseHandler.accept(BytesHelper.subBytes(chunked, 0, nextChunkedLen - (accLen-chunked.length)), socket);
                            //                        }

                            callback.streamWrite(ByteBuffer.wrap(chunked, offset, length), clientSocket -> {
                                // TODO Continue
                            });

                            //                        DEBUG_INFO("PROCESS========\n" + new String(chunked, offset, length));

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void last(byte[] chunked, int offset, int length, ChannelingSocket socket) {

                        try {
                            //                        debugStream.write(chunked);
                            //                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            //                        byte[] streamChunk;
                            //                        while ((streamChunk = chunkQueue.poll()) != null) {
                            //                            outputStream.write(streamChunk);
                            //                        }
                            //                        outputStream.write(chunked);
                            //
                            //                        responseHandler.last(outputStream.toByteArray(), socket);
                            callback.streamWrite(ByteBuffer.wrap(chunked, offset, length), TestServer.this::close);

                            //                        DEBUG_INFO("LAST========\n" + new String(chunked, offset, length));
                            //                        String closingChunked;
                            //                        int clen = chunked.length;
                            //
                            //                        if((clen == 5 && BytesHelper.equals(chunked,"0\r\n\r\n".getBytes())) || (clen == 6 && BytesHelper.equals(chunked,"\r\n0\r\n\r\n".getBytes()))) {
                            //                            callback.streamWrite(ByteBuffer.wrap("\r\n0\r\n\r\n".getBytes()), clientSocket -> {
                            //                                close(clientSocket);
                            //                            });
                            //                        } else {
                            //                            chunked = BytesHelper.subBytes(chunked, 0, chunked.length-5);
                            //
                            //                            DEBUG_INFO(new String(chunked));
                            //                            if (BytesHelper.equals(chunked, "\r\n".getBytes(), chunked.length-2)) {
                            //                                chunked = BytesHelper.subBytes(chunked, 0, chunked.length-2);
                            //                                closingChunked = "\r\n0\r\n\r\n";
                            //                            } else {
                            //                                closingChunked = "\r\n0\r\n\r\n";
                            //                            }
                            //
                            //                            callback.streamWrite(ByteBuffer.wrap(BytesHelper
                            //                                    .concat(String.format(
                            //                                            "%s\r\n", HttpMessageHelper.intToHex(chunked.length)).getBytes(),
                            //                                            chunked,
                            //                                            closingChunked.getBytes())), clientSocket -> {
                            //                                close(clientSocket);
                            //                            });
                            //                        }
                            //
                            //
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void error(Exception e, ChannelingSocket socket) {
                        requestMessage.getClientSocket().close(sc -> {
                        });
                        socket.close(sc -> {
                        });
                        //                    Assertions.fail(e.getMessage(), e);
                        e.printStackTrace();
                    }
                });


            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void DEBUG_INFO(String s) {
        System.out.println(s);
    }


    private void close(ChannelingSocket socket) {
        socket.close(s -> {
        });
    }


    @Test
    public void testSSLServer() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(createKeyManagers("./src/main/resources/keystore.jks", "password", "password"),
//                createTrustManagers(getDefaultKeyStore(), "changeit"),
                getAnyTrustCert(),
                new SecureRandom());

        ChannelingServer channelingServer = new ChannelingServer(channeling, sslContext, "0.0.0.0", 8443);

        channelingServer.setBuffSize(1024);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
        new Thread(() -> channelingServer.listen(Map.of(
                "localhost", this::localHostHandler,
                "channeling.taymindis.com", this::otherHandler
        ))).start();

//
        int tick = 1000;

        while (tick-- > 0) {
            Thread.sleep(999);
            System.out.printf("tick %d\n", tick);
        }


//        new TestKits(channeling).multiThreadTestSSL("channeling.taymindis.com", 8443, 20, 100);

        channelingServer.stop();
    }

    private void localHostHandler(HttpRequestMessage httpRequestMessage, ResponseCallback callback) {

        HttpResponseMessage res = new HttpResponseMessage();

        res.setContent("OK");
        String content = (String) res.getContent();

        res.setCode(200);
        res.setStatusText("OK");
        res.addHeader("Date", dateFormat.format(new Date()));
        res.addHeader("Server", "Channeling/1.0.5");
        res.addHeader("Content-Length", String.valueOf(content.length()));
        res.addHeader("Content-Type", "text/plain");

        callback.write(res, null, this::close);
    }

    private void otherHandler(HttpRequestMessage httpRequestMessage, ResponseCallback callback) {

        HttpResponseMessage res = new HttpResponseMessage();

        res.setContent("<html>ok</html>");
        String content = (String) res.getContent();

        res.setCode(200);
        res.setStatusText("OK");
        res.addHeader("Date", dateFormat.format(new Date()));
        res.addHeader("Server", "Channeling/1.0.5");
        res.addHeader("Content-Length", String.valueOf(content.length()));
        res.addHeader("Content-Type", "text/html");

        callback.write(res, null, this::close);

    }


    @AfterAll
    public static void afterAllTested() {
        channeling.shutdownNow();
    }


    public static void main(String... args) throws Exception {
        TestServer testServer = new TestServer();

        beforeAll();
        testServer.beforeEach();
        testServer.testProxyServerViaStreaming();

    }


    /**
     * Creates the key managers required to initiate the {@link SSLContext}, using a JKS keystore as an input.
     *
     * @param filepath         - the path to the JKS keystore.
     * @param keystorePassword - the keystore's password.
     * @param keyPassword      - the key's passsword.
     * @return {@link KeyManager} array that will be used to initiate the {@link SSLContext}.
     * @throws Exception
     */
    protected KeyManager[] createKeyManagers(String filepath, String keystorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        InputStream keyStoreIS = new FileInputStream(filepath);
        try {
            keyStore.load(keyStoreIS, keystorePassword.toCharArray());
        } finally {
            if (keyStoreIS != null) {
                keyStoreIS.close();
            }
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf.getKeyManagers();
    }

    /**
     * Creates the trust managers required to initiate the {@link SSLContext}, using a JKS keystore as an input.
     *
     * @param filepath         - the path to the JKS keystore.
     * @param keystorePassword - the keystore's password.
     * @return {@link TrustManager} array, that will be used to initiate the {@link SSLContext}.
     * @throws Exception
     */
    protected TrustManager[] createTrustManagers(String filepath, String keystorePassword) throws Exception {
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


    protected TrustManager[] getAnyTrustCert() throws Exception {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        return trustAllCerts;
    }


}
