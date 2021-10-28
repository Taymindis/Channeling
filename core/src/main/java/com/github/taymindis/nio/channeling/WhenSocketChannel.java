package com.github.taymindis.nio.channeling;

import java.nio.channels.SocketChannel;
import java.util.function.Predicate;

public interface WhenSocketChannel extends Predicate<SocketChannel> {
    @Override
    boolean test(SocketChannel socketChannel);
//     = d -> i -> s -> {
//        System.out.println("" + d+ ";" + i+ ";" + s);
//    };
//f.apply(1.0).apply(2).accept("s");
}