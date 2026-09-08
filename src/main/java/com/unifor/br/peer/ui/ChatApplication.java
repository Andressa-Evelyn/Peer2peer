package com.unifor.br.peer.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class ChatApplication extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        StackPane root = new StackPane();
        // opções da janela
        Scene scene = new Scene(root, 300, 250);
        primaryStage.setTitle("Aplicação de Chat");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
