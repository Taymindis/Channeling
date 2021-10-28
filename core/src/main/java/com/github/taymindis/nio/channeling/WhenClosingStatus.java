package com.github.taymindis.nio.channeling;

import java.util.function.Predicate;

public interface WhenClosingStatus extends Predicate<Boolean> {
    @Override
    boolean test(Boolean closingStatus);
}
