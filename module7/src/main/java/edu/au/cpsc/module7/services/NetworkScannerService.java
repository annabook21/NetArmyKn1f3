package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.NetworkHost;
import edu.au.cpsc.module7.models.ScanConfiguration;
import javafx.concurrent.Task;
import javafx.application.Platform;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import com.google.inject.Inject;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;

/**
 * Service for network scanning operations including host discovery and port scanning
 */
public class NetworkScannerService {
    private static final Logger logger = Logger.getLogger(NetworkScannerService.class.getName());
    
    // Service detection patterns
    private static final Map<Integer, String> COMMON_SERVICES = new HashMap<>();
    static {
        COMMON_SERVICES.put(21, "FTP");
        COMMON_SERVICES.put(22, "SSH");
        COMMON_SERVICES.put(23, "Telnet");
        COMMON_SERVICES.put(25, "SMTP");
        COMMON_SERVICES.put(53, "DNS");
        COMMON_SERVICES.put(80, "HTTP");
        COMMON_SERVICES.put(110, "POP3");
        COMMON_SERVICES.put(135, "RPC");
        COMMON_SERVICES.put(139, "NetBIOS");
        COMMON_SERVICES.put(143, "IMAP");
        COMMON_SERVICES.put(443, "HTTPS");
        COMMON_SERVICES.put(993, "IMAPS");
        COMMON_SERVICES.put(995, "POP3S");
        COMMON_SERVICES.put(1723, "PPTP");
        COMMON_SERVICES.put(3306, "MySQL");
        COMMON_SERVICES.put(3389, "RDP");
        COMMON_SERVICES.put(5432, "PostgreSQL");
        COMMON_SERVICES.put(5900, "VNC");
        COMMON_SERVICES.put(8080, "HTTP-Alt");
    }
    
    private ExecutorService executorService;
    private volatile boolean scanRunning = false;
    private final ARPScanner arpScanner;
    // private final AdvancedPortScannerService advancedPortScannerService;
    
    @Inject
    public NetworkScannerService(ARPScanner arpScanner) { //, AdvancedPortScannerService advancedPortScannerService) {
        this.executorService = Executors.newCachedThreadPool();
        this.arpScanner = arpScanner;
        // this.advancedPortScannerService = advancedPortScannerService;
    }
    
    /**
     * Performs a network scan based on the configuration
     */
    public Task<List<NetworkHost>> scanNetwork(ScanConfiguration config, Consumer<String> progressCallback) {
        return new Task<List<NetworkHost>>() {
            @Override
            protected List<NetworkHost> call() throws Exception {
                scanRunning = true;
                List<NetworkHost> discoveredHosts = new ArrayList<>();
                
                try {
                    updateMessage("Parsing target range...");
                    List<String> targetIPs = parseTargetRange(config.getTargetRange());
                    
                    if (targetIPs.isEmpty()) {
                        throw new IllegalArgumentException("No valid IP addresses found in target range");
                    }
                    
                    updateMessage("Starting network scan...");
                    updateProgress(0, targetIPs.size());
                    
                    // Phase 1: Host Discovery
                    if (config.getScanType() != ScanConfiguration.ScanType.PORT_SCAN) {
                        discoveredHosts = performHostDiscovery(targetIPs, config, progressCallback);
                    } else {
                        // For port-only scans, assume all IPs are targets
                        for (String ip : targetIPs) {
                            NetworkHost host = new NetworkHost(ip);
                            host.setAlive(true);
                            discoveredHosts.add(host);
                        }
                    }
                    
                    // Phase 2: Port Scanning
                    if (config.getScanType() == ScanConfiguration.ScanType.PORT_SCAN || 
                        config.getScanType() == ScanConfiguration.ScanType.FULL_SCAN) {
                        updateMessage("Scanning ports...");
                        performPortScanning(discoveredHosts, config, progressCallback);
                    }
                    
                    // Phase 3: Service Detection
                    if (config.isDetectServices()) {
                        updateMessage("Detecting services...");
                        performServiceDetection(discoveredHosts, config);
                    }
                    
                    // Phase 4: OS Detection (basic)
                    if (config.isDetectOS()) {
                        updateMessage("Detecting operating systems...");
                        performOSDetection(discoveredHosts, config);
                    }
                    
                    updateMessage("Scan completed successfully");
                    updateProgress(1, 1);
                    
                    return discoveredHosts;
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Network scan failed", e);
                    updateMessage("Scan failed: " + e.getMessage());
                    throw e;
                } finally {
                    scanRunning = false;
                }
            }
        };
    }
    
