package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.QueryResult;
import edu.au.cpsc.module7.models.QueryType;
import edu.au.cpsc.module7.services.DNSQueryService;
import edu.au.cpsc.module7.services.SettingsService;
import edu.au.cpsc.module7.services.SystemToolsManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;

/**
 * Main window controller for the AlwaysDNS application
 * Handles user interactions and coordinates between UI and services
 */
public class MainWindowController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(MainWindowController.class.getName());

    // FXML Controls
    @FXML private TextField domainTextField;
    @FXML private CheckBox digCheck, nslookupCheck, whoisCheck, hostCheck;
    @FXML private TextArea resultsTextArea;
    @FXML private Button runButton, clearButton, saveButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private MenuItem exportMenuItem, settingsMenuItem;
    @FXML private ComboBox<String> historyComboBox;

    // Services
    private DNSQueryService queryService;
    private SettingsService settingsService;

    // State
    private Task<List<QueryResult>> currentTask;
    private List<String> domainHistory = new ArrayList<>();
    private StringBuilder currentResults = new StringBuilder();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            // Initialize services
            queryService = new DNSQueryService();
            settingsService = new SettingsService();

            // Setup UI
            setupUI();
            setupKeyboardShortcuts();
            loadSettings();

            // Check for required tools
            checkAndSetupTools();

            // Set initial state
            updateUIState(false);
            statusLabel.setText("Ready");

            LOGGER.info("MainWindowController initialized successfully");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize controller", e);
            showErrorAlert("Initialization Error", "Failed to initialize application", e.getMessage());
        }
    }

    /**
     * Checks for required DNS tools and offers to install them if missing
     */
    private void checkAndSetupTools() {
        try {
            SystemToolsManager toolsManager = new SystemToolsManager();
            Map<String, Boolean> toolAvailability = toolsManager.checkToolAvailability();

            List<String> missingTools = toolsManager.getMissingTools();

            if (!missingTools.isEmpty()) {
                LOGGER.warning("Missing DNS tools detected: " + missingTools);

                // Show notification in status
                statusLabel.setText("Missing DNS tools detected - click here to install");
                statusLabel.setStyle("-fx-text-fill: orange; -fx-cursor: hand;");
                statusLabel.setOnMouseClicked(e -> showToolInstallationDialog(toolsManager));

                // Also show immediate dialog asking user
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Missing DNS Tools");
                    alert.setHeaderText("Required DNS tools are missing");
                    alert.setContentText(
                            "The following DNS tools are not installed:\n\n" +
                                    String.join(", ", missingTools) + "\n\n" +
                                    "Would you like to install them automatically?"
                    );

                    ButtonType installButton = new ButtonType("Install Now");
                    ButtonType laterButton = new ButtonType("Later");
                    alert.getButtonTypes().setAll(installButton, laterButton);

                    alert.showAndWait().ifPresent(response -> {
                        if (response == installButton) {
                            showToolInstallationDialog(toolsManager);
                        }
                    });
                });
            } else {
                LOGGER.info("All required DNS tools are available");
                statusLabel.setText("All DNS tools available - Ready");
                statusLabel.setStyle("-fx-text-fill: green;");
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking tool availability", e);
            statusLabel.setText("Could not verify DNS tools - some features may not work");
            statusLabel.setStyle("-fx-text-fill: orange;");
        }
    }

    /**
     * Shows the tool installation dialog
     */
    private void showToolInstallationDialog(SystemToolsManager toolsManager) {
        try {
            ToolInstallationDialog.showInstallationDialog(toolsManager);

            // After dialog closes, re-check tools
            checkAndSetupTools();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error showing tool installation dialog", e);
            showErrorAlert("Tool Installation Error",
                    "Failed to open tool installation dialog",
                    e.getMessage());
        }
    }

    @FXML
    private void handleManageTools() {
        try {
            SystemToolsManager toolsManager = new SystemToolsManager();
            showToolInstallationDialog(toolsManager);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error opening tools manager", e);
            showErrorAlert("Tools Manager Error",
                    "Failed to open DNS tools manager",
                    e.getMessage());
        }
    }

    private void setupUI() {
        // Configure domain input
        domainTextField.setPromptText("Enter domain name (e.g., google.com, amazon.com)");
        domainTextField.textProperty().addListener((obs, oldText, newText) -> {
            runButton.setDisable(newText.trim().isEmpty());
        });

        // Configure results area
        resultsTextArea.setEditable(false);
        resultsTextArea.setWrapText(true);
        resultsTextArea.setStyle("-fx-font-family: 'Courier New', monospace;");

        // Configure checkboxes with default selections
        digCheck.setSelected(true);
        nslookupCheck.setSelected(true);
        whoisCheck.setSelected(true);
        hostCheck.setSelected(false);

        // Configure progress bar
        progressBar.setVisible(false);

        // Configure history combo box
        historyComboBox.setOnAction(e -> {
            String selected = historyComboBox.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isEmpty()) {
                domainTextField.setText(selected);
            }
        });

        // Configure buttons
        clearButton.setOnAction(e -> handleClearResults());
        saveButton.setOnAction(e -> handleSaveResults());

        // Add tooltips
        addTooltips();
    }

    private void setupKeyboardShortcuts() {
        // Enter key in domain field triggers query
        domainTextField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRunQueries();
            }
        });

        // F5 to refresh/run queries - add when scene becomes available
        domainTextField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F5) {
                        handleRunQueries();
                        event.consume();
                    }
                });
            }
        });
    }

    private void addTooltips() {
        runButton.setTooltip(new Tooltip("Run DNS queries for the specified domain (F5)"));
        clearButton.setTooltip(new Tooltip("Clear all results"));
        saveButton.setTooltip(new Tooltip("Save results to file"));

        digCheck.setTooltip(new Tooltip("Domain Information Groper - detailed DNS lookup"));
        nslookupCheck.setTooltip(new Tooltip("Name Server Lookup - basic DNS resolution"));
        whoisCheck.setTooltip(new Tooltip("Domain registration and ownership information"));
        hostCheck.setTooltip(new Tooltip("Simple hostname lookup utility"));
    }

    private void loadSettings() {
        try {
            // Load domain history
            domainHistory = settingsService.getDomainHistory();
            historyComboBox.getItems().setAll(domainHistory);

            // Load other settings
            Map<String, String> settings = settingsService.getSettings();
            // Apply settings as needed

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load settings", e);
        }
    }

    @FXML
    private void handleRunQueries() {
        String domain = domainTextField.getText().trim();

        if (domain.isEmpty()) {
            showWarningAlert("Input Required", "Please enter a domain name to query.");
            domainTextField.requestFocus();
            return;
        }

        if (!isValidDomain(domain)) {
            showWarningAlert("Invalid Domain", "Please enter a valid domain name.");
            domainTextField.requestFocus();
            return;
        }

        // Check if any tests are selected
        if (!digCheck.isSelected() && !nslookupCheck.isSelected() &&
                !whoisCheck.isSelected() && !hostCheck.isSelected()) {
            showWarningAlert("No Tests Selected", "Please select at least one DNS test to run.");
            return;
        }

        // Cancel current task if running
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }

        // Add to history
        addToHistory(domain);

        // Create and configure task
        currentTask = createQueryTask(domain);

        // Bind UI to task
        bindUIToTask(currentTask);

        // Start task
        Thread taskThread = new Thread(currentTask);
        taskThread.setDaemon(true);
        taskThread.start();

        LOGGER.info("Started DNS queries for domain: " + domain);
    }

    private Task<List<QueryResult>> createQueryTask(String domain) {
        Set<QueryType> queryTypes = getSelectedQueryTypes();

        return new Task<List<QueryResult>>() {
            @Override
            protected List<QueryResult> call() throws Exception {
                StringBuilder msg = new StringBuilder();
                msg.append("Initializing DNS queries for ").append(domain).append("...\n");
                updateMessage(msg.toString());
                updateProgress(0, queryTypes.size());

                List<QueryResult> results = new ArrayList<>();
                int completed = 0;

                for (QueryType queryType : queryTypes) {
                    if (isCancelled()) {
                        break;
                    }

                    msg.append("\n--- Running ").append(queryType.name().toLowerCase()).append(" query ---\n");
                    updateMessage(msg.toString());

                    try {
                        QueryResult result = queryService.executeQuery(domain, queryType);
                        results.add(result);

                        msg.append(result.getOutput()).append("\n");
                        updateMessage(msg.toString());

                        updateProgress(++completed, queryTypes.size());

                    } catch (Exception e) {
                        String errorMsg = "Error running " + queryType.name().toLowerCase() + " query: " + e.getMessage();
                        msg.append(errorMsg).append("\n");
                        updateMessage(msg.toString());
                        LOGGER.log(Level.WARNING, errorMsg, e);
                    }
                }

                if (!isCancelled()) {
                    msg.append("\n=== Query execution completed ===\n");
                    updateMessage(msg.toString());
                    updateProgress(queryTypes.size(), queryTypes.size());
                }

                return results;
            }
        };
    }

    private void bindUIToTask(Task<List<QueryResult>> task) {
        // Bind progress
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.visibleProperty().bind(task.runningProperty());

        // Bind results
        resultsTextArea.textProperty().bind(task.messageProperty());

        // Bind button states
        runButton.disableProperty().bind(task.runningProperty());
        runButton.textProperty().bind(
                Bindings.when(task.runningProperty())
                        .then("Cancel")
                        .otherwise("Run Queries")
        );

        // Bind status
        statusLabel.textProperty().bind(
                Bindings.when(task.runningProperty())
                        .then("Running queries...")
                        .otherwise("Ready")
        );

        // Handle completion
        task.setOnSucceeded(e -> {
            updateUIState(false);
            currentResults.setLength(0);
            currentResults.append(resultsTextArea.getText());
            saveButton.setDisable(currentResults.length() == 0);
            statusLabel.textProperty().unbind();
            statusLabel.setText("Queries completed successfully");
            LOGGER.info("DNS queries completed successfully");
        });

        task.setOnFailed(e -> {
            updateUIState(false);
            Throwable exception = task.getException();
            String errorMsg = "Query execution failed: " + (exception != null ? exception.getMessage() : "Unknown error");
            statusLabel.textProperty().unbind();
            statusLabel.setText("Query failed");
            showErrorAlert("Query Failed", errorMsg, null);
            LOGGER.log(Level.SEVERE, errorMsg, exception);
        });

        task.setOnCancelled(e -> {
            updateUIState(false);
            statusLabel.textProperty().unbind();
            statusLabel.setText("Queries cancelled");
            LOGGER.info("DNS queries cancelled by user");
        });

        // Handle cancel button
        runButton.setOnAction(e -> {
            if (task.isRunning()) {
                task.cancel();
            } else {
                handleRunQueries();
            }
        });
    }

    private Set<QueryType> getSelectedQueryTypes() {
        Set<QueryType> types = new HashSet<>();
        if (digCheck.isSelected()) types.add(QueryType.DIG);
        if (nslookupCheck.isSelected()) types.add(QueryType.NSLOOKUP);
        if (whoisCheck.isSelected()) types.add(QueryType.WHOIS);
        if (hostCheck.isSelected()) types.add(QueryType.HOST);
        return types;
    }

    private void addToHistory(String domain) {
        if (!domainHistory.contains(domain)) {
            domainHistory.add(0, domain);
            if (domainHistory.size() > 20) {
                domainHistory = domainHistory.subList(0, 20);
            }
            historyComboBox.getItems().setAll(domainHistory);

            // Save to settings
            try {
                settingsService.saveDomainHistory(domainHistory);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save domain history", e);
            }
        }
    }

    private boolean isValidDomain(String domain) {
        // Basic domain validation
        return domain.matches("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$");
    }

    private void updateUIState(boolean running) {
        if (!progressBar.visibleProperty().isBound()) {
            progressBar.setVisible(running);
        }
        domainTextField.setDisable(running);
        digCheck.setDisable(running);
        nslookupCheck.setDisable(running);
        whoisCheck.setDisable(running);
        hostCheck.setDisable(running);
        historyComboBox.setDisable(running);
        clearButton.setDisable(running);
        saveButton.setDisable(running || currentResults.length() == 0);
    }

    @FXML
    private void handleClearResults() {
        resultsTextArea.clear();
        currentResults.setLength(0);
        saveButton.setDisable(true);
        statusLabel.setText("Results cleared");
    }

    @FXML
    private void handleSaveResults() {
        if (currentResults.length() == 0) {
            showWarningAlert("No Results", "There are no results to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save DNS Query Results");
        fileChooser.setInitialFileName("dns_results_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(saveButton.getScene().getWindow());
        if (file != null) {
            try {
                String content = "AlwaysDNS Query Results\n" +
                        "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                        "Domain: " + domainTextField.getText() + "\n" +
                        "=" + "=".repeat(50) + "\n\n" +
                        currentResults.toString();

                Files.write(file.toPath(), content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                statusLabel.setText("Results saved to " + file.getName());
                LOGGER.info("Results saved to: " + file.getAbsolutePath());

            } catch (IOException e) {
                String errorMsg = "Failed to save results: " + e.getMessage();
                showErrorAlert("Save Error", errorMsg, null);
                LOGGER.log(Level.SEVERE, errorMsg, e);
            }
        }
    }

    @FXML
    private void handleShowSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/au/cpsc/module7/styles/fxml/SettingsDialog.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Settings - AlwaysDNS");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(runButton.getScene().getWindow());
            dialogStage.setScene(new Scene(loader.load()));
            dialogStage.setResizable(false);

            SettingsDialogController controller = loader.getController();
            controller.setSettingsService(settingsService);

            dialogStage.showAndWait();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open settings dialog", e);
            showErrorAlert("Settings Error", "Failed to open settings dialog", e.getMessage());
        }
    }

    @FXML
    private void handleExit() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
        Platform.exit();
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About AlwaysDNS");
        alert.setHeaderText("AlwaysDNS - Professional DNS Query Tool");
        alert.setContentText("Version 1.0\n\n" +
                "A comprehensive DNS query tool with support for:\n" +
                "• dig - Domain Information Groper\n" +
                "• nslookup - Name Server Lookup\n" +
                "• whois - Domain Registration Info\n" +
                "• host - Hostname Lookup\n\n" +
                "Built with JavaFX for cross-platform compatibility.");
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showWarningAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}