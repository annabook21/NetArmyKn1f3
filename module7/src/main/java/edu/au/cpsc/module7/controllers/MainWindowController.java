package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.DNSQueryService;
import edu.au.cpsc.module7.SettingsService;
import edu.au.cpsc.module7.QueryResult;
import edu.au.cpsc.module7.QueryType;
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

            // Set initial state
            updateUIState(false);
            statusLabel.setText("Ready");

            LOGGER.info("MainWindowController initialized successfully");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize controller", e);
            showErrorAlert("Initialization Error", "Failed to initialize application", e.getMessage());
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

        // F5 to refresh/run queries
        domainTextField.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                handleRunQueries();
                event.consume();
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
                updateMessage("Initializing DNS queries for " + domain + "...\n");
                updateProgress(0, queryTypes.size());

                List<QueryResult> results = new ArrayList<>();
                int completed = 0;

                for (QueryType queryType : queryTypes) {
                    if (isCancelled()) {
                        break;
                    }

                    updateMessage(getMessage() + "\n--- Running " + queryType.name().toLowerCase() + " query ---\n");

                    try {
                        QueryResult result = queryService.executeQuery(domain, queryType);
                        results.add(result);

                        // Update progress and message
                        updateMessage(getMessage() + result.getOutput() + "\n");
                        updateProgress(++completed, queryTypes.size());

                    } catch (Exception e) {
                        String errorMsg = "Error running " + queryType.name().toLowerCase() + " query: " + e.getMessage();
                        updateMessage(getMessage() + errorMsg + "\n");
                        LOGGER.log(Level.WARNING, errorMsg, e);
                    }
                }

                if (!isCancelled()) {
                    updateMessage(getMessage() + "\n=== Query execution completed ===\n");
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
                task.runningProperty().map(running -> running ? "Cancel" : "Run Queries")
        );

        // Bind status
        statusLabel.textProperty().bind(
                task.runningProperty().map(running -> running ? "Running queries..." : "Ready")
        );

        // Handle completion
        task.setOnSucceeded(e -> {
            updateUIState(false);
            currentResults.setLength(0);
            currentResults.append(resultsTextArea.getText());
            saveButton.setDisable(currentResults.length() == 0);
            statusLabel.setText("Queries completed successfully");
            LOGGER.info("DNS queries completed successfully");
        });

        task.setOnFailed(e -> {
            updateUIState(false);
            Throwable exception = task.getException();
            String errorMsg = "Query execution failed: " + (exception != null ? exception.getMessage() : "Unknown error");
            statusLabel.setText("Query failed");
            showErrorAlert("Query Failed", errorMsg, null);
            LOGGER.log(Level.SEVERE, errorMsg, exception);
        });

        task.setOnCancelled(e -> {
            updateUIState(false);
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
        progressBar.setVisible(running);
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/yourname/alwaysdns/fxml/SettingsDialog.fxml"));
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