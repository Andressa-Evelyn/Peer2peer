package com.unifor.br.peer.ui.controllers;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.MessageType;
import com.unifor.br.peer.contract.PeerEventListener;
import com.unifor.br.peer.contract.PeerInfo;
import com.unifor.br.peer.contract.PeerNetwork;
import com.unifor.br.peer.ui.state.ChatState;
import javafx.application.Platform;

import java.util.Objects;
import java.util.UUID;

/**
 * Controller responsible for mediating events between the P2P network (PeerNetwork)
 * and the GUI state (ChatState), implementing PeerEventListener.
 *
 * <p>All UI updates are executed inside {@link Platform#runLater(Runnable)}
 * and operations are non-blocking.
 */
public class ChatController implements PeerEventListener {

    private final PeerNetwork network;
    private final ChatState chatState;

    public ChatController(PeerNetwork network, ChatState chatState) {
        this.network = network;
        this.chatState = Objects.requireNonNull(chatState, "chatState cannot be null");
        if (network != null) {
            network.setListener(this);
        }
    }

    @Override
    public void onMessage(Message message) {
        if (message == null) {
            return;
        }
        runOnUI(() -> chatState.getMessages().add(message));
    }

    @Override
    public void onPeerJoined(PeerInfo peer) {
        if (peer == null) {
            return;
        }
        runOnUI(() -> {
            boolean alreadyExists = chatState.getPeers().stream()
                    .anyMatch(existing -> existing.peerId().equals(peer.peerId()));
            if (!alreadyExists) {
                chatState.getPeers().add(peer);
            }
            Message systemMessage = new Message(
                    UUID.randomUUID().toString(),
                    MessageType.HELLO,
                    peer.peerId(),
                    "Sistema",
                    null,
                    peer.display() + " entrou na rede",
                    System.currentTimeMillis()
            );
            chatState.getMessages().add(systemMessage);
        });
    }

    @Override
    public void onPeerLeft(PeerInfo peer) {
        if (peer == null) {
            return;
        }
        runOnUI(() -> {
            chatState.getPeers().removeIf(existing -> existing.peerId().equals(peer.peerId()));
            if (chatState.getSelectedPeer() != null && chatState.getSelectedPeer().peerId().equals(peer.peerId())) {
                chatState.setSelectedPeer(null);
            }
            Message systemMessage = new Message(
                    UUID.randomUUID().toString(),
                    MessageType.LEAVE,
                    peer.peerId(),
                    "Sistema",
                    null,
                    peer.display() + " saiu da rede",
                    System.currentTimeMillis()
            );
            chatState.getMessages().add(systemMessage);
        });
    }

    @Override
    public void onError(String reason) {
        runOnUI(() -> {
            Message errorMessage = new Message(
                    UUID.randomUUID().toString(),
                    MessageType.PING,
                    "sistema",
                    "Sistema",
                    null,
                    "Erro: " + reason,
                    System.currentTimeMillis()
            );
            chatState.getMessages().add(errorMessage);
        });
    }

    /**
     * Sends a message via broadcast or private channel depending on the selected peer.
     */
    public boolean sendMessage(String text) {
        if (text == null || text.trim().isEmpty() || network == null) {
            return false;
        }
        String formattedText = text.trim();
        PeerInfo recipient = chatState.getSelectedPeer();
        if (recipient != null) {
            network.sendPrivate(recipient.peerId(), formattedText);
        } else {
            network.broadcast(formattedText);
        }
        return true;
    }

    public PeerNetwork getNetwork() {
        return network;
    }

    public ChatState getChatState() {
        return chatState;
    }

    private void runOnUI(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            try {
                Platform.runLater(action);
            } catch (IllegalStateException e) {
                // In case JavaFX toolkit is not active in pure unit tests
                action.run();
            }
        }
    }
}
