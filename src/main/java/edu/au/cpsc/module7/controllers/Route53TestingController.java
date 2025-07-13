package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.Route53RoutingPolicyTest;
import edu.au.cpsc.module7.models.Route53RoutingPolicyTest.RoutingPolicyType;
import edu.au.cpsc.module7.models.Route53RoutingPolicyTest.TestResult;
import edu.au.cpsc.module7.services.Route53RoutingPolicyTestingService;
import edu.au.cpsc.module7.services.Route53ResolverTestingService;
import edu.au.cpsc.module7.services.TorProxyService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Controller for Route53 DNS testing with support for high-volume testing
 * and detailed routing policy analysis
 */
public class Route53TestingController implements Initializable {
    
    private static final Logger logger = Logger.getLogger(Route53TestingController.class.getName());
    
    // Configuration Controls
    @FXML private TextField domainField;
    @FXML private ComboBox<RoutingPolicyType> routingPolicyCombo;
    @FXML private TextField iterationsField;
    @FXML private TextField timeoutField;

    
    // Weighted Routing Configuration
    @FXML private VBox weightedConfigPanel;
    @FXML private TableView<WeightedEndpoint> weightedEndpointsTable;
    
    // Tor Configuration
    @FXML private CheckBox useTorCheckBox;
    @FXML private Label torStatusLabel;
    @FXML private TextField torLocationsField;
    @FXML private Button checkTorButton;
    @FXML private Button startTorButton;
    @FXML private Button stopTorButton;
    
    // Advanced Options
    @FXML private ComboBox<String> dnsServerCombo;
    @FXML private ComboBox<String> recordTypeCombo;
    @FXML private CheckBox measureLatencyCheckBox;
    @FXML private CheckBox testFailoverCheckBox;
    
    // Control Buttons
    @FXML private Button startTestButton;
    @FXML private Button stopTestButton;
    @FXML private Button clearResultsButton;
    @FXML private Button exportResultsButton;
    @FXML private ProgressBar testProgressBar;
    @FXML private Label testStatusLabel;
    
    // Results Display
    @FXML private Label testResultLabel;
    @FXML private Label totalQueriesLabel;
    @FXML private Label avgResponseTimeLabel;
    @FXML private Label uniqueEndpointsLabel;
    @FXML private Label successRateLabel;
    @FXML private Label policyComplianceLabel;
    @FXML private TableView<TestResultRow> detailedResultsTable;
    
    // Endpoint Distribution
    @FXML private ComboBox<String> chartTypeCombo;
    @FXML private Button refreshChartButton;
    @FXML private WebView distributionChartView;
    @FXML private TableView<EndpointDistribution> distributionStatsTable;
    
    // Raw Results
    @FXML private ComboBox<String> rawResultsFilterCombo;
    @FXML private Button saveRawResultsButton;
    @FXML private TextArea rawResultsArea;
    
    // Test Log
    @FXML private CheckBox autoScrollLogCheckBox;
    @FXML private ComboBox<String> logLevelCombo;
    @FXML private TextArea testLogArea;
    
    // Services
    private final Route53RoutingPolicyTestingService routingPolicyService;
    private final Route53ResolverTestingService resolverService;
    private final TorProxyService torService;
    
    // A Record Discovery (new)
    @FXML private Button discoverARecordsButton;
    @FXML private TableView<Route53RoutingPolicyTestingService.DiscoveredARecord> discoveredRecordsTable;
    @FXML private Label discoveredRecordsStatusLabel;
    @FXML private Button clearDiscoveredRecordsButton;
    
    // Data

    private final ObservableList<WeightedEndpoint> weightedEndpoints = FXCollections.observableArrayList();
    private final ObservableList<TestResultRow> testResults = FXCollections.observableArrayList();
    private final ObservableList<EndpointDistribution> distributionStats = FXCollections.observableArrayList();
    private final ObservableList<Route53RoutingPolicyTestingService.DiscoveredARecord> discoveredARecords = FXCollections.observableArrayList();
    
    // Test execution
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private Task<Void> currentTestTask;
    private Map<String, Integer> endpointCounts = new HashMap<>();
    private List<Long> responseTimes = new ArrayList<>();
    private AtomicInteger totalQueries = new AtomicInteger(0);
    private AtomicInteger successfulQueries = new AtomicInteger(0);
    
    @Inject
    public Route53TestingController(Route53RoutingPolicyTestingService routingPolicyService,
                                   Route53ResolverTestingService resolverService,
                                   TorProxyService torService) {
        this.routingPolicyService = routingPolicyService;
        this.resolverService = resolverService;
        this.torService = torService;
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupTableColumns();
        checkTorStatus();
        logMessage("Route53 Testing Controller initialized", "INFO");
    }
    
