package com.unifor.br.peer.ui;

import com.unifor.br.peer.contract.PeerNetwork;
import com.unifor.br.peer.net.SocketPeerNetwork;
import com.unifor.br.peer.ui.enums.Route;
import com.unifor.br.peer.ui.state.ApplicationState;
import com.unifor.br.peer.ui.views.ChatView;
import com.unifor.br.peer.ui.views.SignInView;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Router {

    private final Stage stage;
    private final StackPane root = new StackPane();
    private final Scene scene;
    private PeerNetwork network;
    private ApplicationState applicationState;
    private Route currentRoute;

    public Router(Stage stage) {
        this(stage, new SocketPeerNetwork());
    }

    public Router(Stage stage, PeerNetwork network) {
        this(stage, new ApplicationState(network));
    }

    public Router(Stage stage, ApplicationState applicationState) {
        this.stage = stage;
        this.applicationState = applicationState;
        this.network = applicationState != null ? applicationState.network() : null;

        this.scene = new Scene(root, 670, 420);
        var cssUrl = getClass().getResource("/com/unifor/br/peer/ui/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            var relCss = getClass().getResource("style.css");
            if (relCss != null) {
                scene.getStylesheets().add(relCss.toExternalForm());
            }
        }

        if (stage != null) {
            stage.setScene(scene);
        }
    }

    public void navigate(Route route) {
        this.currentRoute = route;
        if (route == null) {
            return;
        }
        switch (route) {
            case SIGN_IN -> showSignIn();
            case CHAT -> showChat();
        }
    }

    private void showSignIn() {
        if (stage != null) {
            stage.setTitle("Aplicação de Chat - Entrada");
        }
        SignInView signInView = new SignInView(stage, network);
        signInView.setOnSuccess(model -> {
            navigate(Route.CHAT);
        });
        root.getChildren().setAll(signInView);
    }

    private void showChat() {
        if (stage != null) {
            stage.setTitle("Aplicação de Chat");
            stage.setResizable(true);
        }
        ChatView chatView = new ChatView(stage, applicationState);
        root.getChildren().setAll(chatView);
    }

    public void shutdown() {
        if (network != null) {
            network.shutdown();
        }
    }

    public Stage getStage() {
        return stage;
    }

    public StackPane getRoot() {
        return root;
    }

    public Scene getScene() {
        return scene;
    }

    public PeerNetwork getNetwork() {
        return network;
    }

    public void setNetwork(PeerNetwork network) {
        this.network = network;
        if (this.applicationState == null || this.applicationState.network() != network) {
            this.applicationState = new ApplicationState(network);
        }
    }

    public ApplicationState getApplicationState() {
        return applicationState;
    }

    public Route getCurrentRoute() {
        return currentRoute;
    }
}
