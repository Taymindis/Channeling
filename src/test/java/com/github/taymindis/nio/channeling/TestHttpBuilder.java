package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        requestBuilder.addHeader("Host", "localhost");
        requestBuilder.setPath("/");


        HttpSingleRequest httpSingleRequest = new HttpSingleRequest(
                cs,
                "127.0.0.1",
                8080,
                requestBuilder.toString(),
                1024
        );
        httpSingleRequest.execute(new HttpSingleRequestCallback() {
            @Override
            public void accept(HttpResponse response, Object attachment) {
//            System.out.println("\""+result+"\"");
                String result = response.getBodyContent();
                if(!result.startsWith("<!DOCTYPE html><html id=\"atomic\"") && !result.contains("Welcome to nginx!")) {
                    System.out.println("Jiatlat");
                }
//                Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));
                totalDone.incrementAndGet();
                countDownLatch.countDown();
            }

            @Override
            public void error(Exception e, ChannelingSocket socket) {
                e.printStackTrace();
                countDownLatch.countDown();

            }
        });

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

        if (port < 0) {
            port = isSSL ? 443 : 80;
        }
        ChannelingSocket cs = channeling.wrapSSL(context, host, port, null);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", host + ":" + port);
        requestBuilder.setPath(uri.getPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : ""));


        HttpSingleRequest httpSingleRequest = new HttpSingleRequest(
                cs,
                uri.getHost(),
                port,
                requestBuilder.toString(),
                isSSL ? cs.getSSLMinimumInputBufferSize() : 1024
        );

        httpSingleRequest.execute(new HttpSingleRequestCallback() {
            @Override
            public void accept(HttpResponse response, Object attachment) {
//            System.out.println("\""+result+"\"");
                String result = response.getBodyContent();
                Map<String, String> headers = response.getHeaderAsMap();
                Assertions.assertTrue(result.toLowerCase().contains("ok"), result.length() > 15 ? result.substring(result.length() - 15) : "");
                totalDone.incrementAndGet();
                countDownLatch.countDown();
            }

            @Override
            public void error(Exception e, ChannelingSocket socket) {
                e.printStackTrace();
                countDownLatch.countDown();
            }
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
        ChannelingSocket cs = channeling.wrap("a");

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


        HttpSingleRequest httpSingleRequest = new HttpRedirectableRequest(
                cs,
                uri.getHost(),
                80,
                requestBuilder.toString(),
                1024,
                (hostTo, portTo, isSSLTo, prevContext) -> {
                    if (isSSLTo) {
                        return channeling.wrapSSL("TLSv1.2", hostTo, portTo, prevContext);
                    }
                    return channeling.wrap(prevContext);
                }
        );
        httpSingleRequest.execute(new HttpSingleRequestCallback() {
            @Override
            public void accept(HttpResponse response, Object attachment) {
//            System.out.println("\""+result+"\"");
                String result = response.getBodyContent();
                Map<String, String> headers = response.getHeaderAsMap();
                Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));
                totalDone.incrementAndGet();
                countDownLatch.countDown();
            }

            @Override
            public void error(Exception e, ChannelingSocket socket) {
                e.printStackTrace();
                countDownLatch.countDown();

            }
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


    @Test
    public void testStreamAndNormalRequestAtOnce() throws Exception {
        int totalCall = 100;
        CountDownLatch countDownLatch = new CountDownLatch(totalCall);

        URI uri = new URI("https://www.google.com.sg/");
        String host = uri.getHost();
        boolean isSSL = uri.getScheme().startsWith("https");

        int port = uri.getPort();

        if (port < 0) {
            port = isSSL ? 443 : 80;
        }
        ChannelingSocket cs2 = channeling.wrapSSL("TLSv1.2", host, port, null);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", host);
        requestBuilder.setPath("/");

        List<String> results = new ArrayList<>();

        for (int i = 0; i < totalCall; i++) {
            ChannelingSocket cs = channeling.wrapSSL("TLSv1.2", host, port, null);

            HttpRequest streamRequest = new HttpStreamRequest(
                    cs,
                    host,
                    port,
                    requestBuilder.toString(),
                    cs.getSSLMinimumInputBufferSize()
            );


//        HttpRequest request = new HttpSingleRequest(
//                cs2,
//                host,
//                port,
//                requestBuilder.toString(),
//                cs.getSSLMinimumInputBufferSize()
//        );

            streamRequest.execute(new HttpStreamRequestCallback() {
                @Override
                public void headerAccept(byte[] chunked, int offset, int length, ChannelingSocket socket) throws IOException {
                    /** DO NOTHING **/
                }   @Override
                public void postHeader(byte[] chunked, int offset, int length, ChannelingSocket socket) throws IOException {
                    /** DO NOTHING **/
                }

                @Override
                public void accept(byte[] chunked, int offset, int length, ChannelingSocket socket) {

                }

                @Override
                public void last(byte[] chunked, int offset, int length, ChannelingSocket socket) {
                    String result = new String(chunked, StandardCharsets.UTF_8);
//                    Assertions.assertTrue(result.contains("</noscript>\r\n</html>"), result);
                    boolean success = result.contains("</body></html>");

                    if(!success) {
                        System.out.println(result);
                    }
                    Assertions.assertTrue(success, result);
                    results.add(result.substring(0, Math.min(result.length(), 100)));
                    countDownLatch.countDown();
                }


                @Override
                public void error(Exception e, ChannelingSocket socket) {
                    Assertions.fail(e.getMessage());
                    e.printStackTrace();
                    countDownLatch.countDown();
                }
            });

            Thread.sleep(100);
        }

//        request.execute((httpResponse, attachment) -> {
//            String result = httpResponse.getBodyContent();
//
//            Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));
//
//            countDownLatch.countDown();
//        }, (e, attachment)  -> {
//            e.printStackTrace();
//            countDownLatch.countDown();
//        });

        countDownLatch.await();

        for (String r :
                results) {
            System.out.println(r);
        }

        logger.info("Done " + totalDone.get());
    }

    @AfterAll
    public static void afterAllTested() {
        channeling.shutdownNow();
    }


}
