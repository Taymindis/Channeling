package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpRequestMessage;
import com.github.taymindis.nio.channeling.http.HttpResponse;
import com.github.taymindis.nio.channeling.http.HttpResponseMessage;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.taymindis.nio.channeling.Channeling.getDefaultKeyStore;


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

        new Thread(()->channelingServer.listen((request) -> {
            return null;
        })).start();

        Thread.sleep(1000 * 1000);
        channelingServer.stop();
    }


    @Test
    public void testSSLServer() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(createKeyManagers("./src/main/resources/keystore.jks", "password", "password"),  createTrustManagers(getDefaultKeyStore(), "changeit"), new SecureRandom());

        ChannelingServer channelingServer = new ChannelingServer(channeling, sslContext, "localhost", 8443);

        channelingServer.setBuffSize(1024);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
        dateFormat = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
        new Thread(()->channelingServer.listen(Map.of(
                "localhost", this::localHostHandler,
                "tadika.org.com", this::otherHandler
        ))).start();

        Thread.sleep(1000 * 1000);
        channelingServer.stop();
    }

    private HttpResponseMessage localHostHandler(HttpRequestMessage httpRequestMessage) {

        HttpResponseMessage<String> res = new HttpResponseMessage<String>();

        res.setContent("OK");
        String content = res.getContent();

        res.setCode(200);
        res.setStatusText("OK");
        res.addHeader("Date", dateFormat.format(new Date()));
        res.addHeader("Server", "Channeling/1.0.5");
        res.addHeader("Content-Length",String.valueOf(content.length()));
        res.addHeader("Content-Type:","text/plain");


        return res;
    }

    private HttpResponseMessage otherHandler(HttpRequestMessage httpRequestMessage) {

        HttpResponseMessage<String> res = new HttpResponseMessage<String>();

        res.setContent("OK");
        String content = res.getContent();

        res.setCode(200);
        res.setStatusText("OK");
        res.addHeader("Date", dateFormat.format(new Date()));
        res.addHeader("Server", "Channeling/1.0.5");
        res.addHeader("Content-Length",String.valueOf(content.length()));
        res.addHeader("Content-Type:","text/plain");

        return res;
    }


    @AfterAll
    public static void afterAllTested() {
        channeling.shutdownNow();
    }


    public static void main(String ...args) throws Exception {
        TestServer testServer = new TestServer();

        beforeAll();
        testServer.beforeEach();
        testServer.testServer();

    }




    /**
     * Creates the key managers required to initiate the {@link SSLContext}, using a JKS keystore as an input.
     *
     * @param filepath - the path to the JKS keystore.
     * @param keystorePassword - the keystore's password.
     * @param keyPassword - the key's passsword.
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
     * @param filepath - the path to the JKS keystore.
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


}
