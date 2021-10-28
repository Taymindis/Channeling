package com.github.taymindis.nio.channeling;

import java.util.function.Predicate;

public interface WhenConnectingStatus extends Predicate<Boolean> {
    @Override
    boolean test(Boolean connectingStatus);
}