package com.github.taymindis.nio.channeling;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

class ChannelingProxyHandler implements ErrorCallback {

    private String externalHost;
    private int externalPort;
    private Consumer<ChannelingSocket> success;
    private Consumer<Exception> error;

    private static final Base64.Encoder encoder = Base64.getEncoder();
    private final ChannelingProxy channelingProxy;
    private final ChannelingSocket channelingSocket;
    private ByteBuffer establishedBuffer;
    private StringBuilder output;

    ChannelingProxyHandler(ChannelingSocket socket, ChannelingProxy channelingProxy) {
        if (channelingProxy == null || channelingProxy.getHost() == null || channelingProxy.getPort() <= 0) {
            throw new IllegalStateException("Invalid Proxy Info ...");
        }
        this.channelingSocket = socket;
        this.channelingProxy = channelingProxy;
    }

    protected void connectViaProxy() {
        channelingSocket
                .then(this::connectThen, this::error);
    }

    protected void connectThen(ChannelingSocket channelingSocket) {
        establishedBuffer = ByteBuffer.wrap(establishingConnection().getBytes(StandardCharsets.UTF_8));
        channelingSocket.write(establishedBuffer, this::establishExternalThen);

    }

    protected void establishExternalThen(ChannelingSocket channelingSocket) {
        if (establishedBuffer.hasRemaining()) {
            channelingSocket.write(establishedBuffer, this::establishExternalThen);
        }

        establishedBuffer = ByteBuffer.allocate(1024);
        output = new StringBuilder();
        channelingSocket.read(establishedBuffer, this::readStatusFromExternalThen);
    }

    protected void readStatusFromExternalThen(ChannelingSocket channelingSocket) {
        establishedBuffer.flip();

        byte[] s = new byte[establishedBuffer.limit() - establishedBuffer.position()];

        establishedBuffer.get(s);

        output.append(new String(s, StandardCharsets.UTF_8));

        if (output.toString().contains("HTTP/1.1 200 Connection established")) {
            success.accept(channelingSocket);
        } else if (output.length() > 12) {
            error(channelingSocket, new Exception(output.toString()));
        } else {
            establishedBuffer.clear();
            channelingSocket.read(establishedBuffer, this::readStatusFromExternalThen);
        }
    }

    private String establishingConnection() {
        String username = channelingProxy.getUserName();
        String password = channelingProxy.getPassword();
        /***********************************
         * HTTP CONNECT protocol RFC 2616
         ***********************************/
        String proxyConnect = "CONNECT " + externalHost + ":" + externalPort + " HTTP/1.1\r\n" + "host: " + externalHost + ":" + externalPort + "\r\n";

        // Add Proxy Authorization if proxyUser and proxyPass is set
        try {
            if (username != null && password != null) {
                String proxyUserPass = String.format("%s:%s", username, password);
                proxyConnect = proxyConnect.concat(" HTTP/1.0\nProxy-Authorization:Basic ").concat(encoder.encodeToString(proxyUserPass.getBytes())).concat("\r\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            proxyConnect = proxyConnect.concat("\r\n");
        }
        return proxyConnect;
    }


    protected void setExternalHost(String externalHost) {
        this.externalHost = externalHost;
    }

    protected void setExternalPort(int externalPort) {
        this.externalPort = externalPort;
    }

    @Override
    public void error(ChannelingSocket sc, Exception e) {
        error.accept(e);
    }

    public String getHost() {
        return channelingProxy.getHost();
    }

    public int getPort() {
        return channelingProxy.getPort();
    }

    public void setSuccess(Consumer<ChannelingSocket> success) {
        this.success = success;
    }

    public void setError(Consumer<Exception> error) {
        this.error = error;
    }
}
