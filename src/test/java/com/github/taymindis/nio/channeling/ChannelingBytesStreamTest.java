package com.github.taymindis.nio.channeling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ChannelingBytesStreamTest {
    private static Logger logger = LoggerFactory.getLogger(ChannelingBytesStreamTest.class);
    ChannelingBytesStream channelingBytesStream = new ChannelingBytesStream();

    @BeforeEach
    public void beforeEach() throws Exception {
        channelingBytesStream.write(ConstantTestBytes.HEADERSBYTE.getBytes());
        channelingBytesStream.write(ConstantTestBytes.NEXT1.getBytes());
        channelingBytesStream.write(ConstantTestBytes.NEXT2.getBytes());
        channelingBytesStream.write(ConstantTestBytes.LAST.getBytes());
    }

    @Test
    public void testSearchBeforeAndAfter() throws IOException, InterruptedException {

        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), true);

        Assertions.assertNotNull(result);

        result = channelingBytesStream.searchBytesBefore("Copyright The Closure Library Authors.\r\n".getBytes(), true, result);

        int totalBytes = result.getTotalBytes();

        Assertions.assertTrue(totalBytes > 0, "Total Bytes should not be 0 !");
        System.out.print("\"");

        result.forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {

                System.out.print(new String(bytes, offset, length));

                return true;
            }
        });
        System.out.print("\"");

    }

    @Test
    public void testSearchAfter() {

        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), false);

        Assertions.assertNotNull(result);

        int totalBytes = result.getTotalBytes();

        Assertions.assertTrue(totalBytes > 0, "Total Bytes should not be 0 !");
        System.out.print("\"");

        result.forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                System.out.print(new String(bytes, offset, length));
                return true;
            }
        });

        System.out.print("\"");
    }

    @Test
    public void testReverseSearchAfter() {

        ChannelingBytesResult result = channelingBytesStream.reverseSearchBytesAfter("ml(d instanceof ".getBytes(), true);

        Assertions.assertNotNull(result);

        int totalBytes = result.getTotalBytes();

//        Assertions.assertTrue(totalBytes>0, "Total Bytes should not be 0 !");
        System.out.print("\"");

        result.forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                System.out.print(new String(bytes, offset, length));
                return true;
            }
        });

        System.out.print("\"");
    }

    @Test
    public void testReverseSearchAfterAndBefore() {
        ChannelingBytesResult result = channelingBytesStream.reverseSearchBytesBefore("rror?d:Error(a)".getBytes(), false);

        Assertions.assertTrue(getData(result).endsWith("E"));

        result = channelingBytesStream.reverseSearchBytesBefore("HTTP/1.1 200 OK\r\n".getBytes(), false, result);
        Assertions.assertEquals(getData(result).length(), 0);


        result = channelingBytesStream.reverseSearchBytesAfter("ml(d instanceof ".getBytes(), true);

        Assertions.assertTrue(getData(result).startsWith("ml(d instanceof "));

        result = channelingBytesStream.reverseSearchBytesAfter("</html>".getBytes(), false, result);
        Assertions.assertEquals(getData(result).length(), 0);
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
