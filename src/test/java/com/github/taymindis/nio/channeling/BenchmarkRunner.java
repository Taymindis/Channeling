package com.github.taymindis.nio.channeling;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void testMethod() {

        byte[] b = ConstantTestBytes.LAST.getBytes();

        b = BytesHelper.subBytes(b, 10, 10 + 2555);

        assert b.length == 2555;

    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void testMethod2() {

        byte[] b = ConstantTestBytes.LAST.getBytes();

        b = ByteBuffer.wrap(b, 10, 2555).array();

        System.out.println(b);

        assert b.length == 2555;
    }

//    public static void main(String[] args) throws RunnerException {
//        Options opt = new OptionsBuilder()
//                .include(BenchmarkRunner.class.getSimpleName())
//                .build();
//
//        new Runner(opt).run();
//    }
}


