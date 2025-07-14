package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.SubnetCalculation;
import edu.au.cpsc.module7.services.SubnetHelperService;
import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.concurrent.Task;

/**
 * Controller for the Subnet Helper tab with beautiful JavaFX interface.
 */
public class SubnetHelperController {

    @Inject
    private SubnetHelperService subnetHelperService;

    // Input Controls
    @FXML private TextField ipAddressField;
    @FXML private TextField cidrField;
    @FXML private TextField numSubnetsField;
    @FXML private Button calculateButton;
    @FXML private Button clearButton;

    // Validation Labels
    @FXML private Label ipValidationLabel;
    @FXML private Label cidrValidationLabel;
    @FXML private Label subnetValidationLabel;

    // Results Display
    @FXML private TextArea networkInfoArea;
    @FXML private TextArea explanationArea;
    @FXML private TableView<SubnetRow> subnetsTable;
    @FXML private TableColumn<SubnetRow, String> subnetColumn;
    @FXML private TableColumn<SubnetRow, String> hostsColumn;
    @FXML private TableColumn<SubnetRow, String> rangeColumn;

    // Progress and Status
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private VBox resultsContainer;

    private ObservableList<SubnetRow> subnetData = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupTableColumns();
        setupValidation();
        setupSampleData();
        resultsContainer.setVisible(false);
        progressIndicator.setVisible(false);
    }

    private void setupTableColumns() {
        subnetColumn.setCellValueFactory(data -> data.getValue().subnetProperty());
        hostsColumn.setCellValueFactory(data -> data.getValue().hostsProperty());
        rangeColumn.setCellValueFactory(data -> data.getValue().rangeProperty());
        
        subnetsTable.setItems(subnetData);
        
        // Style the table
        subnetsTable.setRowFactory(tv -> {
            TableRow<SubnetRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    copyToClipboard(row.getItem().getSubnet());
                }
            });
            return row;
        });
    }

    private void setupValidation() {
        // Real-time IP validation
        ipAddressField.textProperty().addListener((obs, oldText, newText) -> {
            validateIp(newText);
        });

        // Real-time CIDR validation
        cidrField.textProperty().addListener((obs, oldText, newText) -> {
            validateCidr(newText);
        });

        // Real-time subnet count validation
        numSubnetsField.textProperty().addListener((obs, oldText, newText) -> {
            validateSubnetCount(newText);
        });
    }

    private void setupSampleData() {
        ipAddressField.setText("192.168.1.0");
        cidrField.setText("24");
        numSubnetsField.setText("4");
    }

    private void validateIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            setValidationLabel(ipValidationLabel, "Enter an IP address", Color.GRAY);
            return;
        }

        if (subnetHelperService.isValidIp(ip)) {
            setValidationLabel(ipValidationLabel, "✓ Valid IP address", Color.GREEN);
        } else {
            String suggested = subnetHelperService.getClosestValidIp(ip);
            setValidationLabel(ipValidationLabel, "⚠ Invalid IP. Suggested: " + suggested, Color.ORANGE);
        }
    }

    private void validateCidr(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            setValidationLabel(cidrValidationLabel, "Enter CIDR prefix (0-32)", Color.GRAY);
            return;
        }

        if (subnetHelperService.isValidCidr(cidr)) {
            int prefix = Integer.parseInt(cidr);
            long hosts = subnetHelperService.calculateHostsPerSubnet(prefix);
            setValidationLabel(cidrValidationLabel, String.format("✓ Valid CIDR (%,d hosts)", hosts), Color.GREEN);
        } else {
            int suggested = subnetHelperService.getClosestValidCidr(cidr);
            setValidationLabel(cidrValidationLabel, "⚠ Invalid CIDR. Suggested: /" + suggested, Color.ORANGE);
        }
    }

    private void validateSubnetCount(String count) {
        if (count == null || count.trim().isEmpty()) {
            setValidationLabel(subnetValidationLabel, "Enter number of subnets", Color.GRAY);
            return;
        }

        try {
            int num = Integer.parseInt(count);
            if (num <= 0) {
                setValidationLabel(subnetValidationLabel, "⚠ Must be greater than 0", Color.ORANGE);
            } else if (num > 1024) {
                setValidationLabel(subnetValidationLabel, "⚠ Large number may take time", Color.ORANGE);
            } else {
                setValidationLabel(subnetValidationLabel, "✓ Valid subnet count", Color.GREEN);
            }
        } catch (NumberFormatException e) {
            setValidationLabel(subnetValidationLabel, "⚠ Must be a number", Color.ORANGE);
        }
    }

    private void setValidationLabel(Label label, String text, Color color) {
        Platform.runLater(() -> {
            label.setText(text);
            label.setTextFill(color);
        });
    }

    @FXML
    private void calculateSubnets() {
        String ip = ipAddressField.getText().trim();
        String cidr = cidrField.getText().trim();
        String subnetCount = numSubnetsField.getText().trim();

        if (ip.isEmpty() || cidr.isEmpty() || subnetCount.isEmpty()) {
            showStatus("Please fill in all fields", Color.ORANGE);
            return;
        }

        progressIndicator.setVisible(true);
        calculateButton.setDisable(true);
        statusLabel.setText("Calculating subnets...");

        Task<SubnetCalculation> task = new Task<SubnetCalculation>() {
            @Override
            protected SubnetCalculation call() throws Exception {
                Thread.sleep(100); // Small delay for visual feedback
                
                int prefixLength = subnetHelperService.getClosestValidCidr(cidr);
                int numSubnets = Integer.parseInt(subnetCount);
                String validIp = subnetHelperService.getClosestValidIp(ip);

                return subnetHelperService.generateSubnets(validIp, prefixLength, numSubnets);
            }
        };

        task.setOnSucceeded(e -> {
            SubnetCalculation result = task.getValue();
            displayResults(result);
            progressIndicator.setVisible(false);
            calculateButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            showStatus("Error calculating subnets", Color.RED);
            progressIndicator.setVisible(false);
            calculateButton.setDisable(false);
        });

        new Thread(task).start();
    }

    private void displayResults(SubnetCalculation result) {
        Platform.runLater(() -> {
            if (!result.isValid()) {
                showStatus("Error: " + result.getErrorMessage(), Color.RED);
                return;
            }

            resultsContainer.setVisible(true);

            // Display network information
            String networkInfo = subnetHelperService.getSubnetInfo(
                result.getSuggestedIp(), 
                result.getPrefixLength()
            );
            networkInfoArea.setText(networkInfo);

            // Display explanation
            explanationArea.setText(result.getExplanation() + 
                (result.getErrorMessage() != null ? "\n\nNote: " + result.getErrorMessage() : ""));

            // Populate table
            subnetData.clear();
            for (String subnet : result.getGeneratedSubnets()) {
                String[] parts = subnet.split("/");
                int prefix = Integer.parseInt(parts[1]);
                long hosts = subnetHelperService.calculateHostsPerSubnet(prefix);
                
                // Calculate IP range
                String networkAddr = parts[0];
                String[] segments = networkAddr.split("\\.");
                long networkLong = (Long.parseLong(segments[0]) << 24) |
                                  (Long.parseLong(segments[1]) << 16) |
                                  (Long.parseLong(segments[2]) << 8) |
                                  Long.parseLong(segments[3]);
                
                long firstUsable = networkLong + 1;
                long lastUsable = networkLong + (1L << (32 - prefix)) - 2;
                
                String range = String.format("%d.%d.%d.%d - %d.%d.%d.%d",
                    (firstUsable >> 24) & 0xff, (firstUsable >> 16) & 0xff,
                    (firstUsable >> 8) & 0xff, firstUsable & 0xff,
                    (lastUsable >> 24) & 0xff, (lastUsable >> 16) & 0xff,
                    (lastUsable >> 8) & 0xff, lastUsable & 0xff);

                subnetData.add(new SubnetRow(subnet, String.format("%,d", hosts), range));
            }

            showStatus(String.format("✓ Generated %d subnets successfully", result.getGeneratedSubnets().size()), Color.GREEN);
        });
    }

    @FXML
    private void clearAll() {
        ipAddressField.clear();
        cidrField.clear();
        numSubnetsField.clear();
        
        networkInfoArea.clear();
        explanationArea.clear();
        subnetData.clear();
        
        resultsContainer.setVisible(false);
        
        setValidationLabel(ipValidationLabel, "Enter an IP address", Color.GRAY);
        setValidationLabel(cidrValidationLabel, "Enter CIDR prefix (0-32)", Color.GRAY);
        setValidationLabel(subnetValidationLabel, "Enter number of subnets", Color.GRAY);
        
        statusLabel.setText("Ready");
        statusLabel.setTextFill(Color.BLACK);
    }

    @FXML
    private void loadExample() {
        ipAddressField.setText("10.0.0.0");
        cidrField.setText("16");
        numSubnetsField.setText("8");
        calculateSubnets();
    }

    private void showStatus(String message, Color color) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setTextFill(color);
        });
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        showStatus("Copied to clipboard: " + text, Color.BLUE);
    }

    /**
     * Inner class for table row data
     */
    public static class SubnetRow {
        private final SimpleStringProperty subnet;
        private final SimpleStringProperty hosts;
        private final SimpleStringProperty range;

        public SubnetRow(String subnet, String hosts, String range) {
            this.subnet = new SimpleStringProperty(subnet);
            this.hosts = new SimpleStringProperty(hosts);
            this.range = new SimpleStringProperty(range);
        }

        public String getSubnet() { return subnet.get(); }
        public SimpleStringProperty subnetProperty() { return subnet; }

        public String getHosts() { return hosts.get(); }
        public SimpleStringProperty hostsProperty() { return hosts; }

        public String getRange() { return range.get(); }
        public SimpleStringProperty rangeProperty() { return range; }
    }
} 