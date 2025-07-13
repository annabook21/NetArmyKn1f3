package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.AwsFirewallConfiguration;
import edu.au.cpsc.module7.models.AwsFirewallConfiguration.PayloadCategory;
import edu.au.cpsc.module7.models.AwsFirewallConfiguration.TestType;
import edu.au.cpsc.module7.models.FirewallTestResult;
import edu.au.cpsc.module7.models.FirewallTestResult.TestStatus;
import edu.au.cpsc.module7.services.AwsFirewallTestingService;
import edu.au.cpsc.module7.services.FirewallPayloadGenerator;
import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller for the Firewall Rule Tester UI
 * 
 * Note: Despite the "AWS" naming in some classes, this tool does NOT integrate with AWS APIs.
 * It tests firewall effectiveness by sending payloads and analyzing responses.
 */
public class AwsFirewallTesterController {
    
    private static final Logger LOGGER = Logger.getLogger(AwsFirewallTesterController.class.getName());
    
    // Configuration Controls
    @FXML private TextField configurationNameField;
    @FXML private ChoiceBox<TestType> testTypeChoiceBox;
    @FXML private TextField targetResourceField;
    @FXML private TextField timeoutSecondsField;
    @FXML private TextField maxConcurrentTestsField;
    @FXML private CheckBox enableResponseAnalysisCheckBox;
    
    // Payload Selection
    @FXML private ListView<PayloadCategory> payloadCategoriesListView;
    @FXML private TextArea customPayloadsTextArea;
    @FXML private Label payloadCountLabel;
    
    // Test Execution
    @FXML private Button startTestButton;
    @FXML private Button stopTestButton;
    @FXML private ProgressBar testProgressBar;
    @FXML private Label testStatusLabel;
    
    // Results
    @FXML private TableView<FirewallTestResult> resultsTableView;
    @FXML private TableColumn<FirewallTestResult, String> testIdColumn;
    @FXML private TableColumn<FirewallTestResult, String> payloadTypeColumn;
    @FXML private TableColumn<FirewallTestResult, String> statusColumn;
    @FXML private TableColumn<FirewallTestResult, String> responseTimeColumn;
    @FXML private TableColumn<FirewallTestResult, String> detectionMethodColumn;
    
    // Result Details
    @FXML private TextArea resultDetailsTextArea;
    
    // Statistics
    @FXML private Label totalTestsLabel;
    @FXML private Label blockedTestsLabel;
    @FXML private Label allowedTestsLabel;
    @FXML private Label errorTestsLabel;
    @FXML private Label effectivenessLabel;
    
    private final AwsFirewallTestingService testingService;
    private final FirewallPayloadGenerator payloadGenerator;
    private final ObservableList<FirewallTestResult> testResults = FXCollections.observableArrayList();
    private Task<List<FirewallTestResult>> currentTestTask;
    
    @Inject
    public AwsFirewallTesterController(AwsFirewallTestingService testingService, 
                                     FirewallPayloadGenerator payloadGenerator) {
        this.testingService = testingService;
        this.payloadGenerator = payloadGenerator;
    }
    
    @FXML
    public void initialize() {
        LOGGER.info("Initializing Firewall Rule Tester Controller...");
        
        // Initialize configuration controls
        initializeConfigurationControls();
        
        // Initialize payload selection
        initializePayloadSelection();
        
        // Initialize results table
        initializeResultsTable();
        
        // Initialize button states
        updateButtonStates(false);
        
        LOGGER.info("Firewall Rule Tester Controller initialized successfully");
    }
    
    private void initializeConfigurationControls() {
        // Test type choice box
        testTypeChoiceBox.setItems(FXCollections.observableArrayList(TestType.values()));
        testTypeChoiceBox.setValue(TestType.BOTH);
        
        // Default values
        configurationNameField.setText("Firewall Rule Test");
        targetResourceField.setText("https://httpbin.org/post");
        timeoutSecondsField.setText("30");
        maxConcurrentTestsField.setText("5");
        enableResponseAnalysisCheckBox.setSelected(true);
    }
    
