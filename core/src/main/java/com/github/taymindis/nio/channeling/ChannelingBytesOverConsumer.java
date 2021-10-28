package com.github.taymindis.nio.channeling;

public interface ChannelingBytesOverConsumer {
   void process(byte[][] buffs, int byteCount, byte[] bytes);
}