    /**
     * Parses target range (IP, CIDR, range) into list of IP addresses
     */
    private List<String> parseTargetRange(String targetRange) {
        List<String> ips = new ArrayList<>();
        
        if (targetRange == null || targetRange.trim().isEmpty()) {
            return ips;
        }
        
        targetRange = targetRange.trim();
        
        // Handle CIDR notation (e.g., 192.168.1.0/24)
        if (targetRange.contains("/")) {
            ips.addAll(parseCIDR(targetRange));
        }
        // Handle IP range (e.g., 192.168.1.1-192.168.1.10)
        else if (targetRange.contains("-")) {
            ips.addAll(parseIPRange(targetRange));
        }
        // Handle single IP or hostname
        else {
            ips.add(targetRange);
        }
        
        return ips;
    }
    
    private List<String> parseCIDR(String cidr) {
        List<String> ips = new ArrayList<>();
        try {
            String[] parts = cidr.split("/");
            String baseIP = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            if (prefixLength < 16 || prefixLength > 30) {
                throw new IllegalArgumentException("CIDR prefix must be between 16 and 30");
            }
            
            String[] octets = baseIP.split("\\.");
            if (octets.length != 4) {
                throw new IllegalArgumentException("Invalid IP address format");
            }
            
            int baseAddr = (Integer.parseInt(octets[0]) << 24) |
                          (Integer.parseInt(octets[1]) << 16) |
                          (Integer.parseInt(octets[2]) << 8) |
                          Integer.parseInt(octets[3]);
            
            int mask = 0xFFFFFFFF << (32 - prefixLength);
            int networkAddr = baseAddr & mask;
            int hostBits = 32 - prefixLength;
            int numHosts = (1 << hostBits) - 2; // Exclude network and broadcast
            
            // Limit to reasonable number of hosts
            if (numHosts > 1000) {
                numHosts = 1000;
            }
            
            for (int i = 1; i <= numHosts; i++) {
                int hostAddr = networkAddr + i;
                String ip = String.format("%d.%d.%d.%d",
                    (hostAddr >> 24) & 0xFF,
                    (hostAddr >> 16) & 0xFF,
                    (hostAddr >> 8) & 0xFF,
                    hostAddr & 0xFF);
                ips.add(ip);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing CIDR: " + cidr, e);
        }
        
        return ips;
    }
    
    private List<String> parseIPRange(String range) {
        List<String> ips = new ArrayList<>();
        try {
            String[] parts = range.split("-");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid IP range format");
            }
            
            String startIP = parts[0].trim();
            String endIP = parts[1].trim();
            
            // Simple range parsing for last octet
            String[] startOctets = startIP.split("\\.");
            String[] endOctets = endIP.split("\\.");
            
            if (startOctets.length != 4 || endOctets.length != 4) {
                throw new IllegalArgumentException("Invalid IP address format in range");
            }
            
            String baseIP = startOctets[0] + "." + startOctets[1] + "." + startOctets[2] + ".";
            int startHost = Integer.parseInt(startOctets[3]);
            int endHost = Integer.parseInt(endOctets[3]);
            
            for (int i = startHost; i <= endHost; i++) {
                ips.add(baseIP + i);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing IP range: " + range, e);
        }
        
        return ips;
    }
    
    private List<NetworkHost> performHostDiscovery(List<String> targetIPs, ScanConfiguration config, Consumer<String> progressCallback) {
        List<NetworkHost> aliveHosts = new ArrayList<>();
        
        try {
            // Use ARP scanning for much better device discovery
            String networkRange = config.getTargetRange();
            
            Platform.runLater(() -> {
                if (progressCallback != null) {
                    progressCallback.accept("Starting ARP scan - this finds devices that don't respond to ping...");
                }
            });
            
            // Get devices from ARP scan
            Map<String, NetworkHost> arpDevices = arpScanner.performARPScan(networkRange, progressCallback);
            aliveHosts.addAll(arpDevices.values());
            
            int arpFoundCount = aliveHosts.size();
            Platform.runLater(() -> {
                if (progressCallback != null) {
                    progressCallback.accept("ARP scan found " + arpFoundCount + " devices");
                }
            });
            
            // Also try ping scan for any devices ARP missed (if configured)
            if (config.getScanType() == ScanConfiguration.ScanType.FULL_SCAN) {
                Platform.runLater(() -> {
                    if (progressCallback != null) {
                        progressCallback.accept("Performing supplementary ping scan...");
                    }
                });
                
                List<NetworkHost> pingHosts = performPingDiscovery(targetIPs, config, progressCallback);
                
                // Merge ping results with ARP results (avoid duplicates)
                for (NetworkHost pingHost : pingHosts) {
                    boolean exists = aliveHosts.stream()
                        .anyMatch(host -> host.getIpAddress().equals(pingHost.getIpAddress()));
                    if (!exists) {
                        aliveHosts.add(pingHost);
                    }
                }
                
                int totalFoundCount = aliveHosts.size();
                Platform.runLater(() -> {
                    if (progressCallback != null) {
                        progressCallback.accept("Total devices found: " + totalFoundCount);
                    }
                });
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ARP scan failed, falling back to ping scan", e);
            
            Platform.runLater(() -> {
                if (progressCallback != null) {
                    progressCallback.accept("ARP scan failed, using ping scan fallback...");
                }
            });
            
            // Fallback to ping scan if ARP scan fails
            aliveHosts = performPingDiscovery(targetIPs, config, progressCallback);
        }
        
        return aliveHosts;
    }
    
    private List<NetworkHost> performPingDiscovery(List<String> targetIPs, ScanConfiguration config, Consumer<String> progressCallback) {
        List<NetworkHost> aliveHosts = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
        List<Future<NetworkHost>> futures = new ArrayList<>();
        
        for (String ip : targetIPs) {
            Future<NetworkHost> future = executor.submit(() -> {
                NetworkHost host = new NetworkHost(ip);
                
                try {
                    long startTime = System.currentTimeMillis();
                    
                    // Try ping first
                    InetAddress address = InetAddress.getByName(ip);
                    boolean reachable = address.isReachable(config.getTimeout());
                    
                    if (reachable) {
                        host.setAlive(true);
                        host.setResponseTime(System.currentTimeMillis() - startTime);
                        
                        if (config.isResolveHostnames()) {
                            try {
                                String hostname = address.getCanonicalHostName();
                                if (!hostname.equals(ip)) {
                                    host.setHostname(hostname);
                                }
                            } catch (Exception e) {
                                // Hostname resolution failed, ignore
                            }
                        }
                        
                        Platform.runLater(() -> {
                            if (progressCallback != null) {
                                progressCallback.accept("Found host: " + ip + 
                                    (host.getHostname() != null ? " (" + host.getHostname() + ")" : ""));
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    logger.log(Level.FINE, "Host discovery failed for " + ip + ": " + e.getMessage());
                }
                
                return host;
            });
            
            futures.add(future);
        }
        
        // Collect results
        for (Future<NetworkHost> future : futures) {
            try {
                NetworkHost host = future.get();
                if (host.isAlive()) {
                    aliveHosts.add(host);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error collecting host discovery result", e);
            }
        }
        
        executor.shutdown();
        return aliveHosts;
    }
    
    private void performPortScanning(List<NetworkHost> hosts, ScanConfiguration config, Consumer<String> progressCallback) {
        if (config.getPortScanType() == ScanConfiguration.PortScanType.TCP_SYN_SCAN) {
            performSynScan(hosts, config, progressCallback);
        } else {
            performConnectScan(hosts, config, progressCallback);
        }
    }

    private void performConnectScan(List<NetworkHost> hosts, ScanConfiguration config, Consumer<String> progressCallback) {
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
        List<Future<Void>> futures = new ArrayList<>();
        
        for (NetworkHost host : hosts) {
            Future<Void> future = executor.submit(() -> {
                for (int port : config.getPorts()) {
                    try {
                        if (isPortOpen(host.getIpAddress(), port, config.getTimeout())) {
                            host.addOpenPort(port);
                            
                            Platform.runLater(() -> {
                                if (progressCallback != null) {
                                    progressCallback.accept("Open port found: " + host.getIpAddress() + ":" + port);
                                }
                            });
                        }
                    } catch (Exception e) {
                        logger.log(Level.FINE, "Port scan failed for " + host.getIpAddress() + ":" + port + ": " + e.getMessage());
                    }
                }
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for all port scans to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in port scanning", e);
            }
        }
        
        executor.shutdown();
    }

    private void performSynScan(List<NetworkHost> hosts, ScanConfiguration config, Consumer<String> progressCallback) {
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
        List<Future<Void>> futures = new ArrayList<>();

        for (NetworkHost host : hosts) {
            Future<Void> future = executor.submit(() -> {
                try {
                    InetAddress targetIp = InetAddress.getByName(host.getIpAddress());
                    PcapNetworkInterface nif = Pcaps.getDevByAddress(targetIp);

                    if (nif == null) {
                        Platform.runLater(() -> progressCallback.accept("Warning: Could not find network interface for " + host.getIpAddress()));
                        return null;
                    }

                    for (int port : config.getPorts()) {
                        // This requires pcap and root privileges, handle gracefully
                        // Example of how it might be called:
                        // if (advancedPortScannerService.isPortOpen(nif, targetIp, port, config.getTimeout())) {
                        //     host.addOpenPort(port);
                        //     Platform.runLater(() -> progressCallback.accept("Open port (SYN): " + host.getIpAddress() + ":" + port));
                        // }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "SYN scan failed for " + host.getIpAddress(), e);
                }
                return null;
            });
            futures.add(future);
        }

        // Wait for all scans to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in SYN scanning", e);
            }
        }

        executor.shutdown();
    }
    
    private boolean isPortOpen(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private void performServiceDetection(List<NetworkHost> hosts, ScanConfiguration config) {
        for (NetworkHost host : hosts) {
            for (int port : host.getOpenPorts()) {
                String service = COMMON_SERVICES.get(port);
                if (service != null) {
                    host.addService(service);
                } else {
                    host.addService("Unknown");
                }
            }
        }
    }
    
    private void performOSDetection(List<NetworkHost> hosts, ScanConfiguration config) {
        // Basic OS detection based on open ports and patterns
        for (NetworkHost host : hosts) {
            String osGuess = "Unknown";
            
            if (host.getOpenPorts().contains(135) || host.getOpenPorts().contains(139) || host.getOpenPorts().contains(3389)) {
                osGuess = "Windows";
            } else if (host.getOpenPorts().contains(22)) {
                osGuess = "Linux/Unix";
            } else if (host.getOpenPorts().contains(548)) {
                osGuess = "macOS";
            }
            
            host.setOsGuess(osGuess);
        }
    }
    
    public void stopScan() {
        scanRunning = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            // Re-initialize the executor for future scans
            this.executorService = Executors.newCachedThreadPool();
        }
    }
    
    public boolean isScanRunning() {
        return scanRunning;
    }
    
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
} 