    private void setupUI() {
        // Setup combo boxes - only include implemented routing policies
        List<RoutingPolicyType> implementedPolicies = Arrays.asList(
            RoutingPolicyType.WEIGHTED,
            RoutingPolicyType.GEOLOCATION, 
            RoutingPolicyType.LATENCY,
            RoutingPolicyType.FAILOVER
            // Note: IP_BASED and MULTIVALUE_ANSWER are not implemented yet
        );
        routingPolicyCombo.setItems(FXCollections.observableArrayList(implementedPolicies));
        routingPolicyCombo.setValue(RoutingPolicyType.WEIGHTED);
        
        // Setup DNS server options
        dnsServerCombo.setItems(FXCollections.observableArrayList(
            "System Default", "8.8.8.8 (Google)", "1.1.1.1 (Cloudflare)", 
            "9.9.9.9 (Quad9)", "169.254.169.253 (AWS VPC)"
        ));
        dnsServerCombo.setValue("System Default");
        
        // Setup record type options
        recordTypeCombo.setItems(FXCollections.observableArrayList(
            "A", "AAAA", "CNAME", "MX", "TXT", "SRV"
        ));
        recordTypeCombo.setValue("A");
        
        // Setup chart type options
        chartTypeCombo.setItems(FXCollections.observableArrayList(
            "Pie Chart", "Bar Chart", "Line Chart"
        ));
        chartTypeCombo.setValue("Pie Chart");
        
        // Setup filter options
        rawResultsFilterCombo.setItems(FXCollections.observableArrayList(
            "All Results", "Successful Only", "Failed Only", "Unique Endpoints"
        ));
        rawResultsFilterCombo.setValue("All Results");
        
        // Setup log level options
        logLevelCombo.setItems(FXCollections.observableArrayList(
            "DEBUG", "INFO", "WARNING", "ERROR"
        ));
        logLevelCombo.setValue("INFO");
        
        // Setup list views

        
        // Setup routing policy change listener
        routingPolicyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateUIForRoutingPolicy(newVal);
        });
        
        // Setup default values
        iterationsField.setText("1000");
        timeoutField.setText("5000");
        torLocationsField.setText("3");
        
        // Setup initial UI state
        updateUIForRoutingPolicy(RoutingPolicyType.WEIGHTED);
    }
    
    private void setupTableColumns() {
        // Setup weighted endpoints table
        TableColumn<WeightedEndpoint, String> endpointCol = new TableColumn<>("Endpoint");
        endpointCol.setCellValueFactory(new PropertyValueFactory<>("endpoint"));
        endpointCol.setPrefWidth(200);
        
        TableColumn<WeightedEndpoint, Integer> weightCol = new TableColumn<>("Weight");
        weightCol.setCellValueFactory(new PropertyValueFactory<>("weight"));
        weightCol.setPrefWidth(100);
        
        TableColumn<WeightedEndpoint, String> expectedPercentCol = new TableColumn<>("Expected %");
        expectedPercentCol.setCellValueFactory(cellData -> {
            int totalWeight = weightedEndpoints.stream().mapToInt(WeightedEndpoint::getWeight).sum();
            double percentage = totalWeight > 0 ? (cellData.getValue().getWeight() * 100.0) / totalWeight : 0;
            return new SimpleStringProperty(String.format("%.1f%%", percentage));
        });
        expectedPercentCol.setPrefWidth(100);
        
        weightedEndpointsTable.getColumns().addAll(endpointCol, weightCol, expectedPercentCol);
        weightedEndpointsTable.setItems(weightedEndpoints);
        
        // Setup detailed results table
        TableColumn<TestResultRow, String> timestampCol = new TableColumn<>("Timestamp");
        timestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timestampCol.setPrefWidth(150);
        
        TableColumn<TestResultRow, String> queryCol = new TableColumn<>("Query #");
        queryCol.setCellValueFactory(new PropertyValueFactory<>("queryNumber"));
        queryCol.setPrefWidth(80);
        
        TableColumn<TestResultRow, String> resolvedEndpointCol = new TableColumn<>("Resolved Endpoint");
        resolvedEndpointCol.setCellValueFactory(new PropertyValueFactory<>("resolvedEndpoint"));
        resolvedEndpointCol.setPrefWidth(200);
        
        TableColumn<TestResultRow, String> responseTimeCol = new TableColumn<>("Response Time (ms)");
        responseTimeCol.setCellValueFactory(new PropertyValueFactory<>("responseTime"));
        responseTimeCol.setPrefWidth(120);
        
        TableColumn<TestResultRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        
        TableColumn<TestResultRow, String> locationCol = new TableColumn<>("Test Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("testLocation"));
        locationCol.setPrefWidth(150);
        
        detailedResultsTable.getColumns().addAll(timestampCol, queryCol, resolvedEndpointCol, 
                                                responseTimeCol, statusCol, locationCol);
        detailedResultsTable.setItems(testResults);
        
        // Setup distribution stats table
        TableColumn<EndpointDistribution, String> endpointDistCol = new TableColumn<>("Endpoint");
        endpointDistCol.setCellValueFactory(new PropertyValueFactory<>("endpoint"));
        endpointDistCol.setPrefWidth(200);
        
        TableColumn<EndpointDistribution, Integer> countCol = new TableColumn<>("Count");
        countCol.setCellValueFactory(new PropertyValueFactory<>("count"));
        countCol.setPrefWidth(100);
        
        TableColumn<EndpointDistribution, String> percentageCol = new TableColumn<>("Percentage");
        percentageCol.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        percentageCol.setPrefWidth(100);
        
        TableColumn<EndpointDistribution, String> expectedCol = new TableColumn<>("Expected %");
        expectedCol.setCellValueFactory(new PropertyValueFactory<>("expectedPercentage"));
        expectedCol.setPrefWidth(100);
        
        TableColumn<EndpointDistribution, String> deviationCol = new TableColumn<>("Deviation");
        deviationCol.setCellValueFactory(new PropertyValueFactory<>("deviation"));
        deviationCol.setPrefWidth(100);
        
        distributionStatsTable.getColumns().addAll(endpointDistCol, countCol, percentageCol, expectedCol, deviationCol);
        distributionStatsTable.setItems(distributionStats);
        
        // Setup discovered A records table
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> ipCol = new TableColumn<>("IP Address");
        ipCol.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        ipCol.setPrefWidth(120);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> sourceDomainCol = new TableColumn<>("Source Domain");
        sourceDomainCol.setCellValueFactory(new PropertyValueFactory<>("sourceDomain"));
        sourceDomainCol.setPrefWidth(140);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> regionCol = new TableColumn<>("AWS Region");
        regionCol.setCellValueFactory(new PropertyValueFactory<>("awsRegion"));
        regionCol.setPrefWidth(100);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> providerCol = new TableColumn<>("Provider");
        providerCol.setCellValueFactory(new PropertyValueFactory<>("cloudProvider"));
        providerCol.setPrefWidth(80);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> statusCol2 = new TableColumn<>("Status");
        statusCol2.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol2.setPrefWidth(80);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> responseTimeCol2 = new TableColumn<>("Response Time");
        responseTimeCol2.setCellValueFactory(cellData -> {
            long time = cellData.getValue().getResponseTime();
            return new SimpleStringProperty(time > 0 ? time + "ms" : "Unknown");
        });
        responseTimeCol2.setPrefWidth(90);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> suggestedRoleCol = new TableColumn<>("Suggested Role");
        suggestedRoleCol.setCellValueFactory(new PropertyValueFactory<>("suggestedRole"));
        suggestedRoleCol.setPrefWidth(140);
        
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> designationCol = new TableColumn<>("Designation");
        designationCol.setCellValueFactory(new PropertyValueFactory<>("designation"));
        designationCol.setPrefWidth(90);
        
        // Add click-to-select functionality for primary/secondary designation
        TableColumn<Route53RoutingPolicyTestingService.DiscoveredARecord, String> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(column -> {
            return new TableCell<Route53RoutingPolicyTestingService.DiscoveredARecord, String>() {
                private final Button primaryBtn = new Button("Set Primary");
                private final Button secondaryBtn = new Button("Set Secondary");
                private final Button clearBtn = new Button("Clear");
                private final javafx.scene.layout.HBox buttonsBox = new javafx.scene.layout.HBox(5);
                
                {
                    primaryBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
                    secondaryBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
                    clearBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
                    
                    primaryBtn.setOnAction(e -> {
                        Route53RoutingPolicyTestingService.DiscoveredARecord record = getTableView().getItems().get(getIndex());
                        setRecordAsPrimary(record);
                    });
                    
                    secondaryBtn.setOnAction(e -> {
                        Route53RoutingPolicyTestingService.DiscoveredARecord record = getTableView().getItems().get(getIndex());
                        setRecordAsSecondary(record);
                    });
                    
                    clearBtn.setOnAction(e -> {
                        Route53RoutingPolicyTestingService.DiscoveredARecord record = getTableView().getItems().get(getIndex());
                        clearRecordDesignation(record);
                    });
                    
                    buttonsBox.getChildren().addAll(primaryBtn, secondaryBtn, clearBtn);
                }
                
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(buttonsBox);
                    }
                }
            };
        });
        actionCol.setPrefWidth(200);
        
        discoveredRecordsTable.getColumns().addAll(ipCol, sourceDomainCol, regionCol, providerCol, statusCol2, responseTimeCol2, suggestedRoleCol, designationCol, actionCol);
        discoveredRecordsTable.setItems(discoveredARecords);
    }
    
    private void updateUIForRoutingPolicy(RoutingPolicyType policyType) {
        // Show/hide weighted routing configuration
        boolean isWeighted = policyType == RoutingPolicyType.WEIGHTED;
        weightedConfigPanel.setVisible(isWeighted);
        weightedConfigPanel.setManaged(isWeighted);
        
        // Update default iterations based on policy type
        if (isWeighted) {
            iterationsField.setText("10000"); // High volume for weighted testing
        } else if (policyType == RoutingPolicyType.FAILOVER) {
            iterationsField.setText("1"); // Single DNS lookup for failover
        } else {
            iterationsField.setText("1000");
        }
        
        // Update UI hints based on policy type
        String hint = "";
        switch (policyType) {
            case WEIGHTED:
                hint = "Configure weighted routing test - add endpoints with weights";
                break;
            case FAILOVER:
                hint = "Configure failover routing test - Click 'Discover A Records' to automatically find records, then designate primary/secondary";
                break;
            case GEOLOCATION:
                hint = "Configure geolocation routing test - specify expected region";
                break;
            case LATENCY:
                hint = "Configure latency-based routing test - add regional endpoints";
                break;
            default:
                hint = "Configure " + policyType.toString().toLowerCase().replace('_', ' ') + " routing test";
                break;
        }
        
        updateStatusLabel(hint);
    }
    
    private void checkTorStatus() {
        torService.isTorAvailable();
        // This is a simple check - in real implementation, you'd want to check asynchronously
        Platform.runLater(() -> {
            if (torService.isTorAvailable()) {
                torStatusLabel.setText("Tor Status: Available and running");
                torStatusLabel.setStyle("-fx-text-fill: green;");
                startTorButton.setDisable(true);
                stopTorButton.setDisable(false);
                useTorCheckBox.setSelected(true);
            } else {
                torStatusLabel.setText("Tor Status: Not available");
                torStatusLabel.setStyle("-fx-text-fill: red;");
                startTorButton.setDisable(false);
                stopTorButton.setDisable(true);
                useTorCheckBox.setSelected(false);
            }
        });
    }
    
    // Event Handlers
    

    
    @FXML
    private void handleStartTor() {
        Task<Boolean> startTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return torService.startTorDaemon();
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    Boolean success = getValue();
                    if (success) {
                        torStatusLabel.setText("Tor Status: Running ✅");
                        torStatusLabel.setStyle("-fx-text-fill: green;");
                        startTorButton.setDisable(true);
                        stopTorButton.setDisable(false);
                        useTorCheckBox.setSelected(true);
                        logMessage("Tor daemon started successfully", "INFO");
                    } else {
                        torStatusLabel.setText("Tor Status: Start Failed ❌");
                        torStatusLabel.setStyle("-fx-text-fill: red;");
                        logMessage("Failed to start Tor daemon", "ERROR");
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    torStatusLabel.setText("Tor Status: Start Error ❌");
                    torStatusLabel.setStyle("-fx-text-fill: red;");
                    logMessage("Error starting Tor: " + getException().getMessage(), "ERROR");
                });
            }
        };
        
        executorService.submit(startTask);
    }
    
    @FXML
    private void handleStopTor() {
        Task<Boolean> stopTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return torService.stopTorDaemon();
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    Boolean success = getValue();
                    if (success) {
                        torStatusLabel.setText("Tor Status: Stopped ❌");
                        torStatusLabel.setStyle("-fx-text-fill: red;");
                        startTorButton.setDisable(false);
                        stopTorButton.setDisable(true);
                        useTorCheckBox.setSelected(false);
                        logMessage("Tor daemon stopped successfully", "INFO");
                    } else {
                        torStatusLabel.setText("Tor Status: Stop Failed ⚠️");
                        torStatusLabel.setStyle("-fx-text-fill: orange;");
                        logMessage("Failed to stop Tor daemon", "WARNING");
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    torStatusLabel.setText("Tor Status: Stop Error ❌");
                    torStatusLabel.setStyle("-fx-text-fill: red;");
                    logMessage("Error stopping Tor: " + getException().getMessage(), "ERROR");
                });
            }
        };
        
        executorService.submit(stopTask);
    }
    
    @FXML
    private void handleCheckTor() {
        Task<Boolean> torCheckTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return torService.isTorAvailable();
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    boolean available = getValue();
                    if (available) {
                        torStatusLabel.setText("Tor Status: Available and running");
                        torStatusLabel.setStyle("-fx-text-fill: green;");
                        startTorButton.setDisable(true);
                        stopTorButton.setDisable(false);
                        logMessage("Tor check successful - service available", "INFO");
                    } else {
                        torStatusLabel.setText("Tor Status: Not available");
                        torStatusLabel.setStyle("-fx-text-fill: red;");
                        startTorButton.setDisable(false);
                        stopTorButton.setDisable(true);
                        logMessage("Tor check failed - service not available", "WARNING");
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    torStatusLabel.setText("Tor Status: Check failed");
                    torStatusLabel.setStyle("-fx-text-fill: red;");
                    startTorButton.setDisable(false);
                    stopTorButton.setDisable(true);
                    logMessage("Tor check failed: " + getException().getMessage(), "ERROR");
                });
            }
        };
        
        executorService.submit(torCheckTask);
    }
    
    @FXML
    private void handleStartTest() {
        if (currentTestTask != null && !currentTestTask.isDone()) {
            logMessage("Test already in progress", "WARNING");
            return;
        }
        
        // Validate inputs
        String domain = domainField.getText().trim();
        if (domain.isEmpty()) {
            logMessage("Domain field cannot be empty", "ERROR");
            return;
        }
        
        int iterations;
        try {
            iterations = Integer.parseInt(iterationsField.getText().trim());
            if (iterations <= 0) {
                throw new NumberFormatException("Iterations must be positive");
            }
        } catch (NumberFormatException e) {
            logMessage("Invalid iterations value: " + e.getMessage(), "ERROR");
            return;
        }
        
        // Clear previous results
        clearResults();
        
        // Setup UI for test
        startTestButton.setDisable(true);
        stopTestButton.setDisable(false);
        testProgressBar.setVisible(true);
        testProgressBar.setProgress(0);
        
        // Create and start test task
        currentTestTask = createTestTask(domain, iterations);
        executorService.submit(currentTestTask);
        
        logMessage("Started " + routingPolicyCombo.getValue() + " routing test for " + domain + " with " + iterations + " iterations", "INFO");
    }
    
    @FXML
    private void handleStopTest() {
        if (currentTestTask != null && !currentTestTask.isDone()) {
            currentTestTask.cancel(true);
            updateStatusLabel("Test stopped by user");
            logMessage("Test stopped by user", "INFO");
        }
        resetUIAfterTest();
    }
    
    @FXML
    private void handleClearResults() {
        clearResults();
        logMessage("Results cleared", "INFO");
    }
    
    @FXML
    private void handleExportResults() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Test Results");
        fileChooser.setInitialFileName("route53_test_results_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        
        File file = fileChooser.showSaveDialog(startTestButton.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(generateExportData());
                logMessage("Results exported to: " + file.getAbsolutePath(), "INFO");
            } catch (IOException e) {
                logMessage("Failed to export results: " + e.getMessage(), "ERROR");
            }
        }
    }
    
    @FXML
    private void handleRefreshChart() {
        generateDistributionChart();
    }
    
    @FXML
    private void handleSaveRawResults() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Raw Results");
        fileChooser.setInitialFileName("route53_raw_results_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        
        File file = fileChooser.showSaveDialog(startTestButton.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(rawResultsArea.getText());
                logMessage("Raw results saved to: " + file.getAbsolutePath(), "INFO");
            } catch (IOException e) {
                logMessage("Failed to save raw results: " + e.getMessage(), "ERROR");
            }
        }
    }
    
    @FXML
    private void handleClearLog() {
        testLogArea.clear();
    }
    
    // A Record Discovery Event Handlers
    
    @FXML
    private void handleDiscoverARecords() {
        String domain = domainField.getText().trim();
        if (domain.isEmpty()) {
            logMessage("Domain field cannot be empty for A record discovery", "ERROR");
            return;
        }
        
        discoverARecordsButton.setDisable(true);
        discoveredRecordsStatusLabel.setText("Discovering A records for " + domain + "...");
        
        Task<List<Route53RoutingPolicyTestingService.DiscoveredARecord>> discoverTask = new Task<List<Route53RoutingPolicyTestingService.DiscoveredARecord>>() {
            @Override
            protected List<Route53RoutingPolicyTestingService.DiscoveredARecord> call() throws Exception {
                return routingPolicyService.discoverARecordsForDomain(domain).get();
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<Route53RoutingPolicyTestingService.DiscoveredARecord> records = getValue();
                    discoveredARecords.clear();
                    discoveredARecords.addAll(records);
                    
                    // Analyze discovered records
                    long mainDomainRecords = records.stream().filter(r -> r.getSourceDomain().equals(domain)).count();
                    long backupDomainRecords = records.stream().filter(r -> !r.getSourceDomain().equals(domain)).count();
                    Set<String> domainsFound = records.stream().map(r -> r.getSourceDomain()).collect(Collectors.toSet());
                    
                    String status = String.format("Discovered %d A records across %d domains (%d main, %d backup subdomains)", 
                                                 records.size(), domainsFound.size(), mainDomainRecords, backupDomainRecords);
                    discoveredRecordsStatusLabel.setText(status);
                    logMessage(status, "INFO");
                    
                    // Log details of found domains
                    if (domainsFound.size() > 1) {
                        logMessage("Domains checked: " + String.join(", ", domainsFound), "INFO");
                    }
                    
                    // Enhanced auto-designation using suggested roles
                    List<Route53RoutingPolicyTestingService.DiscoveredARecord> primaryCandidates = 
                        records.stream().filter(r -> r.getSuggestedRole() != null && r.getSuggestedRole().contains("Primary")).collect(Collectors.toList());
                    List<Route53RoutingPolicyTestingService.DiscoveredARecord> secondaryCandidates = 
                        records.stream().filter(r -> r.getSuggestedRole() != null && r.getSuggestedRole().contains("Secondary")).collect(Collectors.toList());
                    
                    if (records.size() == 1) {
                        records.get(0).setPrimary(true);
                        discoveredRecordsTable.refresh();
                        logMessage("Automatically designated single A record as primary", "INFO");
                    } else if (primaryCandidates.size() == 1 && secondaryCandidates.size() == 1) {
                        // Perfect match: one primary candidate, one secondary candidate
                        primaryCandidates.get(0).setPrimary(true);
                        secondaryCandidates.get(0).setSecondary(true);
                        discoveredRecordsTable.refresh();
                        logMessage("Auto-designated records based on domain patterns: " + 
                                  primaryCandidates.get(0).getSourceDomain() + " (primary), " +
                                  secondaryCandidates.get(0).getSourceDomain() + " (secondary)", "INFO");
                    } else if (primaryCandidates.size() >= 1 && records.size() >= 2) {
                        // Designate first primary candidate as primary, first reachable non-primary as secondary
                        primaryCandidates.get(0).setPrimary(true);
                        
                        Route53RoutingPolicyTestingService.DiscoveredARecord secondaryChoice = records.stream()
                            .filter(r -> !r.equals(primaryCandidates.get(0)) && r.isReachable())
                            .findFirst().orElse(null);
                        
                        if (secondaryChoice != null) {
                            secondaryChoice.setSecondary(true);
                            logMessage("Auto-designated: " + primaryCandidates.get(0).getSourceDomain() + 
                                      " (primary), " + secondaryChoice.getSourceDomain() + " (secondary)", "INFO");
                        }
                        
                        discoveredRecordsTable.refresh();
                    } else if (records.size() >= 2) {
                        // Fallback: designate first two reachable records
                        List<Route53RoutingPolicyTestingService.DiscoveredARecord> reachableRecords = 
                            records.stream().filter(r -> r.isReachable()).limit(2).collect(Collectors.toList());
                        
                        if (reachableRecords.size() >= 2) {
                            reachableRecords.get(0).setPrimary(true);
                            reachableRecords.get(1).setSecondary(true);
                            discoveredRecordsTable.refresh();
                            logMessage("Auto-designated first two reachable records as primary/secondary", "INFO");
                        }
                    }
                    
                    // Show suggestion if backup domains were found
                    if (backupDomainRecords > 0) {
                        logMessage("Found " + backupDomainRecords + " records in backup subdomains - check 'Suggested Role' column for recommendations", "INFO");
                    }
                    
                    discoverARecordsButton.setDisable(false);
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    String error = "Failed to discover A records: " + getException().getMessage();
                    discoveredRecordsStatusLabel.setText(error);
                    logMessage(error, "ERROR");
                    discoverARecordsButton.setDisable(false);
                });
            }
        };
        
        executorService.submit(discoverTask);
        logMessage("Starting A record discovery for " + domain, "INFO");
    }
    
    @FXML
    private void handleClearDiscoveredRecords() {
        discoveredARecords.clear();
        discoveredRecordsStatusLabel.setText("No A records discovered");
        logMessage("Cleared discovered A records", "INFO");
    }
    
    // A Record Designation Methods
    
    private void setRecordAsPrimary(Route53RoutingPolicyTestingService.DiscoveredARecord record) {
        // Clear any existing primary designation
        for (Route53RoutingPolicyTestingService.DiscoveredARecord r : discoveredARecords) {
            r.setPrimary(false);
        }
        
        // Set this record as primary
        record.setPrimary(true);
        record.setSecondary(false);
        
        discoveredRecordsTable.refresh();
        logMessage("Set " + record.getIpAddress() + " as primary A record", "INFO");
    }
    
    private void setRecordAsSecondary(Route53RoutingPolicyTestingService.DiscoveredARecord record) {
        // Clear any existing secondary designation
        for (Route53RoutingPolicyTestingService.DiscoveredARecord r : discoveredARecords) {
            r.setSecondary(false);
        }
        
        // Set this record as secondary
        record.setSecondary(true);
        record.setPrimary(false);
        
        discoveredRecordsTable.refresh();
        logMessage("Set " + record.getIpAddress() + " as secondary A record", "INFO");
    }
    
    private void clearRecordDesignation(Route53RoutingPolicyTestingService.DiscoveredARecord record) {
        record.setPrimary(false);
        record.setSecondary(false);
        
        discoveredRecordsTable.refresh();
        logMessage("Cleared designation for " + record.getIpAddress(), "INFO");
    }
    
    // Test execution methods
    
    private Task<Void> createTestTask(String domain, int iterations) {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                RoutingPolicyType policyType = routingPolicyCombo.getValue();
                
                switch (policyType) {
                    case WEIGHTED:
                        performWeightedRoutingTest(domain, iterations);
                        break;
                    case GEOLOCATION:
                        performGeolocationRoutingTest(domain, iterations);
                        break;
                    case LATENCY:
                        performLatencyBasedRoutingTest(domain, iterations);
                        break;
                    case FAILOVER:
                        performFailoverRoutingTest(domain, iterations);
                        break;
                    default:
                        performSimpleRoutingTest(domain, iterations);
                        break;
                }
                
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    updateSummaryLabels();
                    generateDistributionChart();
                    updateDistributionStats();
                    resetUIAfterTest();
                    updateStatusLabel("Test completed successfully");
                    logMessage("Test completed successfully", "INFO");
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    resetUIAfterTest();
                    updateStatusLabel("Test failed: " + getException().getMessage());
                    logMessage("Test failed: " + getException().getMessage(), "ERROR");
                });
            }
            
            @Override
            protected void cancelled() {
                Platform.runLater(() -> {
                    resetUIAfterTest();
                    updateStatusLabel("Test cancelled");
                    logMessage("Test cancelled", "INFO");
                });
            }
        };
    }
    
    private void performWeightedRoutingTest(String domain, int iterations) throws Exception {
        Map<String, Integer> expectedWeights = new HashMap<>();
        for (WeightedEndpoint we : weightedEndpoints) {
            expectedWeights.put(we.getEndpoint(), we.getWeight());
        }
        
        // Use high-volume testing for iterations > 5000
        if (iterations > 5000) {
            performHighVolumeWeightedTest(domain, expectedWeights, iterations);
        } else {
            performStandardWeightedTest(domain, expectedWeights, iterations);
        }
    }
    
    private void performHighVolumeWeightedTest(String domain, Map<String, Integer> expectedWeights, int iterations) throws Exception {
        String dnsServer = dnsServerCombo.getValue();
        
        CompletableFuture<Route53RoutingPolicyTest> result = 
            routingPolicyService.testHighVolumeWeightedRouting(domain, expectedWeights, iterations, dnsServer);
        
        Route53RoutingPolicyTest test = result.get();
        
        // Process results from high-volume test
        Map<String, Integer> actualDistribution = test.getActualDistribution();
        
        Platform.runLater(() -> {
            // Update raw results with detailed information
            rawResultsArea.appendText(test.getErrorMessage() + "\n");
            
            // Add summary to results table
            testResults.add(new TestResultRow(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                "High-Volume",
                "Multiple Endpoints",
                test.getResponseTimeMs() + "ms",
                test.getResult().toString(),
                "Local (via dig)"
            ));
            
            // Update endpoint counts for distribution analysis
            endpointCounts.putAll(actualDistribution);
            
            // Update counters
            totalQueries.set(iterations);
            successfulQueries.set(actualDistribution.values().stream().mapToInt(Integer::intValue).sum());
            
            // Update progress
            testProgressBar.setProgress(1.0);
            totalQueriesLabel.setText(String.valueOf(iterations));
        });
        
        logMessage("High-volume weighted routing test completed with " + iterations + " queries", "INFO");
    }
    
    private void performStandardWeightedTest(String domain, Map<String, Integer> expectedWeights, int iterations) throws Exception {
        for (int i = 0; i < iterations && !Thread.currentThread().isInterrupted(); i++) {
            try {
                long startTime = System.currentTimeMillis();
                
                // Perform DNS lookup
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                
                long responseTime = System.currentTimeMillis() - startTime;
                responseTimes.add(responseTime);
                
                if (addresses.length > 0) {
                    String resolvedIP = addresses[0].getHostAddress();
                    String endpoint = mapIPToEndpoint(resolvedIP);
                    
                    endpointCounts.put(endpoint, endpointCounts.getOrDefault(endpoint, 0) + 1);
                    
                    // Add to results
                    final int queryNumber = i + 1;
                    Platform.runLater(() -> {
                        testResults.add(new TestResultRow(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                            String.valueOf(queryNumber),
                            endpoint,
                            responseTime + "ms",
                            "SUCCESS",
                            "Local"
                        ));
                        
                        // Update raw results
                        rawResultsArea.appendText(String.format("Query %d: %s -> %s (%dms)\n", 
                            queryNumber, domain, endpoint, responseTime));
                    });
                    
                    successfulQueries.incrementAndGet();
                }
                
                totalQueries.incrementAndGet();
                
                // Update progress
                final int currentIteration = i + 1;
                Platform.runLater(() -> {
                    testProgressBar.setProgress((double) currentIteration / iterations);
                    totalQueriesLabel.setText(String.valueOf(currentIteration));
                });
                
                // Small delay to avoid overwhelming the DNS server
                Thread.sleep(10);
                
            } catch (Exception e) {
                totalQueries.incrementAndGet();
                final int queryNumber3 = i + 1;
                Platform.runLater(() -> {
                    testResults.add(new TestResultRow(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        String.valueOf(queryNumber3),
                        "ERROR",
                        "0ms",
                        "FAILED",
                        "Local"
                    ));
                    rawResultsArea.appendText(String.format("Query %d: FAILED - %s\n", queryNumber3, e.getMessage()));
                });
                
                logger.log(Level.WARNING, "DNS query failed", e);
            }
        }
    }
    
    private void performGeolocationRoutingTest(String domain, int iterations) throws Exception {
        if (useTorCheckBox.isSelected() && torService.isTorAvailable()) {
            int locations = Integer.parseInt(torLocationsField.getText().trim());
            
            CompletableFuture<Route53RoutingPolicyTest> result = 
                routingPolicyService.testGeolocationRoutingWithTor(domain, locations);
            
            Route53RoutingPolicyTest test = result.get();
            
            Platform.runLater(() -> {
                testResults.add(new TestResultRow(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    "1",
                    test.getActualEndpoint(),
                    test.getResponseTimeMs() + "ms",
                    test.getResult().toString(),
                    "Multiple (via Tor)"
                ));
                
                rawResultsArea.appendText("Geolocation Test Results:\n");
                rawResultsArea.appendText(test.getErrorMessage() + "\n");
            });
            
            successfulQueries.incrementAndGet();
            totalQueries.incrementAndGet();
        } else {
            // Regular geolocation test without Tor
            performSimpleRoutingTest(domain, iterations);
        }
    }
    
    private void performLatencyBasedRoutingTest(String domain, int iterations) throws Exception {
        Map<String, String> regionEndpoints = new HashMap<>();
        for (Route53RoutingPolicyTestingService.DiscoveredARecord record : discoveredARecords) {
            regionEndpoints.put(record.getAwsRegion(), record.getIpAddress());
        }
        
        CompletableFuture<Route53RoutingPolicyTest> result = 
            routingPolicyService.testLatencyBasedRouting(domain, regionEndpoints);
        
        Route53RoutingPolicyTest test = result.get();
        
        Platform.runLater(() -> {
            testResults.add(new TestResultRow(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                "1",
                test.getActualEndpoint(),
                test.getResponseTimeMs() + "ms",
                test.getResult().toString(),
                "Local"
            ));
            
            rawResultsArea.appendText("Latency-based Test Results:\n");
            rawResultsArea.appendText("Expected lowest latency: " + test.getExpectedLowestLatencyRegion() + "\n");
            rawResultsArea.appendText("Actual endpoint: " + test.getActualLowestLatencyRegion() + "\n");
        });
        
        successfulQueries.incrementAndGet();
        totalQueries.incrementAndGet();
    }
    
    private void performFailoverRoutingTest(String domain, int iterations) throws Exception {
        // Find designated primary and secondary records from discovered A records
        final Route53RoutingPolicyTestingService.DiscoveredARecord primaryRecord = 
            discoveredARecords.stream().filter(r -> r.isPrimary()).findFirst().orElse(null);
        final Route53RoutingPolicyTestingService.DiscoveredARecord secondaryRecord = 
            discoveredARecords.stream().filter(r -> r.isSecondary()).findFirst().orElse(null);
        
        // Get IP addresses from discovered A records
        final String primary = primaryRecord != null ? primaryRecord.getIpAddress() : null;
        final String secondary = secondaryRecord != null ? secondaryRecord.getIpAddress() : null;
        
        CompletableFuture<Route53RoutingPolicyTest> result = 
            routingPolicyService.testFailoverRouting(domain, primary, secondary);
        
        Route53RoutingPolicyTest test = result.get();
        
        Platform.runLater(() -> {
            testResults.add(new TestResultRow(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                "1",
                test.getActualEndpoint(),
                test.getResponseTimeMs() + "ms",
                test.getResult().toString(),
                "Local"
            ));
            
            // Display enhanced failover test results
            rawResultsArea.appendText("=== Enhanced Failover Routing Test Results ===\n");
            rawResultsArea.appendText("Domain: " + domain + "\n");
            
            if (primaryRecord != null) {
                rawResultsArea.appendText("Primary A Record (used when healthy): " + primary + "\n");
                rawResultsArea.appendText("  └─ Region: " + primaryRecord.getAwsRegion() + 
                                        ", Provider: " + primaryRecord.getCloudProvider() + 
                                        ", Status: " + primaryRecord.getStatus() + "\n");
            } else if (primary != null) {
                rawResultsArea.appendText("Primary A Record (used when healthy): " + primary + " (manually configured)\n");
            }
            
            if (secondaryRecord != null) {
                rawResultsArea.appendText("Secondary A Record (used when unhealthy): " + secondary + "\n");
                rawResultsArea.appendText("  └─ Region: " + secondaryRecord.getAwsRegion() + 
                                        ", Provider: " + secondaryRecord.getCloudProvider() + 
                                        ", Status: " + secondaryRecord.getStatus() + "\n");
            } else if (secondary != null) {
                rawResultsArea.appendText("Secondary A Record (used when unhealthy): " + secondary + " (manually configured)\n");
            }
            
            rawResultsArea.appendText("\n--- Detailed DNS Analysis ---\n");
            rawResultsArea.appendText(test.getErrorMessage() + "\n");
            
            // Add enhanced summary information
            rawResultsArea.appendText("--- Enhanced Summary ---\n");
            rawResultsArea.appendText("Test Result: " + test.getResult() + "\n");
            rawResultsArea.appendText("Response Time: " + test.getResponseTimeMs() + "ms\n");
            rawResultsArea.appendText("Actual Endpoint: " + test.getActualEndpoint() + "\n");
            rawResultsArea.appendText("Failover Triggered: " + test.isFailoverTriggered() + "\n");
            
            if (discoveredARecords.size() > 0) {
                rawResultsArea.appendText("Total Discovered A Records: " + discoveredARecords.size() + "\n");
                rawResultsArea.appendText("Records Used for Test: " + 
                                        (primary != null ? "Primary" : "None") + 
                                        (secondary != null ? ", Secondary" : "") + "\n");
            }
            
            rawResultsArea.appendText("========================\n\n");
        });
        
        successfulQueries.incrementAndGet();
        totalQueries.incrementAndGet();
    }
    
    private void performSimpleRoutingTest(String domain, int iterations) throws Exception {
        for (int i = 0; i < iterations && !Thread.currentThread().isInterrupted(); i++) {
            try {
                long startTime = System.currentTimeMillis();
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                long responseTime = System.currentTimeMillis() - startTime;
                
                responseTimes.add(responseTime);
                
                if (addresses.length > 0) {
                    String resolvedIP = addresses[0].getHostAddress();
                    String endpoint = mapIPToEndpoint(resolvedIP);
                    
                    endpointCounts.put(endpoint, endpointCounts.getOrDefault(endpoint, 0) + 1);
                    
                    final int queryNumber2 = i + 1;
                    Platform.runLater(() -> {
                        testResults.add(new TestResultRow(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                            String.valueOf(queryNumber2),
                            endpoint,
                            responseTime + "ms",
                            "SUCCESS",
                            "Local"
                        ));
                        
                        rawResultsArea.appendText(String.format("Query %d: %s -> %s (%dms)\n", 
                            queryNumber2, domain, endpoint, responseTime));
                    });
                    
                    successfulQueries.incrementAndGet();
                }
                
                totalQueries.incrementAndGet();
                
                final int currentIteration = i + 1;
                Platform.runLater(() -> {
                    testProgressBar.setProgress((double) currentIteration / iterations);
                    totalQueriesLabel.setText(String.valueOf(currentIteration));
                });
                
                Thread.sleep(10);
                
            } catch (Exception e) {
                totalQueries.incrementAndGet();
                final int queryNumber4 = i + 1;
                Platform.runLater(() -> {
                    testResults.add(new TestResultRow(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        String.valueOf(queryNumber4),
                        "ERROR",
                        "0ms",
                        "FAILED",
                        "Local"
                    ));
                    rawResultsArea.appendText(String.format("Query %d: FAILED - %s\n", queryNumber4, e.getMessage()));
                });
            }
        }
    }
    
    // Helper methods
    
    private String mapIPToEndpoint(String ip) {
        // Map IP to endpoint name using discovered A records
        for (Route53RoutingPolicyTestingService.DiscoveredARecord record : discoveredARecords) {
            if (record.getIpAddress().equals(ip)) {
                return record.getEndpointName() != null ? record.getEndpointName() : ip;
            }
        }
        return ip;
    }
    
    private void clearResults() {
        testResults.clear();
        distributionStats.clear();
        endpointCounts.clear();
        responseTimes.clear();
        totalQueries.set(0);
        successfulQueries.set(0);
        rawResultsArea.clear();
        
        // Clear summary labels
        testResultLabel.setText("Not Started");
        totalQueriesLabel.setText("0");
        avgResponseTimeLabel.setText("0ms");
        uniqueEndpointsLabel.setText("0");
        successRateLabel.setText("0%");
        policyComplianceLabel.setText("Unknown");
    }
    
    private void updateSummaryLabels() {
        // Calculate statistics
        int total = totalQueries.get();
        int successful = successfulQueries.get();
        double successRate = total > 0 ? (successful * 100.0) / total : 0;
        
        double avgResponseTime = responseTimes.isEmpty() ? 0 : 
            responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        
        int uniqueEndpoints = endpointCounts.size();
        
        // Determine overall test result
        TestResult result = TestResult.UNKNOWN;
        if (successRate > 95) {
            result = TestResult.PASSED;
        } else if (successRate > 80) {
            result = TestResult.PARTIAL;
        } else {
            result = TestResult.FAILED;
        }
        
        // Update labels
        testResultLabel.setText(result.toString());
        totalQueriesLabel.setText(String.valueOf(total));
        avgResponseTimeLabel.setText(String.format("%.0fms", avgResponseTime));
        uniqueEndpointsLabel.setText(String.valueOf(uniqueEndpoints));
        successRateLabel.setText(String.format("%.1f%%", successRate));
        
        // Determine policy compliance
        String compliance = determinePolicyCompliance();
        policyComplianceLabel.setText(compliance);
    }
    
    private String determinePolicyCompliance() {
        if (routingPolicyCombo.getValue() == RoutingPolicyType.WEIGHTED && !weightedEndpoints.isEmpty()) {
            // Check if distribution matches expected weights
            int totalWeight = weightedEndpoints.stream().mapToInt(WeightedEndpoint::getWeight).sum();
            int totalQueries = this.totalQueries.get();
            
            if (totalQueries > 0) {
                double maxDeviation = 0;
                for (WeightedEndpoint we : weightedEndpoints) {
                    double expectedPercentage = (we.getWeight() * 100.0) / totalWeight;
                    int actualCount = endpointCounts.getOrDefault(we.getEndpoint(), 0);
                    double actualPercentage = (actualCount * 100.0) / totalQueries;
                    double deviation = Math.abs(expectedPercentage - actualPercentage);
                    maxDeviation = Math.max(maxDeviation, deviation);
                }
                
                if (maxDeviation < 5) {
                    return "COMPLIANT";
                } else if (maxDeviation < 15) {
                    return "PARTIALLY COMPLIANT";
                } else {
                    return "NON-COMPLIANT";
                }
            }
        }
        return "UNKNOWN";
    }
    
    private void updateDistributionStats() {
        distributionStats.clear();
        
        int total = totalQueries.get();
        if (total > 0) {
            for (Map.Entry<String, Integer> entry : endpointCounts.entrySet()) {
                String endpoint = entry.getKey();
                int count = entry.getValue();
                double percentage = (count * 100.0) / total;
                
                // Find expected percentage
                String expectedPercentage = "N/A";
                String deviation = "N/A";
                
                if (routingPolicyCombo.getValue() == RoutingPolicyType.WEIGHTED) {
                    for (WeightedEndpoint we : weightedEndpoints) {
                        if (we.getEndpoint().equals(endpoint)) {
                            int totalWeight = weightedEndpoints.stream().mapToInt(WeightedEndpoint::getWeight).sum();
                            double expected = (we.getWeight() * 100.0) / totalWeight;
                            expectedPercentage = String.format("%.1f%%", expected);
                            deviation = String.format("%.1f%%", Math.abs(percentage - expected));
                            break;
                        }
                    }
                }
                
                distributionStats.add(new EndpointDistribution(
                    endpoint,
                    count,
                    String.format("%.1f%%", percentage),
                    expectedPercentage,
                    deviation
                ));
            }
        }
    }
    
    private void generateDistributionChart() {
        if (distributionChartView != null && !endpointCounts.isEmpty()) {
            WebEngine engine = distributionChartView.getEngine();
            
            // Generate chart HTML with Chart.js
            StringBuilder html = new StringBuilder();
            html.append("<html><head>");
            html.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>");
            html.append("</head><body>");
            html.append("<canvas id=\"distributionChart\" width=\"400\" height=\"300\"></canvas>");
            html.append("<script>");
            
            // Prepare data
            html.append("const data = {");
            html.append("labels: [");
            for (String endpoint : endpointCounts.keySet()) {
                html.append("'").append(endpoint).append("',");
            }
            html.append("],");
            html.append("datasets: [{");
            html.append("data: [");
            for (Integer count : endpointCounts.values()) {
                html.append(count).append(",");
            }
            html.append("],");
            html.append("backgroundColor: ['#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF', '#FF9F40']");
            html.append("}]");
            html.append("};");
            
            // Create chart
            html.append("const ctx = document.getElementById('distributionChart').getContext('2d');");
            html.append("new Chart(ctx, {");
            html.append("type: '").append(chartTypeCombo.getValue().toLowerCase().replace(" chart", "")).append("',");
            html.append("data: data,");
            html.append("options: { responsive: true, maintainAspectRatio: false }");
            html.append("});");
            
            html.append("</script>");
            html.append("</body></html>");
            
            engine.loadContent(html.toString());
        }
    }
    
    private void resetUIAfterTest() {
        startTestButton.setDisable(false);
        stopTestButton.setDisable(true);
        testProgressBar.setVisible(false);
        testProgressBar.setProgress(0);
    }
    
    private void updateStatusLabel(String message) {
        testStatusLabel.setText(message);
    }
    
    private void logMessage(String message, String level) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logEntry = String.format("[%s] %s: %s\n", timestamp, level, message);
            testLogArea.appendText(logEntry);
            
            if (autoScrollLogCheckBox.isSelected()) {
                testLogArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }
    
    private String generateExportData() {
        StringBuilder sb = new StringBuilder();
        sb.append("Route53 DNS Testing Results\n");
        sb.append("==========================\n\n");
        sb.append("Test Configuration:\n");
        sb.append("Domain: ").append(domainField.getText()).append("\n");
        sb.append("Routing Policy: ").append(routingPolicyCombo.getValue()).append("\n");
        sb.append("Iterations: ").append(iterationsField.getText()).append("\n");
        sb.append("Timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        
        sb.append("Summary:\n");
        sb.append("Total Queries: ").append(totalQueriesLabel.getText()).append("\n");
        sb.append("Success Rate: ").append(successRateLabel.getText()).append("\n");
        sb.append("Average Response Time: ").append(avgResponseTimeLabel.getText()).append("\n");
        sb.append("Unique Endpoints: ").append(uniqueEndpointsLabel.getText()).append("\n");
        sb.append("Policy Compliance: ").append(policyComplianceLabel.getText()).append("\n\n");
        
        sb.append("Endpoint Distribution:\n");
        for (EndpointDistribution dist : distributionStats) {
            sb.append(String.format("  %s: %d queries (%.1s%%) - Expected: %s, Deviation: %s\n",
                dist.getEndpoint(), dist.getCount(), dist.getPercentage(), 
                dist.getExpectedPercentage(), dist.getDeviation()));
        }
        
        sb.append("\nRaw Results:\n");
        sb.append(rawResultsArea.getText());
        
        return sb.toString();
    }
    
    // Inner classes for table data
    
    public static class WeightedEndpoint {
        private String endpoint;
        private int weight;
        
        public WeightedEndpoint(String endpoint, int weight) {
            this.endpoint = endpoint;
            this.weight = weight;
        }
        
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
    }
    
    public static class TestResultRow {
        private String timestamp;
        private String queryNumber;
        private String resolvedEndpoint;
        private String responseTime;
        private String status;
        private String testLocation;
        
        public TestResultRow(String timestamp, String queryNumber, String resolvedEndpoint, 
                           String responseTime, String status, String testLocation) {
            this.timestamp = timestamp;
            this.queryNumber = queryNumber;
            this.resolvedEndpoint = resolvedEndpoint;
            this.responseTime = responseTime;
            this.status = status;
            this.testLocation = testLocation;
        }
        
        // Getters and setters
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getQueryNumber() { return queryNumber; }
        public void setQueryNumber(String queryNumber) { this.queryNumber = queryNumber; }
        public String getResolvedEndpoint() { return resolvedEndpoint; }
        public void setResolvedEndpoint(String resolvedEndpoint) { this.resolvedEndpoint = resolvedEndpoint; }
        public String getResponseTime() { return responseTime; }
        public void setResponseTime(String responseTime) { this.responseTime = responseTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTestLocation() { return testLocation; }
        public void setTestLocation(String testLocation) { this.testLocation = testLocation; }
    }
    
    public static class EndpointDistribution {
        private String endpoint;
        private int count;
        private String percentage;
        private String expectedPercentage;
        private String deviation;
        
        public EndpointDistribution(String endpoint, int count, String percentage, 
                                  String expectedPercentage, String deviation) {
            this.endpoint = endpoint;
            this.count = count;
            this.percentage = percentage;
            this.expectedPercentage = expectedPercentage;
            this.deviation = deviation;
        }
        
        // Getters and setters
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public String getPercentage() { return percentage; }
        public void setPercentage(String percentage) { this.percentage = percentage; }
        public String getExpectedPercentage() { return expectedPercentage; }
        public void setExpectedPercentage(String expectedPercentage) { this.expectedPercentage = expectedPercentage; }
        public String getDeviation() { return deviation; }
        public void setDeviation(String deviation) { this.deviation = deviation; }
    }
} 