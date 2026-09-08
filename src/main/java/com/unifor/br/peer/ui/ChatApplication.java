package com.unifor.br.peer.ui;

import com.unifor.br.peer.ui.enums.Route;
import javafx.application.Application;
import javafx.stage.Stage;

public class ChatApplication extends Application {
    private Router router;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        router = new Router(primaryStage);
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
