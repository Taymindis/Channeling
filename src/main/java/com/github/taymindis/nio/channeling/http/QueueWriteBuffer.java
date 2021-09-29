package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.Then;

import java.nio.ByteBuffer;

public class QueueWriteBuffer {
   private ByteBuffer nb;
   private Then $then;

    public QueueWriteBuffer(ByteBuffer nb, Then $then) {
        this.nb = nb;
        this.$then = $then;
    }

    public ByteBuffer getNb() {
        return nb;
    }

    public Then get$then() {
        return $then;
    }
}
