package com.github.taymindis.nio.channeling;

import java.io.ByteArrayOutputStream;

public class ChannellingProcessStream extends ByteArrayOutputStream {

    public byte[] getBuf(){
        return buf;
    }

}
