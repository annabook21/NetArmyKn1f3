package edu.au.cpsc.module7.controllers;

import com.google.inject.Inject;
import edu.au.cpsc.module7.models.CapturedPacket;
import edu.au.cpsc.module7.services.TcpdumpPacketCaptureService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import java.io.IOException;
import java.util.Arrays;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Controller for the Packet Analyzer (Mini Wireshark) interface
 */
public class PacketAnalyzerController {
    private static final Logger logger = Logger.getLogger(PacketAnalyzerController.class.getName());
    
    // Capture Controls
    @FXML private ComboBox<String> interfaceCombo;
    @FXML private Button refreshInterfacesButton;
    @FXML private TextField captureFilterField;
    @FXML private Button filterHelpButton;
    @FXML private Button startCaptureButton;
    @FXML private Button stopCaptureButton;
    @FXML private Button clearPacketsButton;
    @FXML private Button simulationModeButton;
    @FXML private Label captureStatusLabel;
    
    // Protocol Filters
    @FXML private CheckBox showAllCheck;
    @FXML private CheckBox showTcpCheck;
    @FXML private CheckBox showUdpCheck;
    @FXML private CheckBox showHttpCheck;
    @FXML private CheckBox showHttpsCheck;
    @FXML private CheckBox showDnsCheck;
    @FXML private CheckBox showDhcpCheck;
    @FXML private CheckBox showArpCheck;
    @FXML private CheckBox showIcmpCheck;
    
    // Packet List Tab
    @FXML private Label totalPacketsLabel;
    @FXML private Label tcpPacketsLabel;
    @FXML private Label udpPacketsLabel;
    @FXML private Label httpPacketsLabel;
    @FXML private Label dnsPacketsLabel;
    @FXML private Label captureTimeLabel;
    @FXML private TableView<CapturedPacket> packetTable;
    @FXML private TableColumn<CapturedPacket, String> timeColumn;
    @FXML private TableColumn<CapturedPacket, String> sourceColumn;
    @FXML private TableColumn<CapturedPacket, String> destinationColumn;
    @FXML private TableColumn<CapturedPacket, String> protocolColumn;
    @FXML private TableColumn<CapturedPacket, String> lengthColumn;
    @FXML private TableColumn<CapturedPacket, String> infoColumn;
    
    // Packet Details Tab
    @FXML private TreeView<String> protocolTreeView;
    @FXML private TextArea rawDataArea;
    
    // Statistics Tab
    @FXML private WebView protocolChartView;
    @FXML private WebView trafficChartView;
    @FXML private TableView<ProtocolStatistic> statisticsTable;
    @FXML private TableColumn<ProtocolStatistic, String> statProtocolColumn;
    @FXML private TableColumn<ProtocolStatistic, String> statPacketsColumn;
    @FXML private TableColumn<ProtocolStatistic, String> statBytesColumn;
    @FXML private TableColumn<ProtocolStatistic, String> statPercentColumn;
    @FXML private TableColumn<ProtocolStatistic, String> statAvgSizeColumn;
    
    // Export Tab
    @FXML private RadioButton exportPcapRadio;
    @FXML private RadioButton exportCsvRadio;
    @FXML private RadioButton exportJsonRadio;
    @FXML private Button exportButton;
    @FXML private Label exportStatusLabel;
    
    // Services and Data
    private final TcpdumpPacketCaptureService captureService;
    private ObservableList<CapturedPacket> allPackets;
    private FilteredList<CapturedPacket> filteredPackets;
    private ObservableList<ProtocolStatistic> protocolStats;
    private ScheduledExecutorService updateScheduler;
    private LocalDateTime captureStartTime;

    @Inject
    public PacketAnalyzerController(TcpdumpPacketCaptureService captureService) {
        this.captureService = captureService;
    }

