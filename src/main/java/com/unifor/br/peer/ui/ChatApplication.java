package com.unifor.br.peer.ui;

import com.unifor.br.peer.contract.PeerNetwork;
import com.unifor.br.peer.net.SocketPeerNetwork;
import com.unifor.br.peer.ui.enums.Route;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

public class ChatApplication extends Application {
    private Router router;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        URL iconUrl = ChatApplication.class.getResource("/com/unifor/br/peer/ui/icon.png");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
//        PeerNetwork network = new FakePeerNetwork();
        PeerNetwork network = new SocketPeerNetwork();
        router = new Router(primaryStage, network);
        primaryStage.setOnCloseRequest(e -> router.shutdown());
        router.navigate(Route.SIGN_IN);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        if (router != null) {
            router.shutdown();
        }
        super.stop();
    }
}
