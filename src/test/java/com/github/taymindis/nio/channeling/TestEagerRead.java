package com.github.taymindis.nio.channeling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static com.github.taymindis.nio.channeling.Channeling.*;

public class TestEagerRead {
    private static Logger logger = LoggerFactory.getLogger(TestEagerRead.class);

    @Test
    public void testingEagerReadSocket() throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Channeling channeling = Channeling.startNewChanneling(1, 2 * 1000, 15 * 1000);
        ChannelingSocket socket = channeling.wrap(null);
        socket.withConnect("127.0.0.1", 8080)
                .when(whenConnected)
                .then(sc -> {
                    StringBuilder a = new StringBuilder();
                    for(int i = 0 ; i< 5000; i++) {
                        a.append(i);
                    }

                    sc.withEagerRead(2048).when((WhenReadWriteProcess)bytesProcessed -> {
                        logger.info("Eager read ... waiting consume {} ", bytesProcessed );
                        return bytesProcessed > 0;
                    })
                            .then(sc2 -> {


                                System.out.println(sc2.getReadBuffer());
                                sc.withClose().when(whenClosed)
                                        .then(scclose -> {

                                            System.out.println("Done Close");
                                            Assertions.assertTrue(true);
                                            countDownLatch.countDown();
                                        });

                            });
                }, (sc, e) -> {
                    e.printStackTrace();
                    countDownLatch.countDown();
                    Assertions.fail(e.getMessage());
                });


        System.out.println("SETUP FINISHED");
        Thread.sleep(1500);
        socket.noEagerRead();
        countDownLatch.await();
        channeling.shutdown();
        Assertions.assertTrue(true);

    }
}