    @FXML
    public void initialize() {
        logger.info("PacketAnalyzerController initializing...");
        
        // Initialize collections
        allPackets = FXCollections.observableArrayList();
        filteredPackets = new FilteredList<>(allPackets);
        protocolStats = FXCollections.observableArrayList();
        
        // Setup UI components
        setupTableColumns();
        setupProtocolFilters();
        setupStatisticsTable();
        setupPacketListener();
        
        // Load network interfaces
        refreshNetworkInterfaces();
        
        // Setup periodic updates
        updateScheduler = Executors.newScheduledThreadPool(1);
        updateScheduler.scheduleAtFixedRate(this::updateStatistics, 1, 1, TimeUnit.SECONDS);
        
        // Initialize status
        updateCaptureStatus("Ready to capture", false);
        
        logger.info("PacketAnalyzerController initialized successfully");
    }
    
    private void setupTableColumns() {
        // Configure packet table columns
        timeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))));
        sourceColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getSourceAddress()));
        destinationColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDestinationAddress()));
        protocolColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getProtocol()));
        lengthColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.valueOf(cellData.getValue().getLength())));
        infoColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getInfo()));
        
        // Set filtered list to table
        packetTable.setItems(filteredPackets);
        
        // Setup row selection listener
        packetTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                displayPacketDetails(newSelection);
            }
        });
        
        // Setup row factory for protocol-based coloring
        packetTable.setRowFactory(tv -> {
            TableRow<CapturedPacket> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    row.getStyleClass().removeAll("tcp-row", "udp-row", "http-row", "dns-row", "arp-row");
                    switch (newItem.getProtocol().toLowerCase()) {
                        case "tcp": row.getStyleClass().add("tcp-row"); break;
                        case "udp": row.getStyleClass().add("udp-row"); break;
                        case "http": case "https": row.getStyleClass().add("http-row"); break;
                        case "dns": row.getStyleClass().add("dns-row"); break;
                        case "arp": row.getStyleClass().add("arp-row"); break;
                    }
                }
            });
            return row;
        });
    }
    
    private void setupProtocolFilters() {
        // Setup protocol filter listeners
        showAllCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // Select all other checkboxes
                showTcpCheck.setSelected(true);
                showUdpCheck.setSelected(true);
                showHttpCheck.setSelected(true);
                showHttpsCheck.setSelected(true);
                showDnsCheck.setSelected(true);
                showDhcpCheck.setSelected(true);
                showArpCheck.setSelected(true);
                showIcmpCheck.setSelected(true);
            }
            updatePacketFilter();
        });
        
        // Individual protocol filters
        showTcpCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showUdpCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showHttpCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showHttpsCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showDnsCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showDhcpCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showArpCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
        showIcmpCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updatePacketFilter());
    }
    
    private void setupStatisticsTable() {
        statProtocolColumn.setCellValueFactory(new PropertyValueFactory<>("protocol"));
        statPacketsColumn.setCellValueFactory(new PropertyValueFactory<>("packets"));
        statBytesColumn.setCellValueFactory(new PropertyValueFactory<>("bytes"));
        statPercentColumn.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        statAvgSizeColumn.setCellValueFactory(new PropertyValueFactory<>("avgSize"));
        
        statisticsTable.setItems(protocolStats);
    }
    
    private void setupPacketListener() {
        // TcpdumpPacketCaptureService uses ObservableList directly
        // Link the capture service's packet list to our allPackets list
        captureService.getCapturedPackets().addListener((javafx.collections.ListChangeListener<CapturedPacket>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    allPackets.addAll(change.getAddedSubList());
                    
                    // Auto-scroll to bottom if table is near the end
                    Platform.runLater(() -> {
                        if (packetTable.getItems().size() > 0) {
                            int lastVisibleIndex = packetTable.getItems().size() - 1;
                            packetTable.scrollTo(lastVisibleIndex);
                        }
                    });
                }
            }
        });
    }
    
    private void updatePacketFilter() {
        filteredPackets.setPredicate(packet -> {
            if (showAllCheck.isSelected()) {
                return true;
            }
            
            String protocol = packet.getProtocol().toLowerCase();
            
            if (showTcpCheck.isSelected() && protocol.equals("tcp")) return true;
            if (showUdpCheck.isSelected() && protocol.equals("udp")) return true;
            if (showHttpCheck.isSelected() && protocol.equals("http")) return true;
            if (showHttpsCheck.isSelected() && protocol.equals("https")) return true;
            if (showDnsCheck.isSelected() && protocol.equals("dns")) return true;
            if (showDhcpCheck.isSelected() && protocol.equals("dhcp")) return true;
            if (showArpCheck.isSelected() && protocol.equals("arp")) return true;
            if (showIcmpCheck.isSelected() && protocol.equals("icmp")) return true;
            
            return false;
        });
    }
    
    private void displayPacketDetails(CapturedPacket packet) {
        // Build protocol tree
        TreeItem<String> root = new TreeItem<>("Packet " + packet.getId());
        root.setExpanded(true);
        
        // Frame information
        TreeItem<String> frameItem = new TreeItem<>("Frame " + packet.getId());
        frameItem.getChildren().addAll(
            new TreeItem<>("Arrival Time: " + packet.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))),
            new TreeItem<>("Frame Length: " + packet.getLength() + " bytes"),
            new TreeItem<>("Protocol: " + packet.getProtocol())
        );
        root.getChildren().add(frameItem);
        
        // Network layer information
        if (!packet.getSourceAddress().isEmpty()) {
            TreeItem<String> ipItem = new TreeItem<>("Internet Protocol Version 4");
            ipItem.getChildren().addAll(
                new TreeItem<>("Source Address: " + packet.getSourceAddress()),
                new TreeItem<>("Destination Address: " + packet.getDestinationAddress())
            );
            root.getChildren().add(ipItem);
        }
        
        // Transport layer information
        if (packet.getSourcePort() > 0) {
            TreeItem<String> transportItem = new TreeItem<>(packet.getProtocol() + " Protocol");
            transportItem.getChildren().addAll(
                new TreeItem<>("Source Port: " + packet.getSourcePort()),
                new TreeItem<>("Destination Port: " + packet.getDestinationPort())
            );
            root.getChildren().add(transportItem);
        }
        
        // Application layer information
        if (packet.isHTTP()) {
            TreeItem<String> httpItem = new TreeItem<>("Hypertext Transfer Protocol");
            if (packet.getHttpMethod() != null) {
                httpItem.getChildren().add(new TreeItem<>("Method: " + packet.getHttpMethod()));
            }
            if (packet.getHttpUserAgent() != null) {
                httpItem.getChildren().add(new TreeItem<>("User-Agent: " + packet.getHttpUserAgent()));
            }
            root.getChildren().add(httpItem);
        }
        
        if (packet.isARP()) {
            TreeItem<String> arpItem = new TreeItem<>("Address Resolution Protocol");
            if (packet.getArpOperation() != null) {
                arpItem.getChildren().add(new TreeItem<>("Operation: " + packet.getArpOperation()));
            }
            root.getChildren().add(arpItem);
        }
        
        protocolTreeView.setRoot(root);
        
        // Display raw data in hex format
        displayRawData(packet.getRawData());
    }
    
    private void displayRawData(byte[] data) {
        if (data == null || data.length == 0) {
            rawDataArea.setText("No raw data available");
            return;
        }
        
        StringBuilder hexBuilder = new StringBuilder();
        StringBuilder asciiBuilder = new StringBuilder();
        
        for (int i = 0; i < data.length; i++) {
            if (i % 16 == 0) {
                if (i > 0) {
                    hexBuilder.append("  ").append(asciiBuilder.toString()).append("\n");
                    asciiBuilder.setLength(0);
                }
                hexBuilder.append(String.format("%04X: ", i));
            }
            
            byte b = data[i];
            hexBuilder.append(String.format("%02X ", b & 0xFF));
            
            // ASCII representation
            if (b >= 32 && b <= 126) {
                asciiBuilder.append((char) b);
            } else {
                asciiBuilder.append('.');
            }
        }
        
        // Add remaining ASCII
        if (asciiBuilder.length() > 0) {
            int padding = 16 - (data.length % 16);
            if (padding < 16) {
                for (int i = 0; i < padding; i++) {
                    hexBuilder.append("   ");
                }
            }
            hexBuilder.append("  ").append(asciiBuilder.toString());
        }
        
        rawDataArea.setText(hexBuilder.toString());
    }
    
    private void updateStatistics() {
        if (!captureService.isCapturing() && allPackets.isEmpty()) {
            return;
        }
        
        Platform.runLater(() -> {
            // Update packet counts - calculate from allPackets
            Map<String, Long> stats = calculateProtocolStatistics();
            totalPacketsLabel.setText("Total: " + allPackets.size());
            tcpPacketsLabel.setText("TCP: " + stats.getOrDefault("TCP", 0L));
            udpPacketsLabel.setText("UDP: " + stats.getOrDefault("UDP", 0L));
            httpPacketsLabel.setText("HTTP: " + stats.getOrDefault("HTTP", 0L));
            dnsPacketsLabel.setText("DNS: " + stats.getOrDefault("DNS", 0L));
            
            // Update capture time
            if (captureStartTime != null) {
                Duration duration = Duration.between(captureStartTime, LocalDateTime.now());
                long hours = duration.toHours();
                long minutes = duration.toMinutes() % 60;
                long seconds = duration.getSeconds() % 60;
                captureTimeLabel.setText(String.format("Duration: %02d:%02d:%02d", hours, minutes, seconds));
            }
            
            // Update protocol statistics table
            updateProtocolStatistics(stats);
            
            // Update charts
            updateCharts(stats);
        });
    }
    
    private void updateProtocolStatistics(Map<String, Long> stats) {
        protocolStats.clear();
        
        long totalPackets = stats.values().stream().mapToLong(Long::longValue).sum();
        long totalBytes = allPackets.stream().mapToLong(CapturedPacket::getLength).sum();
        
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            String protocol = entry.getKey();
            long packets = entry.getValue();
            
            if (packets > 0) {
                long bytes = allPackets.stream()
                    .filter(p -> p.getProtocol().equalsIgnoreCase(protocol))
                    .mapToLong(CapturedPacket::getLength)
                    .sum();
                
                double percentage = totalPackets > 0 ? (packets * 100.0) / totalPackets : 0;
                double avgSize = packets > 0 ? (double) bytes / packets : 0;
                
                protocolStats.add(new ProtocolStatistic(
                    protocol,
                    String.valueOf(packets),
                    formatBytes(bytes),
                    String.format("%.1f%%", percentage),
                    String.format("%.0f bytes", avgSize)
                ));
            }
        }
    }
    
    private void updateCharts(Map<String, Long> stats) {
        // Generate protocol distribution chart
        String protocolChartHtml = generateProtocolChart(stats);
        protocolChartView.getEngine().loadContent(protocolChartHtml);
        
        // Generate traffic over time chart
        String trafficChartHtml = generateTrafficChart();
        trafficChartView.getEngine().loadContent(trafficChartHtml);
    }
    
    private String generateProtocolChart(Map<String, Long> stats) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            if (entry.getValue() > 0) {
                if (data.length() > 0) data.append(",");
                data.append(String.format("{protocol:'%s', count:%d}", entry.getKey(), entry.getValue()));
            }
        }
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://d3js.org/d3.v7.min.js"></script>
                <style>
                    body { margin: 0; font-family: Arial, sans-serif; }
                    .chart { width: 100%%; height: 280px; }
                </style>
            </head>
            <body>
                <div id="chart" class="chart"></div>
                <script>
                    const data = [%s];
                    const width = 300, height = 280, radius = Math.min(width, height) / 2;
                    
                    const svg = d3.select("#chart")
                        .append("svg")
                        .attr("width", width)
                        .attr("height", height)
                        .append("g")
                        .attr("transform", "translate(" + width/2 + "," + height/2 + ")");
                    
                    const color = d3.scaleOrdinal(d3.schemeCategory10);
                    const pie = d3.pie().value(d => d.count);
                    const arc = d3.arc().innerRadius(0).outerRadius(radius - 10);
                    
                    const arcs = svg.selectAll("arc")
                        .data(pie(data))
                        .enter()
                        .append("g");
                    
                    arcs.append("path")
                        .attr("d", arc)
                        .attr("fill", (d, i) => color(i));
                    
                    arcs.append("text")
                        .attr("transform", d => "translate(" + arc.centroid(d) + ")")
                        .attr("text-anchor", "middle")
                        .text(d => d.data.protocol)
                        .style("font-size", "12px")
                        .style("fill", "white");
                </script>
            </body>
            </html>
            """, data.toString());
    }
    
    private String generateTrafficChart() {
        // Simple traffic over time chart
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://d3js.org/d3.v7.min.js"></script>
                <style>
                    body { margin: 0; font-family: Arial, sans-serif; }
                    .chart { width: 100%; height: 280px; }
                    .line { fill: none; stroke: #4CAF50; stroke-width: 2px; }
                    .axis { stroke: #333; }
                </style>
            </head>
            <body>
                <div id="chart" class="chart"></div>
                <script>
                    // Placeholder for traffic over time chart
                    const svg = d3.select("#chart")
                        .append("svg")
                        .attr("width", 300)
                        .attr("height", 280);
                    
                    svg.append("text")
                        .attr("x", 150)
                        .attr("y", 140)
                        .attr("text-anchor", "middle")
                        .text("Traffic chart will appear here")
                        .style("font-size", "14px")
                        .style("fill", "#666");
                </script>
            </body>
            </html>
            """;
    }
    
    private Map<String, Long> calculateProtocolStatistics() {
        Map<String, Long> stats = new HashMap<>();
        
        for (CapturedPacket packet : allPackets) {
            String protocol = packet.getProtocol().toUpperCase();
            stats.put(protocol, stats.getOrDefault(protocol, 0L) + 1);
        }
        
        return stats;
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    // Event Handlers
    @FXML
    private void handleRefreshInterfaces() {
        refreshNetworkInterfaces();
    }
    
    @FXML
    private void handleFilterHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("BPF Filter Help");
        alert.setHeaderText("Berkeley Packet Filter (BPF) Syntax");
        alert.setContentText("""
            Common BPF filter examples:
            
            • tcp port 80 - HTTP traffic
            • udp port 53 - DNS queries
            • host 192.168.1.1 - Traffic to/from specific host
            • net 192.168.1.0/24 - Traffic in subnet
            • tcp and port 443 - HTTPS traffic
            • icmp - ICMP packets only
            • arp - ARP packets only
            • not tcp - Everything except TCP
            
            Combine with 'and', 'or', 'not' operators.
            """);
        alert.showAndWait();
    }
    
    @FXML
    private void handleStartCapture() {
        String selectedInterface = interfaceCombo.getSelectionModel().getSelectedItem();
        if (selectedInterface == null) {
            showAlert("No Interface Selected", "Please select a network interface first.");
            return;
        }
        
        String filter = captureFilterField.getText().trim();
        
        // Create temporary capture file
        String captureFile = "/tmp/netarmykn1f3_capture_" + System.currentTimeMillis() + ".pcap";
        
        try {
            captureService.startCapture(selectedInterface, filter, captureFile);
            captureStartTime = LocalDateTime.now();
            updateCaptureStatus("Capturing packets...", true);
            logger.info("Packet capture started on interface: " + selectedInterface);
        } catch (Exception e) {
            showAlert("Capture Failed", "Failed to start packet capture: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleStopCapture() {
        captureService.stopCapture();
        updateCaptureStatus("Capture stopped", false);
        logger.info("Packet capture stopped");
    }
    
    @FXML
    private void handleClearPackets() {
        allPackets.clear();
        captureService.clearPackets();
        protocolTreeView.setRoot(null);
        rawDataArea.clear();
        updateCaptureStatus("Packets cleared", false);
        logger.info("Captured packets cleared");
    }
    
    @FXML
    private void handleProtocolFilter() {
        updatePacketFilter();
    }
    
    @FXML
    private void handleExportPackets() {
        if (allPackets.isEmpty()) {
            showAlert("No Packets", "No packets to export. Start capturing first.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Packets");
        
        String format;
        String extension;
        if (exportPcapRadio.isSelected()) {
            format = "PCAP";
            extension = "*.pcap";
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PCAP files", extension));
        } else if (exportCsvRadio.isSelected()) {
            format = "CSV";
            extension = "*.csv";
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", extension));
        } else {
            format = "JSON";
            extension = "*.json";
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", extension));
        }
        
        File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file != null) {
            try {
                exportPackets(file, format);
                exportStatusLabel.setText("Exported " + allPackets.size() + " packets to " + file.getName());
            } catch (IOException e) {
                showAlert("Export Failed", "Failed to export packets: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleSimulationMode() {
        // Show dialog with proper macOS packet capture setup
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Enable Real Packet Capture on macOS");
        alert.setHeaderText("Set up packet capture permissions (macOS method)");
        alert.setContentText("""
            On macOS, packet capture requires special setup due to System Integrity Protection.
            
            EASY METHOD (Recommended):
            1. Install Wireshark which handles all permissions automatically:
               brew install --cask wireshark
            
            2. After installation, restart this application
            
            MANUAL METHOD (Advanced):
            1. Install ChmodBPF: 
               curl https://raw.githubusercontent.com/wireshark/wireshark/master/tools/macos-setup.sh | bash
            
            2. Restart this application
            
            The easy method installs Wireshark and automatically configures packet capture permissions.
            Would you like to proceed?
            """);
        
        ButtonType wiresharkButton = new ButtonType("Install Wireshark (Easy)");
        ButtonType manualButton = new ButtonType("Manual Setup");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(wiresharkButton, manualButton, cancelButton);
        alert.getDialogPane().setPrefWidth(700);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == wiresharkButton) {
                installWiresharkMethod();
            } else if (result.get() == manualButton) {
                manualSetupMethod();
            }
        }
    }
    
    private void installWiresharkMethod() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Install Wireshark");
        confirmAlert.setHeaderText("Automatic packet capture setup");
        confirmAlert.setContentText("""
            This will install Wireshark using Homebrew, which automatically sets up packet capture permissions.
            
            Command to run:
            brew install --cask wireshark
            
            This is the recommended method on macOS and works without disabling security features.
            
            Continue with installation?
            """);
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            executeWiresharkInstall();
        }
    }
    
    private void executeWiresharkInstall() {
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Installing Wireshark...");
        progressAlert.setHeaderText("Please wait");
        progressAlert.setContentText("Installing Wireshark and setting up packet capture permissions...");
        progressAlert.getDialogPane().lookupButton(ButtonType.OK).setVisible(false);
        
        // Add cancel button to stop hanging installations
        ButtonType cancelButton = new ButtonType("Cancel Installation", ButtonBar.ButtonData.CANCEL_CLOSE);
        progressAlert.getButtonTypes().add(cancelButton);
        progressAlert.show();
        
        Thread installThread = new Thread(() -> {
            final Process[] processHolder = new Process[1]; // Use array to make it effectively final
            try {
                // First check if Homebrew is installed
                ProcessBuilder brewCheck = new ProcessBuilder("which", "brew");
                Process brewProcess = brewCheck.start();
                boolean brewInstalled = brewProcess.waitFor() == 0;
                
                if (!brewInstalled) {
                    Platform.runLater(() -> {
                        progressAlert.close();
                        showInstallationError("Homebrew is not installed. Please install Homebrew first:\n\n/bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"\n\nThen try the setup again.");
                    });
                    return;
                }
                
                // Update progress
                Platform.runLater(() -> progressAlert.setContentText("Homebrew found. Installing Wireshark..."));
                
                // Execute brew install with timeout
                ProcessBuilder pb = new ProcessBuilder("brew", "install", "--cask", "wireshark");
                pb.redirectErrorStream(true); // Combine stdout and stderr
                processHolder[0] = pb.start();
                
                // Wait for completion with timeout (5 minutes)
                boolean finished = processHolder[0].waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
                
                if (!finished) {
                    // Installation timed out
                    processHolder[0].destroyForcibly();
                    Platform.runLater(() -> {
                        progressAlert.close();
                        showInstallationError("Installation timed out after 5 minutes. This could mean:\n\n• Slow internet connection\n• Homebrew needs updating\n• User interaction required\n\nTry running this command manually in Terminal:\nbrew install --cask wireshark");
                    });
                    return;
                }
                
                int exitCode = processHolder[0].exitValue();
                
                Platform.runLater(() -> {
                    progressAlert.close();
                    
                    if (exitCode == 0) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Installation Complete!");
                        successAlert.setHeaderText("Wireshark installed successfully");
                        successAlert.setContentText("""
                            ✅ Wireshark installed and packet capture permissions configured!
                            
                            • Restart this application for changes to take effect
                            • Packet capture will work without sudo
                            • You can use both this app and Wireshark for packet analysis
                            
                            Please restart the NetArmyKn1f3 application now.
                            """);
                        successAlert.showAndWait();
                    } else {
                        // Read error output
                        StringBuilder errorOutput = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(processHolder[0].getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                errorOutput.append(line).append("\n");
                            }
                        } catch (Exception e) {
                            errorOutput.append("Could not read error output: ").append(e.getMessage());
                        }
                        
                        String errorMsg = "Homebrew installation failed (exit code: " + exitCode + ")\n\n" + errorOutput.toString();
                        if (errorOutput.toString().contains("already installed")) {
                            Alert alreadyInstalledAlert = new Alert(Alert.AlertType.INFORMATION);
                            alreadyInstalledAlert.setTitle("Already Installed");
                            alreadyInstalledAlert.setHeaderText("Wireshark is already installed");
                            alreadyInstalledAlert.setContentText("✅ Wireshark is already installed!\n\nRestart this application to use packet capture.");
                            alreadyInstalledAlert.showAndWait();
                        } else {
                            showInstallationError(errorMsg);
                        }
                    }
                });
                
            } catch (InterruptedException e) {
                // Thread was interrupted (user cancelled)
                if (processHolder[0] != null) {
                    processHolder[0].destroyForcibly();
                }
                Platform.runLater(() -> {
                    progressAlert.close();
                    showAlert("Installation Cancelled", "Wireshark installation was cancelled.");
                });
            } catch (Exception e) {
                if (processHolder[0] != null) {
                    processHolder[0].destroyForcibly();
                }
                Platform.runLater(() -> {
                    progressAlert.close();
                    showInstallationError("Installation error: " + e.getMessage());
                });
            }
        });
        
        // Handle cancel button
        progressAlert.setOnCloseRequest(event -> {
            installThread.interrupt();
        });
        
        installThread.setDaemon(true);
        installThread.start();
    }
    
    private void manualSetupMethod() {
        Alert manualAlert = new Alert(Alert.AlertType.INFORMATION);
        manualAlert.setTitle("Manual Setup Instructions");
        manualAlert.setHeaderText("Advanced packet capture setup");
        manualAlert.setContentText("""
            For manual setup, run these commands in Terminal:
            
            1. Install ChmodBPF:
            curl https://raw.githubusercontent.com/wireshark/wireshark/master/tools/macos-setup.sh | bash
            
            2. Add your user to the access_bpf group:
            sudo dseditgroup -o edit -a $(whoami) -t user access_bpf
            
            3. Restart your computer for changes to take effect
            
            4. Restart this application
            
            This method gives you packet capture without installing Wireshark.
            """);
        manualAlert.getDialogPane().setPrefWidth(650);
        manualAlert.showAndWait();
    }
    
    private void showInstallationError(String errorMessage) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Installation Failed");
        errorAlert.setHeaderText("Could not complete automatic setup");
        errorAlert.setContentText(errorMessage + """
            
            
            Alternative options:
            • Try the manual setup method
            • Install Wireshark manually from wireshark.org
            • Run this application with: sudo mvn exec:java -Dexec.mainClass="edu.au.cpsc.module7.App"
            """);
        errorAlert.getDialogPane().setPrefWidth(600);
        errorAlert.showAndWait();
    }
    
    private void performOneTimeSudoSetup() {
        // Legacy method - show that it won't work on modern macOS
        Alert infoAlert = new Alert(Alert.AlertType.WARNING);
        infoAlert.setTitle("macOS Security Restriction");
        infoAlert.setHeaderText("chmod +s method blocked by System Integrity Protection");
        infoAlert.setContentText("""
            The traditional chmod +s method doesn't work on modern macOS due to System Integrity Protection (SIP).
            
            Please use the "Setup Real Capture" button which provides macOS-compatible methods:
            
            • Wireshark installation (automatic setup)
            • ChmodBPF method (manual setup)
            
            Both methods work with macOS security features enabled.
            """);
        infoAlert.getDialogPane().setPrefWidth(650);
        infoAlert.showAndWait();
    }
    
    private void refreshNetworkInterfaces() {
        String[] interfaceArray = captureService.getAvailableInterfaces();
        List<String> interfaces = Arrays.asList(interfaceArray);
        interfaceCombo.getItems().clear();
        interfaceCombo.getItems().addAll(interfaces);
        
        if (!interfaces.isEmpty()) {
            interfaceCombo.getSelectionModel().selectFirst();
        }
        
        logger.info("Refreshed network interfaces: " + interfaces.size() + " found");
    }
    
    private void updateCaptureStatus(String message, boolean capturing) {
        Platform.runLater(() -> {
            captureStatusLabel.setText(message);
            startCaptureButton.setDisable(capturing);
            stopCaptureButton.setDisable(!capturing);
            clearPacketsButton.setDisable(capturing);
        });
    }
    
    private void exportPackets(File file, String format) throws IOException {
        switch (format) {
            case "CSV":
                exportToCsv(file);
                break;
            case "JSON":
                exportToJson(file);
                break;
            case "PCAP":
                exportToPcap(file);
                break;
        }
    }
    
    private void exportToCsv(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Time,Source,Destination,Protocol,Length,Info\n");
            
            for (CapturedPacket packet : allPackets) {
                writer.write(String.format("%s,%s,%s,%s,%d,\"%s\"\n",
                    packet.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                    packet.getSourceAddress(),
                    packet.getDestinationAddress(),
                    packet.getProtocol(),
                    packet.getLength(),
                    packet.getInfo().replace("\"", "\"\"")));
            }
        }
    }
    
    private void exportToJson(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{\n  \"packets\": [\n");
            
            for (int i = 0; i < allPackets.size(); i++) {
                CapturedPacket packet = allPackets.get(i);
                writer.write(String.format("""
                    {
                      "id": %d,
                      "timestamp": "%s",
                      "source": "%s",
                      "destination": "%s",
                      "protocol": "%s",
                      "length": %d,
                      "info": "%s"
                    }""",
                    packet.getId(),
                    packet.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                    packet.getSourceAddress(),
                    packet.getDestinationAddress(),
                    packet.getProtocol(),
                    packet.getLength(),
                    packet.getInfo().replace("\"", "\\\"")));
                
                if (i < allPackets.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            
            writer.write("  ]\n}");
        }
    }
    
    private void exportToPcap(File file) throws IOException {
        // Note: This is a simplified PCAP export
        // For full PCAP support, you'd need to use a proper PCAP library
        showAlert("PCAP Export", "PCAP export requires additional libraries. Use CSV or JSON for now.");
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    public void shutdown() {
        if (updateScheduler != null) {
            updateScheduler.shutdown();
        }
        if (captureService != null) {
            captureService.shutdown();
        }
    }
    
    // Inner class for protocol statistics
    public static class ProtocolStatistic {
        private final String protocol;
        private final String packets;
        private final String bytes;
        private final String percentage;
        private final String avgSize;
        
        public ProtocolStatistic(String protocol, String packets, String bytes, String percentage, String avgSize) {
            this.protocol = protocol;
            this.packets = packets;
            this.bytes = bytes;
            this.percentage = percentage;
            this.avgSize = avgSize;
        }
        
        public String getProtocol() { return protocol; }
        public String getPackets() { return packets; }
        public String getBytes() { return bytes; }
        public String getPercentage() { return percentage; }
        public String getAvgSize() { return avgSize; }
    }
} 