package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.services.SystemToolsManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dialog for managing DNS tool installation
 */
public class ToolInstallationDialog extends Stage implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(ToolInstallationDialog.class.getName());

    private final SystemToolsManager toolsManager;
    private final Map<String, Boolean> toolAvailability;

    // UI Components
    private VBox mainContainer;
    private Label titleLabel;
    private Label systemInfoLabel;
    private GridPane toolsGrid;
    private CheckBox usePortableCheckBox;
    private ProgressBar progressBar;
    private TextArea logTextArea;
    private Button installButton;
    private Button cancelButton;
    private Button closeButton;

    private Task<Boolean> currentInstallTask;

    public ToolInstallationDialog(SystemToolsManager toolsManager, Map<String, Boolean> toolAvailability) {
        this.toolsManager = toolsManager;
        this.toolAvailability = toolAvailability;

        initializeDialog();
        createUI();
        setupEventHandlers();
    }

    private void initializeDialog() {
        setTitle("DNS Tools Installation Manager");
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setResizable(true);
        setMinWidth(600);
        setMinHeight(500);
    }

    private void createUI() {
        mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(20));

        // Title and system info
        titleLabel = new Label("DNS Tools Installation Manager");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        systemInfoLabel = new Label("System: " + toolsManager.getSystemInfo().getDescription());
        systemInfoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Tools status grid
        createToolsGrid();

        // Installation options
        usePortableCheckBox = new CheckBox("Use portable installations (recommended if admin access is unavailable)");
        usePortableCheckBox.setSelected(true);

        // Progress section
        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        logTextArea.setPrefRowCount(8);
        logTextArea.setStyle("-fx-font-family: monospace;");
        logTextArea.setVisible(false);

        // Buttons
        HBox buttonBox = createButtonBox();

        // Add all components
        mainContainer.getChildren().addAll(
                titleLabel,
                systemInfoLabel,
                new Separator(),
                new Label("Tool Status:"),
                toolsGrid,
                new Separator(),
                usePortableCheckBox,
                progressBar,
                logTextArea,
                buttonBox
        );

        VBox.setVgrow(logTextArea, Priority.ALWAYS);

        Scene scene = new Scene(mainContainer);
        setScene(scene);

        // Apply CSS if available
        try {
            URL cssUrl = getClass().getResource("/edu/au/cpsc/module7/styles/terminal.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load CSS", e);
        }
    }

    private void createToolsGrid() {
        toolsGrid = new GridPane();
        toolsGrid.setHgap(10);
        toolsGrid.setVgap(10);
        toolsGrid.setPadding(new Insets(10));
        toolsGrid.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        // Headers
        toolsGrid.add(new Label("Tool"), 0, 0);
        toolsGrid.add(new Label("Status"), 1, 0);
        toolsGrid.add(new Label("Description"), 2, 0);

        // Set header style
        for (int i = 0; i < 3; i++) {
            Label header = (Label) toolsGrid.getChildren().get(i);
            header.setStyle("-fx-font-weight: bold;");
        }

        int row = 1;
        for (Map.Entry<String, Boolean> entry : toolAvailability.entrySet()) {
            String tool = entry.getKey();
            boolean available = entry.getValue();

            Label toolLabel = new Label(tool);
            toolLabel.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");

            Label statusLabel = new Label(available ? "✓ Installed" : "✗ Missing");
            statusLabel.setStyle(available ?
                    "-fx-text-fill: green; -fx-font-weight: bold;" :
                    "-fx-text-fill: red; -fx-font-weight: bold;");

            String description = getToolDescription(tool);
            Label descLabel = new Label(description);
            descLabel.setWrapText(true);
            descLabel.setMaxWidth(200);

            toolsGrid.add(toolLabel, 0, row);
            toolsGrid.add(statusLabel, 1, row);
            toolsGrid.add(descLabel, 2, row);

            row++;
        }
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        installButton = new Button("Install Missing Tools");
        installButton.setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-font-weight: bold;");
        installButton.setDefaultButton(true);

        cancelButton = new Button("Cancel Installation");
        cancelButton.setVisible(false);

        closeButton = new Button("Close");
        closeButton.setCancelButton(true);

        // Only show install button if there are missing tools
        List<String> missingTools = toolsManager.getMissingTools();
        installButton.setDisable(missingTools.isEmpty());

        if (missingTools.isEmpty()) {
            installButton.setText("All Tools Installed");
        }

        buttonBox.getChildren().addAll(installButton, cancelButton, closeButton);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        return buttonBox;
    }

    private void setupEventHandlers() {
        installButton.setOnAction(e -> startInstallation());
        cancelButton.setOnAction(e -> cancelInstallation());
        closeButton.setOnAction(e -> close());

        setOnCloseRequest(e -> {
            if (currentInstallTask != null && currentInstallTask.isRunning()) {
                currentInstallTask.cancel();
            }
        });
    }

    private void startInstallation() {
        List<String> missingTools = toolsManager.getMissingTools();
        if (missingTools.isEmpty()) {
            showInfoAlert("No Installation Needed", "All required DNS tools are already installed.");
            return;
        }

        // Show confirmation dialog
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Installation");
        confirmAlert.setHeaderText("Install Missing DNS Tools");
        confirmAlert.setContentText(
                "The following tools will be installed:\n\n" +
                        String.join("\n", missingTools) + "\n\n" +
                        "This may require administrator privileges. Continue?"
        );

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                performInstallation(missingTools);
            }
        });
    }

    private void performInstallation(List<String> missingTools) {
        // Prepare UI for installation
        installButton.setVisible(false);
        cancelButton.setVisible(true);
        progressBar.setVisible(true);
        logTextArea.setVisible(true);

        // Create installation task
        boolean usePortable = usePortableCheckBox.isSelected();
        currentInstallTask = toolsManager.createInstallationTask(missingTools, usePortable);

        // Bind UI to task
        progressBar.progressProperty().bind(currentInstallTask.progressProperty());
        logTextArea.textProperty().bind(currentInstallTask.messageProperty());

        // Handle task completion
        currentInstallTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                boolean success = currentInstallTask.getValue();
                installationCompleted(success);
            });
        });

        currentInstallTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = currentInstallTask.getException();
                String errorMsg = "Installation failed: " + (exception != null ? exception.getMessage() : "Unknown error");
                logTextArea.appendText("\n\nERROR: " + errorMsg);
                installationCompleted(false);
                LOGGER.log(Level.SEVERE, errorMsg, exception);
            });
        });

        currentInstallTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                logTextArea.appendText("\n\nInstallation cancelled by user.");
                installationCompleted(false);
            });
        });

        // Start installation
        Thread installThread = new Thread(currentInstallTask);
        installThread.setDaemon(true);
        installThread.start();

        LOGGER.info("Started installation of missing tools: " + missingTools);
    }

    private void cancelInstallation() {
        if (currentInstallTask != null && currentInstallTask.isRunning()) {
            currentInstallTask.cancel();
        }
    }

    private void installationCompleted(boolean success) {
        // Reset UI
        cancelButton.setVisible(false);
        progressBar.setVisible(false);
        closeButton.setText(success ? "Close" : "Close");

        if (success) {
            // Refresh tool availability
            Map<String, Boolean> newAvailability = toolsManager.checkToolAvailability();

            // Update the grid
            toolsGrid.getChildren().clear();
            createToolsGrid();

            showInfoAlert("Installation Complete",
                    "DNS tools have been installed successfully!\n\n" +
                            "You can now use all DNS query features.");
        } else {
            installButton.setVisible(true);
            installButton.setText("Retry Installation");

            showErrorAlert("Installation Failed",
                    "Some tools could not be installed automatically.\n\n" +
                            "Please check the log above for details or install them manually.");
        }
    }

    private String getToolDescription(String tool) {
        switch (tool.toLowerCase()) {
            case "dig":
                return "Domain Information Groper - Advanced DNS lookup tool with detailed output";
            case "nslookup":
                return "Name Server Lookup - Basic DNS resolution utility";
            case "whois":
                return "Domain registration and ownership information lookup";
            case "host":
                return "Simple hostname to IP address lookup utility";
            default:
                return "DNS utility tool";
        }
    }

    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // This method is called if using FXML, but we're creating UI programmatically
    }

    /**
     * Factory method to create and show the installation dialog
     */
    public static void showInstallationDialog(SystemToolsManager toolsManager) {
        Map<String, Boolean> availability = toolsManager.checkToolAvailability();

        ToolInstallationDialog dialog = new ToolInstallationDialog(toolsManager, availability);
        dialog.showAndWait();
    }

}
