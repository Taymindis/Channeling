package com.github.taymindis.nio.channeling;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

public interface ChannelingSSL {
    void connect(Consumer<Boolean> status, Consumer<Exception> error) throws IOException;
    void write(byte[] message, Consumer<Integer> status, Consumer<Exception> error) throws IOException;
    void read(Consumer<ByteArrayOutputStream> status, Consumer<Exception> error) throws IOException;
    void read(int size, Consumer<ByteArrayOutputStream> status, Consumer<Exception> error) throws IOException;
    void close(Consumer<Boolean> consumer, Consumer<Exception> error) throws IOException;
}
