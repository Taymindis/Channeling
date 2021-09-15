package com.github.taymindis.nio.channeling;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Predicate;

public interface ChannelingSocket {
    Object getContext();

    void setContext(Object context);

    /**
     * Get Native socket channel
     *
     * @return socketchannel
     */
    SocketChannel getSocketChannel();
    ServerSocketChannel getServerSocketChannel();
    long getActionTime();
    int getLastProcessedBytes();

    void noEagerRead();
    boolean isEagerRead();

    /**
     IO Task API builder
     * @param  remote remote address
     * @return ChannelingSocket to ready for run
     */
    ChannelingSocket withConnect(SocketAddress remote);
    ChannelingSocket withConnect(String host, int port);
    ChannelingSocket withAccept();
    ChannelingSocket withRead();
    ChannelingSocket withRead(int length/*, boolean autoResize*/);
    ChannelingSocket withRead(ByteBuffer readBuffer);

    /**
     set force OP_WRITE event as this event is opening for selector and by using this event to do force read
     * @return ChannelingSocket
     */
    ChannelingSocket withEagerRead();
    ChannelingSocket withEagerRead(int length/*, boolean autoResize*/);
    ChannelingSocket withEagerRead(ByteBuffer readBuffer);

    ChannelingSocket withWrite(ByteBuffer messageBuffer);
    ChannelingSocket withClose();

    /**
     IO Task function call
     *
     * @param  remote remote address
     * @param  then then predicate
     *
     */
    void connect(SocketAddress remote, Then then);
    void connect(String host, int port, Then then);
    void connect(SocketAddress remote, WhenConnectingStatus when, Then then);
    void connect(String host, int port, WhenConnectingStatus when, Then then);
    void read(Then then);
    void read(WhenChannelingSocket when, Then then);
    void read(int length, Then then);
    void read(int length, WhenChannelingSocket when, Then then);
    void read(ByteBuffer readBufferHolder, Then then);
    void read(ByteBuffer readBufferHolder, WhenChannelingSocket when, Then then);
    void write(ByteBuffer messageBuffer, Then then);
    void write(ByteBuffer messageBuffer, WhenChannelingSocket when, Then then);
    void close(Then then);
    void close(WhenChannelingSocket when, Then then);


    void connect(SocketAddress remote, Then then, ErrorCallback errorCallback);
    void connect(String host, int port, Then then, ErrorCallback errorCallback);
    void connect(SocketAddress remote, WhenConnectingStatus when, Then then, ErrorCallback errorCallback);
    void connect(String host, int port, WhenConnectingStatus when, Then then, ErrorCallback errorCallback);
    void read(Then then, ErrorCallback errorCallback);
    void read(WhenChannelingSocket when, Then then, ErrorCallback errorCallback);
    void read(int length, Then then, ErrorCallback errorCallback);
    void read(int length, WhenChannelingSocket when, Then then, ErrorCallback errorCallback);
    void read(ByteBuffer readBufferHolder, Then then, ErrorCallback errorCallback);
    void read(ByteBuffer readBufferHolder, WhenChannelingSocket when, Then then, ErrorCallback errorCallback);
    void write(ByteBuffer messageBuffer, Then then, ErrorCallback errorCallback);
    void write(ByteBuffer messageBuffer, WhenChannelingSocket when, Then then, ErrorCallback errorCallback);
    void close(Then then, ErrorCallback errorCallback);
    void close(WhenChannelingSocket when, Then then, ErrorCallback errorCallback);



    /**
     * For checking by bytebuffer curr been using
     *
     * @param whenPredicate when predicate
     * @return ChannelingSocket
     */
    ChannelingSocket when(WhenWritingByteBuffer whenPredicate);
    ChannelingSocket when(WhenReadingByteBuffer whenPredicate);

    /**
     * For checking by socketchannel
     *
     * @param whenPredicate when predicate
     * @return ChannelingSocket
     */
    ChannelingSocket when(WhenSocketChannel whenPredicate);

    /**
     * For Socketchannel when connecting status whether is connected
     *
     * @param whenPredicate when predicate
     * @return ChannelingSocket
     */
    ChannelingSocket when(WhenConnectingStatus whenPredicate);

    /**
     * For Socketchannel when closing status whether is connected
     *
     * @param whenPredicate when predicate
     * @return ChannelingSocket
     */
    ChannelingSocket when(WhenClosingStatus whenPredicate);

    /**
     * For Socketchannel when channelingsocket been call in
     *
     * @param whenPredicate when predicate
     * @return ChannelingSocket
     */
    ChannelingSocket when(WhenChannelingSocket whenPredicate);

    /**
     * For Socketchannel where after read write process
     *
     * @param whenReadWriteProcess when read write process predicate
     * @return ChannelingSocket
     */
    ChannelingSocket when(WhenReadWriteProcess whenReadWriteProcess);

    void then(Then then$, ErrorCallback errorCallback);
    void then(Then then$);

    Predicate getCurrentPredicate();

    ChannelingTask getPredicateTask();

    ChannelingTask getIoTask();

    ByteBuffer getReadBuffer();

    SocketAddress getRemoteAddress();

    ByteBuffer getCurrWritingBuffer();

    Then getThen();

    ErrorCallback getErrorCallBack();

    int getSSLMinimumInputBufferSize();


    /**
     * Internal Core only scope
     * @param rt num of bytes
     */
    void setLastProcessedBytes(int rt);
    void setPredicateTask(Predicate<?> predicateTask);
    void setIoTask(ChannelingTask channelingTask);
    boolean tryRemoveEagerRead();

    boolean isSSL();
}
