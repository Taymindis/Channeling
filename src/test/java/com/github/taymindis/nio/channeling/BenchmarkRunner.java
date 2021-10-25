package com.github.taymindis.nio.channeling;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {
    private static ChannelingBytesStream channelingBytesStream = new ChannelingBytesStream(25600);
    private static ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private static ChannelingByteWriter channelingByteWriter = new ChannelingByteWriter(25600);

    public static void main(String[] args) throws Exception {
        channelingBytesStream.write(ConstantTestBytes.HEADERSBYTE.getBytes());
        channelingBytesStream.write(ConstantTestBytes.NEXT1.getBytes());
        channelingBytesStream.write(ConstantTestBytes.NEXT2.getBytes());
        channelingBytesStream.write(ConstantTestBytes.LAST.getBytes());
        channelingByteWriter.write(ConstantTestBytes.HEADERSBYTE.getBytes());
        channelingByteWriter.write(ConstantTestBytes.NEXT1.getBytes());
        channelingByteWriter.write(ConstantTestBytes.NEXT2.getBytes());
        channelingByteWriter.write(ConstantTestBytes.LAST.getBytes());
        outputStream.write(ConstantTestBytes.HEADERSBYTE.getBytes());
        outputStream.write(ConstantTestBytes.NEXT1.getBytes());
        outputStream.write(ConstantTestBytes.NEXT2.getBytes());
        outputStream.write(ConstantTestBytes.LAST.getBytes());
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

//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
//    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
//    public void channelingBytesStreamBenchMark() throws IOException {
////        byte[] result = new byte[channelingBytesStream.size()];
////        final int[] totalWrite = {0};
//
//        byte[] result = channelingBytesStream.toByteArray();
//
//        Assertions.assertEquals(result.length, channelingBytesStream.size());
//
//    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void outputStreamTest() throws IOException {
        byte[] result = outputStream.toByteArray();
        Assertions.assertEquals(result.length, channelingBytesStream.size());

    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void channelingBytesStream2BenchMark() throws IOException {
        ChannelingBytes result = channelingByteWriter.readToChannelingBytes();
        Assertions.assertEquals(result.getLength(), channelingBytesStream.size());

    }

//    public static void main(String[] args) throws RunnerException {
//        Options opt = new OptionsBuilder()
//                .include(BenchmarkRunner.class.getSimpleName())
//                .build();
//
//        new Runner(opt).run();
//    }
}


