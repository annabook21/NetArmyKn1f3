package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.CapturedPacket;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
     * Start packet capture on specified interface
     */
    public void startCapture(String interfaceName, String filter, String outputFile) {
        if (capturing) {
            stopCapture();
        }
        
        this.currentInterface = interfaceName;
        this.captureFilter = filter;
        this.captureFile = new File(outputFile);
        
        executorService.submit(() -> {
            try {
                logger.info("Starting tcpdump capture on interface: " + interfaceName);
                
                // Build tcpdump command
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(TCPDUMP_PATH,
                    "-i", interfaceName,           // Interface
                    "-w", outputFile,              // Write to file
                    "-U",                          // Unbuffered output
                    "-s", "65535");                // Snapshot length
                
                // Add filter if specified
                if (filter != null && !filter.trim().isEmpty()) {
                    pb.command().add(filter);
                }
                
                // Start the process
                tcpdumpProcess = pb.start();
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
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error starting tcpdump capture", e);
                Platform.runLater(() -> {
                    // Notify UI of error
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
            pb.command(TCPDUMP_PATH, "-D");  // List interfaces
            
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                java.util.List<String> interfaces = new java.util.ArrayList<>();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    // Parse interface list format: "1.en0 [Up, Running]"
                    if (line.contains(".")) {
                        String interfaceName = line.split("\\.")[1].split(" ")[0];
                        interfaces.add(interfaceName);
                    }
                }
                
                return interfaces.toArray(new String[0]);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting available interfaces", e);
            return new String[]{"en0", "en1", "lo0"}; // Default interfaces
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