package com.github.taymindis.nio.channeling;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

public interface WhenReadingByteBuffer extends Predicate<ByteBuffer> {
    @Override
    boolean test(ByteBuffer readBuf);
}