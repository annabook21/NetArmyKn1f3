package edu.au.cpsc.module7;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AlwaysDNS - A professional DNS query tool with JavaFX GUI
 * Production-ready implementation with proper error handling and modern design
 */
public class App extends Application {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    @Override
    public void start(Stage primaryStage) {
        try {
            // Set up uncaught exception handler
            Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);

            // Load FXML
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/edu/au/cpsc/module7/styles/fxml/MainWindow.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 750);

            // Apply CSS theme
            loadStylesheet(scene);

            // Configure stage
            configureStage(primaryStage, scene);

            // Show the application
            primaryStage.show();

            LOGGER.info("AlwaysDNS application started successfully");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start application", e);
            showErrorAlert("Application Startup Error",
                    "Failed to start AlwaysDNS application",
                    e.getMessage());
            Platform.exit();
        }
    }

    private void loadStylesheet(Scene scene) {
        try {
            URL cssUrl = getClass().getResource("/edu/au/cpsc/module7/styles/terminal.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                LOGGER.info("Stylesheet loaded successfully");
            } else {
                LOGGER.warning("CSS stylesheet not found - using default styling");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load stylesheet", e);
        }
    }

    private void configureStage(Stage stage, Scene scene) {
        stage.setTitle("AlwaysDNS - Professional DNS Query Tool");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);

        // Add application icon if available
        try {
            URL iconUrl = getClass().getResource("/edu/au/cpsc/module7/icons/app-icon.png");
            if (iconUrl != null) {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load application icon", e);
        }

        // Center stage on screen
        stage.centerOnScreen();

        // Handle close request
        stage.setOnCloseRequest(event -> {
            LOGGER.info("Application closing...");
            Platform.exit();
        });
    }

    private void handleUncaughtException(Thread thread, Throwable throwable) {
        LOGGER.log(Level.SEVERE, "Uncaught exception in thread " + thread.getName(), throwable);

        Platform.runLater(() -> {
            showErrorAlert("Unexpected Error",
                    "An unexpected error occurred",
                    throwable.getMessage());
        });
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        LOGGER.info("AlwaysDNS application stopped");
    }

    public static void main(String[] args) {
        // Set system properties for better JavaFX experience
        // Preloader disabled (class not present)
        // System.setProperty("javafx.preloader", "edu.au.cpsc.module7.Preloader");
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        launch(args);
    }
}