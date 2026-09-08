package com.unifor.br.peer.ui.state;

import com.unifor.br.peer.contract.PeerNetwork;

public class ApplicationState {
    private final PeerNetwork network;
    private final ChatState chatState;

    public ApplicationState(PeerNetwork network) {
        this.network = network;
        this.chatState = new ChatState();
    }

    public PeerNetwork network() {
        return network;
    }

    public ChatState chatState() {
        return chatState;
    }
}
