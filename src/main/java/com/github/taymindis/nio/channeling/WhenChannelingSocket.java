package com.github.taymindis.nio.channeling;

import java.util.function.Predicate;

public interface WhenChannelingSocket extends Predicate<ChannelingSocket> {
    @Override
    boolean test(ChannelingSocket channelingSocket);
}
