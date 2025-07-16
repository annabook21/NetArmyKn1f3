package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.CapturedPacket;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Packet capture service using tcpdump command-line tool.
 * This approach is more reliable than pcap4j on macOS.
 */
public class TcpdumpPacketCaptureService {
    private static final Logger logger = Logger.getLogger(TcpdumpPacketCaptureService.class.getName());
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObservableList<CapturedPacket> capturedPackets = FXCollections.observableArrayList();
    
    private Process tcpdumpProcess;
    private boolean capturing = false;
    private String currentInterface;
    private String captureFilter;
    private File captureFile;
    
    // Tcpdump command path
    private static final String TCPDUMP_PATH = "/usr/sbin/tcpdump";
    
    /**
     * Check if we can run tcpdump without sudo
     */
    private boolean canRunTcpdumpWithoutSudo() {
        try {
            // Check if user is in access_bpf group (ChmodBPF installed)
            ProcessBuilder groupCheck = new ProcessBuilder("groups", System.getProperty("user.name"));
            Process groupProcess = groupCheck.start();
            boolean finished = groupProcess.waitFor(2, TimeUnit.SECONDS);
            
            if (finished && groupProcess.exitValue() == 0) {
                // Read groups output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(groupProcess.getInputStream()))) {
                    String groupsLine = reader.readLine();
                    if (groupsLine != null && groupsLine.contains("access_bpf")) {
                        logger.info("User is in access_bpf group - packet capture without sudo enabled");
                        return true;
                    }
                }
            }
            
            // Fallback: Test actual packet capture access with a quick interface list
            ProcessBuilder pb = new ProcessBuilder(TCPDUMP_PATH, "-D");
            Process process = pb.start();
            finished = process.waitFor(2, TimeUnit.SECONDS);
            if (finished) {
                boolean canCapture = process.exitValue() == 0;
                if (canCapture) {
                    logger.info("tcpdump can list interfaces without sudo - packet capture enabled");
                } else {
                    logger.info("tcpdump requires sudo for packet capture");
                }
                return canCapture;
            }
            process.destroyForcibly();
            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking packet capture permissions", e);
            return false;
        }
    }
    
    /**
     * Build the tcpdump command with or without sudo
     */
    private List<String> buildTcpdumpCommand(String interfaceName, String outputFile, String filter) {
        List<String> command = new ArrayList<>();
        
        // Check if we need sudo (on macOS, tcpdump usually requires root)
        boolean needsSudo = !canRunTcpdumpWithoutSudo();
        
        if (needsSudo) {
            command.add("sudo");
            command.add("-n"); // Non-interactive sudo
        }
        
        command.add(TCPDUMP_PATH);
        command.add("-i");
        command.add(interfaceName);
        command.add("-w");
        command.add(outputFile);
        command.add("-U");          // Unbuffered output
        command.add("-s");
        command.add("65535");       // Snapshot length
        
        // Add filter if specified
        if (filter != null && !filter.trim().isEmpty()) {
            command.add(filter);
        }
        
        return command;
    }
    
    /**
     * Start packet capture on specified interface
     */
    public void startCapture(String interfaceName, String filter, String outputFile) {
        if (capturing) {
            stopCapture();
        }
        
        // Check if tcpdump exists and is executable
        File tcpdumpFile = new File(TCPDUMP_PATH);
        if (!tcpdumpFile.exists() || !tcpdumpFile.canExecute()) {
            throw new RuntimeException("tcpdump not found or not executable at " + TCPDUMP_PATH + 
                "\nTry installing with: brew install tcpdump (if using Homebrew)" +
                "\nOr check if tcpdump is in a different location");
        }
        
        this.currentInterface = interfaceName;
        this.captureFilter = filter;
        this.captureFile = new File(outputFile);
        
        executorService.submit(() -> {
            try {
                logger.info("Starting tcpdump capture on interface: " + interfaceName);
                
                // Build tcpdump command
                List<String> command = buildTcpdumpCommand(interfaceName, outputFile, filter);
                
                // Start the process
                tcpdumpProcess = new ProcessBuilder(command).start();
                capturing = true;
                
                logger.info("Tcpdump capture started successfully");
                
                // Monitor the process
                int exitCode = tcpdumpProcess.waitFor();
                
                if (exitCode == 0) {
                    logger.info("Tcpdump capture completed successfully");
                } else {
                    // Read error output
                    String errorOutput = readErrorOutput(tcpdumpProcess);
                    logger.warning("Tcpdump exit code: " + exitCode + ", Error: " + errorOutput);
                    
                    // Show user-friendly error guidance
                    Platform.runLater(() -> {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                        alert.setTitle("Packet Capture Failed");
                        alert.setHeaderText("Tcpdump returned exit code: " + exitCode);
                        alert.setContentText(getTcpdumpErrorGuidance(errorOutput));
                        alert.getDialogPane().setPrefWidth(600);
                        alert.show();
                    });
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error starting tcpdump capture", e);
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Packet Capture Error");
                    alert.setHeaderText("Failed to start packet capture");
                    alert.setContentText(getTcpdumpErrorGuidance(e.getMessage() != null ? e.getMessage() : "Unknown error"));
                    alert.getDialogPane().setPrefWidth(600);
                    alert.show();
                });
            } finally {
                capturing = false;
                tcpdumpProcess = null;
            }
        });
    }
    
    /**
     * Stop packet capture
     */
    public void stopCapture() {
        if (tcpdumpProcess != null && capturing) {
            logger.info("Stopping tcpdump capture");
            
            try {
                // Send SIGTERM to tcpdump process
                tcpdumpProcess.destroy();
                
                // Wait for process to complete
                tcpdumpProcess.waitFor();
                
                logger.info("Tcpdump capture stopped");
                
                // Parse captured packets from file
                parsePacketsFromFile();
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error stopping tcpdump capture", e);
            }
        }
        
        capturing = false;
        tcpdumpProcess = null;
    }
    
    /**
     * Parse packets from tcpdump output file
     */
    private void parsePacketsFromFile() {
        if (captureFile == null || !captureFile.exists()) {
            logger.warning("Capture file not found: " + captureFile);
            return;
        }
        
        executorService.submit(() -> {
            try {
                // Use tcpdump to read and parse the capture file
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(TCPDUMP_PATH,
                    "-r", captureFile.getAbsolutePath(),  // Read from file
                    "-n",                                  // Don't resolve addresses
                    "-t",                                  // Don't print timestamp
                    "-v");                                 // Verbose output
                
                Process readProcess = pb.start();
                
                // Read output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(readProcess.getInputStream()))) {
                    
                    String line;
                    int packetNumber = 1;
                    
                    while ((line = reader.readLine()) != null) {
                        CapturedPacket packet = parsePacketLine(line, packetNumber++);
                        if (packet != null) {
                            Platform.runLater(() -> capturedPackets.add(packet));
                        }
                    }
                }
                
                readProcess.waitFor();
                logger.info("Parsed " + capturedPackets.size() + " packets from capture file");
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing capture file", e);
            }
        });
    }
    
    /**
     * Parse a single packet line from tcpdump output
     */
    private CapturedPacket parsePacketLine(String line, int packetNumber) {
        try {
            // Parse tcpdump output format
            // Example: "IP 192.168.1.100.52345 > 192.168.1.1.53: UDP, length 32"
            
            String protocol = "Unknown";
            String source = "Unknown";
            String destination = "Unknown";
            int length = 0;
            String info = line;
            
            // Basic parsing - can be enhanced
            if (line.contains("IP ")) {
                protocol = "IP";
                
                // Extract source and destination
                String[] parts = line.split(" ");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equals("IP") && i + 1 < parts.length) {
                        String[] endpoints = parts[i + 1].split(" > ");
                        if (endpoints.length == 2) {
                            source = endpoints[0].split("\\.")[0] + "." + 
                                   endpoints[0].split("\\.")[1] + "." + 
                                   endpoints[0].split("\\.")[2] + "." + 
                                   endpoints[0].split("\\.")[3];
                            destination = endpoints[1].split("\\.")[0] + "." + 
                                        endpoints[1].split("\\.")[1] + "." + 
                                        endpoints[1].split("\\.")[2] + "." + 
                                        endpoints[1].split("\\.")[3];
                            break;
                        }
                    }
                }
                
                // Extract length
                if (line.contains("length ")) {
                    String lengthStr = line.substring(line.indexOf("length ") + 7);
                    String[] lengthParts = lengthStr.split(" ");
                    if (lengthParts.length > 0) {
                        try {
                            length = Integer.parseInt(lengthParts[0]);
                        } catch (NumberFormatException e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }
            
            return new CapturedPacket(
                packetNumber,
                LocalDateTime.now(),
                source,
                destination,
                0,                              // sourcePort - extracted from tcpdump if available
                0,                              // destinationPort - extracted from tcpdump if available
                protocol,
                length,
                new byte[0],                    // rawData - not available from tcpdump text output
                info
            );
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing packet line: " + line, e);
            return null;
        }
    }
    
    /**
     * Read error output from process
     */
    private String readErrorOutput(Process process) {
        StringBuilder error = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reading process error output", e);
        }
        return error.toString();
    }
    
    /**
     * Get user-friendly error message for common tcpdump issues
     */
    private String getTcpdumpErrorGuidance(String errorOutput) {
        StringBuilder guidance = new StringBuilder();
        guidance.append("Packet capture failed.\n\n");
        
        if (errorOutput.contains("Operation not permitted") || errorOutput.contains("Permission denied")) {
            // Check if user has ChmodBPF installed
            boolean hasChmodBPF = false;
            try {
                ProcessBuilder groupCheck = new ProcessBuilder("groups", System.getProperty("user.name"));
                Process groupProcess = groupCheck.start();
                groupProcess.waitFor(2, TimeUnit.SECONDS);
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(groupProcess.getInputStream()))) {
                    String groupsLine = reader.readLine();
                    hasChmodBPF = groupsLine != null && groupsLine.contains("access_bpf");
                }
            } catch (Exception e) {
                // Ignore
            }
            
            if (hasChmodBPF) {
                guidance.append("🎉 GOOD NEWS: ChmodBPF is installed and you're in the access_bpf group!\n\n");
                guidance.append("RESTART REQUIRED:\n");
                guidance.append("• Please restart this application to apply the new permissions\n");
                guidance.append("• After restart, real packet capture should work without sudo\n\n");
                guidance.append("ALTERNATIVE - SIMULATION MODE (works immediately):\n");
                guidance.append("• Click 'Enable Simulation Mode' for immediate packet analysis\n");
                guidance.append("• Generates realistic HTTP, DNS, TCP, UDP traffic for learning\n\n");
            } else {
                guidance.append("PERMISSION ISSUE - EASY SOLUTIONS:\n\n");
                guidance.append("🎯 SIMULATION MODE (RECOMMENDED):\n");
                guidance.append("• Click 'Enable Simulation Mode' below\n");
                guidance.append("• Generates realistic network traffic for analysis\n");
                guidance.append("• No permissions required, works immediately\n");
                guidance.append("• Perfect for learning packet analysis\n\n");
                
                guidance.append("🔧 REAL CAPTURE SETUP:\n");
                guidance.append("• Install Wireshark: brew install --cask wireshark\n");
                guidance.append("• Restart this application after installation\n");
                guidance.append("• Wireshark handles all permissions automatically\n\n");
            }
        }
        
        if (errorOutput.contains("No such device") || errorOutput.contains("SIOCGIFHWADDR")) {
            guidance.append("INTERFACE ISSUE:\n");
            guidance.append("• Check that the selected interface exists and is available\n");
            guidance.append("• Try refreshing the interface list\n");
            guidance.append("• Some virtual interfaces may not support packet capture\n\n");
        }
        
        if (errorOutput.contains("tcpdump: command not found")) {
            guidance.append("TCPDUMP NOT FOUND:\n");
            guidance.append("• tcpdump is not installed or not in PATH\n");
            guidance.append("• Install with: brew install tcpdump\n");
            guidance.append("• Or install Wireshark which includes tcpdump: brew install --cask wireshark\n\n");
        }
        
        guidance.append("Error details: ").append(errorOutput);
        
        return guidance.toString();
    }
    
    /**
     * Start simulation mode - generates sample network traffic for analysis
     */
    public void startSimulationMode() {
        if (capturing) {
            stopCapture();
        }
        
        this.currentInterface = "Simulation Mode";
        this.captureFilter = "";
        capturing = true;
        
        executorService.submit(() -> {
            logger.info("Starting packet capture simulation mode");
            
            try {
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Simulation Mode Active");
                    alert.setHeaderText("Packet Capture Simulation Started");
                    alert.setContentText("Generating sample network traffic for analysis.\n\n" +
                        "• No root privileges required\n" +
                        "• Simulates HTTP, DNS, TCP, UDP traffic\n" +
                        "• Perfect for learning packet analysis\n" +
                        "• All packet analyzer features work normally\n\n" +
                        "Click 'Stop Capture' to end simulation.");
                    alert.show();
                });
                
                generateSimulatedTraffic();
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in simulation mode", e);
            } finally {
                capturing = false;
            }
        });
    }
    
    /**
     * Generate realistic network traffic for analysis
     */
    private void generateSimulatedTraffic() {
        int packetId = 1;
        String[] sourceIPs = {"192.168.1.100", "192.168.1.101", "10.0.0.50", "172.16.1.20"};
        String[] destIPs = {"8.8.8.8", "1.1.1.1", "192.168.1.1", "172.217.14.110"};
        
        while (capturing) {
            try {
                // Generate different types of traffic
                if (packetId % 5 == 0) {
                    generateDNSPacket(packetId++, sourceIPs, destIPs);
                } else if (packetId % 3 == 0) {
                    generateHTTPPacket(packetId++, sourceIPs, destIPs);
                } else if (packetId % 7 == 0) {
                    generateARPPacket(packetId++, sourceIPs);
                } else {
                    generateTCPUDPPacket(packetId++, sourceIPs, destIPs);
                }
                
                // Realistic timing between packets
                Thread.sleep(500 + (int)(Math.random() * 2000)); // 0.5-2.5 seconds
                
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error generating simulated packet", e);
            }
        }
    }
    
    private void generateDNSPacket(int packetId, String[] sourceIPs, String[] destIPs) {
        String source = sourceIPs[(int)(Math.random() * sourceIPs.length)];
        String dest = "8.8.8.8"; // DNS server
        String[] domains = {"google.com", "github.com", "stackoverflow.com", "youtube.com", "amazon.com"};
        String domain = domains[(int)(Math.random() * domains.length)];
        
        CapturedPacket packet = new CapturedPacket(
            packetId,
            LocalDateTime.now(),
            source,
            dest,
            (int)(Math.random() * 60000 + 1024), // Random source port
            53, // DNS port
            "DNS",
            64,
            new byte[64],
            "DNS Query for " + domain
        );
        
        Platform.runLater(() -> capturedPackets.add(packet));
    }
    
    private void generateHTTPPacket(int packetId, String[] sourceIPs, String[] destIPs) {
        String source = sourceIPs[(int)(Math.random() * sourceIPs.length)];
        String dest = destIPs[(int)(Math.random() * destIPs.length)];
        String[] methods = {"GET", "POST", "PUT", "DELETE"};
        String method = methods[(int)(Math.random() * methods.length)];
        String[] paths = {"/", "/api/users", "/login", "/dashboard", "/images/logo.png"};
        String path = paths[(int)(Math.random() * paths.length)];
        
        CapturedPacket packet = new CapturedPacket(
            packetId,
            LocalDateTime.now(),
            source,
            dest,
            (int)(Math.random() * 60000 + 1024),
            80, // HTTP port
            "HTTP",
            (int)(Math.random() * 1400 + 100),
            new byte[512],
            "HTTP " + method + " " + path
        );
        
        packet.setHttpMethod(method);
        packet.setHttpUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        
        Platform.runLater(() -> capturedPackets.add(packet));
    }
    
    private void generateARPPacket(int packetId, String[] sourceIPs) {
        String source = sourceIPs[(int)(Math.random() * sourceIPs.length)];
        String dest = "192.168.1.1"; // Gateway
        
        CapturedPacket packet = new CapturedPacket(
            packetId,
            LocalDateTime.now(),
            source,
            dest,
            0, 0, // No ports for ARP
            "ARP",
            42,
            new byte[42],
            "ARP Request: Who has " + dest + "? Tell " + source
        );
        
        packet.setArpOperation("Request");
        
        Platform.runLater(() -> capturedPackets.add(packet));
    }
    
    private void generateTCPUDPPacket(int packetId, String[] sourceIPs, String[] destIPs) {
        String source = sourceIPs[(int)(Math.random() * sourceIPs.length)];
        String dest = destIPs[(int)(Math.random() * destIPs.length)];
        String protocol = Math.random() > 0.5 ? "TCP" : "UDP";
        int[] commonPorts = {22, 25, 110, 143, 443, 993, 995, 3306, 5432};
        int destPort = commonPorts[(int)(Math.random() * commonPorts.length)];
        
        CapturedPacket packet = new CapturedPacket(
            packetId,
            LocalDateTime.now(),
            source,
            dest,
            (int)(Math.random() * 60000 + 1024),
            destPort,
            protocol,
            (int)(Math.random() * 1000 + 50),
            new byte[256],
            protocol + " traffic to port " + destPort
        );
        
        Platform.runLater(() -> capturedPackets.add(packet));
    }
    
    /**
     * Get captured packets list
     */
    public ObservableList<CapturedPacket> getCapturedPackets() {
        return capturedPackets;
    }
    
    /**
     * Check if currently capturing
     */
    public boolean isCapturing() {
        return capturing;
    }
    
    /**
     * Get current interface
     */
    public String getCurrentInterface() {
        return currentInterface;
    }
    
    /**
     * Clear captured packets
     */
    public void clearPackets() {
        capturedPackets.clear();
    }
    
    /**
     * Export captured packets to file
     */
    public void exportPackets(File outputFile) {
        if (captureFile != null && captureFile.exists()) {
            try {
                // Copy the raw capture file
                java.nio.file.Files.copy(
                    captureFile.toPath(),
                    outputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                logger.info("Exported capture to: " + outputFile.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error exporting capture", e);
            }
        }
    }
    
    /**
     * Get available network interfaces
     */
    public String[] getAvailableInterfaces() {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            List<String> command = new ArrayList<>();
            
            // Check if we need sudo for listing interfaces
            boolean needsSudo = !canRunTcpdumpWithoutSudo();
            if (needsSudo) {
                command.add("sudo");
                command.add("-n");
            }
            command.add(TCPDUMP_PATH);
            command.add("-D");  // List interfaces
            
            pb.command(command);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                java.util.List<String> interfaces = new java.util.ArrayList<>();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    // Parse interface list format: "1.en0 [Up, Running]"
                    if (line.contains(".")) {
                        String[] parts = line.split("\\.");
                        if (parts.length > 1) {
                            String interfaceName = parts[1].split(" ")[0];
                            // Just use the clean interface name for tcpdump compatibility
                            interfaces.add(interfaceName);
                        }
                    }
                }
                
                // If no interfaces found via tcpdump, try Java's NetworkInterface
                if (interfaces.isEmpty()) {
                    logger.warning("No interfaces found via tcpdump, falling back to Java NetworkInterface");
                    return getJavaNetworkInterfaces();
                }
                
                return interfaces.toArray(new String[0]);
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting available interfaces via tcpdump", e);
            // Fallback to Java's NetworkInterface API
            return getJavaNetworkInterfaces();
        }
    }
    
    /**
     * Fallback method to get interfaces using Java's NetworkInterface API
     */
    private String[] getJavaNetworkInterfaces() {
        try {
            java.util.List<String> interfaces = new java.util.ArrayList<>();
            java.util.Enumeration<java.net.NetworkInterface> networkInterfaces = 
                java.net.NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    // Only add the interface name, not the display name, for tcpdump compatibility
                    interfaces.add(networkInterface.getName());
                }
            }
            
            // Add common macOS interfaces as fallback
            if (interfaces.isEmpty()) {
                interfaces.add("en0");
                interfaces.add("en1");
                interfaces.add("lo0");
            }
            
            return interfaces.toArray(new String[0]);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting network interfaces", e);
            // Final fallback
            return new String[]{"en0", "en1", "lo0"};
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        stopCapture();
        executorService.shutdown();
    }
} 