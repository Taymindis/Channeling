package com.github.taymindis.nio.channeling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelingBytesStreamTest {
    private static Logger logger = LoggerFactory.getLogger(ChannelingBytesStreamTest.class);
    ChannelingBytesStream channelingBytesStream = new ChannelingBytesStream(30);

    @BeforeEach
    public void beforeEach() throws Exception {
        channelingBytesStream.write(ConstantTestBytes.HEADERSBYTE.getBytes());
        channelingBytesStream.write(ConstantTestBytes.NEXT1.getBytes());
        channelingBytesStream.write(ConstantTestBytes.NEXT2.getBytes());
        channelingBytesStream.write(ConstantTestBytes.LAST.getBytes());
    }


    @Test
    public void testSearch() {
        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), false);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(getData(result).startsWith("<!doctype html><html itemscope="));

        result = channelingBytesStream.searchBytesBefore("\r\n\r\n".getBytes(), true);

        Assertions.assertTrue(getData(result).startsWith("HTTP/1.1 200 OK\r\n"));
        Assertions.assertTrue(getData(result).endsWith("\r\n\r\n"));

        result = channelingBytesStream.searchBytesAfter("X-XSS-Protection: ".getBytes(), false, result);

        Assertions.assertTrue(getData(result).startsWith("0\r\n"));

        Assertions.assertTrue(getData(result).endsWith("Content-Type: text/html; charset=ISO-8859-1\r\n\r\n"));
    }

    @Test
    public void testReverseSearch1() {

        ChannelingBytesResult result = channelingBytesStream.reverseSearchBytesAfter("</body></html>".getBytes(), true);

        Assertions.assertNotNull(result);

        int totalBytes = result.getTotalBytes();

        Assertions.assertEquals(totalBytes, "</body></html>".getBytes().length);

        result = channelingBytesStream.reverseSearchBytesBefore("</html>".getBytes(), true, result);

        Assertions.assertEquals(result.getTotalBytes(), "</body></html>".getBytes().length);

        showData(result);

    }

    @Test
    public void testReverseSearch2() {
        ChannelingBytesResult result = channelingBytesStream.reverseSearchBytesBefore("rror?d:Error(a)".getBytes(), false);

        Assertions.assertTrue(getData(result).endsWith("E"));

        result = channelingBytesStream.reverseSearchBytesBefore("HTTP/1.1 200 OK\r\n".getBytes(), false, result);
        Assertions.assertEquals(getData(result).length(), 0);


        result = channelingBytesStream.reverseSearchBytesAfter("ml(d instanceof ".getBytes(), true);

        Assertions.assertTrue(getData(result).startsWith("ml(d instanceof "));

        result = channelingBytesStream.reverseSearchBytesAfter("</html>".getBytes(), false, result);
        Assertions.assertEquals(getData(result).length(), 0);
    }

    @Test
    public void testFlip() {
        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), false);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(getData(result).startsWith("<!doctype html><html itemscope="));

        ChannelingBytesResult flippedResult = result.flipBackward();

        Assertions.assertTrue(getData(flippedResult).startsWith("HTTP/1.1 200 OK\r\n"));
        Assertions.assertTrue(getData(flippedResult).endsWith("\r\n\r\n"));

        flippedResult = flippedResult.flipForward();

        Assertions.assertTrue(getData(flippedResult).startsWith("<!doctype html><html itemscope="));
        Assertions.assertTrue(getData(flippedResult).endsWith("</body></html>"));



    }

    @Test
    public void testResetRead() {
        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), false);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(getData(result).startsWith("<!doctype html><html itemscope="));

        ChannelingBytes bytes = new ChannelingBytes();

        result.read(bytes);

        Assertions.assertTrue(new String(bytes.getBuff(), bytes.getOffset(), bytes.getLength()).startsWith("<!doctype html>"));

        result.resetRead();

        result.read(bytes);

        Assertions.assertTrue(new String(bytes.getBuff(), bytes.getOffset(), bytes.getLength()).startsWith("<!doctype html>"));

        result.read(bytes);

        Assertions.assertFalse(new String(bytes.getBuff(), bytes.getOffset(), bytes.getLength()).startsWith("<!doctype html>"));


    }
    @Test
    public void testReset() {
        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), false);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(getData(result).startsWith("<!doctype html><html itemscope="));
        channelingBytesStream.reset();
        result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), false);
        Assertions.assertNull(result);
    }

    private void showData(ChannelingBytesResult result) {
        System.out.print("\"");

        result.forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                System.out.print(new String(bytes, offset, length));
                return true;
            }
        });

        System.out.print("\"");
        System.out.println("\n\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n");
    }

    private String getData(ChannelingBytesResult result) {
        StringBuilder a = new StringBuilder();
        result.forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                a.append(new String(bytes, offset, length));
                return true;
            }
        });
        return a.toString();
    }
}
