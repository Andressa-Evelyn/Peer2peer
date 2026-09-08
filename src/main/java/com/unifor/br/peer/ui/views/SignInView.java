package com.unifor.br.peer.ui.views;

import com.unifor.br.peer.contract.PeerNetwork;
import com.unifor.br.peer.ui.models.SignInModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.Consumer;

/**
 * Tela de entrada do chat P2P.
 *
 * Permite ao usuário informar:
 * - Nome de usuário
 * - Porta de escuta
 * - Host:Porta para entrar numa rede existente (opcional)
 *
 */
public class SignInView extends VBox {

    private final TextField usernameField;
    private final TextField portField;
    private final TextField connectNetworkField;
    private final Label errorLabel;
    private final Button submitButton;

    private Stage stage;
    private PeerNetwork peerNetwork;
    private Consumer<SignInModel> onSuccess;

    public SignInView(Stage stage) {
        this(stage, null);
    }

    public SignInView(Stage stage, PeerNetwork peerNetwork) {
        this.stage = stage;
        this.peerNetwork = peerNetwork;

        setSpacing(15);
        setPadding(new Insets(25, 30, 25, 30));
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: #f7f9fa;");

        Label titleLabel = new Label("Entrar no Chat P2P");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1a202c;");

        Label subtitleLabel = new Label("Configure sua identidade e porta na rede");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096;");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(12);
        formGrid.setAlignment(Pos.CENTER);

        Label lblUsername = new Label("Nome de usuário:");
        lblUsername.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d3748;");
        usernameField = new TextField();
        usernameField.setPromptText("Ex: João");
        usernameField.setPrefWidth(220);

        Label lblPort = new Label("Porta de escuta:");
        lblPort.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d3748;");
        portField = new TextField();
        portField.setPromptText("Ex: 5000");
        portField.setPrefWidth(220);

        Label lblNetwork = new Label("Host:Porta existente:");
        lblNetwork.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d3748;");
        connectNetworkField = new TextField();
        connectNetworkField.setPromptText("Ex: 127.0.0.1:5000 (opcional)");
        connectNetworkField.setPrefWidth(220);

        formGrid.add(lblUsername, 0, 0);
        formGrid.add(usernameField, 1, 0);

        formGrid.add(lblPort, 0, 1);
        formGrid.add(portField, 1, 1);

        formGrid.add(lblNetwork, 0, 2);
        formGrid.add(connectNetworkField, 1, 2);

        errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(340);
        errorLabel.setAlignment(Pos.CENTER);
        errorLabel.setStyle("-fx-text-fill: #e53e3e; -fx-font-size: 12px; -fx-font-weight: bold;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        submitButton = new Button("Entrar");
        submitButton.setDefaultButton(true);
        submitButton.setPrefWidth(140);
        submitButton.setStyle("-fx-background-color: #3182ce; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 5px; -fx-cursor: hand;");
        submitButton.setOnAction(e -> handleSignIn());

        getChildren().addAll(
                titleLabel,
                subtitleLabel,
                formGrid,
                errorLabel,
                submitButton
        );
    }

    /**
     * Valida os campos fornecidos e retorna uma instância de {@link SignInModel}.
     * Lança {@link IllegalArgumentException} ou {@link IllegalStateException} caso alguma validação falhe.
     */
    public static SignInModel validate(String username, String portText, String connectNetwork) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome de usuário não pode ficar vazio.");
        }

        if (portText == null || portText.trim().isEmpty()) {
            throw new IllegalArgumentException("A porta de escuta não pode ficar vazia.");
        }

        int port;
        try {
            port = Integer.parseInt(portText.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A porta de escuta deve ser um número válido.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("A porta de escuta deve estar entre 1 e 65535.");
        }

        if (!isPortAvailable(port)) {
            throw new IllegalStateException("A porta " + port + " já está em uso ou indisponível.");
        }

        String network = connectNetwork != null ? connectNetwork.trim() : "";
        if (!network.isEmpty()) {
            String[] parts = network.split(":", -1);
            if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                throw new IllegalArgumentException("O campo host:porta deve estar no formato 'host:porta' (ex: 127.0.0.1:5000).");
            }

            int remotePort;
            try {
                remotePort = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("A porta remota deve ser um número válido.");
            }

            if (remotePort < 1 || remotePort > 65535) {
                throw new IllegalArgumentException("A porta remota deve estar entre 1 e 65535.");
            }
        }

        return new SignInModel(username.trim(), port, network);
    }

    /**
     * Verifica se a porta local especificada está livre para bind.
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Executa a validação e processamento da entrada na UI.
     */
    public SignInModel handleSignIn() {
        clearError();
        try {
            SignInModel model = validate(
                    usernameField.getText(),
                    portField.getText(),
                    connectNetworkField.getText()
            );

            if (peerNetwork != null) {
                peerNetwork.start(model.getUsername(), model.getPort());
                if (!model.getConnectExistedNetwork().isEmpty()) {
                    String[] parts = model.getConnectExistedNetwork().split(":", 2);
                    peerNetwork.connectTo(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                }
            }

            if (onSuccess != null) {
                onSuccess.accept(model);
            }

            return model;
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError(e.getMessage());
            return null;
        } catch (Exception e) {
            showError("Erro inesperado ao iniciar: " + e.getMessage());
            return null;
        }
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public TextField getUsernameField() {
        return usernameField;
    }

    public TextField getPortField() {
        return portField;
    }

    public TextField getConnectNetworkField() {
        return connectNetworkField;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }

    public Button getSubmitButton() {
        return submitButton;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public void setPeerNetwork(PeerNetwork peerNetwork) {
        this.peerNetwork = peerNetwork;
    }

    public PeerNetwork getPeerNetwork() {
        return peerNetwork;
    }

    public void setOnSuccess(Consumer<SignInModel> onSuccess) {
        this.onSuccess = onSuccess;
    }
}
