package com.github.taymindis.nio.channeling;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class ChannelingTimeoutFeature implements ChannelingPlugin{

    private static final int DEFAULT_BUFFER_TURNOVER = 1000;
    private int bufferingCount = 0;
    private final long connectionTimeoutInMs;
    private final long readWriteTimeOutInMs;

    ChannelingTimeoutFeature(long connectionTimeoutInMs, long readWriteTimeOutInMs) {
        this.connectionTimeoutInMs = connectionTimeoutInMs;
        this.readWriteTimeOutInMs = readWriteTimeOutInMs;
    }

    @Override
    public void checkKeys(Set<SelectionKey> allKeys) throws TimeoutException, IOException {
        if (bufferingCount++ > DEFAULT_BUFFER_TURNOVER) {
            long currTime = new Date().getTime();
            bufferingCount = 0;
            for (SelectionKey key : allKeys) {
                ChannelingSocket channelingSocket = (ChannelingSocket) key.attachment();
                if(channelingSocket instanceof ChannelServerRunner) {
                    continue;
                }
                if (channelingSocket != null) {
                    long actionTime = channelingSocket.getActionTime();
                    long elapsedTimeInMs = currTime - actionTime;

                    if (channelingSocket.getIoTask() == ChannelingTask.DO_CONNECT && elapsedTimeInMs >= connectionTimeoutInMs) {
                        key.cancel();
                        channelingSocket.withClose();
                        TimeoutException timeout =
                                new TimeoutException("Connecting timeout, IO has spent around" + connectionTimeoutInMs + "++ms");
                        channelingSocket.getErrorCallBack().error(channelingSocket, timeout);
                        throw timeout;
                    }

                    if (channelingSocket.getIoTask() != ChannelingTask.DO_CONNECT && elapsedTimeInMs >= readWriteTimeOutInMs) {
                        key.cancel();
                        channelingSocket.withClose();
                        TimeoutException timeout =
                                new TimeoutException("Read / Writing timeout, IO has spent around " + readWriteTimeOutInMs + "++ms");
                        channelingSocket.getErrorCallBack().error(channelingSocket, timeout);
                        throw timeout;
                    }
                }
            }
        }
    }
}
