package com.graphviz;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Entry point for the JavaFX application.
 * JavaFX requires a class that extends Application; this one simply loads the
 * main FXML file and shows the window.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/graphviz/MainView.fxml")
        );
        Scene scene = new Scene(loader.load(), 960, 640);

        // Attach the modern light stylesheet that styles all the controls.
        scene.getStylesheets().add(
                getClass().getResource("/com/graphviz/styles.css").toExternalForm()
        );

        primaryStage.setTitle("Graph Algorithm Visualizer");
        primaryStage.setScene(scene);
        // Keep a sensible minimum so the toolbar rows never clip.
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(560);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
