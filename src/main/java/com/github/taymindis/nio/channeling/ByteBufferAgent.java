package com.github.taymindis.nio.channeling;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * This is Thread unsafe
 */
public class ByteBufferAgent {
    private long totalBytes = -1;
    private Queue<ByteBuffer> refs = new LinkedList<ByteBuffer>();

    public ByteBufferAgent(ByteBuffer bb) {

    }

    public ByteBufferAgent add(ByteBuffer bb) {
        refs.add(bb);
        return this;
    }

    public ByteBufferAgent addAll(Collection<? extends ByteBuffer>  bbs) {
        refs.addAll(bbs);
        return this;
    }







//      for (int i = 0; i < arr.length; i++)
//            fifo.add (new Integer (arr[i]));
//
//        System.out.print (fifo.remove() + ".");
//        while (! fifo.isEmpty())
//                System.out.print (fifo.remove());
//        System.out.println();

}
