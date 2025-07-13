package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.NetworkHost;
import edu.au.cpsc.module7.models.ScanConfiguration;
import edu.au.cpsc.module7.services.NetworkScannerService;
import edu.au.cpsc.module7.services.NetworkVisualizationService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import com.google.inject.Inject;
import netscape.javascript.JSObject;

/**
 * Controller for the Network Scanner interface - RESTORED working version
 */
public class NetworkScannerController {
    private static final Logger logger = Logger.getLogger(NetworkScannerController.class.getName());
    
    // Configuration Controls
    @FXML private TextField targetRangeField;
    @FXML private Button autoDetectButton;
    @FXML private ComboBox<ScanConfiguration.ScanType> scanTypeCombo;
    @FXML private ComboBox<ScanConfiguration.PortScanType> portScanTypeCombo;
    @FXML private TextField timeoutField;
    @FXML private TextField threadsField;
    @FXML private CheckBox resolveHostnamesCheck;
    @FXML private CheckBox detectServicesCheck;
    @FXML private CheckBox detectOSCheck;
    @FXML private CheckBox performTracerouteCheck;
    
    // Control Buttons
    @FXML private Button startScanButton;
    @FXML private Button stopScanButton;
    @FXML private ProgressBar scanProgressBar;
    @FXML private Label scanStatusLabel;
    
    // Results Summary
    @FXML private Label hostsFoundLabel;
    @FXML private Label onlineHostsLabel;
    @FXML private Label totalPortsLabel;
    
    // Results Table
    @FXML private TableView<NetworkHost> resultsTable;
    @FXML private TableColumn<NetworkHost, String> statusColumn;
    @FXML private TableColumn<NetworkHost, String> ipColumn;
    @FXML private TableColumn<NetworkHost, String> hostnameColumn;
    @FXML private TableColumn<NetworkHost, String> responseTimeColumn;
    @FXML private TableColumn<NetworkHost, String> openPortsColumn;
    @FXML private TableColumn<NetworkHost, String> servicesColumn;
    @FXML private TableColumn<NetworkHost, String> osColumn;
    
    // Network Map
    @FXML private Button refreshMapButton;
    @FXML private Button exportMapButton;
    @FXML private WebView networkMapWebView;
    @FXML private ChoiceBox<String> layoutChoiceBox;
    
    // Scan Log
    @FXML private TextArea logTextArea;
    @FXML private CheckBox autoScrollCheck;
    
    // Services
    private final NetworkScannerService scannerService;
    private final NetworkVisualizationService visualizationService;
    
    // Data
    private ObservableList<NetworkHost> scanResults;
    private Task<List<NetworkHost>> currentScanTask;

    @Inject
    public NetworkScannerController(NetworkScannerService scannerService, NetworkVisualizationService visualizationService) {
        this.scannerService = scannerService;
        this.visualizationService = visualizationService;
    }
    
    @FXML
    public void initialize() {
        logger.info("NetworkScannerController initializing...");
        
        scanResults = FXCollections.observableArrayList();
        
        setupUI();
        setupTableColumns();
        detectLocalNetwork();
        displayNetworkInfo();
        setupWebViewBridge();
        
        logger.info("NetworkScannerController initialized successfully");
    }
    
    private void displayNetworkInfo() {
        try {
            addLogEntry("=== NETWORK SCANNER READY ===");
            addLogEntry("Basic network scanner with working progress tracking");
            
            InetAddress localHost = InetAddress.getLocalHost();
            addLogEntry("Local Host: " + localHost.getHostAddress() + " (" + localHost.getHostName() + ")");
        } catch (Exception e) {
            addLogEntry("Error getting network information: " + e.getMessage());
        }
    }

