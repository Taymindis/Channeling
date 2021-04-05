/*
 * APACHE LICENSE, VERSION 2.0
 * Original Source code are from https://github.com/baswerc/niossl
 */
package com.github.taymindis.nio.channeling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

class SSLEngineBuffer {
    private final SocketChannel socketChannel;

    private final SSLEngine sslEngine;

    private final ExecutorService executorService;

    private final ByteBuffer networkInboundBuffer;

    private final ByteBuffer networkOutboundBuffer;

    private final int minimumApplicationBufferSize;

    private final ByteBuffer unwrapBuffer;

    private final ByteBuffer wrapBuffer;

    private static Logger logger = LoggerFactory.getLogger(SSLEngineBuffer.class);

    public SSLEngineBuffer(SocketChannel socketChannel, SSLEngine sslEngine, ExecutorService executorService) {
        this.socketChannel = socketChannel;
        this.sslEngine = sslEngine;
        this.executorService = executorService;


        SSLSession session = sslEngine.getSession();
        int networkBufferSize = session.getPacketBufferSize();

        networkInboundBuffer = ByteBuffer.allocate(networkBufferSize);

        networkOutboundBuffer = ByteBuffer.allocate(networkBufferSize);
        networkOutboundBuffer.flip();


        minimumApplicationBufferSize = session.getApplicationBufferSize();
        unwrapBuffer = ByteBuffer.allocate(minimumApplicationBufferSize);
        wrapBuffer = ByteBuffer.allocate(minimumApplicationBufferSize);
        wrapBuffer.flip();
    }

    int unwrap(ByteBuffer applicationInputBuffer) throws IOException {
        if (applicationInputBuffer.capacity() < minimumApplicationBufferSize) {
            throw new IllegalArgumentException("Application buffer size must be at least: " + minimumApplicationBufferSize);
        }

        if (unwrapBuffer.position() != 0) {
            unwrapBuffer.flip();
            while (unwrapBuffer.hasRemaining() && applicationInputBuffer.hasRemaining()) {
                applicationInputBuffer.put(unwrapBuffer.get());
            }
            unwrapBuffer.compact();
        }

        int totalUnwrapped = 0;
        int unwrapped, wrapped;

        do {
            totalUnwrapped += unwrapped = doUnwrap(applicationInputBuffer);
            wrapped = doWrap(wrapBuffer);
        }
        while (unwrapped > 0 || wrapped > 0 && (networkOutboundBuffer.hasRemaining() && networkInboundBuffer.hasRemaining()));

        return totalUnwrapped;
    }

    int wrap(ByteBuffer applicationOutboundBuffer) throws IOException {
        int wrapped = doWrap(applicationOutboundBuffer);
        doUnwrap(unwrapBuffer);
        return wrapped;
    }

    int flushNetworkOutbound() throws IOException {
        return send(socketChannel, networkOutboundBuffer);
    }

    int send(SocketChannel channel, ByteBuffer buffer) throws IOException {
        int totalWritten = 0;
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer);

