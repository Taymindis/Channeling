package com.github.taymindis.nio.channeling;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Set;

public class ChannelingEagerSocketFeature implements ChannelingPlugin{

    private final Channeling channeling;

    public ChannelingEagerSocketFeature(Channeling channeling) {
        this.channeling = channeling;
    }

    @Override
    public void checkKeys(Set<SelectionKey> allKeys) throws IOException {
        if(channeling.noEagerSocket) {
            return;
        }
        for (SelectionKey key : allKeys) {
            ChannelingSocket channelingSocket = (ChannelingSocket) key.attachment();
            if (channelingSocket != null) {

                if (channelingSocket.isEagerRead()) {
                    // If it is force call back, set last process bytes to 0
                    channelingSocket.setLastProcessedBytes(0);
                    channelingSocket.getThen().callback(channelingSocket);
                }
            }
        }
    }

}
