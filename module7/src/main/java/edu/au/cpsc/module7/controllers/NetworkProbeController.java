package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.networkprobe.ProbingYourNetworkClient;
import edu.au.cpsc.module7.networkprobe.PublicServerProbe;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class NetworkProbeController {

    @FXML
    private TextField serverAddressField;
    @FXML
    private TextField udpPortField;
    @FXML
    private TextField tcpPortField;
    @FXML
    private TextField durationField;
    @FXML
    private CheckBox runTcpCheckBox;
    @FXML
    private CheckBox runUdpCheckBox;
    @FXML
    private CheckBox runTracerouteCheckBox;
    @FXML
    private Button runProbeButton;
    @FXML
    private Button stopProbeButton;
    @FXML
    private TextArea resultsArea;
    @FXML
    private RadioButton customServerRadio;
    @FXML
    private RadioButton publicServerRadio;
    @FXML
    private ToggleGroup probeTypeGroup;
    @FXML
    private Label modeDescriptionLabel;
    @FXML
    private TitledPane advancedOptionsPane;
    @FXML
    private VBox localServerPanel;
    @FXML
    private Label serverStatusLabel;
    @FXML
    private Button startServerButton;
    @FXML
    private Button stopServerButton;
    @FXML
    private Button clearResultsButton;
    @FXML
    private Button saveResultsButton;

    private Task<Void> currentProbeTask;
    private Process localServerProcess;

    @FXML
    public void initialize() {
        // Set up radio button group
        probeTypeGroup = new ToggleGroup();
        customServerRadio.setToggleGroup(probeTypeGroup);
        publicServerRadio.setToggleGroup(probeTypeGroup);
        
        // Set default selection to public server (recommended)
        publicServerRadio.setSelected(true);
        
        // Add listeners to update UI based on probe type
        probeTypeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            updateProbeTypeUI();
        });
        
        // Initialize UI
        updateProbeTypeUI();
        
        // Add welcome message
        resultsArea.setText("🚀 Network Probe Ready!\n\n" +
                           "Quick Start:\n" +
                           "1. Enter a target (domain or IP)\n" +
                           "2. Click 'Run Network Probe'\n" +
                           "3. View comprehensive analysis results\n\n" +
                           "💡 Tip: Use the Quick buttons for common targets\n" +
                           "🌐 Public Server mode works with any website or server\n" +
                           "🔧 Custom Server mode requires local server setup\n\n" +
                           "Ready to probe networks! 🔍\n");
    }
    
    private void updateProbeTypeUI() {
        boolean isCustomServer = customServerRadio.isSelected();
        boolean isPublicServer = publicServerRadio.isSelected();
        
        // Update description based on mode
        if (isPublicServer) {
            modeDescriptionLabel.setText("🌐 Tests any public server using standard protocols (HTTP, DNS, SSH, etc.). No setup required!");
            modeDescriptionLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-size: 12px;");
        } else {
            modeDescriptionLabel.setText("🔧 Tests custom echo services. Requires ProbingYourNetworkServer running on target host.");
            modeDescriptionLabel.setStyle("-fx-text-fill: #f57c00; -fx-font-size: 12px;");
        }
        
        // Show/hide advanced options and local server panel based on mode
        advancedOptionsPane.setVisible(isCustomServer);
        advancedOptionsPane.setManaged(isCustomServer);
        localServerPanel.setVisible(isCustomServer);
        localServerPanel.setManaged(isCustomServer);
        
        // Enable/disable fields based on probe type
        udpPortField.setDisable(!isCustomServer);
        tcpPortField.setDisable(!isCustomServer);
        durationField.setDisable(!isCustomServer);
        runTcpCheckBox.setDisable(!isCustomServer);
        runUdpCheckBox.setDisable(!isCustomServer);
        
        if (isPublicServer) {
            // For public server probing, always enable traceroute
            runTracerouteCheckBox.setSelected(true);
        }
    }

    @FXML
    private void handleRunProbe() {
        resultsArea.clear();
        runProbeButton.setDisable(true);
        stopProbeButton.setDisable(false);
        
        String serverAddress = serverAddressField.getText().trim();
        boolean isPublicServer = publicServerRadio.isSelected();
        
        if (serverAddress.isEmpty()) {
            resultsArea.appendText("❌ Please enter a server address\n");
            runProbeButton.setDisable(false);
            stopProbeButton.setDisable(true);
            return;
        }
        
        // Add timestamp
        resultsArea.appendText("🕐 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
        resultsArea.appendText("🎯 Target: " + serverAddress + "\n");
        resultsArea.appendText("🔧 Mode: " + (isPublicServer ? "Public Server" : "Custom Server") + "\n\n");
        
        if (isPublicServer) {
            runPublicServerProbe(serverAddress);
        } else {
            runCustomServerProbe(serverAddress);
        }
    }
    
    private void runPublicServerProbe(String serverAddress) {
        currentProbeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("🔍 Running public server probe for: " + serverAddress + "\n");
                
                try {
                    // Use Maven to run the PublicServerProbe to ensure proper classpath
                    ProcessBuilder pb = new ProcessBuilder();
                    pb.command("mvn", "exec:java@run-public-probe", 
                              "-Dexec.args=--host " + serverAddress + " -v");
                    pb.directory(new File(System.getProperty("user.dir")));
                    pb.redirectErrorStream(true);
                    
                    Process process = pb.start();
                    
                    // Read the process output in real-time
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            final String outputLine = line;
                            Platform.runLater(() -> resultsArea.appendText(outputLine + "\n"));
                        }
                    }
                    
                    // If cancelled, destroy the process
                    if (isCancelled()) {
                        process.destroyForcibly();
                        Platform.runLater(() -> resultsArea.appendText("\n⏹️ Probe cancelled by user\n"));
                        return null;
                    }
                    
                    // Wait for process completion
                    int exitCode = process.waitFor();
                    
                    Platform.runLater(() -> {
                        if (exitCode == 0) {
                            resultsArea.appendText("\n✅ Public server probe completed successfully!\n");
                            resultsArea.appendText("💡 Tip: Use 'Save' button to export these results\n");
                        } else {
                            resultsArea.appendText("\n❌ Public server probe completed with errors (exit code: " + exitCode + ")\n");
                        }
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        resultsArea.appendText("❌ Error running public server probe: " + e.getMessage() + "\n");
                    });
                }
                
                return null;
            }
        };
        
        startProbeTask();
    }
    
    private void runCustomServerProbe(String serverAddress) {
        // Check if we're testing a host that likely won't have the services
        boolean isPublicDNS = serverAddress.equals("8.8.8.8") || serverAddress.equals("1.1.1.1") || 
                             serverAddress.equals("8.8.4.4") || serverAddress.equals("1.0.0.1");
        
        if (isPublicDNS && (runTcpCheckBox.isSelected() || runUdpCheckBox.isSelected())) {
            resultsArea.appendText("⚠️ WARNING: Testing public DNS servers for custom ports will likely fail!\n");
            resultsArea.appendText("💡 Consider switching to 'Public Server' mode for better results.\n\n");
        }

        currentProbeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<String> args = buildArguments();
                updateMessage("🔍 Running custom server test with args: " + String.join(" ", args) + "\n");
                
                // Create a separate process to run the client so we can terminate it
                ProcessBuilder pb = new ProcessBuilder();
                pb.command("mvn", "exec:java@run-client", "-Dexec.args=" + String.join(" ", args));
                pb.redirectErrorStream(true);
                
                try {
                    Process process = pb.start();
                    
                    // Read the process output in real-time
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            final String outputLine = line;
                            Platform.runLater(() -> resultsArea.appendText(outputLine + "\n"));
                        }
                    }
                    
                    // If cancelled, destroy the process
                    if (isCancelled()) {
                        process.destroyForcibly();
                        Platform.runLater(() -> resultsArea.appendText("\n⏹️ Test cancelled by user\n"));
                        return null;
                    }
                    
                    // Wait for process completion
                    int exitCode = process.waitFor();
                    
                    Platform.runLater(() -> {
                        if (exitCode == 0) {
                            resultsArea.appendText("\n✅ Custom server test completed successfully!\n");
                        } else {
                            resultsArea.appendText("\n❌ Custom server test completed with errors (exit code: " + exitCode + ")\n");
                            if (isPublicDNS) {
                                resultsArea.appendText("💡 TIP: For public servers, try 'Public Server' mode instead.\n");
                            } else {
                                resultsArea.appendText("💡 TIP: Make sure the ProbingYourNetworkServer is running on the target host.\n");
                                resultsArea.appendText("🚀 Use 'Start Local Server' for local testing.\n");
                            }
                        }
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        resultsArea.appendText("❌ Error running custom server test: " + e.getMessage() + "\n");
                    });
                }
                
                return null;
            }
        };
        
        startProbeTask();
    }
    
    private void startProbeTask() {
        currentProbeTask.setOnSucceeded(e -> {
            runProbeButton.setDisable(false);
            stopProbeButton.setDisable(true);
        });

        currentProbeTask.setOnFailed(e -> {
            runProbeButton.setDisable(false);
            stopProbeButton.setDisable(true);
            Throwable exception = currentProbeTask.getException();
            resultsArea.appendText("❌ Test failed: " + exception.getMessage() + "\n");
        });

        currentProbeTask.setOnCancelled(e -> {
            runProbeButton.setDisable(false);
            stopProbeButton.setDisable(true);
        });

        // Run the task in a background thread
        Thread probeThread = new Thread(currentProbeTask);
        probeThread.setDaemon(true);
        probeThread.start();
    }

    @FXML
    private void handleStopProbe() {
        if (currentProbeTask != null && !currentProbeTask.isDone()) {
            currentProbeTask.cancel(true);
            runProbeButton.setDisable(false);
            stopProbeButton.setDisable(true);
            resultsArea.appendText("\n⏹️ Test stopped by user.\n");
        }
    }
    
    @FXML
    private void handleTestLocalServer() {
        // Switch to custom server mode
        customServerRadio.setSelected(true);
        updateProbeTypeUI();
        
        // Set up for local server testing
        serverAddressField.setText("localhost");
        udpPortField.setText("5001");
        tcpPortField.setText("5002");
        durationField.setText("5");
        runTcpCheckBox.setSelected(true);
        runUdpCheckBox.setSelected(false);
        runTracerouteCheckBox.setSelected(false);
        
        resultsArea.appendText("🏠 Local Server Test Configuration Applied\n");
        resultsArea.appendText("💡 Make sure to start the local server first!\n\n");
    }
    
    @FXML
    private void handleTestGoogleDNS() {
        // Switch to public server mode
        publicServerRadio.setSelected(true);
        updateProbeTypeUI();
        
        // Set up for Google DNS testing
        serverAddressField.setText("8.8.8.8");
        
        resultsArea.appendText("🌐 Google DNS Test Configuration Applied\n");
        resultsArea.appendText("This will test Google's public DNS server (8.8.8.8)\n\n");
    }
    
    @FXML
    private void handleTestPublicWebsite() {
        // Switch to public server mode
        publicServerRadio.setSelected(true);
        updateProbeTypeUI();
        
        // Set up for website testing
        serverAddressField.setText("github.com");
        
        resultsArea.appendText("😈 GitHub Test Configuration Applied\n");
        resultsArea.appendText("Time to probe the devilish depths of GitHub's network! 👹\n\n");
    }
    
    @FXML
    private void handleStartLocalServer() {
        if (localServerProcess != null && localServerProcess.isAlive()) {
            resultsArea.appendText("⚠️ Local server is already running!\n");
            return;
        }
        
        resultsArea.appendText("🚀 Starting Local Server...\n");
        startServerButton.setDisable(true);
        
        // Start server in background task
        Task<Void> serverTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    ProcessBuilder pb = new ProcessBuilder();
                    pb.command("mvn", "exec:java@run-server");
                    pb.directory(new File(System.getProperty("user.dir")));
                    
                    localServerProcess = pb.start();
                    
                    // Give it a moment to start
                    Thread.sleep(2000);
                    
                    Platform.runLater(() -> {
                        if (localServerProcess.isAlive()) {
                            serverStatusLabel.setText("✅ Running");
                            serverStatusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                            stopServerButton.setDisable(false);
                            resultsArea.appendText("✅ Local server started successfully!\n");
                            resultsArea.appendText("📡 Server running on ports: UDP 5001, TCP 5002\n");
                            resultsArea.appendText("🏠 You can now test against 'localhost'\n\n");
                        } else {
                            serverStatusLabel.setText("❌ Failed to Start");
                            serverStatusLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                            resultsArea.appendText("❌ Failed to start local server\n");
                            resultsArea.appendText("💡 Try starting manually: mvn exec:java@run-server\n\n");
                        }
                        startServerButton.setDisable(false);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        serverStatusLabel.setText("❌ Error");
                        serverStatusLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        resultsArea.appendText("❌ Error starting server: " + e.getMessage() + "\n\n");
                        startServerButton.setDisable(false);
                    });
                }
                
                return null;
            }
        };
        
        Thread serverThread = new Thread(serverTask);
        serverThread.setDaemon(true);
        serverThread.start();
    }
    
    @FXML
    private void handleStopLocalServer() {
        if (localServerProcess != null && localServerProcess.isAlive()) {
            localServerProcess.destroyForcibly();
            serverStatusLabel.setText("⏹️ Stopped");
            serverStatusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-weight: bold;");
            stopServerButton.setDisable(true);
            resultsArea.appendText("⏹️ Local server stopped\n\n");
        }
    }
    
    @FXML
    private void handleClearResults() {
        resultsArea.clear();
        resultsArea.appendText("🧹 Results cleared\n\n");
    }
    
    @FXML
    private void handleSaveResults() {
        if (resultsArea.getText().trim().isEmpty()) {
            resultsArea.appendText("⚠️ No results to save\n");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Network Probe Results");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        // Set default filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        fileChooser.setInitialFileName("network_probe_results_" + timestamp + ".txt");
        
        File file = fileChooser.showSaveDialog(saveResultsButton.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Network Probe Results\n");
                writer.write("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
                writer.write("Target: " + serverAddressField.getText() + "\n");
                writer.write("Mode: " + (publicServerRadio.isSelected() ? "Public Server" : "Custom Server") + "\n\n");
                writer.write(resultsArea.getText());
                
                resultsArea.appendText("💾 Results saved to: " + file.getAbsolutePath() + "\n");
            } catch (IOException e) {
                resultsArea.appendText("❌ Error saving results: " + e.getMessage() + "\n");
            }
        }
    }

    private List<String> buildArguments() {
        List<String> args = new ArrayList<>();
        args.add("-s");
        args.add(serverAddressField.getText());
        args.add("-u");
        args.add(udpPortField.getText());
        args.add("-t");
        args.add(tcpPortField.getText());
        args.add("-d");
        args.add(durationField.getText());

        if (runTcpCheckBox.isSelected() && !runUdpCheckBox.isSelected()) {
            args.add("-p");
            args.add("tcp");
        } else if (!runTcpCheckBox.isSelected() && runUdpCheckBox.isSelected()) {
            args.add("-p");
            args.add("udp");
        }
        
        if (!runTracerouteCheckBox.isSelected()) {
            args.add("--no-traceroute");
        }
        
        return args;
    }
} 