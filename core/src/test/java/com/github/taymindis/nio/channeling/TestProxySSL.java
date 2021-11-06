package com.github.taymindis.nio.channeling;

import com.github.taymindis.nio.channeling.http.HttpResponse;
import com.github.taymindis.nio.channeling.http.HttpSingleRequestCallback;
import com.github.taymindis.nio.channeling.http.HttpSingleRequest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class TestProxySSL {

    private static Channeling channeling;
    private static Logger logger = LoggerFactory.getLogger(TestProxySSL.class);
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
    public void testProxySSL() throws Exception {
//         System.setProperty("javax.net.debug", "all");
//        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ChannelingSocket cs = channeling.wrapProxySSL(channelingProxy, "TLSv1.2", "openapi.abcfoo.com", 443, null);

        HttpSingleRequest httpSingleRequest = new HttpSingleRequest(
                cs,
                "openapi.abcfoo.com",
                443,
                "GET / HTTP/1.1\n" +
                        "Host: openapi.abcfoo.com\n" +
                        "Cookie: NID=210=zsJ4sKLPNL85W2JAWWs0wZbodLdnpQ2ddUDoHBQexjKYPQTC2or0prNFtplFahgeO16ygFejz4mPaczdjHZWQ-qiDwX4aw2A5FC-7zesz6olSqz2tvEom2YlaFWn7hNFebJ1_lgdheS4iPPhCGGVlZCjhUYhBaqvcZkfZpeW0zo; 1P_JAR=2021-02-26-06\n\n"
        );

        httpSingleRequest.execute(new HttpSingleRequestCallback() {
            @Override
            public void accept(HttpResponse response, Object attachment) {
//            System.out.println("\""+result+"\"");
                String result = response.getBodyContent();
                Assertions.assertTrue(result.contains("</html>"), result.substring(result.length() - 15));
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
    public void multiThreadTestProxySSL() throws ExecutionException, InterruptedException {
        int mThread = 4;
        ExecutorService mSvc = Executors.newFixedThreadPool(mThread);

        List<Future> listF = new ArrayList<>();


        for (int i = 0; i < mThread; i++) {
            listF.add(mSvc.submit(() -> {
                try {
                    for (int x = 0; x < 400; x++)
                        testProxySSL();
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
