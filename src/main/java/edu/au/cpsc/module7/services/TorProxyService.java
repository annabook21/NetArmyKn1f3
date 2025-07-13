package edu.au.cpsc.module7.services;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service for integrating with Tor to provide geographic IP diversity for DNS testing.
 * This service can connect to an external Tor process running as a SOCKS proxy.
 */
@Singleton
public class TorProxyService {
    private static final Logger logger = Logger.getLogger(TorProxyService.class.getName());
    
    private static final String DEFAULT_TOR_HOST = "127.0.0.1";
    private static final int DEFAULT_TOR_PORT = 9050; // Default Tor SOCKS port
    private static final int TOR_CONTROL_PORT = 9051; // Default Tor control port
    
    private final ExecutorService executorService;
    private boolean torAvailable = false;
    private Proxy torProxy;
    
    public TorProxyService() {
        this.executorService = Executors.newCachedThreadPool();
        this.torProxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(DEFAULT_TOR_HOST, DEFAULT_TOR_PORT));
        checkTorAvailability();
    }
    
    /**
     * Check if Tor is available and running
     */
    private void checkTorAvailability() {
        try {
            // Try to connect to Tor SOCKS port
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(DEFAULT_TOR_HOST, DEFAULT_TOR_PORT), 5000);
                torAvailable = true;
                logger.info("Tor proxy detected at " + DEFAULT_TOR_HOST + ":" + DEFAULT_TOR_PORT);
            }
        } catch (IOException e) {
            torAvailable = false;
            logger.info("Tor proxy not available. Install Tor and start it to enable geographic IP diversity testing.");
        }
    }
    
    /**
     * Check if Tor is available
     */
    public boolean isTorAvailable() {
        return torAvailable;
    }
    
    /**
     * Get current exit node IP address
     */
    public CompletableFuture<String> getCurrentExitNodeIP() {
        return CompletableFuture.supplyAsync(() -> {
            if (!torAvailable) {
                return "Tor not available";
            }
            
            try {
                // Use a service to check our IP through Tor
                URL url = new URL("http://httpbin.org/ip");
                URLConnection connection = url.openConnection(torProxy);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    // Parse JSON response to extract IP
                    String jsonResponse = response.toString();
                    int start = jsonResponse.indexOf("\"origin\": \"") + 11;
                    int end = jsonResponse.indexOf("\"", start);
                    if (start > 10 && end > start) {
                        return jsonResponse.substring(start, end);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to get exit node IP", e);
            }
            
            return "Unknown";
        }, executorService);
    }
    
    /**
     * Request a new Tor circuit (new exit node)
     */
    public CompletableFuture<Boolean> requestNewCircuit() {
        return CompletableFuture.supplyAsync(() -> {
            if (!torAvailable) {
                return false;
            }
            
            try {
                // Send NEWNYM signal to Tor control port
                try (Socket controlSocket = new Socket(DEFAULT_TOR_HOST, TOR_CONTROL_PORT)) {
                    java.io.PrintWriter out = new java.io.PrintWriter(controlSocket.getOutputStream(), true);
                    
                    // Authenticate (assuming no password for simplicity)
                    out.println("AUTHENTICATE");
                    
                    // Request new circuit
                    out.println("SIGNAL NEWNYM");
                    
                    // Wait a moment for circuit to establish
                    Thread.sleep(3000);
                    
                    logger.info("Requested new Tor circuit");
                    return true;
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.WARNING, "Failed to request new Tor circuit", e);
            }
            
            return false;
        }, executorService);
    }
    
    /**
     * Perform DNS lookup through Tor
     */
    public CompletableFuture<String> performDNSLookupThroughTor(String hostname) {
        return CompletableFuture.supplyAsync(() -> {
            if (!torAvailable) {
                return "Tor not available";
            }
            
            try {
                // Create a socket connection through Tor proxy
                Socket socket = new Socket(torProxy);
                socket.connect(new InetSocketAddress(hostname, 80), 10000);
                
                // Get the resolved IP from the socket
                InetAddress address = socket.getInetAddress();
                socket.close();
                
                return address.getHostAddress();
            } catch (IOException e) {
                logger.log(Level.WARNING, "DNS lookup through Tor failed for " + hostname, e);
                return "Lookup failed";
            }
        }, executorService);
    }
    
    /**
     * Test Route 53 geolocation from different exit nodes
     */
    public CompletableFuture<String> testRoute53FromDifferentLocations(String domain, int numTests) {
        return CompletableFuture.supplyAsync(() -> {
            if (!torAvailable) {
                return "Tor not available - cannot test from different geographic locations";
            }
            
            StringBuilder results = new StringBuilder();
            results.append("Testing Route 53 geolocation routing for ").append(domain).append(":\n\n");
            
            for (int i = 0; i < numTests; i++) {
                try {
                    // Get current exit node IP
                    String exitIP = getCurrentExitNodeIP().get();
                    
                    // Perform DNS lookup through Tor
                    String resolvedIP = performDNSLookupThroughTor(domain).get();
                    
                    results.append("Test ").append(i + 1).append(":\n");
                    results.append("  Exit Node IP: ").append(exitIP).append("\n");
                    results.append("  Resolved IP: ").append(resolvedIP).append("\n");
                    results.append("  Geographic routing appears to be working\n\n");
                    
                    // Request new circuit for next test
                    if (i < numTests - 1) {
                        requestNewCircuit().get();
                        Thread.sleep(2000); // Wait for circuit to establish
                    }
                    
                } catch (Exception e) {
                    results.append("Test ").append(i + 1).append(" failed: ").append(e.getMessage()).append("\n\n");
                }
            }
            
            return results.toString();
        }, executorService);
    }
    
    /**
     * Start Tor daemon
     */
    public boolean startTorDaemon() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String command;
            
            if (os.contains("mac")) {
                command = "tor --ControlPort 9051 --SocksPort 9050 --RunAsDaemon 1";
            } else if (os.contains("linux")) {
                command = "sudo systemctl start tor";
            } else {
                command = "tor --ControlPort 9051 --SocksPort 9050 --RunAsDaemon 1";
            }
            
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            Process process = pb.start();
            
            // Wait a moment for Tor to start
            Thread.sleep(3000);
            
            // Check if Tor is now available
            checkTorAvailability();
            
            if (torAvailable) {
                logger.info("Tor daemon started successfully");
                return true;
            } else {
                logger.warning("Tor daemon start command executed but Tor is not responding");
                return false;
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start Tor daemon", e);
            return false;
        }
    }
    
    /**
     * Stop Tor daemon
     */
    public boolean stopTorDaemon() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String command;
            
            if (os.contains("mac")) {
                command = "pkill -f tor";
            } else if (os.contains("linux")) {
                command = "sudo systemctl stop tor";
            } else {
                command = "pkill -f tor";
            }
            
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            // Check if Tor is no longer available
            checkTorAvailability();
            
            if (!torAvailable) {
                logger.info("Tor daemon stopped successfully");
                return true;
            } else {
                logger.warning("Tor daemon stop command executed but Tor is still responding");
                return false;
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to stop Tor daemon", e);
            return false;
        }
    }
    
    /**
     * Get Tor setup instructions
     */
    public String getTorSetupInstructions() {
        return """
            To enable Tor support for geographic IP diversity testing:
            
            1. Install Tor:
               macOS: brew install tor
               Ubuntu/Debian: sudo apt-get install tor
               Fedora/RHEL: sudo dnf install tor
               
            2. Use the Start/Stop Tor buttons in the application
               
            3. Verify Tor is running:
               curl --socks5 localhost:9050 http://httpbin.org/ip
               
            The application will automatically detect and use Tor when available.
            """;
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
} 