            if (written == 0) {
                break;
            } else if (written < 0) {
                return (totalWritten == 0) ? written : totalWritten;
            }
            totalWritten += written;
        }
        logger.debug("sent: " + totalWritten + " out to socket");
        return totalWritten;
    }

    void close() {
        try {
            sslEngine.closeInbound();
        } catch (Exception e) {
        }

        try {
            sslEngine.closeOutbound();
        } catch (Exception e) {
        }
    }

    private int doUnwrap(ByteBuffer applicationInputBuffer) throws IOException {
        logger.debug("unwrap:");

        int totalReadFromChannel = 0;

        // Keep looping until peer has no more data ready or the applicationInboundBuffer is full
        UNWRAP:
        do {
            // 1. Pull data from peer into networkInboundBuffer

            int readFromChannel = 0;
            while (networkInboundBuffer.hasRemaining()) {
                int read = socketChannel.read(networkInboundBuffer);
                logger.debug("unwrap: socket read " + read + "(" + readFromChannel + ", " + totalReadFromChannel + ")");
                if (read <= 0) {
                    if ((read < 0) && (readFromChannel == 0) && (totalReadFromChannel == 0)) {
                        // No work done and we've reached the end of the channel from peer
                        logger.debug("unwrap: exit: end of channel");
                        return read;
                    }
                    break;
                } else {
                    readFromChannel += read;
                }
            }


            networkInboundBuffer.flip();
            if (!networkInboundBuffer.hasRemaining()) {
                networkInboundBuffer.compact();
                //wrap(applicationOutputBuffer, applicationInputBuffer, false);
                return totalReadFromChannel;
            }

            totalReadFromChannel += readFromChannel;

            try {
                SSLEngineResult result = sslEngine.unwrap(networkInboundBuffer, applicationInputBuffer);
                logger.debug("unwrap: result: " + result);

                switch (result.getStatus()) {
                    case OK:
                        SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                        switch (handshakeStatus) {
                            case NEED_UNWRAP:
                                break;

                            case NEED_WRAP:
                                break UNWRAP;

                            case NEED_TASK:
                                runHandshakeTasks();
                                break;

                            case NOT_HANDSHAKING:
                            default:
                                break;
                        }
                        break;

                    case BUFFER_OVERFLOW:
                        logger.debug("unwrap: buffer overflow");
                        break UNWRAP;

                    case CLOSED:
                        logger.debug("unwrap: exit: ssl closed");
                        return totalReadFromChannel == 0 ? -1 : totalReadFromChannel;

                    case BUFFER_UNDERFLOW:
                        logger.debug("unwrap: buffer underflow");
                        break;
                }
            } finally {
                networkInboundBuffer.compact();
            }
        }
        while (applicationInputBuffer.hasRemaining());

        return totalReadFromChannel;
    }

    private int doWrap(ByteBuffer applicationOutboundBuffer) throws IOException {
        logger.debug("wrap:");
        int totalWritten = 0;

        // 1. Send any data already wrapped out channel

        if (networkOutboundBuffer.hasRemaining()) {
            totalWritten = send(socketChannel, networkOutboundBuffer);
            if (totalWritten < 0) {
                return totalWritten;
            }
        }

        // 2. Any data in application buffer ? Wrap that and send it to peer.

        WRAP:
        while (true) {
            networkOutboundBuffer.compact();
            SSLEngineResult result = sslEngine.wrap(applicationOutboundBuffer, networkOutboundBuffer);
            logger.debug("wrap: result: " + result);

            networkOutboundBuffer.flip();
            if (networkOutboundBuffer.hasRemaining()) {
                int written = send(socketChannel, networkOutboundBuffer);
                if (written < 0) {
                    return totalWritten == 0 ? written : totalWritten;
                } else {
                    totalWritten += written;
                }
            }

            switch (result.getStatus()) {
                case OK:
                    switch (result.getHandshakeStatus()) {
                        case NEED_WRAP:
                            break;

                        case NEED_UNWRAP:
                            break WRAP;

                        case NEED_TASK:
                            runHandshakeTasks();
                            logger.debug("wrap: exit: need tasks");
                            break;

                        case NOT_HANDSHAKING:
                            if (applicationOutboundBuffer.hasRemaining()) {
                                break;
                            } else {
                                break WRAP;
                            }
                    }
                    break;

                case BUFFER_OVERFLOW:
                    logger.debug("wrap: exit: buffer overflow");
                    break WRAP;

                case CLOSED:
                    logger.debug("wrap: exit: closed");
                    break WRAP;

                case BUFFER_UNDERFLOW:
                    logger.debug("wrap: exit: buffer underflow");
                    break WRAP;
            }
        }

        logger.debug("wrap: return: " + totalWritten);
        return totalWritten;
    }

    private void runHandshakeTasks() {
        while (true) {
            final Runnable runnable = sslEngine.getDelegatedTask();
            if (runnable == null) {
                break;
            } else {
                executorService.execute(runnable);
            }
        }
    }

    public int getMinimumApplicationBufferSize() {
        return minimumApplicationBufferSize;
    }
}
