package com.iae;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Application entry point. Loads the main FXML layout and opens the primary stage.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/iae/gui/view/main.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);

        primaryStage.setTitle("IAE \u2014 Integrated Assignment Environment");
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(450);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
