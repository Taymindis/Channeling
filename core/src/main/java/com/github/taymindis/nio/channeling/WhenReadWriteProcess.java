package com.github.taymindis.nio.channeling;

import java.util.function.Predicate;

public interface WhenReadWriteProcess extends Predicate<Integer> {
    @Override
    boolean test(Integer bytesProcessed);
}