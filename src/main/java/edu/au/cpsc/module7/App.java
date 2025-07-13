package edu.au.cpsc.module7;

import com.google.inject.Guice;
import com.google.inject.Injector;
import edu.au.cpsc.module7.controllers.ToolInstallationDialog;
import edu.au.cpsc.module7.di.AppModule;
import edu.au.cpsc.module7.services.SystemToolsManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
 * NetArmyKn1f3 - A comprehensive network analysis and monitoring tool with JavaFX GUI
 */
public class App extends Application {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    private static Injector injector;

    @Override
    public void init() {
        injector = Guice.createInjector(new AppModule());
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Set up uncaught exception handler
            Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/au/cpsc/module7/styles/fxml/MainWindow.fxml"));
            loader.setControllerFactory(injector::getInstance);
            Parent root = loader.load();

            Scene scene = new Scene(root, 1200, 800);
            loadStylesheet(scene);
            configureStage(primaryStage, scene);
            primaryStage.show();

            LOGGER.info("NetArmyKn1f3 application started successfully");

            // Perform dependency check AFTER the main window is shown
            SystemToolsManager toolsManager = injector.getInstance(SystemToolsManager.class);
            java.util.List<String> missingTools = toolsManager.getMissingTools();
            LOGGER.info("Missing tools detected: " + missingTools);
            
            if (!missingTools.isEmpty()) {
                LOGGER.info("Showing tool installation dialog for missing tools: " + missingTools);
                // Small delay to ensure main window is fully rendered
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(500); // Give UI time to settle
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ToolInstallationDialog.showInstallationDialog(toolsManager);
                });
            } else {
                LOGGER.info("No missing tools detected, skipping installation dialog");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start application", e);
            showErrorAlert("Application Startup Error",
                    "Failed to start NetArmyKn1f3 application",
                    e.getMessage());
            Platform.exit();
        }
    }

    private void loadStylesheet(Scene scene) {
        try {
            String stylesheet = getClass().getResource("/edu/au/cpsc/module7/styles/nord-theme.css").toExternalForm();
            scene.getStylesheets().add(stylesheet);
            LOGGER.info("Stylesheet loaded successfully");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load stylesheet", e);
        }
    }

    private void configureStage(Stage stage, Scene scene) {
        stage.setTitle("NetArmyKn1f3 - Network Analysis & Monitoring Tool");
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
        LOGGER.info("NetArmyKn1f3 application stopped");
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