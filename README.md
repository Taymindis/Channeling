# Channeling
The java Nio Based, Async Non Block tcp / http client.


#### HTTP

```java

public void testRequestBuilder() throws Exception {
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


        httpRequest.get(httpResponse -> {
            String result = httpResponse.getBodyContent();
            Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));
            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, Throwable::printStackTrace);


        countDownLatch.await();

        if (totalDone.get() % 100 == 0)
            logger.info("Done " + totalDone.get());
    }
    
```


#### HTTPS

```java


public void testSSL() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ChannelingSocket cs = channeling.wrapSSL("TLSv1.2", "mycamel.abcfoo.com", 443, null);
        String host = "mycamel.abcfoo.com";
        HttpRequest httpRequest = new HttpRequest(
                cs,
                host,
                443,
                "GET / HTTP/1.1\n" +
                        "Host: "+host+"\n\n",
//                "Connection: close\n" + // Do not close
                cs.getSSLMinimumInputBufferSize(),false
        );


        httpRequest.get(httpResponse -> {
            String result = httpResponse.getBodyContent();
            Assertions.assertTrue(result.toLowerCase().contains("</html>"), result.substring(result.length() - 15));

            totalDone.incrementAndGet();
            countDownLatch.countDown();
        }, Throwable::printStackTrace);


        countDownLatch.await();
        if(totalDone.get() % 100 == 0)
        System.out.println("Done " + totalDone.get());
    }
    
```



#### TCP example

```java

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
    
```

### With http proxy 

```java

 public void testProxy() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ChannelingSocket cs = channeling.wrapProxy(channelingProxy, null);

        HttpRequest httpRequest = new HttpRequest(
                cs,
                "openapi.abcfoo.com",
                80,
                "GET / HTTP/1.1\n" +
                "Host: openapi.abcfoo.com:80\n\n",
                1024
        );

        httpRequest.get(httpResponse -> {
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

```