package com.unifor.br.peer.ui;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.PeerEventListener;
import com.unifor.br.peer.contract.PeerInfo;
import com.unifor.br.peer.contract.PeerNetwork;

import java.util.ArrayList;
import java.util.List;

public class FakePeerNetwork implements PeerNetwork {
    private PeerEventListener listener;
    private int nextFakePeerId = 3;
    private final PeerInfo me = new PeerInfo("fake-eu", "eu", "127.0.0.1", 5000);
    private final List<PeerInfo> peers = new ArrayList<>(List.of(
            new PeerInfo("fake-1", "joão", "127.0.0.1", 5001),
            new PeerInfo("fake-2", "maria", "127.0.0.1", 5002)));
    private boolean shutdownCalled = false;

    @Override
    public void setListener(PeerEventListener l) {
        this.listener = l;
    }

    @Override
    public void start(String username, int porta) {
        if (listener != null) {
            peers.forEach(listener::onPeerJoined);
        }
    }

    @Override
    public void connectTo(String host, int port) {
        PeerInfo novo = new PeerInfo("fake-" + this.nextFakePeerId, "other-" + this.nextFakePeerId, host, port);
        peers.add(novo);
        this.nextFakePeerId++;
        if (listener != null) {
            listener.onPeerJoined(novo);
        }
    }

    @Override
    public void broadcast(String texto) {
        if (listener != null) {
            listener.onMessage(Message.chat(me, texto));
        }
    }

    @Override
    public void sendPrivate(String peerId, String texto) {
        if (listener != null) {
            listener.onMessage(Message.privado(me, peerId, texto));
        }
    }

    @Override
    public List<PeerInfo> connectedPeers() {
        return List.copyOf(peers);
    }

    @Override
    public PeerInfo self() {
        return me;
    }

    @Override
    public void shutdown() {
        this.shutdownCalled = true;
    }

    public boolean isShutdownCalled() {
        return shutdownCalled;
    }
}
