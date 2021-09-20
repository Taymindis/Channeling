package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

//        int tick = 1000;
//
//        while(tick--> 0) {
//            Thread.sleep(999);
//            System.out.printf("tick %d\n", tick);
//        }
        Thread.sleep(1000);

        new TestKits(channeling).multiThreadTestLocalhost("localhost", 8080, 4, 1000);

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

        while(tick--> 0) {
            Thread.sleep(999);
            System.out.printf("tick %d\n", tick);
        }

        channelingServer.stop();
    }

    private void proxyHandler(HttpRequestMessage requestMessage, ResponseCallback callback) {
        try {
            String host = "localhost";
            int port = 9090;


            ChannelingSocket cs = channeling.wrap(null);

            HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

            requestBuilder.setMethod("GET");
            requestBuilder.addHeader("Host", String.format("%s:%d", host, port));
            requestBuilder.setPath("/");



            SingleRequest httpRequest = new HttpRequest(
                    cs,
                    host,
                    port,
                    requestBuilder.toString(),
                    1024
            );
            HttpResponseMessage res = new HttpResponseMessage();

            httpRequest.execute(httpResponse -> {
                res.setCode(httpResponse.getCode());
                res.setStatusText(httpResponse.getStatusText());
                res.setContent(httpResponse.getBodyContent());
                res.addHeader("Content-Type", httpResponse.getHeader("Content-Type"));

                String content = (String) res.getContent();

                res.addHeader("Date", dateFormat.format(new Date()));
                res.addHeader("Server", "Channeling/1.0.5");
                res.addHeader("Content-Length", String.valueOf(content.length()));
                res.addHeader("Content-Type", "text/plain");

                callback.write(res, null);
            }, e -> {
                Assertions.fail(e.getMessage(), e);
            });





        } catch (IOException e) {
            e.printStackTrace();
        }
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

        while(tick--> 0) {
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

        callback.write(res, null);
    }

    private void otherHandler(HttpRequestMessage httpRequestMessage, ResponseCallback callback) {

        HttpResponseMessage res = new HttpResponseMessage();

        res.setContent("<html>ok</html>");
        String content =(String) res.getContent();

        res.setCode(200);
        res.setStatusText("OK");
        res.addHeader("Date", dateFormat.format(new Date()));
        res.addHeader("Server", "Channeling/1.0.5");
        res.addHeader("Content-Length", String.valueOf(content.length()));
        res.addHeader("Content-Type", "text/html");

        callback.write(res, null);

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
