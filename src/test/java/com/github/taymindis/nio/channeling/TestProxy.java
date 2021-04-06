package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpRequest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class TestProxy {

    private static Channeling channeling;
    private static Logger logger = LoggerFactory.getLogger(TestProxy.class);
    private static ChannelingProxy channelingProxy = new ChannelingProxy("127.0.0.1", 3128);

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
    public void testProxy() throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ChannelingSocket cs = channeling.wrapProxy(channelingProxy, null);

//        HttpRequest httpRequest = new HttpRequest(
//                cs,
//                "jenkov.com",
//                80,
//                "GET /about/index.html HTTP/1.1\n" +
//                        "Host: jenkov.com:80\n\n"
//        );
        HttpRequest httpRequest = new HttpRequest(
                cs,
                "openapi.abcfoo.com",
                80,
                "GET / HTTP/1.1\n" +
                "Host: openapi.abcfoo.com:80\n\n",
                1024
        );

        httpRequest.get(httpResponse -> {
//            System.out.println("\""+result+"\"");
            String result = httpResponse.getBodyContent();
            Assertions.assertTrue(result.contains("</html>"), result.substring(result.length() - 15));
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, e -> {
            countDownLatch.countDown();
            e.printStackTrace();
        });


        countDownLatch.await();

        if(totalDone.get() % 100 == 0)
            System.out.println("Done " + totalDone.get());
    }

    @Test
    public void multiThreadTestProxy() throws ExecutionException, InterruptedException {
        int mThread = 4;
        ExecutorService mSvc = Executors.newFixedThreadPool(mThread);

        List<Future> listF = new ArrayList<>();


        for (int i = 0; i < mThread; i++) {
            listF.add(mSvc.submit(() -> {
                try {
                    for (int x = 0; x < 400; x++)
                        testProxy();
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
        channeling.shutdown();
    }


}
