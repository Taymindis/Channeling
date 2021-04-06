package com.github.taymindis.nio.channeling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

public class Test1 {
    private static Logger logger = LoggerFactory.getLogger(Test1.class);

    @Test
    public void testingSocket() throws IOException, InterruptedException {
        logger.info("Start Testing ");
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Channeling channeling = Channeling.startNewChanneling(1, 2 * 1000, 15 * 1000);
        ChannelingSocket socket = channeling.wrap(null);
        socket.withConnect("127.0.0.1", 8080)
                .when(Channeling.whenConnected)
                .then(sc -> {
                    StringBuilder a = new StringBuilder();
                    for(int i = 0 ; i< 5000; i++) {
                        a.append(i);
                    }

                    sc.withWrite(ByteBuffer.wrap(a.toString().getBytes(StandardCharsets.UTF_8))).when(Channeling.whenNoMoreToWrite)
                            .then(sc1 -> {
                                System.out.println("done");
                                sc1.withRead(2048).when(Channeling.whenByteConsumed)
                                        .then(sc2 -> {

                                            System.out.println(sc2.getReadBuffer());

//                                            System.out.println(new String(sc2.getReadBuffer().flip().array()));


                                            sc.withClose().when(Channeling.whenClosed)
                                                    .then(scclose -> {

                                                        logger.info("Done Close");
                                                        Assertions.assertTrue(true);
                                                        countDownLatch.countDown();
                                                    });

                                        });
                            });
                }, (sc, e) -> {
                    e.printStackTrace();
                    countDownLatch.countDown();
                    Assertions.fail(e.getMessage());
                });


        logger.info("SETUP FINISHED");

        countDownLatch.await();
        channeling.shutdown();
        Assertions.assertTrue(true);
    }


    @Test
    public void testingTimeoutSocket() throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Channeling channeling = Channeling.startNewChanneling(1, 2 * 1000, 3 * 1000);
        ChannelingSocket socket = channeling.wrap(null);
        socket.withConnect("127.0.0.1", 8080)
                .when(Channeling.whenConnected)
                .then(sc -> {
                    StringBuilder a = new StringBuilder();
                    for(int i = 0 ; i< 5000; i++) {
                        a.append(i);
                    }

                    sc.withWrite(ByteBuffer.wrap(a.toString().getBytes(StandardCharsets.UTF_8))).when(Channeling.whenNoMoreToWrite)
                            .then(sc1 -> {
                                System.out.println("waiting for Timeout");
                            });
                }, (sc, e) -> {
                    if(e instanceof TimeoutException) {
                        Assertions.assertTrue(true);
                        countDownLatch.countDown();
                    } else {
                        Assertions.fail(e.getMessage());
                    }
                });


        logger.info("TESTING NORMAL SOCKET");

        countDownLatch.await();
        channeling.shutdown();
        Assertions.assertTrue(true);
    }
}
