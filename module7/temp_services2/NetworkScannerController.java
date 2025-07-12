package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.NetworkHost;
import edu.au.cpsc.module7.models.ScanConfiguration;
import edu.au.cpsc.module7.services.NetworkScannerService;
import edu.au.cpsc.module7.services.NetworkVisualizationService;
import edu.au.cpsc.module7.services.AdvancedNetworkScanner;
import edu.au.cpsc.module7.services.EnhancedNetworkScanner;
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
// Logging removed for compilation

import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Advanced Network Scanner interface
 */
public class NetworkScannerController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(NetworkScannerController.class);
    
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
    
    // Scan Log
    @FXML private TextArea logTextArea;
    @FXML private CheckBox autoScrollCheck;
    
    // Services
    private NetworkScannerService scannerService;
    private AdvancedNetworkScanner advancedScanner;
    private EnhancedNetworkScanner enhancedScanner;
    private NetworkVisualizationService visualizationService;
    
    // Data
    private ObservableList<NetworkHost> scanResults;
    private Task<AdvancedNetworkScanner.NetworkAnalysisResult> currentScanTask;
    private AdvancedNetworkScanner.GatewayInfo gatewayInfo;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scannerService = NetworkScannerService.getInstance();
        advancedScanner = AdvancedNetworkScanner.getInstance();
        enhancedScanner = EnhancedNetworkScanner.getInstance();
        visualizationService = NetworkVisualizationService.getInstance();
        scanResults = FXCollections.observableArrayList();
        
        setupUI();
        setupTableColumns();
        detectLocalNetwork();
        
        // Display initial network information
        displayNetworkInfo();
    }
    
    private void displayNetworkInfo() {
        try {
            // Show basic network information in the log
            addLogEntry("=== NETWORK ANALYSIS TOOL ===");
            addLogEntry("Advanced network scanner with nmap-like capabilities");
            addLogEntry("Initializing network interfaces...");
            
            // Get local network information
            InetAddress localHost = InetAddress.getLocalHost();
            addLogEntry("Local Host: " + localHost.getHostAddress() + " (" + localHost.getHostName() + ")");
            
        } catch (Exception e) {
            addLogEntry("Error getting network information: " + e.getMessage());
        }
    }
    
    private void setupUI() {
        // Setup combo boxes
        scanTypeCombo.setItems(FXCollections.observableArrayList(ScanConfiguration.ScanType.values()));
        scanTypeCombo.setValue(ScanConfiguration.ScanType.FULL_SCAN); // Default to comprehensive scan
        
        portScanTypeCombo.setItems(FXCollections.observableArrayList(ScanConfiguration.PortScanType.values()));
        portScanTypeCombo.setValue(ScanConfiguration.PortScanType.COMPREHENSIVE);
        
        // Setup results table
        resultsTable.setItems(scanResults);
        
        // Setup initial state
        updateScanStatus("Ready for advanced network analysis", false);
        updateResultsSummary();
        
        // Add listeners
        scanTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean enablePortOptions = newVal == ScanConfiguration.ScanType.PORT_SCAN || 
                                      newVal == ScanConfiguration.ScanType.FULL_SCAN;
            portScanTypeCombo.setDisable(!enablePortOptions);
        });
        
        // Enable advanced options by default
        detectServicesCheck.setSelected(true);
        detectOSCheck.setSelected(true);
        performTracerouteCheck.setSelected(true);
    }
    
    private void setupTableColumns() {
        statusColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getStatusIcon()));
        
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        
        hostnameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getHostname() != null ? 
                cellData.getValue().getHostname() : ""));
        
        responseTimeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getResponseTime() > 0 ? 
                cellData.getValue().getResponseTime() + "ms" : ""));
        
        openPortsColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.valueOf(cellData.getValue().getOpenPorts().size())));
        
        servicesColumn.setCellValueFactory(cellData -> {
            List<String> services = cellData.getValue().getServices();
            List<String> vulnerabilities = cellData.getValue().getVulnerabilities();
            
            String display = String.join(", ", services);
            if (!vulnerabilities.isEmpty()) {
                display += " [VULNS: " + vulnerabilities.size() + "]";
            }
            return new SimpleStringProperty(display);
        });
        
        osColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getOsGuess() != null ? 
                cellData.getValue().getOsGuess() : ""));
    }
    
    private void detectLocalNetwork() {
        try {
            // Get local IP address
            InetAddress localHost = InetAddress.getLocalHost();
            String localIP = localHost.getHostAddress();
            
            // Try to determine network range
            if (localIP.startsWith("192.168.")) {
                String[] parts = localIP.split("\\.");
                String networkRange = parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
                targetRangeField.setText(networkRange);
            } else if (localIP.startsWith("10.")) {
                String[] parts = localIP.split("\\.");
                String networkRange = parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
                targetRangeField.setText(networkRange);
            } else if (localIP.startsWith("172.")) {
                String[] parts = localIP.split("\\.");
                String networkRange = parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
                targetRangeField.setText(networkRange);
            } else {
                targetRangeField.setText("192.168.1.0/24");
            }
            
        } catch (Exception e) {
            logger.warn("Could not detect local network", e);
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
            startAdvancedNetworkScan(config);
        } catch (Exception e) {
            addLogEntry("Error starting scan: " + e.getMessage());
            updateScanStatus("Error: " + e.getMessage(), false);
        }
    }
    
    @FXML
    private void handleStopScan() {
        if (currentScanTask != null && !currentScanTask.isDone()) {
            currentScanTask.cancel(true);
            advancedScanner.stopScan();
            updateScanStatus("Scan stopped by user", false);
            addLogEntry("Advanced scan stopped by user");
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
    
    private void startAdvancedNetworkScan(ScanConfiguration config) {
        // Clear previous results
        scanResults.clear();
        updateResultsSummary();
        
        addLogEntry("=== STARTING ADVANCED NETWORK ANALYSIS ===");
        addLogEntry("Target: " + config.getTargetRange());
        addLogEntry("Scan Type: " + config.getScanType().getDisplayName());
        addLogEntry("Port Scan: " + config.getPortScanType().getDisplayName());
        addLogEntry("Advanced Features: Service Detection=" + config.isDetectServices() + 
                   ", OS Detection=" + config.isDetectOS() + 
                   ", Traceroute=" + config.isPerformTraceroute());
        
        // Create advanced scan task
        currentScanTask = advancedScanner.performComprehensiveAnalysis(config, this::addLogEntry);
        
        // Setup task event handlers
        currentScanTask.setOnRunning(e -> {
            updateScanStatus("Performing comprehensive network analysis...", true);
            addLogEntry("Advanced network analysis started...");
        });
        
        currentScanTask.setOnSucceeded(e -> {
            AdvancedNetworkScanner.NetworkAnalysisResult result = currentScanTask.getValue();
            Platform.runLater(() -> {
                // Store gateway information
                gatewayInfo = result.getGatewayInfo();
                
                // Display gateway and connectivity information
                displayGatewayInfo(gatewayInfo);
                
                // Add discovered hosts
                scanResults.addAll(result.getDiscoveredHosts());
                updateResultsSummary();
                
                // Generate comprehensive report
                generateScanReport(result);
                
                updateScanStatus("Advanced analysis completed - " + result.getDiscoveredHosts().size() + " hosts analyzed", false);
                addLogEntry("=== ANALYSIS COMPLETED ===");
                addLogEntry("Total hosts discovered: " + result.getDiscoveredHosts().size());
                addLogEntry("Gateway: " + gatewayInfo.getDefaultGateway());
                addLogEntry("External IP: " + gatewayInfo.getExternalIP());
                
                // Update network map with advanced data
                updateNetworkMap();
            });
        });
        
        currentScanTask.setOnFailed(e -> {
            Throwable exception = currentScanTask.getException();
            Platform.runLater(() -> {
                updateScanStatus("Advanced scan failed: " + exception.getMessage(), false);
                addLogEntry("Advanced scan failed: " + exception.getMessage());
            });
        });
        
        currentScanTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                updateScanStatus("Advanced scan cancelled", false);
                addLogEntry("Advanced scan was cancelled");
            });
        });
        
        // Bind progress only
        scanProgressBar.progressProperty().bind(currentScanTask.progressProperty());
        
        // Add listener to update status from task messages
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
    
    private void displayGatewayInfo(AdvancedNetworkScanner.GatewayInfo gatewayInfo) {
        addLogEntry("=== NETWORK CONNECTIVITY ANALYSIS ===");
        addLogEntry("Default Gateway: " + gatewayInfo.getDefaultGateway());
        addLogEntry("External IP Address: " + gatewayInfo.getExternalIP());
        
        if (!gatewayInfo.getInternetPath().isEmpty()) {
            addLogEntry("Internet Path (" + gatewayInfo.getInternetPath().size() + " hops):");
            for (int i = 0; i < gatewayInfo.getInternetPath().size(); i++) {
                addLogEntry("  " + (i + 1) + ". " + gatewayInfo.getInternetPath().get(i));
            }
        }
        
        addLogEntry("Routing Table Entries: " + gatewayInfo.getRoutes().size());
    }
    
    private void generateScanReport(AdvancedNetworkScanner.NetworkAnalysisResult result) {
        addLogEntry("=== SECURITY ANALYSIS REPORT ===");
        
        int totalHosts = result.getDiscoveredHosts().size();
        int hostsWithVulns = (int) result.getDiscoveredHosts().stream()
            .filter(h -> !h.getVulnerabilities().isEmpty()).count();
        int totalVulns = result.getDiscoveredHosts().stream()
            .mapToInt(h -> h.getVulnerabilities().size()).sum();
        int totalOpenPorts = result.getDiscoveredHosts().stream()
            .mapToInt(h -> h.getOpenPorts().size()).sum();
        
        addLogEntry("Hosts Scanned: " + totalHosts);
        addLogEntry("Hosts with Vulnerabilities: " + hostsWithVulns);
        addLogEntry("Total Vulnerabilities Found: " + totalVulns);
        addLogEntry("Total Open Ports: " + totalOpenPorts);
        
        if (hostsWithVulns > 0) {
            addLogEntry("=== HIGH-RISK HOSTS ===");
            result.getDiscoveredHosts().stream()
                .filter(h -> !h.getVulnerabilities().isEmpty())
                .forEach(h -> {
                    addLogEntry("🚨 " + h.getIpAddress() + " - " + h.getVulnerabilities().size() + " vulnerabilities");
                    h.getVulnerabilities().forEach(v -> addLogEntry("    - " + v));
                });
        }
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
        int vulnerableHosts = (int) scanResults.stream().filter(h -> !h.getVulnerabilities().isEmpty()).count();
        
        Platform.runLater(() -> {
            hostsFoundLabel.setText("Hosts Found: " + totalHosts);
            onlineHostsLabel.setText("Online: " + onlineHosts + " | Vulnerable: " + vulnerableHosts);
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
                "<html><body><h2>No scan results to display</h2><p>Run an advanced network scan to see the comprehensive network map.</p></body></html>");
            return;
        }
        
        try {
            String mapHTML = visualizationService.generateNetworkMapHTML(scanResults);
            networkMapWebView.getEngine().loadContent(mapHTML);
            addLogEntry("Advanced network map updated with " + scanResults.size() + " hosts and security information");
        } catch (Exception e) {
            logger.error("Error updating network map", e);
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
        fileChooser.setTitle("Export Advanced Network Map");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("HTML Files", "*.html"));
        
        File file = fileChooser.showSaveDialog(exportMapButton.getScene().getWindow());
        if (file != null) {
            try {
                String mapHTML = visualizationService.generateNetworkMapHTML(scanResults);
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(mapHTML);
                }
                addLogEntry("Advanced network map exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Error exporting network map", e);
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
        fileChooser.setTitle("Export Advanced Scan Results");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        
        File file = fileChooser.showSaveDialog(startScanButton.getScene().getWindow());
        if (file != null) {
            try {
                exportAdvancedResultsToFile(file);
                addLogEntry("Advanced scan results exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Error exporting results", e);
                addLogEntry("Error exporting results: " + e.getMessage());
            }
        }
    }
    
    private void exportAdvancedResultsToFile(File file) throws Exception {
        String extension = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase();
        
        try (FileWriter writer = new FileWriter(file)) {
            if ("csv".equals(extension)) {
                // Enhanced CSV format with security information
                writer.write("Status,IP Address,Hostname,Response Time,Open Ports,Services,OS,Risk Level,Vulnerabilities,Network Path\n");
                for (NetworkHost host : scanResults) {
                    writer.write(String.format("%s,%s,%s,%s,%d,%s,%s,%s,%s,%s\n",
                        host.getStatusIcon(),
                        host.getIpAddress(),
                        host.getHostname() != null ? host.getHostname() : "",
                        host.getResponseTime() > 0 ? host.getResponseTime() + "ms" : "",
                        host.getOpenPorts().size(),
                        String.join(";", host.getServices()),
                        host.getOsGuess() != null ? host.getOsGuess() : "",
                        host.getRiskLevel(),
                        String.join(";", host.getVulnerabilities()),
                        String.join(";", host.getNetworkPath())));
                }
            } else {
                // Enhanced text format with comprehensive information
                writer.write("ADVANCED NETWORK SCAN RESULTS\n");
                writer.write("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n");
                
                if (gatewayInfo != null) {
                    writer.write("NETWORK CONNECTIVITY:\n");
                    writer.write("Default Gateway: " + gatewayInfo.getDefaultGateway() + "\n");
                    writer.write("External IP: " + gatewayInfo.getExternalIP() + "\n\n");
                }
                
                writer.write("DISCOVERED HOSTS:\n");
                writer.write("================\n\n");
                
                for (NetworkHost host : scanResults) {
                    writer.write("Host: " + host.getIpAddress() + "\n");
                    if (host.getHostname() != null) {
                        writer.write("  Hostname: " + host.getHostname() + "\n");
                    }
                    writer.write("  Status: " + (host.isAlive() ? "Online" : "Offline") + "\n");
                    writer.write("  Risk Level: " + host.getRiskLevel() + "\n");
                    if (host.getResponseTime() > 0) {
                        writer.write("  Response Time: " + host.getResponseTime() + "ms\n");
                    }
                    writer.write("  Open Ports: " + host.getOpenPorts().size() + "\n");
                    if (!host.getOpenPorts().isEmpty()) {
                        writer.write("  Ports: " + host.getOpenPorts().stream()
                            .map(String::valueOf).collect(Collectors.joining(", ")) + "\n");
                    }
                    if (!host.getServices().isEmpty()) {
                        writer.write("  Services: " + String.join(", ", host.getServices()) + "\n");
                    }
                    if (host.getOsGuess() != null) {
                        writer.write("  OS: " + host.getOsGuess() + "\n");
                    }
                    if (!host.getVulnerabilities().isEmpty()) {
                        writer.write("  VULNERABILITIES:\n");
                        for (String vuln : host.getVulnerabilities()) {
                            writer.write("    - " + vuln + "\n");
                        }
                    }
                    if (!host.getNetworkPath().isEmpty()) {
                        writer.write("  Network Path: " + String.join(" -> ", host.getNetworkPath()) + "\n");
                    }
                    writer.write("\n");
                }
            }
        }
    }
    
    @FXML
    private void handleClearResults() {
        scanResults.clear();
        updateResultsSummary();
        networkMapWebView.getEngine().loadContent("");
        addLogEntry("Advanced scan results cleared");
    }
    
    @FXML
    private void handleClearLog() {
        logTextArea.clear();
        addLogEntry("=== ADVANCED NETWORK SCANNER LOG ===");
    }
    
    @FXML
    private void handleSaveLog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Advanced Scan Log");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        
        File file = fileChooser.showSaveDialog(logTextArea.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(logTextArea.getText());
                addLogEntry("Advanced scan log saved to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Error saving log", e);
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
    
    public void shutdown() {
        if (currentScanTask != null && !currentScanTask.isDone()) {
            currentScanTask.cancel(true);
        }
        if (advancedScanner != null) {
            advancedScanner.shutdown();
        }
        if (scannerService != null) {
            scannerService.shutdown();
        }
    }
} 