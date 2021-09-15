package com.github.taymindis.nio.channeling;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelingServer {

    private boolean isActive = false;
    private final ChannelingSocket channelServerRunner;

    public ChannelingServer(Channeling channeling) throws Exception {
        this.channelServerRunner = channeling.wrapServer(null, "localhost", 8080);
    }

    public void start() {
        isActive = true;
        AtomicBoolean waitForAccept = new AtomicBoolean(false);
        while (isActive) {

            if(!waitForAccept.compareAndSet(false, true)) {
                continue;
            }


            channelServerRunner.withAccept().then(channelingSocket -> {
                SocketChannel socketChannel = null;
                try {
                    socketChannel = (channelingSocket.getServerSocketChannel()).accept();

                    socketChannel.configureBlocking(false);
                    waitForAccept.set(false);
//                SSLEngine engine = context.createSSLEngine();
//                engine.setUseClientMode(false);
//                engine.beginHandshake();
//
//                if (doHandshake(socketChannel, engine)) {
//                    socketChannel.register(selector, SelectionKey.OP_READ, engine);
//                } else {
//                    socketChannel.close();
//                    log.debug("Connection closed due to handshake failure.");
//                }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        }

    }


    public void stop() {
        isActive = false;
    }
}
