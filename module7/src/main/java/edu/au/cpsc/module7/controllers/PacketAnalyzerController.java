package edu.au.cpsc.module7.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Controller for the Packet Analyzer tab
 */
public class PacketAnalyzerController implements Initializable {
    
    private static final Logger logger = Logger.getLogger(PacketAnalyzerController.class.getName());
    
    // FXML fields matching PacketAnalyzer.fxml exactly
    @FXML private ComboBox<String> interfaceCombo;
    @FXML private TextField captureFilterField;
    @FXML private Button startCaptureButton;
    @FXML private Button stopCaptureButton;
    @FXML private Button clearPacketsButton;
    @FXML private Button exportButton;
    @FXML private TableView<String> packetTable;
    @FXML private TableColumn<String, String> timeColumn;
    @FXML private TableColumn<String, String> sourceColumn;
    @FXML private TableColumn<String, String> destinationColumn;
    @FXML private TableColumn<String, String> protocolColumn;
    @FXML private TableColumn<String, String> lengthColumn;
    @FXML private TableColumn<String, String> infoColumn;
    @FXML private TreeView<String> protocolTreeView;
    @FXML private TextArea rawDataArea;
    @FXML private Label captureStatusLabel;
    @FXML private Label totalPacketsLabel;
    @FXML private Label tcpPacketsLabel;
    @FXML private Label udpPacketsLabel;
    @FXML private Label httpPacketsLabel;
    @FXML private Label dnsPacketsLabel;
    @FXML private Label captureTimeLabel;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("PacketAnalyzerController initializing...");
        
        // Setup interface combo
        if (interfaceCombo != null) {
            interfaceCombo.getItems().addAll(
                "eth0 (Ethernet)",
                "wlan0 (Wi-Fi)",
                "lo (Loopback)",
                "any (All Interfaces)"
            );
            interfaceCombo.setValue("any (All Interfaces)");
        }
        
        // Setup default values
        if (captureStatusLabel != null) {
            captureStatusLabel.setText("Ready to capture");
        }
        if (totalPacketsLabel != null) {
            totalPacketsLabel.setText("Total: 0");
        }
        if (tcpPacketsLabel != null) {
            tcpPacketsLabel.setText("TCP: 0");
        }
        if (udpPacketsLabel != null) {
            udpPacketsLabel.setText("UDP: 0");
        }
        if (httpPacketsLabel != null) {
            httpPacketsLabel.setText("HTTP: 0");
        }
        if (dnsPacketsLabel != null) {
            dnsPacketsLabel.setText("DNS: 0");
        }
        if (captureTimeLabel != null) {
            captureTimeLabel.setText("Duration: 00:00:00");
        }
        if (rawDataArea != null) {
            rawDataArea.setText("Select a packet to view raw data...");
        }
        
        logger.info("PacketAnalyzerController initialized successfully");
    }
    
    @FXML
    private void handleRefreshInterfaces() {
        if (interfaceCombo != null) {
            interfaceCombo.getItems().clear();
            interfaceCombo.getItems().addAll(
                "eth0 (Ethernet)",
                "wlan0 (Wi-Fi)",
                "lo (Loopback)",
                "any (All Interfaces)"
            );
            interfaceCombo.setValue("any (All Interfaces)");
        }
        logger.info("Network interfaces refreshed");
    }
    
    @FXML
    private void handleFilterHelp() {
        showAlert("Filter Help", 
            "Capture Filter Examples:\n\n" +
            "• tcp - Capture only TCP packets\n" +
            "• udp - Capture only UDP packets\n" +
            "• icmp - Capture only ICMP packets\n" +
            "• host 192.168.1.1 - Capture packets from/to specific host\n" +
            "• port 80 - Capture packets on port 80\n" +
            "• tcp and port 443 - Capture HTTPS traffic\n" +
            "• not arp - Exclude ARP packets\n\n" +
            "Leave empty to capture all packets."
        );
    }
    
    @FXML
    private void handleStartCapture() {
        if (captureStatusLabel != null) {
            captureStatusLabel.setText("Capturing...");
        }
        if (startCaptureButton != null) {
            startCaptureButton.setDisable(true);
        }
        if (stopCaptureButton != null) {
            stopCaptureButton.setDisable(false);
        }
        logger.info("Packet capture started (simulated)");
    }
    
    @FXML
    private void handleStopCapture() {
        if (captureStatusLabel != null) {
            captureStatusLabel.setText("Capture stopped");
        }
        if (startCaptureButton != null) {
            startCaptureButton.setDisable(false);
        }
        if (stopCaptureButton != null) {
            stopCaptureButton.setDisable(true);
        }
        logger.info("Packet capture stopped");
    }
    
    @FXML
    private void handleClearPackets() {
        if (packetTable != null) {
            packetTable.getItems().clear();
        }
        if (totalPacketsLabel != null) {
            totalPacketsLabel.setText("Total: 0");
        }
        if (tcpPacketsLabel != null) {
            tcpPacketsLabel.setText("TCP: 0");
        }
        if (udpPacketsLabel != null) {
            udpPacketsLabel.setText("UDP: 0");
        }
        if (httpPacketsLabel != null) {
            httpPacketsLabel.setText("HTTP: 0");
        }
        if (dnsPacketsLabel != null) {
            dnsPacketsLabel.setText("DNS: 0");
        }
        if (rawDataArea != null) {
            rawDataArea.setText("Select a packet to view raw data...");
        }
        logger.info("Packet capture cleared");
    }
    
    @FXML
    private void handleProtocolFilter() {
        logger.info("Protocol filter updated");
    }
    
    @FXML
    private void handleExportPackets() {
        logger.info("Export packets feature not yet implemented");
        showAlert("Export", "Export functionality is not yet implemented in this demo version.");
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
} 