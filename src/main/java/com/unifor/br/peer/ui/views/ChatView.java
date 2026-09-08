package com.unifor.br.peer.ui.views;

import com.unifor.br.peer.contract.Message;
import com.unifor.br.peer.contract.PeerInfo;
import com.unifor.br.peer.contract.PeerNetwork;
import com.unifor.br.peer.ui.controllers.ChatController;
import com.unifor.br.peer.ui.state.ApplicationState;
import com.unifor.br.peer.ui.state.ChatState;
import javafx.application.Platform;
import javafx.collections.transformation.FilteredList;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Tela principal do Chat P2P (Tarefa B3).
 *
 * <p>Componentes principais:
 * <ul>
 *   <li><b>Esquerda:</b> Lista de peers conectados, dados do usuário local e opção de alternar entre broadcast e privado.</li>
 *   <li><b>Centro:</b> Histórico de mensagens com suporte a mensagens públicas, privadas e de sistema, com rolagem automática.</li>
 *   <li><b>Embaixo:</b> Campo de digitação com envio por Enter e botão de envio.</li>
 * </ul>
 */
public class ChatView extends BorderPane {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PeerNetwork network;
    private final ChatState chatState;
    private final ChatController controller;

    private final ListView<PeerInfo> peerListView;
    private final ListView<Message> messageListView;
    private final TextField messageField;
    private final Button sendButton;
    private final Label currentChatHeaderLabel;
    private final Label selfInfoLabel;
    private final Button broadcastButton;
    private final Button createConversationButton;
    private final FilteredList<Message> filteredMessages;

    public ChatView(ApplicationState applicationState) {
        this(applicationState != null ? applicationState.network() : null,
                applicationState != null ? applicationState.chatState() : new ChatState());
    }

    public ChatView(PeerNetwork network, ChatState chatState) {
        this.network = network;
        this.chatState = chatState != null ? chatState : new ChatState();
        this.controller = new ChatController(this.network, this.chatState);
        this.filteredMessages = new FilteredList<>(this.chatState.getMessages(), this::shouldDisplayMessageForCurrentConversation);

        getStyleClass().add("chat-view-root");
        setPrefSize(900, 600);

        // --- PAINEL LATERAL ESQUERDO (Lista de Peers) ---
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(15));
        sidebar.setPrefWidth(260);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setStyle("-fx-background-color: #2d3748; -fx-text-fill: white;");

        Label sidebarTitle = new Label("Peers na Rede");
        sidebarTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #f7fafc;");

        selfInfoLabel = new Label();
        selfInfoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e0;");
        updateSelfInfo();

