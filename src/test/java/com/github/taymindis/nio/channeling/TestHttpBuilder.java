package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpRedirectableRequest;
import com.github.taymindis.nio.channeling.http.HttpRequest;
import com.github.taymindis.nio.channeling.http.HttpRequestBuilder;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.taymindis.nio.channeling.Channeling.createTrustManagers;


public class TestHttpBuilder {

    private static Channeling channeling;
    private static Logger logger = LoggerFactory.getLogger(TestHttpBuilder.class);

    AtomicInteger totalDone;

    private static SSLContext context;

    @BeforeEach
    public void beforeEach() throws Exception {
        totalDone = new AtomicInteger(0);
    }

    @BeforeAll
    public static void beforeAll() throws Exception {
        channeling = Channeling.startNewChanneling(8, 100 * 1000, 1000 * 1000);
        channeling.enableSSL(4);


        context = SSLContext.getInstance("TLSv1.2");
        context.init(null,
                createTrustManagers("./src/main/resources/keystore.jks", "password"), new SecureRandom());
    }


    @Test
    public void testRequestBuilder() throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ChannelingSocket cs = channeling.wrap(null);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", "127.0.0.1:80");
        requestBuilder.setPath("/");



        HttpRequest httpRequest = new HttpRequest(
                cs,
                "127.0.0.1",
                80,
                requestBuilder.toString(),
                1024
        );


        httpRequest.execute(httpResponse -> {
//            System.out.println("\""+result+"\"");
            String result = httpResponse.getBodyContent();
            Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, Throwable::printStackTrace);


        countDownLatch.await();

        if (totalDone.get() % 100 == 0)
            logger.info("Done " + totalDone.get());
    }

    @Test
    public void testRequestBuilderWithUri() throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        CountDownLatch countDownLatch = new CountDownLatch(1);

        URI uri = new URI("https://channeling.taymindis.com:8443/");
        String host = uri.getHost();
        boolean isSSL = uri.getScheme().startsWith("https");

        int port = uri.getPort();

        if(port < 0) {
            port = isSSL ? 443 : 80;
        }
        ChannelingSocket cs = channeling.wrapSSL(context, host, port, null);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", host + ":"+port);
        requestBuilder.setPath( uri.getPath() + (uri.getRawQuery()!=null? "?"+uri.getRawQuery():""));


        HttpRequest httpRequest = new HttpRequest(
                cs,
                uri.getHost(),
                port,
                requestBuilder.toString(),
                isSSL? cs.getSSLMinimumInputBufferSize() : 1024
        );


        httpRequest.execute(httpResponse -> {
//            System.out.println("\""+result+"\"");
            String result = httpResponse.getBodyContent();
            Map<String,String> headers = httpResponse.getHeaderAsMap();
            Assertions.assertTrue(result.toLowerCase().contains("ok"), result.length() > 15 ? result.substring(result.length() - 15):"");
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, e -> {
            e.printStackTrace();
            countDownLatch.countDown();
        });


        countDownLatch.await();

        if (totalDone.get() % 100 == 0)
            logger.info("Done " + totalDone.get());
    }


    @Test
    public void testRequestRedirectionWithUri() throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        channeling.enableSSL(4);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ChannelingSocket cs = channeling.wrap(null);

        URI uri = new URI("http://mykiehls.crmxs.com/");
        String host = uri.getHost();
        boolean isSSL = uri.getScheme().startsWith("https");

        int port = uri.getPort();

        if (port < 0) {
            port = isSSL ? 443 : 80;
        }

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", host + ":" + port);
        requestBuilder.setPath(uri.getPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : ""));


        HttpRequest httpRequest = new HttpRedirectableRequest(
                cs,
                uri.getHost(),
                80,
                requestBuilder.toString(),
                1024,
                (hostTo, portTo, isSSLTo) -> {
                    if (isSSLTo) {
                        return channeling.wrapSSL("TLSv1.2", hostTo, portTo, null);
                    }
                    return channeling.wrap(null);
                }
        );

        httpRequest.execute(httpResponse -> {
//            System.out.println("\""+result+"\"");
            String result = httpResponse.getBodyContent();
            Map<String, String> headers = httpResponse.getHeaderAsMap();
            Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, e -> {
            e.printStackTrace();
            countDownLatch.countDown();
        });


        countDownLatch.await();

        if (totalDone.get() % 100 == 0)
            logger.info("Done " + totalDone.get());
    }

    @Test
    public void multiThreadTestLocalhost() throws ExecutionException, InterruptedException {
        int mThread = 4;
        ExecutorService mSvc = Executors.newFixedThreadPool(mThread);

        List<Future> listF = new ArrayList<>();


        for (int i = 0; i < mThread; i++) {
            listF.add(mSvc.submit(() -> {
                try {
                    for (int x = 0; x < 1000; x++)
                        testRequestBuilder();
                } catch (Exception e) {
                    Assertions.fail(e);
                    e.printStackTrace();
                }
            }));
        }

        for (Future f : listF) {
            f.get();
        }
    }

    @AfterAll
    public static void afterAllTested() {
        channeling.shutdownNow();
    }


}
