//package com.github.taymindis.nio.channeling;
//
//import java.nio.ByteBuffer;
//import java.util.Arrays;
//
//public class BufferTraining {
//
//    ByteBuffer buffer = ByteBuffer.allocate(100);
//
//
//
//
//
//    public static void main(String ...args ) {
//        new BufferTraining().run();
//    }
//
//    private void run() {
//
//        String a = "a\n0\n\n";
//
//        a = a.substring(2);
//
//
//        printBuffer();
//        fillByte(100);
//        printBuffer();
//        fillByte(20);
//        printBuffer();
//        readByte(20);
//        printBuffer();
//        buffer.flip();
//        printBuffer();
//        readByte(20);
//        printBuffer();
//        buffer.compact();
//        printBuffer();
//        buffer.compact();
//        printBuffer();
////        buffer.flip();
////        printBuffer();
////        buffer.get();
////        printBuffer();
//    }
//
//    private void fillByte(int i) {
//        byte[] a = new byte[i];
//        Arrays.fill(a, (byte) 1);
//        buffer.put(a);
//    }
//    private void readByte(int i) {
//        byte[] a = new byte[i];
//        buffer.get(a);
//       System.out.println("read " + new String(a).length());
//    }
//
//
//    private void printBuffer() {
//
//        System.out.printf("position = %4d, limit = %4d, capacity = %4d, remaing = %4d%n",
//                buffer.position(), buffer.limit(), buffer.capacity(), buffer.remaining());
//
//    }
//
//
//}
