package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpResponse;
import com.github.taymindis.nio.channeling.http.HttpSingleRequestCallback;
import com.github.taymindis.nio.channeling.http.HttpSingleRequest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class TestSSL {

    private static Channeling channeling;
    private static Logger logger = LoggerFactory.getLogger(TestSSL.class);

    AtomicInteger totalDone;

    @BeforeEach
    public void beforeEach() throws Exception {
        totalDone = new AtomicInteger(0);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        channeling = Channeling.startNewChanneling(8, 100 * 1000, 1000 * 1000);
        channeling.enableSSL(4);
    }

    @Test
    public void testSSL() throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        String targetHost = "www.google.com.sg";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ChannelingSocket cs = channeling.wrapSSL("TLSv1.2", targetHost, 443, null);
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** DO NOT USE RAW STRING, use HttpRequestBuilder instead **/
        /** Please check the test case TestHttpBuilder **/
        HttpSingleRequest httpSingleRequest = new HttpSingleRequest(
                cs,
                targetHost,
                443,
                "GET / HTTP/1.1\n" +
                        "Host: "+targetHost+"\n\n",
//                "Connection: close\n" + // Do not close
                cs.getSSLMinimumInputBufferSize(),false
        );

        httpSingleRequest.execute(new HttpSingleRequestCallback() {
            @Override
            public void accept(HttpResponse response, Object attachment) {
                String result = response.getBodyContent();
//            System.out.println("\""+result+"\"");
                Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));

                totalDone.incrementAndGet();
                countDownLatch.countDown();
            }

            @Override
            public void error(Exception e, ChannelingSocket socket) {
                countDownLatch.countDown();
                e.printStackTrace();
            }
        });

        countDownLatch.await();
        if(totalDone.get() % 100 == 0)
        System.out.println("Done " + totalDone.get());
    }


    @Test
    public void multiThreadTestSSL() throws ExecutionException, InterruptedException {
        int mThread = 4;
        ExecutorService mSvc = Executors.newFixedThreadPool(mThread);

        List<Future> listF = new ArrayList<>();


        for (int i = 0; i < mThread; i++) {
            listF.add(mSvc.submit(() -> {
                try {
                    for (int x = 0; x < 400; x++)
                        testSSL();
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
