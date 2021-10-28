package com.github.taymindis.nio.channeling;

public class ChannelingProxy {
    final private String host;
    final private int port;
    final private String userName;
    final private String password;


    public ChannelingProxy(String host, int port) {
        this(host, port, null, null);
    }

    public ChannelingProxy(String host, int port, String userName, String password) {
        this.host = host;
        this.port = port;
        this.userName = userName;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}
