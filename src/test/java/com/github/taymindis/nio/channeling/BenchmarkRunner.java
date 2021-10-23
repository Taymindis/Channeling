package com.github.taymindis.nio.channeling;

import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {

    @State(Scope.Thread)
    public static class MyState {

        ChannelingBytesStream channelingBytesStream;
        ByteArrayOutputStream outputStream;
        ChannelingBytesStream2 channelingBytesStream2;

        public void doSetup() throws IOException {
            channelingBytesStream = new ChannelingBytesStream(256);
            outputStream = new ByteArrayOutputStream();
            channelingBytesStream2 = new ChannelingBytesStream2(25600);
            channelingBytesStream.write(ConstantTestBytes.HEADERSBYTE.getBytes());
            channelingBytesStream.write(ConstantTestBytes.NEXT1.getBytes());
            channelingBytesStream.write(ConstantTestBytes.NEXT2.getBytes());
            channelingBytesStream.write(ConstantTestBytes.LAST.getBytes());
            channelingBytesStream2.write(ConstantTestBytes.HEADERSBYTE.getBytes());
            channelingBytesStream2.write(ConstantTestBytes.NEXT1.getBytes());
            channelingBytesStream2.write(ConstantTestBytes.NEXT2.getBytes());
            channelingBytesStream2.write(ConstantTestBytes.LAST.getBytes());
            outputStream.write(ConstantTestBytes.HEADERSBYTE.getBytes());
            outputStream.write(ConstantTestBytes.NEXT1.getBytes());
            outputStream.write(ConstantTestBytes.NEXT2.getBytes());
            outputStream.write(ConstantTestBytes.LAST.getBytes());
        }

        @TearDown(Level.Iteration)
        public void doTearDown() {
//            System.out.println("Do TearDown");
        }
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
//    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
//    public void testMethod() {
//
//        byte[] b = ConstantTestBytes.LAST.getBytes();
//
//        b = BytesHelper.subBytes(b, 10, 10 + 2555);
//
//        assert b.length == 2555;
//
//    }
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
//    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
//    public void testMethod2() {
//
//        byte[] b = ConstantTestBytes.LAST.getBytes();
//
//        b = ByteBuffer.wrap(b, 10, 2555).array();
//
//        System.out.println(b);
//
//        assert b.length == 2555;
//    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 30, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void channelingBytesStreamBenchMark(MyState state) throws IOException {
        state.doSetup();
//        String result = new String(state.channelingBytesStream.toByteArray());
        state.channelingBytesStream.searchBytes("\r\n\r\n".getBytes(), true, false);
        state.channelingBytesStream.reverseSearchBytesAfter("</html>".getBytes(), true);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 30, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void outputStreamTest(MyState state) throws IOException {
        state.doSetup();
        String result = new String(state.outputStream.toByteArray());
        int a = result.indexOf("\r\n\r\n");
        boolean b = result.endsWith("</html>");
//        Assertions.assertEquals(result.length, state.outputStream.size());

    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 30, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void channelingBytesStream2BenchMark(MyState state) throws IOException {
        state.doSetup();
        String result = state.channelingBytesStream2.toString();
        int a = result.indexOf("\r\n\r\n");
        boolean b = result.endsWith("</html>");
//        Assertions.assertEquals(result.getLength(),  state.channelingBytesStream2.size());

    }

//    public static void main(String[] args) throws RunnerException {
//        Options opt = new OptionsBuilder()
//                .include(BenchmarkRunner.class.getSimpleName())
//                .build();
//
//        new Runner(opt).run();
//    }
}


