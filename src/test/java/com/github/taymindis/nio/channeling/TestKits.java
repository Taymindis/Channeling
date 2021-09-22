package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpSingleRequest;
import com.github.taymindis.nio.channeling.http.HttpRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.taymindis.nio.channeling.Channeling.createTrustManagers;

public class TestKits {
    private static final Logger logger = LoggerFactory.getLogger(TestKits.class);

    private final Channeling channeling;
    private final AtomicInteger totalDone;


    private final SSLContext context;

    public TestKits(Channeling channeling) throws Exception {
        this.channeling = channeling;

        this.totalDone = new AtomicInteger(0);

        context = SSLContext.getInstance("TLSv1.2");
        context.init(null,
                createTrustManagers("./src/main/resources/keystore.jks", "password"), new SecureRandom());
    }

    @Test
    public void testRequestBuilder(String host, int port) throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ChannelingSocket cs = channeling.wrap(null);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", String.format("%s:%d", host, port));
        requestBuilder.setPath("/");



        HttpSingleRequest httpSingleRequest = new HttpSingleRequest(
                cs,
                host,
                port,
                requestBuilder.toString(),
                1024
        );


        httpSingleRequest.execute((httpResponse, attachment) -> {
//            System.out.println("\""+result+"\"");
            String result = httpResponse.getBodyContent();
            Assertions.assertTrue(result.toLowerCase().contains("ok"));
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, (e , attachment)-> {
//            e.printStackTrace();
            countDownLatch.countDown();
            Assertions.fail(e.getMessage(), e);
        });


        countDownLatch.await();

        if (totalDone.get() % 100 == 0)
            System.out.println("Done " + totalDone.get());
    }


    public void multiThreadTestLocalhost(String host, int port, int mThread, int threadPerRequest) throws ExecutionException, InterruptedException {
        ExecutorService mSvc = Executors.newFixedThreadPool(mThread);

        List<Future> listF = new ArrayList<>();


        for (int i = 0; i < mThread; i++) {
            listF.add(mSvc.submit(() -> {
                try {
                    for (int x = 0; x < threadPerRequest; x++)
                        testRequestBuilder(host, port);
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


    public void multiThreadTestSSL(String host, int port, int mThread, int threadPerRequest) throws ExecutionException, InterruptedException {
        ExecutorService mSvc = Executors.newFixedThreadPool(mThread);

        List<Future> listF = new ArrayList<>();


        for (int i = 0; i < mThread; i++) {
            listF.add(mSvc.submit(() -> {
                try {
                    for (int x = 0; x < threadPerRequest; x++)
                        testSSLRequestBuilder(host, port);
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

    private void testSSLRequestBuilder(String host, int port) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ChannelingSocket cs = channeling.wrapSSL(context, host, port, null);

        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        requestBuilder.setMethod("GET");
        requestBuilder.addHeader("Host", String.format("%s:%d", host, port));
        requestBuilder.setPath("/");


        HttpSingleRequest httpSingleRequest = new HttpSingleRequest(
                cs,
                host,
                port,
                "GET / HTTP/1.1\n" +
                        "Host: "+host+":"+port+"\n\n",
//                "Connection: close\n" + // Do not close
                cs.getSSLMinimumInputBufferSize(),false
        );


        httpSingleRequest.execute((httpResponse, attachment) -> {
//            System.out.println("\""+result+"\"");
            String result = httpResponse.getBodyContent();
            Assertions.assertTrue(result.toLowerCase().contains("ok"));
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, (e, attachment) -> {
//            e.printStackTrace();
            countDownLatch.countDown();
            Assertions.fail(e.getMessage(), e);
        });


        countDownLatch.await();

        if (totalDone.get() % 100 == 0)
            logger.info("Done " + totalDone.get());

    }

}
