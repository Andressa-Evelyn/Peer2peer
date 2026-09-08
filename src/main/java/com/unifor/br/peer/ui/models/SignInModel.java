package com.unifor.br.peer.ui.models;

public class SignInModel {
    private String username;
    private int port;
    private String connectExistedNetwork;

    public SignInModel(String username, int port, String connectExistedNetwork) {
        this.username = username;
        this.port = port;
        this.connectExistedNetwork = connectExistedNetwork;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getConnectExistedNetwork() {
        return connectExistedNetwork;
    }

    public void setConnectExistedNetwork(String connectExistedNetwork) {
        this.connectExistedNetwork = connectExistedNetwork;
    }
}
