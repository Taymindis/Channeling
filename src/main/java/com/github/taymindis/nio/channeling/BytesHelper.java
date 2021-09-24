package com.github.taymindis.nio.channeling;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BytesHelper {

    public static byte[] concat(byte[] b1, byte[] b2) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(b1);
        outputStream.write(b2);
        return outputStream.toByteArray();
    }

    public static int indexOf(byte[] data, byte[] target) {
        if (target == null || data == null || target.length == 0) {
            return -1;
        }
        int i = 0, sz = data.length - target.length + 1;
        tryNext:
        for (; i < sz; i++) {
            for (int x = 0, y = i; x < target.length; x++, y++) {
                if (data[y] != target[x]) {
                    continue tryNext;
                }
            }
            return i;
        }
        return -1;
    }

    public static boolean equals(byte[] data, byte[] target) {
        if (target == null || data == null || data.length != target.length) {
            return false;
        }

        for (int i = 0, sz = data.length; i < sz; i++) {
            if (data[i] != target[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean equals(byte[] data, byte[] target, int startingPoint) {
        if (target == null || data == null || (data.length - startingPoint) != target.length) {
            return false;
        }

        for (int i = startingPoint, j = 0, sz = data.length; i < sz; i++, j++) {
            if (data[i] != target[j]) {
                return false;
            }
        }
        return true;
    }

    public static boolean contains(byte[] data, byte[] target) {
        return indexOf(data, target) != -1;
    }

    public static byte[] subBytes(byte[] data, int beginIndex) {
        return subBytes(data, beginIndex, data.length);
    }

    public static byte[] subBytes(byte[] data, int beginIndex, int endIndex) {
        return Arrays.copyOfRange(data, beginIndex, endIndex);
    }


    public static void main(String... args) {

        byte[] outer = {1, 2, 3, 4};
        assertEquals(0, indexOf(outer, new byte[]{1, 2}));
        assertEquals(1, indexOf(outer, new byte[]{2, 3}));
        assertEquals(2, indexOf(outer, new byte[]{3, 4}));
        assertEquals(-1, indexOf(outer, new byte[]{4, 4}));
        assertEquals(-1, indexOf(outer, new byte[]{4, 5}));
        assertEquals(-1, indexOf(outer, new byte[]{4, 5, 6, 7, 8}));


        String a = "T";
        String b = "t";

        System.out.println(equals(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.US_ASCII)));
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException("Error comparison");
        }
    }
}