    private void setupWebViewBridge() {
        networkMapWebView.getEngine().getLoadWorker().stateProperty().addListener(
            (obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    JSObject window = (JSObject) networkMapWebView.getEngine().executeScript("window");
                    window.setMember("javaApp", this);
                }
            }
        );
    }

    public void onHostClicked(String ipAddress) {
        Platform.runLater(() -> {
            for (NetworkHost host : resultsTable.getItems()) {
                if (host.getIpAddress().equals(ipAddress)) {
                    resultsTable.getSelectionModel().select(host);
                    resultsTable.scrollTo(host);
                    break;
                }
            }
        });
    }
    
    private void setupUI() {
        // Setup combo boxes
        scanTypeCombo.setItems(FXCollections.observableArrayList(ScanConfiguration.ScanType.values()));
        scanTypeCombo.setValue(ScanConfiguration.ScanType.PING_SWEEP);
        
        portScanTypeCombo.setItems(FXCollections.observableArrayList(ScanConfiguration.PortScanType.values()));
        portScanTypeCombo.setValue(ScanConfiguration.PortScanType.TCP_CONNECT);
        
        // Setup results table
        resultsTable.setItems(scanResults);
        
        // Setup initial state
        updateScanStatus("Ready for network scanning", false);
        updateResultsSummary();
        
        // Setup default values
        timeoutField.setText("3000");
        threadsField.setText("50");
        resolveHostnamesCheck.setSelected(true);
        detectServicesCheck.setSelected(true);
        detectOSCheck.setSelected(false);
        performTracerouteCheck.setSelected(false);

        // Setup layout choice box
        layoutChoiceBox.setItems(FXCollections.observableArrayList("Ring", "Force"));
        layoutChoiceBox.setValue("Ring");
        layoutChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateNetworkMap();
            }
        });
    }
    
    private void setupTableColumns() {
        statusColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().isAlive() ? "🟢" : "🔴"));
        
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        
        hostnameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getHostname() != null ? 
                cellData.getValue().getHostname() : ""));
        
        responseTimeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getResponseTime() > 0 ? 
                cellData.getValue().getResponseTime() + "ms" : ""));
        
        openPortsColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.valueOf(cellData.getValue().getOpenPorts().size())));
        
        servicesColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.join(", ", cellData.getValue().getServices())));
        
        osColumn.setCellValueFactory(new PropertyValueFactory<>("osGuess"));

        resultsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                highlightNodeInMap(newSelection.getIpAddress());
            } else {
                highlightNodeInMap(null); // Clear highlight when selection is cleared
            }
        });
    }
    
    private void highlightNodeInMap(String ipAddress) {
        if (networkMapWebView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
            String script = String.format("highlightNode('%s')", ipAddress == null ? "" : ipAddress);
            networkMapWebView.getEngine().executeScript(script);
        }
    }
    
    private void detectLocalNetwork() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String localIP = localHost.getHostAddress();
            
            if (localIP.startsWith("192.168.40.")) {
                targetRangeField.setText("192.168.40.0/24");
            } else if (localIP.startsWith("192.168.")) {
                String[] parts = localIP.split("\\.");
                String networkRange = parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
                targetRangeField.setText(networkRange);
            } else if (localIP.startsWith("10.")) {
                String[] parts = localIP.split("\\.");
                String networkRange = parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
                targetRangeField.setText(networkRange);
            } else {
                targetRangeField.setText("192.168.1.0/24");
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not detect local network", e);
            targetRangeField.setText("192.168.1.0/24");
        }
    }
    
    @FXML
    private void handleAutoDetect() {
        detectLocalNetwork();
        addLogEntry("Auto-detected local network range: " + targetRangeField.getText());
    }
    
    @FXML
    private void handleStartScan() {
        if (currentScanTask != null && !currentScanTask.isDone()) {
            return; // Scan already running
        }
        
        try {
            ScanConfiguration config = createScanConfiguration();
            startNetworkScan(config);
        } catch (Exception e) {
            addLogEntry("Error starting scan: " + e.getMessage());
            updateScanStatus("Error: " + e.getMessage(), false);
        }
    }
    
    @FXML
    private void handleStopScan() {
        if (currentScanTask != null && !currentScanTask.isDone()) {
            currentScanTask.cancel(true);
            scannerService.stopScan();
            updateScanStatus("Scan stopped by user", false);
            addLogEntry("Network scan stopped by user");
        }
    }
    
    private ScanConfiguration createScanConfiguration() {
        ScanConfiguration config = new ScanConfiguration();
        
        config.setTargetRange(targetRangeField.getText().trim());
        config.setScanType(scanTypeCombo.getValue());
        config.setPortScanType(portScanTypeCombo.getValue());
        
        try {
            config.setTimeout(Integer.parseInt(timeoutField.getText()));
        } catch (NumberFormatException e) {
            config.setTimeout(3000);
        }
        
        try {
            config.setThreads(Integer.parseInt(threadsField.getText()));
        } catch (NumberFormatException e) {
            config.setThreads(50);
        }
        
        config.setResolveHostnames(resolveHostnamesCheck.isSelected());
        config.setDetectServices(detectServicesCheck.isSelected());
        config.setDetectOS(detectOSCheck.isSelected());
        config.setPerformTraceroute(performTracerouteCheck.isSelected());
        
        return config;
    }
    
    private void startNetworkScan(ScanConfiguration config) {
        // Clear previous results
        scanResults.clear();
        updateResultsSummary();
        
        addLogEntry("=== STARTING NETWORK SCAN ===");
        addLogEntry("Target: " + config.getTargetRange());
        addLogEntry("Scan Type: " + config.getScanType().getDisplayName());
        addLogEntry("Port Scan: " + config.getPortScanType().getDisplayName());
        
        // Create scan task using working NetworkScannerService
        currentScanTask = scannerService.scanNetwork(config, this::addLogEntry);
        
        // Setup task event handlers with WORKING progress bar binding
        currentScanTask.setOnRunning(e -> {
            updateScanStatus("Performing network scan...", true);
            addLogEntry("Network scan started...");
        });
        
        currentScanTask.setOnSucceeded(e -> {
            List<NetworkHost> result = currentScanTask.getValue();
            Platform.runLater(() -> {
                scanResults.addAll(result);
                updateResultsSummary();
                
                updateScanStatus("Scan completed - " + result.size() + " hosts found", false);
                addLogEntry("=== SCAN COMPLETED ===");
                addLogEntry("Total hosts discovered: " + result.size());
                
                // Update network map
                updateNetworkMap();
            });
        });
        
        currentScanTask.setOnFailed(e -> {
            Throwable exception = currentScanTask.getException();
            Platform.runLater(() -> {
                updateScanStatus("Scan failed: " + exception.getMessage(), false);
                addLogEntry("Scan failed: " + exception.getMessage());
            });
        });
        
        currentScanTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                updateScanStatus("Scan cancelled", false);
                addLogEntry("Scan was cancelled");
            });
        });
        
        // CRITICAL: Bind progress bar to task progress - THIS WAS MISSING!
        scanProgressBar.progressProperty().bind(currentScanTask.progressProperty());
        
        // Update status from task messages
        currentScanTask.messageProperty().addListener((obs, oldMessage, newMessage) -> {
            if (newMessage != null && !newMessage.isEmpty()) {
                Platform.runLater(() -> {
                    scanStatusLabel.setText(newMessage);
                });
            }
        });
        
        // Start the task
        Thread scanThread = new Thread(currentScanTask);
        scanThread.setDaemon(true);
        scanThread.start();
    }
    
    private void updateScanStatus(String message, boolean scanning) {
        Platform.runLater(() -> {
            scanStatusLabel.setText(message);
            startScanButton.setDisable(scanning);
            stopScanButton.setDisable(!scanning);
            scanProgressBar.setVisible(scanning);
            
            // Unbind progress bar when scan is not running
            if (!scanning && currentScanTask != null) {
                scanProgressBar.progressProperty().unbind();
                scanProgressBar.setProgress(0);
            }
        });
    }
    
    private void updateResultsSummary() {
        int totalHosts = scanResults.size();
        int onlineHosts = (int) scanResults.stream().filter(NetworkHost::isAlive).count();
        int totalPorts = scanResults.stream().mapToInt(h -> h.getOpenPorts().size()).sum();
        
        Platform.runLater(() -> {
            hostsFoundLabel.setText("Hosts Found: " + totalHosts);
            onlineHostsLabel.setText("Online: " + onlineHosts);
            totalPortsLabel.setText("Total Open Ports: " + totalPorts);
        });
    }
    
    private void addLogEntry(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logEntry = "[" + timestamp + "] " + message + "\n";
            logTextArea.appendText(logEntry);
            
            if (autoScrollCheck.isSelected()) {
                logTextArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }
    
    @FXML
    private void handleRefreshMap() {
        updateNetworkMap();
    }
    
    private void updateNetworkMap() {
        if (scanResults.isEmpty()) {
            networkMapWebView.getEngine().loadContent(
                "<html><body><h2>No scan results to display</h2><p>Run a network scan to see the network map.</p></body></html>");
            return;
        }
        
        try {
            String selectedLayout = layoutChoiceBox.getValue();
            String mapHTML = visualizationService.generateNetworkMapHTML(scanResults, selectedLayout);
            networkMapWebView.getEngine().loadContent(mapHTML);
            addLogEntry("Network map updated with " + scanResults.size() + " hosts using " + selectedLayout + " layout");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating network map", e);
            addLogEntry("Error updating network map: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleExportMap() {
        if (scanResults.isEmpty()) {
            showAlert("No Data", "No scan results to export.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Network Map");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("HTML Files", "*.html"));
        
        File file = fileChooser.showSaveDialog(exportMapButton.getScene().getWindow());
        if (file != null) {
            try {
                String mapHTML = visualizationService.generateNetworkMapHTML(scanResults);
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(mapHTML);
                }
                addLogEntry("Network map exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error exporting network map", e);
                addLogEntry("Error exporting network map: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleExportResults() {
        if (scanResults.isEmpty()) {
            showAlert("No Data", "No scan results to export.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Scan Results");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        
        File file = fileChooser.showSaveDialog(startScanButton.getScene().getWindow());
        if (file != null) {
            try {
                exportResultsToFile(file);
                addLogEntry("Scan results exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error exporting results", e);
                addLogEntry("Error exporting results: " + e.getMessage());
            }
        }
    }
    
    private void exportResultsToFile(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        content.append("IP Address,Hostname,Response Time,Open Ports,Services,OS Guess\n");
        
        for (NetworkHost host : scanResults) {
            content.append(host.getIpAddress()).append(",")
                   .append(host.getHostname() != null ? host.getHostname() : "").append(",")
                   .append(host.getResponseTime()).append(",")
                   .append(host.getOpenPorts().size()).append(",")
                   .append(String.join(";", host.getServices())).append(",")
                   .append(host.getOsGuess() != null ? host.getOsGuess() : "").append("\n");
        }
        
        java.nio.file.Files.write(file.toPath(), content.toString().getBytes());
    }
    
    @FXML
    private void handleClearResults() {
        scanResults.clear();
        updateResultsSummary();
        addLogEntry("Results cleared");
    }
    
    @FXML
    private void handleClearLog() {
        logTextArea.clear();
    }
    
    @FXML
    private void handleSaveLog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Scan Log");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        
        File file = fileChooser.showSaveDialog(startScanButton.getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.write(file.toPath(), logTextArea.getText().getBytes());
                addLogEntry("Log saved to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving log", e);
                addLogEntry("Error saving log: " + e.getMessage());
            }
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
} 