    private void initializePayloadSelection() {
        // Payload categories list
        ObservableList<PayloadCategory> categories = FXCollections.observableArrayList(
            payloadGenerator.getAllCategories());
        payloadCategoriesListView.setItems(categories);
        payloadCategoriesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Select common categories by default
        payloadCategoriesListView.getSelectionModel().selectIndices(0, 1, 2, 3);
        
        // Custom payload example
        customPayloadsTextArea.setText(
            "custom_sql=' OR 1=1 UNION SELECT * FROM users--\n" +
            "custom_xss=<script>alert('custom')</script>\n" +
            "custom_path=../../../etc/passwd"
        );
        
        // Update payload count when selection changes
        payloadCategoriesListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> updatePayloadCount());
        
        updatePayloadCount();
    }
    
    private void initializeResultsTable() {
        // Configure table columns
        testIdColumn.setCellValueFactory(new PropertyValueFactory<>("testId"));
        payloadTypeColumn.setCellValueFactory(new PropertyValueFactory<>("payloadType"));
        statusColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        responseTimeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getResponseTimeMs() + "ms"));
        detectionMethodColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.join(", ", cellData.getValue().getDetectionMethods())));
        
        // Color-code status column
        statusColumn.setCellFactory(column -> new TableCell<FirewallTestResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (TestStatus.valueOf(item)) {
                        case BLOCKED:
                            setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            break;
                        case ALLOWED:
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            break;
                        case TIMEOUT:
                            setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                            break;
                        case ERROR:
                            setStyle("-fx-text-fill: gray; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });
        
        // Set table data
        resultsTableView.setItems(testResults);
        
        // Show details when row is selected
        resultsTableView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    showResultDetails(newSelection);
                }
            });
    }
    
    private void updatePayloadCount() {
        int count = 0;
        for (PayloadCategory category : payloadCategoriesListView.getSelectionModel().getSelectedItems()) {
            count += payloadGenerator.generatePayloads(category, 5).size(); // 5 base payloads with variations
            count += payloadGenerator.generateUrlEncodedPayloads(category).size(); // Encoded variants
        }
        
        // Add custom payloads
        String customText = customPayloadsTextArea.getText().trim();
        if (!customText.isEmpty()) {
            count += customText.split("\n").length;
        }
        
        payloadCountLabel.setText("Estimated payloads: " + count);
    }
    
    @FXML
    private void handleStartTest() {
        try {
            // Build configuration
            AwsFirewallConfiguration config = buildConfiguration();
            
            // Create and start test task
            currentTestTask = testingService.executeFirewallTests(config);
            
            // Bind UI to task
            testProgressBar.progressProperty().bind(currentTestTask.progressProperty());
            testStatusLabel.textProperty().bind(currentTestTask.messageProperty());
            
            // Handle task completion
            currentTestTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    List<FirewallTestResult> results = currentTestTask.getValue();
                    testResults.clear();
                    testResults.addAll(results);
                    updateStatistics();
                    updateButtonStates(false);
                    testStatusLabel.setText("Test completed successfully");
                });
            });
            
            currentTestTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    updateButtonStates(false);
                    testStatusLabel.setText("Test failed: " + currentTestTask.getException().getMessage());
                });
            });
            
            currentTestTask.setOnCancelled(e -> {
                Platform.runLater(() -> {
                    updateButtonStates(false);
                    testStatusLabel.setText("Test cancelled");
                });
            });
            
            // Start task
            Thread testThread = new Thread(currentTestTask);
            testThread.setDaemon(true);
            testThread.start();
            
            updateButtonStates(true);
            
        } catch (Exception e) {
            showErrorAlert("Configuration Error", "Failed to start test: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleStopTest() {
        if (currentTestTask != null && currentTestTask.isRunning()) {
            currentTestTask.cancel();
        }
    }
    
    @FXML
    private void handleClearResults() {
        testResults.clear();
        resultDetailsTextArea.clear();
        updateStatistics();
    }

    @FXML
    private void openTldrFailLink() {
        try {
            // Open the tldr.fail website in the default browser
            java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://tldr.fail/"));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open tldr.fail link", e);
            showErrorAlert("Link Error", "Could not open https://tldr.fail/ in browser. Please visit the link manually.");
        }
    }

    private AwsFirewallConfiguration buildConfiguration() {
        // Parse custom payloads
        Map<String, String> customPayloads = new HashMap<>();
        String customText = customPayloadsTextArea.getText().trim();
        if (!customText.isEmpty()) {
            for (String line : customText.split("\n")) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    customPayloads.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        
        return new AwsFirewallConfiguration.Builder()
            .configurationName(configurationNameField.getText())
            .testType(testTypeChoiceBox.getValue())
            .awsRegion("N/A") // Not used in current implementation
            .targetResource(targetResourceField.getText())
            .payloadCategories(new ArrayList<>(payloadCategoriesListView.getSelectionModel().getSelectedItems()))
            .customPayloads(customPayloads)
            .timeoutSeconds(Integer.parseInt(timeoutSecondsField.getText()))
            .maxConcurrentTests(Integer.parseInt(maxConcurrentTestsField.getText()))
            .enableCloudWatchLogs(false) // Not implemented
            .enableResponseAnalysis(enableResponseAnalysisCheckBox.isSelected())
            .build();
    }
    
    private void updateButtonStates(boolean testRunning) {
        startTestButton.setDisable(testRunning);
        stopTestButton.setDisable(!testRunning);
        
        if (!testRunning) {
            testProgressBar.progressProperty().unbind();
            testStatusLabel.textProperty().unbind();
            testProgressBar.setProgress(0);
        }
    }
    
    private void showResultDetails(FirewallTestResult result) {
        StringBuilder details = new StringBuilder();
        details.append("Test ID: ").append(result.getTestId()).append("\n");
        details.append("Timestamp: ").append(result.getTimestamp()).append("\n");
        details.append("Target: ").append(result.getTargetResource()).append("\n");
        details.append("Payload Type: ").append(result.getPayloadType()).append("\n");
        details.append("Status: ").append(result.getStatus()).append("\n");
        details.append("Firewall Type: ").append(result.getFirewallType()).append("\n");
        details.append("Response Time: ").append(result.getResponseTimeMs()).append("ms\n");
        details.append("HTTP Status Code: ").append(result.getHttpStatusCode()).append("\n");
        details.append("Detection Methods: ").append(String.join(", ", result.getDetectionMethods())).append("\n");
        
        if (result.getErrorMessage() != null) {
            details.append("Error: ").append(result.getErrorMessage()).append("\n");
        }
        
        details.append("\nPayload:\n").append(result.getPayload()).append("\n");
        
        if (result.getResponseBody() != null && !result.getResponseBody().trim().isEmpty()) {
            details.append("\nResponse Body:\n").append(result.getResponseBody());
        }
        
        resultDetailsTextArea.setText(details.toString());
    }
    
    private void updateStatistics() {
        int total = testResults.size();
        int blocked = (int) testResults.stream().filter(r -> r.getStatus() == TestStatus.BLOCKED).count();
        int allowed = (int) testResults.stream().filter(r -> r.getStatus() == TestStatus.ALLOWED).count();
        int errors = (int) testResults.stream().filter(r -> r.getStatus() == TestStatus.ERROR).count();
        
        totalTestsLabel.setText(String.valueOf(total));
        blockedTestsLabel.setText(String.valueOf(blocked));
        allowedTestsLabel.setText(String.valueOf(allowed));
        errorTestsLabel.setText(String.valueOf(errors));
        
        // Calculate effectiveness (percentage of malicious payloads blocked)
        double effectiveness = total > 0 ? (double) blocked / total * 100 : 0;
        effectivenessLabel.setText(String.format("%.1f%%", effectiveness));
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
} 