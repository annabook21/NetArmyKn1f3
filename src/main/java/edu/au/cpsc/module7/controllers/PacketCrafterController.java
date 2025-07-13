
package edu.au.cpsc.module7.controllers;

import com.google.inject.Inject;
import edu.au.cpsc.module7.models.QueryResult;
import edu.au.cpsc.module7.services.SystemToolsManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

public class PacketCrafterController {

    @FXML private TextField targetHostField;
    @FXML private ChoiceBox<String> scanTypeChoiceBox;
    @FXML private TextField portField;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private TextField additionalFlagsField;
    @FXML private Button executeButton;
    @FXML private TextArea outputArea;

    @Inject
    private SystemToolsManager toolsManager;

    @FXML
    public void initialize() {
        // Populate the scan type choice box
        scanTypeChoiceBox.setItems(FXCollections.observableArrayList(
                "Default (TCP)", "SYN", "ACK", "FIN", "RST", "PUSH", "URG", "XMAS", "NULL", "Scan"
        ));
        scanTypeChoiceBox.getSelectionModel().selectFirst();

        // Set a default value for the spinner
        countSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
    }

    @FXML
    private void handleExecute() {
        String target = targetHostField.getText();
        if (target == null || target.isBlank()) {
            outputArea.setText("Error: Target Host cannot be empty.");
            return;
        }

        List<String> args = buildHping3Args(target);

        // Disable button to prevent multiple executions
        executeButton.setDisable(true);
        outputArea.setText("Executing hping3 command...\n");

        // Run the command in a background thread
        Thread executionThread = new Thread(() -> {
            QueryResult result = toolsManager.executeHping3(args);

            Platform.runLater(() -> {
                outputArea.setText(formatResult(result, args));
                executeButton.setDisable(false);
            });
        });

        executionThread.setDaemon(true);
        executionThread.start();
    }

    private List<String> buildHping3Args(String target) {
        List<String> args = new ArrayList<>();

        // Add scan type
        String scanType = scanTypeChoiceBox.getValue();
        switch (scanType) {
            case "SYN": args.add("--syn"); break;
            case "ACK": args.add("--ack"); break;
            case "FIN": args.add("--fin"); break;
            case "RST": args.add("--rst"); break;
            case "PUSH": args.add("--push"); break;
            case "URG": args.add("--urg"); break;
            case "XMAS": args.add("--xmas"); break;
            case "NULL": args.add("--null"); break;
            case "Scan": args.add("--scan"); break;
            // "Default (TCP)" requires no flag
        }

        // Add port if specified
        String port = portField.getText();
        if (port != null && !port.isBlank()) {
            args.add("--port");
            args.add(port);
        }

        // Add count
        args.add("--count");
        args.add(String.valueOf(countSpinner.getValue()));

        // Add any additional flags
        String additionalFlags = additionalFlagsField.getText();
        if (additionalFlags != null && !additionalFlags.isBlank()) {
            // Simple split by space; a more robust solution might handle quoted arguments
            args.addAll(List.of(additionalFlags.split("\\s+")));
        }

        // Add the target host last
        args.add(target);

        return args;
    }

    private String formatResult(QueryResult result, List<String> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Command ---\n");
        sb.append("hping3 ").append(String.join(" ", args)).append("\n\n");
        sb.append("--- Output ---\n");
        if (result.getOutput() != null && !result.getOutput().isBlank()) {
            sb.append(result.getOutput()).append("\n");
        } else {
            sb.append("No standard output.\n");
        }
        if (result.getErrorOutput() != null && !result.getErrorOutput().isBlank()) {
            sb.append("\n--- Error ---\n");
            sb.append(result.getErrorOutput()).append("\n");
        }
        sb.append("\n--- Exit Code: ").append(result.getExitCode()).append(" ---");
        return sb.toString();
    }
} 