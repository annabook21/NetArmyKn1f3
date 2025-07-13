package edu.au.cpsc.module7.controllers;

import com.google.inject.Inject;
import edu.au.cpsc.module7.models.QueryResult;
import edu.au.cpsc.module7.networkprobe.ProbingYourNetworkClient;
import edu.au.cpsc.module7.networkprobe.PublicServerProbe;
import edu.au.cpsc.module7.services.SettingsService;
import edu.au.cpsc.module7.services.SystemToolsManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class NetworkProbeController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(NetworkProbeController.class.getName());

    @FXML private TextField serverAddressField;
    @FXML private RadioButton publicServerRadio;
    @FXML private RadioButton customServerRadio;
    @FXML private Label modeDescriptionLabel;
    @FXML private TitledPane advancedOptionsPane;
    @FXML private TextField udpPortField;
    @FXML private TextField tcpPortField;
    @FXML private TextField durationField;
    @FXML private CheckBox runTcpCheckBox;
    @FXML private CheckBox runUdpCheckBox;
    @FXML private ChoiceBox<String> pathAnalysisChoiceBox;
    @FXML private VBox localServerPanel;
    @FXML private Label serverStatusLabel;
    @FXML private Button startServerButton;
    @FXML private Button stopServerButton;
    @FXML private Button runProbeButton;
    @FXML private Button stopProbeButton;
    @FXML private TextArea resultsArea;
    @FXML private Button clearResultsButton;
    @FXML private Button saveResultsButton;

    private final SettingsService settingsService;
    private final SystemToolsManager systemToolsManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Task<Void> currentProbeTask;
    private Process localServerProcess;

    @Inject
    public NetworkProbeController(SettingsService settingsService, SystemToolsManager systemToolsManager) {
        this.settingsService = settingsService;
        this.systemToolsManager = systemToolsManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ToggleGroup group = new ToggleGroup();
        publicServerRadio.setToggleGroup(group);
        customServerRadio.setToggleGroup(group);
        publicServerRadio.setSelected(true);

        // Initialize ChoiceBox for path analysis
        pathAnalysisChoiceBox.getItems().addAll("None", "Traceroute", "MTR");
        pathAnalysisChoiceBox.setValue("Traceroute");

        // Add listener to update UI based on selected probe type
        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateProbeTypeUI();
            }
        });
        updateProbeTypeUI();

        // Add welcome message
        resultsArea.setText("🚀 Network Probe Ready!\n\n" +
                "Quick Start:\n" +
                "1. Enter a target (domain or IP)\n" +
                "2. Select path analysis tool (Traceroute/MTR)\n" +
                "3. Click 'Run Network Probe'\n" +
                "4. View comprehensive analysis results\n\n" +
                "💡 Tip: Use the Quick buttons for common targets\n" +
                "🌐 Public Server mode works with any website or server\n" +
                "🔧 Custom Server mode requires local server setup\n\n" +
                "Ready to probe networks! 🔍\n");
    }

    private void updateProbeTypeUI() {
        boolean isCustom = customServerRadio.isSelected();
        
        // Advanced options are for public mode
        advancedOptionsPane.setDisable(isCustom);
        
        // Local server panel is for custom mode
        localServerPanel.setDisable(!isCustom);
        
        // Update description
        if (isCustom) {
            modeDescriptionLabel.setText("🔧 Tests custom echo services. Requires ProbingYourNetworkServer running on target host.");
            modeDescriptionLabel.setStyle("-fx-text-fill: #f57c00; -fx-font-size: 12px;");
        } else {
            modeDescriptionLabel.setText("🌐 Tests any public server using standard protocols (HTTP, DNS, SSH, etc.). No setup required!");
            modeDescriptionLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-size: 12px;");
        }
    }

    @FXML
    private void handleRunProbe() {
        String target = serverAddressField.getText();
        if (target == null || target.trim().isEmpty()) {
            resultsArea.setText("Error: Target address cannot be empty.");
            return;
        }

        runProbeButton.setDisable(true);
        stopProbeButton.setDisable(false);
        resultsArea.clear();
        appendResults("Starting probe for " + target + "...\n");

        boolean isPublicServer = publicServerRadio.isSelected();

        if (isPublicServer) {
            runPublicServerProbe(target);
        } else {
            runCustomServerProbe(target);
        }
    }

    private void runPublicServerProbe(String target) {
        currentProbeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Path Analysis
                String analysisType = pathAnalysisChoiceBox.getValue();
                if (!"None".equals(analysisType)) {
                    updateMessage("Running " + analysisType + "...");
                    Platform.runLater(() -> appendResults("--- Running " + analysisType + " ---\n"));

                    QueryResult result;
                    if ("Traceroute".equals(analysisType)) {
                        result = systemToolsManager.executeTraceroute(target);
                    } else { // MTR
                        result = systemToolsManager.executeMtr(target);
                    }

                    if (result.isSuccess()) {
                        Platform.runLater(() -> appendResults(result.getOutput() + "\n"));
                    } else {
                        Platform.runLater(() -> appendResults("Error: " + result.getErrorOutput() + "\n"));
                    }
                }

                // Run PublicServerProbe
                try {
                    ProcessBuilder pb = new ProcessBuilder();
                    pb.command("mvn", "exec:java@run-public-probe", 
                              "-Dexec.args=--host " + target + " -v");
                    pb.directory(new File(System.getProperty("user.dir")));
                    pb.redirectErrorStream(true);
                    
                    Process process = pb.start();
                    
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            final String outputLine = line;
                            Platform.runLater(() -> appendResults(outputLine + "\n"));
                        }
                    }
                    
                    if (isCancelled()) {
                        process.destroyForcibly();
                        Platform.runLater(() -> appendResults("\n⏹️ Probe cancelled by user\n"));
                        return null;
                    }
                    
                    int exitCode = process.waitFor();
                    
                    Platform.runLater(() -> {
                        if (exitCode == 0) {
                            appendResults("\n✅ Public server probe completed successfully!\n");
                        } else {
                            appendResults("\n❌ Public server probe completed with errors (exit code: " + exitCode + ")\n");
                        }
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendResults("❌ Error running public server probe: " + e.getMessage() + "\n");
                    });
                }

                updateMessage("Probe finished.");
                return null;
            }
        };

        currentProbeTask.setOnSucceeded(e -> onProbeFinished());
        currentProbeTask.setOnFailed(e -> {
            appendResults("\nPROBE FAILED\n");
            Throwable ex = e.getSource().getException();
            if (ex != null) {
                appendResults(ex.getMessage());
            }
            onProbeFinished();
        });
        currentProbeTask.setOnCancelled(e -> onProbeFinished());

        executorService.submit(currentProbeTask);
    }

    private void runCustomServerProbe(String serverAddress) {
        // Check if we're testing a host that likely won't have the services
        boolean isPublicDNS = serverAddress.equals("8.8.8.8") || serverAddress.equals("1.1.1.1") || 
                             serverAddress.equals("8.8.4.4") || serverAddress.equals("1.0.0.1");
        
        if (isPublicDNS && (runTcpCheckBox.isSelected() || runUdpCheckBox.isSelected())) {
            appendResults("⚠️ WARNING: Testing public DNS servers for custom ports will likely fail!\n");
            appendResults("💡 Consider switching to 'Public Server' mode for better results.\n\n");
        }

        currentProbeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<String> args = buildArguments();
                updateMessage("🔍 Running custom server test with args: " + String.join(" ", args) + "\n");
                
                ProcessBuilder pb = new ProcessBuilder();
                pb.command("mvn", "exec:java@run-client", "-Dexec.args=" + String.join(" ", args));
                pb.redirectErrorStream(true);
                
                try {
                    Process process = pb.start();
                    
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            final String outputLine = line;
                            Platform.runLater(() -> appendResults(outputLine + "\n"));
                        }
                    }
                    
                    if (isCancelled()) {
                        process.destroyForcibly();
                        Platform.runLater(() -> appendResults("\n⏹️ Test cancelled by user\n"));
                        return null;
                    }
                    
                    int exitCode = process.waitFor();
                    
                    Platform.runLater(() -> {
                        if (exitCode == 0) {
                            appendResults("\n✅ Custom server test completed successfully!\n");
                        } else {
                            appendResults("\n❌ Custom server test completed with errors (exit code: " + exitCode + ")\n");
                            if (isPublicDNS) {
                                appendResults("💡 TIP: For public servers, try 'Public Server' mode instead.\n");
                            } else {
                                appendResults("💡 TIP: Make sure the ProbingYourNetworkServer is running on the target host.\n");
                                appendResults("🚀 Use 'Start Local Server' for local testing.\n");
                            }
                        }
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendResults("❌ Error running custom server test: " + e.getMessage() + "\n");
                    });
                }
                
                return null;
            }
        };
        
        currentProbeTask.setOnSucceeded(e -> onProbeFinished());
        currentProbeTask.setOnFailed(e -> {
            appendResults("\nTEST FAILED\n");
            Throwable ex = e.getSource().getException();
            if (ex != null) {
                appendResults(ex.getMessage());
            }
            onProbeFinished();
        });
        currentProbeTask.setOnCancelled(e -> onProbeFinished());

        executorService.submit(currentProbeTask);
    }

    @FXML
    private void handleStopProbe() {
        if (currentProbeTask != null && currentProbeTask.isRunning()) {
            currentProbeTask.cancel(true);
            appendResults("\n--- Probe Cancelled by User ---\n");
        }
        onProbeFinished();
    }

    private void onProbeFinished() {
        runProbeButton.setDisable(false);
        stopProbeButton.setDisable(true);
    }

    private void appendResults(String text) {
        Platform.runLater(() -> resultsArea.appendText(text));
    }

    @FXML private void handleTestGoogleDNS() { 
        publicServerRadio.setSelected(true);
        updateProbeTypeUI();
        serverAddressField.setText("8.8.8.8"); 
        appendResults("🌐 Google DNS Test Configuration Applied\n");
    }
    
    @FXML private void handleTestPublicWebsite() { 
        publicServerRadio.setSelected(true);
        updateProbeTypeUI();
        serverAddressField.setText("github.com"); 
        appendResults("😈 GitHub Test Configuration Applied\n");
    }
    
    @FXML private void handleTestLocalServer() { 
        customServerRadio.setSelected(true);
        updateProbeTypeUI();
        serverAddressField.setText("localhost");
        udpPortField.setText("5001");
        tcpPortField.setText("5002");
        durationField.setText("5");
        runTcpCheckBox.setSelected(true);
        runUdpCheckBox.setSelected(false);
        appendResults("🏠 Local Server Test Configuration Applied\n");
    }

    @FXML
    private void handleStartLocalServer() {
        if (localServerProcess != null && localServerProcess.isAlive()) {
            appendResults("⚠️ Local server is already running!\n");
            return;
        }
        
        appendResults("🚀 Starting Local Server...\n");
        startServerButton.setDisable(true);
        
        Task<Void> serverTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    ProcessBuilder pb = new ProcessBuilder();
                    pb.command("mvn", "exec:java@run-server");
                    pb.directory(new File(System.getProperty("user.dir")));
                    
                    localServerProcess = pb.start();
                    Thread.sleep(2000);
                    
                    Platform.runLater(() -> {
                        if (localServerProcess.isAlive()) {
                            serverStatusLabel.setText("✅ Running");
                            serverStatusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                            stopServerButton.setDisable(false);
                            appendResults("✅ Local server started successfully!\n");
                        } else {
                            serverStatusLabel.setText("❌ Failed to Start");
                            serverStatusLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                            appendResults("❌ Failed to start local server\n");
                        }
                        startServerButton.setDisable(false);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        serverStatusLabel.setText("❌ Error");
                        serverStatusLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        appendResults("❌ Error starting server: " + e.getMessage() + "\n");
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
            appendResults("⏹️ Local server stopped\n\n");
        }
    }
    
    @FXML private void handleClearResults() { resultsArea.clear(); }
    
    @FXML
    private void handleSaveResults() {
        if (resultsArea.getText().trim().isEmpty()) {
            appendResults("⚠️ No results to save\n");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Network Probe Results");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
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
                
                appendResults("💾 Results saved to: " + file.getAbsolutePath() + "\n");
            } catch (IOException e) {
                appendResults("❌ Error saving results: " + e.getMessage() + "\n");
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
        
        return args;
    }
} 