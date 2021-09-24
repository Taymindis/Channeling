package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class TestServer {

    private static Channeling channeling;
    private static Logger logger = LoggerFactory.getLogger(TestServer.class);
    private static SimpleDateFormat dateFormat;

    AtomicInteger totalDone;

    @BeforeEach
    public void beforeEach() throws Exception {
        totalDone = new AtomicInteger(0);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        channeling = Channeling.startNewChanneling(1, 100 * 1000, 1000 * 1000);
        channeling.enableSSL(1);
    }

    @Test
    public void testServer() throws Exception {
        ChannelingServer channelingServer = new ChannelingServer(channeling, "localhost", 8080);

        channelingServer.setBuffSize(1024);


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

        new Thread(() -> channelingServer.listen(this::proxyHandler)).start();

        int tick = 1000;

        while (tick-- > 0) {
            Thread.sleep(999);
            System.out.printf("tick %d\n", tick);
        }

        channelingServer.stop();
    }

    @Test
    public void testProxyServerViaStreaming() throws Exception {
        ChannelingServer channelingServer = new ChannelingServer(channeling, "localhost", 8080);

        channelingServer.setBuffSize(1024);


        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");

        new Thread(() -> channelingServer.listen(this::proxyStreamHandler)).start();

        int tick = 1000;

        while (tick-- > 0) {
            Thread.sleep(999);
            System.out.printf("tick %d\n", tick);
        }

        channelingServer.stop();
    }

    private void proxyHandler(HttpRequestMessage requestMessage, ResponseCallback callback) {
        try {

            URI uri = new URI("https://www.google.com.sg/");
            String host = uri.getHost();
            boolean isSSL = uri.getScheme().startsWith("https");

            int port = uri.getPort();

            if (port < 0) {
                port = isSSL ? 443 : 80;
            }
            ChannelingSocket cs = channeling.wrapSSL("TLSv1.2", host, port, null);

            HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

            requestBuilder.setMethod("GET");
            requestBuilder.addHeader("Host", String.format("%s:%d", host, port));
            requestBuilder.setPath("/");


            HttpRequest httpRequest = new HttpSingleRequest(
                    cs,
                    host,
                    port,
                    requestBuilder.toString(),
                    isSSL ? cs.getSSLMinimumInputBufferSize() : 1024
            );
            HttpResponseMessage res = new HttpResponseMessage();

            httpRequest.execute(new HttpResponseCallback() {
                @Override
                public void accept(HttpResponse httpResponse, Object attachment) {
                    res.setCode(httpResponse.getCode());
                    res.setStatusText(httpResponse.getStatusText());
                    res.setContent(httpResponse.getBodyContent());
                    res.addHeader("Content-Type", httpResponse.getHeader("Content-Type"));

                    String content = (String) res.getContent();

                    res.addHeader("Date", dateFormat.format(new Date()));
                    res.addHeader("Server", "Channeling/2.0.0");
                    res.addHeader("Content-Length", String.valueOf(content.length() + 2));
                    res.addHeader("Content-Type", "text/html");

                    callback.write(res, null, TestServer.this::close);

                }

                @Override
                public void error(Exception e, ChannelingSocket socket) {
                    Assertions.fail(e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void proxyStreamHandler(HttpRequestMessage requestMessage, ResponseCallback callback) {
        try {

            URI uri = new URI("https://www.google.com.sg/");
            String host = uri.getHost();
            boolean isSSL = uri.getScheme().startsWith("https");

            int port = uri.getPort();

            if (port < 0) {
                port = isSSL ? 443 : 80;
            }
            ChannelingSocket cs = channeling.wrapSSL("TLSv1.2", host, port, null);

            HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

            requestBuilder.setMethod("GET");
            requestBuilder.addHeader("Host", String.format("%s:%d", host, port));
            requestBuilder.setPath("/");


            HttpRequest httpRequest = new HttpStreamRequest(
                    cs,
                    host,
                    port,
                    requestBuilder.toString(),
                    isSSL ? cs.getSSLMinimumInputBufferSize() : 1024
            );

            httpRequest.execute(new HttpStreamRequestCallback() {
                @Override
                public void first(byte[] chunked, String headersContent, ChannelingSocket socket) throws Exception {
//                    byte[] headerBytes =
//                            String.format("HTTP/1.1 200 OK\r\n" +
//                                    "Content-Type: text/html\r\n" +
//                                    "Transfer-Encoding: chunked\r\n\r\n" +
//                                    "%d\r\n", chunked.length)
//                                    .getBytes();

                    Map<String, String> headerMap = HttpMessageHelper.massageHeaderContentToHeaderMap(headersContent);

                    headerMap.put("Transfer-Encoding", "chunked");
                    headerMap.remove("Content-Length");

                    byte[] headerBytes = HttpMessageHelper.headerToBytes(headerMap, "HTTP/1.1 200 OK");

                    callback.streamWrite(ByteBuffer.wrap(BytesHelper.concat(headerBytes, chunked)), clientSocket -> {
                    });
                }

                @Override
                public void accept(byte[] chunked, ChannelingSocket socket) {
                    callback.streamWrite(ByteBuffer.wrap(chunked), clientSocket -> {
                    });
                }

                @Override
                public void last(byte[] chunked, ChannelingSocket socket) {
                    System.out.println(new String(BytesHelper.subBytes(chunked, 0, chunked.length - 5)));
                    callback.streamWrite(ByteBuffer.wrap(chunked), clientSocket -> {
                        close(clientSocket);
                    });

                }

                @Override
                public void error(Exception e, ChannelingSocket socket) {
                    Assertions.fail(e.getMessage(), e);
                }
            });


        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        testServer.testServer();

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
