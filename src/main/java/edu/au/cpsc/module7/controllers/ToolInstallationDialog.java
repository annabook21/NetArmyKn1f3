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

    public ToolInstallationDialog(SystemToolsManager toolsManager) {
        this.toolsManager = toolsManager;
        this.toolAvailability = toolsManager.checkToolAvailability();

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
            URL cssUrl = getClass().getResource("/edu/au/cpsc/module7/styles/nord-theme.css");
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
                currentInstallTask.cancel(true);
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

        currentInstallTask.setOnSucceeded(event -> installationCompleted(currentInstallTask.getValue()));
        currentInstallTask.setOnFailed(event -> installationCompleted(false));
        currentInstallTask.setOnCancelled(event -> installationCompleted(false));


        new Thread(currentInstallTask).start();
    }

    private void cancelInstallation() {
        if (currentInstallTask != null && currentInstallTask.isRunning()) {
            currentInstallTask.cancel(true);
        }
    }

    private void installationCompleted(boolean success) {
        Platform.runLater(() -> {
            progressBar.progressProperty().unbind();
            logTextArea.textProperty().unbind();
            progressBar.setProgress(success ? 1.0 : 0.0);
            installButton.setVisible(true);
            cancelButton.setVisible(false);

            if (success) {
                showInfoAlert("Success", "Tools installed successfully. Please restart the application.");
                // Re-check availability and update UI
                toolsManager.checkToolAvailability();
                createToolsGrid(); // This will redraw the grid with new status
            } else {
                showErrorAlert("Failed", "Installation failed. Check logs for details.");
            }
            // Update button state based on new availability
            installButton.setDisable(toolsManager.getMissingTools().isEmpty());
        });
    }

    private String getToolDescription(String tool) {
        switch (tool) {
            case "nmap":
                return "Network discovery and security auditing tool";
            case "traceroute":
                return "Network diagnostic tool for tracing packet routes";
            case "mtr":
                return "Network diagnostic tool combining ping and traceroute";
            case "hping3":
                String desc = "Command-line packet crafting and analysis tool";
                // Check if nping is available as alternative on macOS
                if (toolsManager.hasHping3Alternative()) {
                    desc += " (nping alternative available)";
                }
                return desc;
            case "dig":
                return "DNS lookup tool for detailed domain information";
            case "tcpdump":
                return "Packet capture and analysis tool for network monitoring";
            case "tor":
                return "Tor anonymity network for geographic IP diversity testing";
            case "curl":
                return "Command-line tool for transferring data with URLs";
            default:
                return "Network utility tool";
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
        // This is not called when creating the stage manually
    }

    public static void showInstallationDialog(SystemToolsManager toolsManager) {
        // Now checks for availability internally
        ToolInstallationDialog dialog = new ToolInstallationDialog(toolsManager);
        dialog.showAndWait();
    }
}
