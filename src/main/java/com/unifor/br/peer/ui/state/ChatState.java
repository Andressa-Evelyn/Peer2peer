package com.unifor.br.peer.ui.state;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.PeerInfo;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ChatState {
    private final ObservableList<PeerInfo> peers =
            FXCollections.observableArrayList();

    private final ObservableList<Message> messages =
            FXCollections.observableArrayList();

    private PeerInfo selectedPeer;

    public ObservableList<PeerInfo> getPeers() {
        return peers;
    }

    public ObservableList<Message> getMessages() {
        return messages;
    }

    public PeerInfo getSelectedPeer() {
        return selectedPeer;
    }

    public void setSelectedPeer(PeerInfo peer) {
        this.selectedPeer = peer;
    }
}
