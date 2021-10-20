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
    public void testSearchBefore() throws IOException, InterruptedException {

        ChannelingBytesResult result = channelingBytesStream.searchBytesAfter("\r\n\r\n".getBytes(), true);

        Assertions.assertNotNull(result);

        result = channelingBytesStream.searchBytesBefore("<!doc".getBytes(), false, result);

        int totalBytes = result.getTotalBytes();

        Assertions.assertTrue(totalBytes>0, "Total Bytes should not be 0 !");
        System.out.print("\"");

        result.flush(new ChannelingBytesLoop() {
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

        Assertions.assertTrue(totalBytes>0, "Total Bytes should not be 0 !");
        System.out.print("\"");

        result.flush(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                System.out.print(new String(bytes, offset, length));
                return true;
            }
        });

        System.out.print("\"");

    }
}
