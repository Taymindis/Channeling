package com.github.taymindis.nio.channeling;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Predicate;

abstract class AbstractChannelRunner implements ChannelingSocket {
    @Override
    public Object getContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setContext(Object context) {

        throw new UnsupportedOperationException();
    }

    @Override
    public SocketChannel getSocketChannel() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ServerSocketChannel getServerSocketChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getActionTime() {
        throw new UnsupportedOperationException();

    }

    @Override
    public int getLastProcessedBytes() {
        throw new UnsupportedOperationException();

    }

    @Override
    public void noEagerRead() {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEagerRead() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withConnect(SocketAddress remote) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withConnect(String host, int port) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withRead() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withRead(int length) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withRead(ByteBuffer readBuffer) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withEagerRead() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withEagerRead(int length) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withEagerRead(ByteBuffer readBuffer) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withWrite(ByteBuffer messageBuffer) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket withClose() {
        throw new UnsupportedOperationException();

    }

    @Override
    public void connect(SocketAddress remote, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(String host, int port, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(SocketAddress remote, WhenConnectingStatus when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(String host, int port, WhenConnectingStatus when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(WhenChannelingSocket when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(int length, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(int length, WhenChannelingSocket when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(ByteBuffer readBufferHolder, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(ByteBuffer readBufferHolder, WhenChannelingSocket when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void write(ByteBuffer messageBuffer, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void write(ByteBuffer messageBuffer, WhenChannelingSocket when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void close(Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void close(WhenChannelingSocket when, Then then) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(SocketAddress remote, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(String host, int port, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(SocketAddress remote, WhenConnectingStatus when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(String host, int port, WhenConnectingStatus when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(int length, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(int length, WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(ByteBuffer readBufferHolder, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void read(ByteBuffer readBufferHolder, WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void write(ByteBuffer messageBuffer, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void write(ByteBuffer messageBuffer, WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void close(Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void close(WhenChannelingSocket when, Then then, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }


    @Override
    public ChannelingSocket when(WhenWritingByteBuffer whenPredicate) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket when(WhenReadingByteBuffer whenPredicate) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket when(WhenSocketChannel whenPredicate) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket when(WhenConnectingStatus whenPredicate) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket when(WhenClosingStatus whenPredicate) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket when(WhenChannelingSocket whenPredicate) {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingSocket when(WhenReadWriteProcess whenReadWriteProcess) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void then(Then then$, ErrorCallback errorCallback) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void then(Then then$) {

        throw new UnsupportedOperationException();
    }

    @Override
    public Predicate getCurrentPredicate() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingTask getPredicateTask() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ChannelingTask getIoTask() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ByteBuffer getReadBuffer() {
        throw new UnsupportedOperationException();

    }

    @Override
    public SocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ByteBuffer getCurrWritingBuffer() {
        throw new UnsupportedOperationException();

    }

    @Override
    public Then getThen() {
        throw new UnsupportedOperationException();

    }

    @Override
    public ErrorCallback getErrorCallBack() {
        throw new UnsupportedOperationException();

    }

    @Override
    public int getSSLMinimumInputBufferSize() {
        throw new UnsupportedOperationException();

    }

    @Override
    public void setLastProcessedBytes(int rt) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void setPredicateTask(Predicate<?> predicateTask) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void setIoTask(ChannelingTask channelingTask) {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryRemoveEagerRead() {
        throw new UnsupportedOperationException();

    }

    @Override
    public boolean isSSL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelingSocket withAccept() {
        throw new UnsupportedOperationException();
    }
}