        peerListView = new ListView<>(this.chatState.getPeers());
        VBox.setVgrow(peerListView, Priority.ALWAYS);
        peerListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PeerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText("👤 " + item.display());
                    setStyle("-fx-padding: 8px; -fx-font-size: 13px;");
                }
            }
        });

        peerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            this.chatState.setSelectedPeer(newVal);
            updateChatHeader();
            refreshMessageFilter();
        });

        createConversationButton = new Button("➕ Nova conversa");
        createConversationButton.setMaxWidth(Double.MAX_VALUE);
        createConversationButton.setStyle("-fx-background-color: #2b6cb0; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-alignment: CENTER-LEFT;");
        createConversationButton.setOnAction(e -> openNewConversationDialog());

        broadcastButton = new Button("📢 Conversa Geral (Todos)");
        broadcastButton.setMaxWidth(Double.MAX_VALUE);
        broadcastButton.setStyle("-fx-background-color: #4a5568; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-alignment: CENTER-LEFT;");
        broadcastButton.setOnAction(e -> {
            peerListView.getSelectionModel().clearSelection();
            this.chatState.setSelectedPeer(null);
            updateChatHeader();
            refreshMessageFilter();
        });

        sidebar.getChildren().addAll(sidebarTitle, selfInfoLabel, createConversationButton, broadcastButton, new Separator(), peerListView);
        setLeft(sidebar);

        // --- PAINEL CENTRAL (Histórico + Cabeçalho + Envio) ---
        BorderPane centerPane = new BorderPane();
        centerPane.setStyle("-fx-background-color: #edf2f7;");

        // Cabeçalho do Chat
        HBox headerBox = new HBox(10);
        headerBox.setPadding(new Insets(12, 15, 12, 15));
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        currentChatHeaderLabel = new Label("📢 Conversa Geral (Pública)");
        currentChatHeaderLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2d3748;");
        headerBox.getChildren().add(currentChatHeaderLabel);
        centerPane.setTop(headerBox);

        // Lista de Mensagens
        messageListView = new ListView<>(filteredMessages);
        messageListView.setStyle("-fx-background-color: #edf2f7; -fx-background-insets: 0;");
        messageListView.setCellFactory(lv -> new MessageListCell());
        VBox.setVgrow(messageListView, Priority.ALWAYS);
        centerPane.setCenter(messageListView);

        // Auto-scroll ao adicionar novas mensagens
        filteredMessages.addListener((ListChangeListener<Message>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    Platform.runLater(() -> {
                        int total = filteredMessages.size();
                        if (total > 0) {
                            messageListView.scrollTo(total - 1);
                        }
                    });
                }
            }
        });

        // Painel Inferior de Envio
        HBox inputBar = new HBox(10);
        inputBar.setPadding(new Insets(12, 15, 12, 15));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        messageField = new TextField();
        messageField.setPromptText("Digite sua mensagem e pressione Enter...");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        messageField.setStyle("-fx-padding: 8px 12px; -fx-font-size: 13px; -fx-background-radius: 4px;");
        messageField.setOnAction(e -> handleSend());

        sendButton = new Button("Enviar");
        sendButton.setDefaultButton(true);
        sendButton.setPrefWidth(90);
        sendButton.setStyle("-fx-background-color: #3182ce; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 14px; -fx-background-radius: 4px; -fx-cursor: hand;");
        sendButton.setOnAction(e -> handleSend());

        inputBar.getChildren().addAll(messageField, sendButton);
        centerPane.setBottom(inputBar);

        setCenter(centerPane);
    }

    public void handleSend() {
        String text = messageField.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        boolean sent = controller.sendMessage(text);
        if (sent) {
            messageField.clear();
            messageField.requestFocus();
        }
    }

    private void updateSelfInfo() {
        if (network != null && network.self() != null) {
            PeerInfo self = network.self();
            selfInfoLabel.setText("Você: " + self.username() + " (Porta: " + self.port() + ")");
            return;
        }
        selfInfoLabel.setText("Você: (iniciando...)");
    }

    private void updateChatHeader() {
        PeerInfo selected = chatState.getSelectedPeer();
        if (selected != null) {
            currentChatHeaderLabel.setText("🔒 Conversa Privada com " + selected.display());
            currentChatHeaderLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #805ad5;");
        } else {
            currentChatHeaderLabel.setText("📢 Conversa Geral (Pública)");
            currentChatHeaderLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2d3748;");
        }
    }

    public ListView<PeerInfo> getPeerListView() {
        return peerListView;
    }

    public ListView<Message> getMessageListView() {
        return messageListView;
    }

    public TextField getMessageField() {
        return messageField;
    }

    public Button getSendButton() {
        return sendButton;
    }

    public ChatController getController() {
        return controller;
    }

    public ChatState getChatState() {
        return chatState;
    }

    public PeerNetwork getNetwork() {
        return network;
    }

    public Label getCurrentChatHeaderLabel() {
        return currentChatHeaderLabel;
    }

    public Label getSelfInfoLabel() {
        return selfInfoLabel;
    }

    public Button getBroadcastButton() {
        return broadcastButton;
    }

    public Button getCreateConversationButton() {
        return createConversationButton;
    }

    private void refreshMessageFilter() {
        filteredMessages.setPredicate(this::shouldDisplayMessageForCurrentConversation);
    }

    private boolean shouldDisplayMessageForCurrentConversation(Message message) {
        if (message == null) {
            return false;
        }

        if (isSystemMessage(message)) {
            return true;
        }

        PeerInfo selected = chatState.getSelectedPeer();
        if (selected == null) {
            return !message.isPrivada();
        }

        if (!message.isPrivada()) {
            return false;
        }

        return isMessageVisibleInPrivateConversation(message, selected.peerId());
    }

    private boolean isSystemMessage(Message message) {
        return !message.isConversa() || "Sistema".equalsIgnoreCase(message.fromUser());
    }

    private boolean isMessageVisibleInPrivateConversation(Message message, String selectedPeerId) {
        if (network == null || network.self() == null || selectedPeerId == null) {
            return false;
        }
        String selfPeerId = network.self().peerId();
        String fromPeerId = message.fromPeerId();
        String toPeerId = message.toPeerId();

        return (selfPeerId.equals(fromPeerId) && selectedPeerId.equals(toPeerId))
                || (selectedPeerId.equals(fromPeerId) && selfPeerId.equals(toPeerId));
    }

    private void openNewConversationDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nova conversa");
        dialog.setHeaderText("Conectar a um peer");
        dialog.setContentText("Host:porta");

        dialog.showAndWait().ifPresent(value -> {
            String input = value != null ? value.trim() : "";
            String[] parts = input.split(":");
            if (parts.length != 2 || parts[0].isBlank()) {
                controller.onError("Endereço inválido. Use host:porta");
                return;
            }

            try {
                int port = Integer.parseInt(parts[1].trim());
                if (port < 1 || port > 65535) {
                    controller.onError("Porta inválida. Use um valor entre 1 e 65535");
                    return;
                }
                if (network == null) {
                    controller.onError("Rede indisponível para criar nova conversa");
                    return;
                }
                network.connectTo(parts[0].trim(), port);
            } catch (NumberFormatException e) {
                controller.onError("Porta inválida. Use um número entre 1 e 65535");
            }
        });
    }

    /**
     * Célula customizada para exibição estilizada de mensagens de chat, privadas e de sistema.
     */
    private class MessageListCell extends ListCell<Message> {

        @Override
        protected void updateItem(Message item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            setStyle("-fx-background-color: transparent; -fx-padding: 4px 10px;");

            String time = TIME_FORMATTER.format(Instant.ofEpochMilli(item.timestamp()));
            boolean isSelf = network != null && network.self() != null
                    && item.fromPeerId() != null
                    && item.fromPeerId().equals(network.self().peerId());

            // Mensagens de Sistema (HELLO, LEAVE ou remetente "Sistema")
            if (!item.isConversa() || "Sistema".equalsIgnoreCase(item.fromUser())) {
                HBox systemBox = new HBox(6);
                systemBox.setAlignment(Pos.CENTER);
                systemBox.setPadding(new Insets(4, 8, 4, 8));

                Label systemLabel = new Label("[" + time + "] ℹ️ " + item.texto());
                if (item.texto() != null && item.texto().startsWith("Erro:")) {
                    systemLabel.setStyle("-fx-text-fill: #e53e3e; -fx-font-size: 11px; -fx-font-style: italic; -fx-font-weight: bold;");
                } else {
                    systemLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-style: italic;");
                }

                systemBox.getChildren().add(systemLabel);
                setGraphic(systemBox);
                return;
            }

            // Mensagem de Conversa (Pública ou Privada)
            VBox bubble = new VBox(3);
            bubble.setMaxWidth(480);
            bubble.setPadding(new Insets(8, 12, 8, 12));

            HBox header = new HBox(6);
            header.setAlignment(Pos.CENTER_LEFT);

            Label senderLabel = new Label(isSelf ? "Você" : item.fromUser());
            senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            Label timeLabel = new Label(time);
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isSelf ? "#ebf8ff" : "#a0aec0") + ";");

            header.getChildren().addAll(senderLabel, timeLabel);

            if (item.isPrivada()) {
                Label privateBadge = new Label(isSelf ? "[Privada para " + item.toPeerId() + "]" : "[Privada]");
                privateBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #9f7aea; -fx-background-color: #faf5ff; -fx-padding: 1 4; -fx-background-radius: 3;");
                header.getChildren().add(privateBadge);
            }

            Label contentLabel = new Label(item.texto());
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-font-size: 13px;");

            if (isSelf) {
                bubble.setStyle("-fx-background-color: #3182ce; -fx-background-radius: 12px 12px 2px 12px;");
                senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #ffffff;");
                contentLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #ffffff;");
            } else if (item.isPrivada()) {
                bubble.setStyle("-fx-background-color: #f3e8ff; -fx-border-color: #d6bcfa; -fx-border-width: 1px; -fx-background-radius: 12px 12px 12px 2px; -fx-border-radius: 12px 12px 12px 2px;");
                senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #6b46c1;");
                contentLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2d3748;");
            } else {
                bubble.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 12px 12px 12px 2px; -fx-border-radius: 12px 12px 12px 2px;");
                senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #2b6cb0;");
                contentLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2d3748;");
            }

            bubble.getChildren().addAll(header, contentLabel);

            HBox container = new HBox();
            container.setFillHeight(true);
            if (isSelf) {
                container.setAlignment(Pos.CENTER_RIGHT);
            } else {
                container.setAlignment(Pos.CENTER_LEFT);
            }
            container.getChildren().add(bubble);

            setGraphic(container);
        }
    }
}
