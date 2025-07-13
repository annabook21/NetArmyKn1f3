package edu.au.cpsc.module7.controllers;

import com.google.inject.Inject;
import edu.au.cpsc.module7.models.QueryResult;
import edu.au.cpsc.module7.services.DNSQueryService;
import edu.au.cpsc.module7.services.SettingsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainWindowController {

    private static final Logger LOGGER = Logger.getLogger(MainWindowController.class.getName());

    @FXML
    private MenuBar menuBar;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField domainTextField;
    @FXML
    private CheckBox digCheck;
    @FXML
    private CheckBox nslookupCheck;
    @FXML
    private CheckBox whoisCheck;
    @FXML
    private CheckBox hostCheck;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TableView<QueryResult> resultsTable;

    private final DNSQueryService dnsQueryService;
    private final SettingsService settingsService;
    private final FXMLLoader fxmlLoader;
    private final ObservableList<QueryResult> queryResults = FXCollections.observableArrayList();

    @Inject
    public MainWindowController(DNSQueryService dnsQueryService, SettingsService settingsService, FXMLLoader fxmlLoader) {
        this.dnsQueryService = dnsQueryService;
        this.settingsService = settingsService;
        this.fxmlLoader = fxmlLoader;
    }

    public void initialize() {
        LOGGER.info("MainWindowController Initializing...");

        if(resultsTable != null) {
            setupResultsTable();
        }
        updateStatus("Ready. Enter a domain to begin.", false);
        loadSettings();
    }

    private void setupResultsTable() {
        TableColumn<QueryResult, String> timestampCol = new TableColumn<>("Timestamp");
        timestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));

        TableColumn<QueryResult, String> commandCol = new TableColumn<>("Command");
        commandCol.setCellValueFactory(new PropertyValueFactory<>("command"));

        TableColumn<QueryResult, String> resultCol = new TableColumn<>("Result");
        resultCol.setCellValueFactory(new PropertyValueFactory<>("result"));
        resultCol.setPrefWidth(600);

        resultsTable.getColumns().addAll(timestampCol, commandCol, resultCol);
        resultsTable.setItems(queryResults);
    }

    private void loadSettings() {
        List<String> lastQueries = settingsService.getLastQueries();
        if (lastQueries != null && !lastQueries.isEmpty()) {
            // historyComboBox.setItems(FXCollections.observableArrayList(lastQueries));
        }
    }

    @FXML
    private void handleRunQueries() {
        String domain = domainTextField.getText();
        if (domain == null || domain.trim().isEmpty()) {
            updateStatus("Domain cannot be empty.", true);
            return;
        }

        List<DNSQueryService.QueryType> selectedQueries = new ArrayList<>();
        if (digCheck.isSelected()) selectedQueries.add(DNSQueryService.QueryType.DIG);
        if (nslookupCheck.isSelected()) selectedQueries.add(DNSQueryService.QueryType.NSLOOKUP);
        if (whoisCheck.isSelected()) selectedQueries.add(DNSQueryService.QueryType.WHOIS);
        if (hostCheck.isSelected()) selectedQueries.add(DNSQueryService.QueryType.HOST);

        if (selectedQueries.isEmpty()) {
            updateStatus("No query types selected.", true);
            return;
        }

        progressBar.setVisible(true);
        // Use the injected dnsQueryService
        dnsQueryService.configure(domain, selectedQueries);
        dnsQueryService.setOnSucceeded(event -> {
            queryResults.addAll(dnsQueryService.getValue());
            progressBar.setVisible(false);
            updateStatus("Queries completed.", false);
        });
        dnsQueryService.setOnFailed(event -> {
            progressBar.setVisible(false);
            updateStatus("Queries failed.", true);
        });
        
        // This should be moved to a concurrency service later
        new Thread(dnsQueryService).start();
    }

    @FXML
    private void handleSave() {
        if (queryResults.isEmpty()) {
            updateStatus("No results to save.", true);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Query Results");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(resultsTable.getScene().getWindow());

        if (file != null) {
            try {
                StringBuilder content = new StringBuilder();
                for (QueryResult result : queryResults) {
                    content.append(result.toString()).append("\n\n");
                }
                Files.write(file.toPath(), content.toString().getBytes());
                updateStatus("Results saved to " + file.getAbsolutePath(), false);
            } catch (IOException e) {
                updateStatus("Failed to save results.", true);
                LOGGER.log(Level.SEVERE, "Failed to save results", e);
            }
        }
    }

    @FXML
    private void handleClear() {
        queryResults.clear();
        domainTextField.clear();
        updateStatus("Cleared results.", false);
    }

    @FXML
    private void handleSettings() {
        try {
            fxmlLoader.setLocation(getClass().getResource("/edu/au/cpsc/module7/styles/fxml/SettingsDialog.fxml"));
            Parent root = fxmlLoader.load();
            Stage settingsStage = new Stage();
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.setTitle("Settings");
            settingsStage.setScene(new Scene(root));
            // The controller is already managed by Guice, so we don't need to get it here
            settingsStage.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open settings dialog", e);
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleAbout() {
        showAlert("About", "DNS Query Tool\nVersion 1.0\nDeveloped for CPSC 2710");
    }

    @FXML
    private void handleManageTools() {
        showAlert("Manage Tools", "Tool management functionality not yet implemented.");
    }

    @FXML
    private void handlePacketAnalyzer() {
        try {
            // Use the injected fxmlLoader
            fxmlLoader.setLocation(getClass().getResource("/edu/au/cpsc/module7/styles/fxml/PacketAnalyzer.fxml"));
            Parent root = fxmlLoader.load();
            Stage packetStage = new Stage();
            packetStage.setTitle("📡 Packet Analyzer (PCAP Tool)");
            packetStage.setScene(new Scene(root, 1200, 800));
            packetStage.show();
            updateStatus("Packet Analyzer opened in new window", false);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open packet analyzer", e);
            updateStatus("Failed to open Packet Analyzer: " + e.getMessage(), true);
        }
    }

    private void updateStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("Status: " + message);
                if (isError) {
                    statusLabel.setStyle("-fx-text-fill: red;");
                } else {
                    statusLabel.setStyle("");
                }
            }
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}