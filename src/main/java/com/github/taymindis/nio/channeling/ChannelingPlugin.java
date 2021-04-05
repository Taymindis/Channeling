package com.github.taymindis.nio.channeling;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public interface ChannelingPlugin {
    void checkKeys(Set<SelectionKey> allKeys) throws IOException, TimeoutException;
}
