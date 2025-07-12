package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.NetworkHost;
import edu.au.cpsc.module7.models.ScanConfiguration;
import javafx.concurrent.Task;
import javafx.application.Platform;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Advanced network scanner with nmap-like capabilities
 */
public class AdvancedNetworkScanner {
    // Mock logger implementation for compilation
    private static final MockLogger logger = new MockLogger();
    
    static class MockLogger {
        public void error(String msg, Exception e) { System.err.println(msg + ": " + e.getMessage()); }
        public void debug(String msg, Object... args) { /* debug disabled */ }
        public void debug(String msg, Exception e) { /* debug disabled */ }
        public void warn(String msg, Object... args) { System.out.println("WARN: " + msg); }
    }
    private static AdvancedNetworkScanner instance;
    
    // Advanced service detection patterns
    private static final Map<Integer, ServiceInfo> ADVANCED_SERVICES = new HashMap<>();
    static {
        ADVANCED_SERVICES.put(21, new ServiceInfo("FTP", "220", "vsftpd|ProFTPD|FileZilla"));
        ADVANCED_SERVICES.put(22, new ServiceInfo("SSH", "SSH-", "OpenSSH|Dropbear"));
        ADVANCED_SERVICES.put(23, new ServiceInfo("Telnet", "login:", "Linux|Windows"));
        ADVANCED_SERVICES.put(25, new ServiceInfo("SMTP", "220", "Postfix|Sendmail|Exchange"));
        ADVANCED_SERVICES.put(53, new ServiceInfo("DNS", "", "BIND|dnsmasq"));
        ADVANCED_SERVICES.put(80, new ServiceInfo("HTTP", "HTTP/", "Apache|nginx|IIS"));
        ADVANCED_SERVICES.put(110, new ServiceInfo("POP3", "+OK", "Dovecot|Courier"));
        ADVANCED_SERVICES.put(135, new ServiceInfo("RPC", "", "Microsoft Windows RPC"));
        ADVANCED_SERVICES.put(139, new ServiceInfo("NetBIOS", "", "Samba|Windows"));
        ADVANCED_SERVICES.put(143, new ServiceInfo("IMAP", "* OK", "Dovecot|Courier"));
        ADVANCED_SERVICES.put(443, new ServiceInfo("HTTPS", "", "Apache|nginx|IIS"));
        ADVANCED_SERVICES.put(993, new ServiceInfo("IMAPS", "* OK", "Dovecot|Courier"));
        ADVANCED_SERVICES.put(995, new ServiceInfo("POP3S", "+OK", "Dovecot|Courier"));
        ADVANCED_SERVICES.put(1723, new ServiceInfo("PPTP", "", "Microsoft PPTP VPN"));
        ADVANCED_SERVICES.put(3306, new ServiceInfo("MySQL", "", "MySQL|MariaDB"));
        ADVANCED_SERVICES.put(3389, new ServiceInfo("RDP", "", "Microsoft Terminal Services"));
        ADVANCED_SERVICES.put(5432, new ServiceInfo("PostgreSQL", "", "PostgreSQL"));
        ADVANCED_SERVICES.put(5900, new ServiceInfo("VNC", "RFB", "RealVNC|TightVNC"));
        ADVANCED_SERVICES.put(8080, new ServiceInfo("HTTP-Alt", "HTTP/", "Tomcat|Jetty"));
    }
    
    private ExecutorService executorService;
    private volatile boolean scanRunning = false;
    
    private AdvancedNetworkScanner() {
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public static AdvancedNetworkScanner getInstance() {
        if (instance == null) {
            instance = new AdvancedNetworkScanner();
        }
        return instance;
    }
    
    /**
     * Performs comprehensive network analysis including gateway detection
     */
    public Task<NetworkAnalysisResult> performComprehensiveAnalysis(ScanConfiguration config, Consumer<String> progressCallback) {
        return new Task<NetworkAnalysisResult>() {
            @Override
            protected NetworkAnalysisResult call() throws Exception {
                scanRunning = true;
                NetworkAnalysisResult result = new NetworkAnalysisResult();
                
                try {
                    updateMessage("Starting network analysis...");
                    updateProgress(0, 7);
                    progressCallback.accept("🚀 Starting comprehensive network analysis...");
                    
                    // Phase 1: Network topology and gateway detection
                    updateMessage("Detecting gateways and routes...");
                    updateProgress(1, 7);
                    result.setGatewayInfo(detectGatewayAndRoutes());
                    progressCallback.accept("✅ Gateway detected: " + result.getGatewayInfo().getDefaultGateway());
                    
                    // Phase 2: Enhanced host discovery using ARP scanning
                    updateMessage("Performing advanced host discovery...");
                    updateProgress(2, 7);
                    Map<String, NetworkHost> discoveredHosts = discoverHosts(config.getTargetRange(), progressCallback);
                    List<NetworkHost> hosts = new ArrayList<>(discoveredHosts.values());
                    result.setDiscoveredHosts(hosts);
                    progressCallback.accept("✅ Found " + hosts.size() + " hosts");
                    
                    // Phase 3: Advanced port scanning with service detection
                    if (config.getScanType() != ScanConfiguration.ScanType.PING_SWEEP && !hosts.isEmpty()) {
                        updateMessage("Performing port scanning...");
                        updateProgress(3, 7);
                        performAdvancedPortScanning(hosts, config, progressCallback);
                        progressCallback.accept("✅ Port scanning completed");
                    } else {
                        updateProgress(3, 7);
                    }
                    
                    // Phase 4: Service fingerprinting and banner grabbing
                    if (config.isDetectServices() && !hosts.isEmpty()) {
                        updateMessage("Fingerprinting services...");
                        updateProgress(4, 7);
                        performServiceFingerprinting(hosts, progressCallback);
                        progressCallback.accept("✅ Service fingerprinting completed");
                    } else {
                        updateProgress(4, 7);
                    }
                    
                    // Phase 5: OS fingerprinting
                    if (config.isDetectOS() && !hosts.isEmpty()) {
                        updateMessage("Performing OS detection...");
                        updateProgress(5, 7);
                        performAdvancedOSDetection(hosts, progressCallback);
                        progressCallback.accept("✅ OS detection completed");
                    } else {
                        updateProgress(5, 7);
                    }
                    
                    // Phase 6: Vulnerability scanning
                    updateMessage("Scanning for vulnerabilities...");
                    updateProgress(6, 7);
                    if (!hosts.isEmpty()) {
                        performBasicVulnerabilityScanning(hosts, progressCallback);
                        progressCallback.accept("✅ Vulnerability scanning completed");
                    }
                    
                    // Phase 7: Network topology mapping (quick)
                    if (config.isPerformTraceroute() && !hosts.isEmpty()) {
                        updateMessage("Quick topology mapping...");
                        updateProgress(7, 7);
                        performNetworkTopologyMapping(hosts, result.getGatewayInfo(), progressCallback);
                    } else {
                        updateProgress(7, 7);
                    }
                    
                    updateMessage("✅ Analysis completed successfully!");
                    progressCallback.accept("🎉 Network analysis completed successfully!");
                    
                    return result;
                    
                } catch (Exception e) {
                    logger.error("Network analysis failed", e);
                    updateMessage("❌ Analysis failed: " + e.getMessage());
                    progressCallback.accept("❌ Analysis failed: " + e.getMessage());
                    throw e;
                } finally {
                    scanRunning = false;
                }
            }
        };
    }
    
    /**
     * Discover hosts on the network using multiple methods
     */
    public Map<String, NetworkHost> discoverHosts(String networkRange, Consumer<String> progressCallback) {
        Map<String, NetworkHost> discoveredHosts = new ConcurrentHashMap<>();
        
        try {
            progressCallback.accept("🔍 Starting comprehensive network discovery...");
            
            // Method 1: ARP-based discovery (most reliable for local network)
            progressCallback.accept("📡 Performing ARP-based device discovery...");
            ARPScanner arpScanner = ARPScanner.getInstance();
            Map<String, NetworkHost> arpHosts = arpScanner.performARPScan(networkRange, progressCallback);
            discoveredHosts.putAll(arpHosts);
            
            // Method 2: Ping sweep for additional hosts
            progressCallback.accept("🏓 Performing ping sweep...");
            Map<String, NetworkHost> pingHosts = performPingSweep(networkRange, progressCallback);
            
            // Merge ping results with ARP results
            for (Map.Entry<String, NetworkHost> entry : pingHosts.entrySet()) {
                String ip = entry.getKey();
                NetworkHost pingHost = entry.getValue();
                
                if (discoveredHosts.containsKey(ip)) {
                    // Merge information - prefer ARP data for MAC addresses
                    NetworkHost existingHost = discoveredHosts.get(ip);
                    if (existingHost.getHostname() == null && pingHost.getHostname() != null) {
                        existingHost.setHostname(pingHost.getHostname());
                    }
                    existingHost.setAlive(true);
                } else {
                    discoveredHosts.put(ip, pingHost);
                }
            }
            
            // Method 3: Network interface discovery
            progressCallback.accept("🔌 Discovering network interfaces...");
            addNetworkInterfaces(discoveredHosts, progressCallback);
            
            progressCallback.accept("✅ Host discovery completed. Found " + discoveredHosts.size() + " devices");
            
        } catch (Exception e) {
            logger.error("Error during host discovery", e);
            progressCallback.accept("❌ Host discovery error: " + e.getMessage());
        }
        
        return discoveredHosts;
    }
    
    /**
     * Detect default gateway and analyze routing table
     */
    private GatewayInfo detectGatewayAndRoutes() {
        GatewayInfo gatewayInfo = new GatewayInfo();
        
        try {
            // Get default gateway using multiple methods
            String gateway = detectDefaultGateway();
            gatewayInfo.setDefaultGateway(gateway);
            
            // Get routing table (quick, local operation)
            List<RouteEntry> routes = getRoutingTable();
            gatewayInfo.setRoutes(routes);
            
            // Detect external connectivity (with timeout)
            String externalIP = detectExternalIP();
            gatewayInfo.setExternalIP(externalIP);
            
            // Quick trace route to external host (limited hops)
            if (!"Unknown".equals(externalIP)) {
                List<String> traceroute = performTraceroute("8.8.8.8");
                gatewayInfo.setInternetPath(traceroute);
            }
            
        } catch (Exception e) {
            logger.error("Error detecting gateway information", e);
        }
        
        return gatewayInfo;
    }
    
    private String detectDefaultGateway() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return detectWindowsGateway();
        } else if (os.contains("mac")) {
            return detectMacGateway();
        } else {
            return detectLinuxGateway();
        }
    }
    
    private String detectWindowsGateway() throws Exception {
        Process process = Runtime.getRuntime().exec("route print 0.0.0.0");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        
        Pattern pattern = Pattern.compile("\\s*0\\.0\\.0\\.0\\s+0\\.0\\.0\\.0\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
        
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        throw new Exception("Could not detect Windows gateway");
    }
    
    private String detectMacGateway() throws Exception {
        Process process = Runtime.getRuntime().exec("route -n get default");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("gateway:")) {
                return line.split(":")[1].trim();
            }
        }
        
        throw new Exception("Could not detect macOS gateway");
    }
    
    private String detectLinuxGateway() throws Exception {
        Process process = Runtime.getRuntime().exec("ip route show default");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        
        if (line != null && line.contains("via")) {
            String[] parts = line.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("via".equals(parts[i])) {
                    return parts[i + 1];
                }
            }
        }
        
        throw new Exception("Could not detect Linux gateway");
    }
    
    private List<RouteEntry> getRoutingTable() {
        List<RouteEntry> routes = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("route print");
            } else if (os.contains("mac")) {
                process = Runtime.getRuntime().exec("netstat -rn");
            } else {
                process = Runtime.getRuntime().exec("ip route");
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                RouteEntry route = parseRouteEntry(line, os);
                if (route != null) {
                    routes.add(route);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting routing table", e);
        }
        
        return routes;
    }
    
    private RouteEntry parseRouteEntry(String line, String os) {
        // Parse route entries based on OS format
        // This is a simplified parser - could be enhanced further
        try {
            if (os.contains("mac") || os.contains("linux")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    return new RouteEntry(parts[0], parts[1], parts[2]);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors for individual lines
        }
        return null;
    }
    
    private String detectExternalIP() {
        try {
            URL url = new URL("https://api.ipify.org");
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(3000);  // 3 second timeout
            connection.setReadTimeout(3000);     // 3 second timeout
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String externalIP = reader.readLine();
            reader.close();
            return externalIP != null ? externalIP : "Unknown";
        } catch (Exception e) {
            logger.debug("Could not detect external IP (timeout or network issue)", e);
            return "Unknown";
        }
    }
    
    private List<String> performTraceroute(String destination) {
        List<String> hops = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("tracert -h 8 -w 2000 " + destination);
            } else {
                process = Runtime.getRuntime().exec("traceroute -m 8 -w 2 " + destination);
            }
            
            // Add timeout to prevent hanging
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("Traceroute to {} timed out", destination);
                return hops;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            Pattern ipPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ipPattern.matcher(line);
                if (matcher.find()) {
                    hops.add(matcher.group(1));
                }
            }
            
        } catch (Exception e) {
            logger.error("Error performing traceroute", e);
        }
        
        return hops;
    }
    
    private List<NetworkHost> performAdvancedHostDiscovery(ScanConfiguration config, Consumer<String> progressCallback) {
        List<NetworkHost> hosts = new ArrayList<>();
        List<String> targetIPs = parseTargetRange(config.getTargetRange());
        
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
        List<Future<NetworkHost>> futures = new ArrayList<>();
        
        for (String ip : targetIPs) {
            Future<NetworkHost> future = executor.submit(() -> {
                NetworkHost host = new NetworkHost(ip);
                
                // Multiple discovery techniques
                boolean alive = false;
                long finalResponseTime = -1;
                
                // 1. ICMP ping
                try {
                    long startTime = System.currentTimeMillis();
                    InetAddress address = InetAddress.getByName(ip);
                    if (address.isReachable(config.getTimeout())) {
                        alive = true;
                        finalResponseTime = System.currentTimeMillis() - startTime;
                    }
                } catch (Exception e) {
                    // Continue with other methods
                }
                
                // 2. TCP ping on common ports if ICMP failed
                if (!alive) {
                    int[] commonPorts = {80, 443, 22, 23, 21, 25, 53, 135, 139, 445};
                    for (int port : commonPorts) {
                        try (Socket socket = new Socket()) {
                            long startTime = System.currentTimeMillis();
                            socket.connect(new InetSocketAddress(ip, port), config.getTimeout());
                            alive = true;
                            finalResponseTime = System.currentTimeMillis() - startTime;
                            host.addOpenPort(port);
                            break;
                        } catch (Exception e) {
                            // Continue trying other ports
                        }
                    }
                }
                
                if (alive) {
                    host.setAlive(true);
                    host.setResponseTime(finalResponseTime);
                    
                    // Resolve hostname
                    if (config.isResolveHostnames()) {
                        try {
                            InetAddress address = InetAddress.getByName(ip);
                            String hostname = address.getCanonicalHostName();
                            if (!hostname.equals(ip)) {
                                host.setHostname(hostname);
                            }
                        } catch (Exception e) {
                            // Hostname resolution failed
                        }
                    }
                    
                    // Capture final response time for lambda
                    final long responseTimeForLambda = finalResponseTime;
                    
                    Platform.runLater(() -> {
                        if (progressCallback != null) {
                            progressCallback.accept("Discovered host: " + ip + 
                                (host.getHostname() != null ? " (" + host.getHostname() + ")" : "") +
                                " [" + responseTimeForLambda + "ms]");
                        }
                    });
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
                    hosts.add(host);
                }
            } catch (Exception e) {
                logger.error("Error collecting host discovery result", e);
            }
        }
        
        executor.shutdown();
        return hosts;
    }
    
    private void performAdvancedPortScanning(List<NetworkHost> hosts, ScanConfiguration config, Consumer<String> progressCallback) {
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreads());
        List<Future<Void>> futures = new ArrayList<>();
        
        for (NetworkHost host : hosts) {
            Future<Void> future = executor.submit(() -> {
                List<Integer> portsToScan = config.getPorts();
                
                // Use different scanning techniques based on configuration
                switch (config.getPortScanType()) {
                    case TCP_CONNECT:
                        performTCPConnectScan(host, portsToScan, config, progressCallback);
                        break;
                    case TCP_SYN:
                        // Note: SYN scan requires raw sockets (root privileges)
                        progressCallback.accept("SYN scan requires elevated privileges, falling back to TCP connect");
                        performTCPConnectScan(host, portsToScan, config, progressCallback);
                        break;
                    case UDP:
                        performUDPScan(host, portsToScan, config, progressCallback);
                        break;
                    case COMPREHENSIVE:
                        performTCPConnectScan(host, portsToScan, config, progressCallback);
                        performUDPScan(host, Arrays.asList(53, 67, 68, 69, 123, 161, 162), config, progressCallback);
                        break;
                }
                
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for completion
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                logger.error("Error in advanced port scanning", e);
            }
        }
        
        executor.shutdown();
    }
    
    private void performTCPConnectScan(NetworkHost host, List<Integer> ports, ScanConfiguration config, Consumer<String> progressCallback) {
        for (int port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host.getIpAddress(), port), config.getTimeout());
                host.addOpenPort(port);
                
                Platform.runLater(() -> {
                    if (progressCallback != null) {
                        progressCallback.accept("Open TCP port found: " + host.getIpAddress() + ":" + port);
                    }
                });
            } catch (Exception e) {
                // Port is closed or filtered
            }
        }
    }
    
    private void performUDPScan(NetworkHost host, List<Integer> ports, ScanConfiguration config, Consumer<String> progressCallback) {
        for (int port : ports) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(config.getTimeout());
                
                // Send UDP probe
                byte[] buffer = new byte[0];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, 
                    InetAddress.getByName(host.getIpAddress()), port);
                socket.send(packet);
                
                // Try to receive response
                byte[] responseBuffer = new byte[1024];
                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                
                try {
                    socket.receive(responsePacket);
                    host.addOpenPort(port);
                    
                    Platform.runLater(() -> {
                        if (progressCallback != null) {
                            progressCallback.accept("Open UDP port found: " + host.getIpAddress() + ":" + port);
                        }
                    });
                } catch (SocketTimeoutException e) {
                    // No response - port might be open but service doesn't respond to empty packets
                    // This is a limitation of UDP scanning
                }
                
            } catch (Exception e) {
                // Error in UDP scanning
            }
        }
    }
    
    private void performServiceFingerprinting(List<NetworkHost> hosts, Consumer<String> progressCallback) {
        for (NetworkHost host : hosts) {
            for (int port : host.getOpenPorts()) {
                try {
                    ServiceInfo serviceInfo = ADVANCED_SERVICES.get(port);
                    if (serviceInfo != null) {
                        String banner = grabBanner(host.getIpAddress(), port);
                        String detectedService = analyzeService(port, banner, serviceInfo);
                        host.addService(detectedService);
                        
                        Platform.runLater(() -> {
                            if (progressCallback != null) {
                                progressCallback.accept("Service detected on " + host.getIpAddress() + ":" + port + " - " + detectedService);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.debug("Error fingerprinting service on {}:{}", host.getIpAddress(), port, e);
                }
            }
        }
    }
    
    private String grabBanner(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 3000);
            socket.setSoTimeout(3000);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            
            // Try to get banner
            StringBuilder banner = new StringBuilder();
            
            // For HTTP services, send HTTP request
            if (port == 80 || port == 8080 || port == 443) {
                writer.println("HEAD / HTTP/1.0\r\n\r\n");
            }
            
            // Read response
            String line;
            int maxLines = 5;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                banner.append(line).append("\n");
                lineCount++;
            }
            
            return banner.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    private String analyzeService(int port, String banner, ServiceInfo serviceInfo) {
        String service = serviceInfo.getName();
        
        if (banner != null && !banner.isEmpty()) {
            // Analyze banner for version information
            Pattern versionPattern = Pattern.compile(serviceInfo.getVersionPattern());
            Matcher matcher = versionPattern.matcher(banner);
            
            if (matcher.find()) {
                service += " (" + matcher.group(0) + ")";
            }
            
            // Check for specific implementations
            if (banner.toLowerCase().contains("apache")) {
                service += " - Apache";
            } else if (banner.toLowerCase().contains("nginx")) {
                service += " - nginx";
            } else if (banner.toLowerCase().contains("microsoft")) {
                service += " - Microsoft";
            }
        }
        
        return service;
    }
    
    private void performAdvancedOSDetection(List<NetworkHost> hosts, Consumer<String> progressCallback) {
        for (NetworkHost host : hosts) {
            String osGuess = performTCPFingerprinting(host);
            host.setOsGuess(osGuess);
            
            Platform.runLater(() -> {
                if (progressCallback != null) {
                    progressCallback.accept("OS detected for " + host.getIpAddress() + ": " + osGuess);
                }
            });
        }
    }
    
    private String performTCPFingerprinting(NetworkHost host) {
        // Advanced OS detection based on TCP/IP stack characteristics
        // This is a simplified version - real nmap uses much more sophisticated techniques
        
        Set<Integer> openPorts = new HashSet<>(host.getOpenPorts());
        
        // Windows signatures
        if (openPorts.contains(135) && openPorts.contains(139) && openPorts.contains(445)) {
            return "Microsoft Windows (SMB/RPC)";
        }
        
        if (openPorts.contains(3389)) {
            return "Microsoft Windows (RDP enabled)";
        }
        
        // Linux/Unix signatures
        if (openPorts.contains(22) && !openPorts.contains(135)) {
            return "Linux/Unix (SSH)";
        }
        
        // macOS signatures
        if (openPorts.contains(548) || openPorts.contains(5900)) {
            return "macOS (AFP/VNC)";
        }
        
        // Network device signatures
        if (openPorts.contains(23) && openPorts.contains(80) && openPorts.size() < 5) {
            return "Network Device (Router/Switch)";
        }
        
        return "Unknown";
    }
    
    private void performBasicVulnerabilityScanning(List<NetworkHost> hosts, Consumer<String> progressCallback) {
        for (NetworkHost host : hosts) {
            List<String> vulnerabilities = new ArrayList<>();
            
            // Check for common vulnerabilities
            for (int port : host.getOpenPorts()) {
                switch (port) {
                    case 21:
                        vulnerabilities.add("FTP service exposed (potential anonymous access)");
                        break;
                    case 23:
                        vulnerabilities.add("Telnet service exposed (unencrypted)");
                        break;
                    case 135:
                        vulnerabilities.add("RPC service exposed (potential RCE)");
                        break;
                    case 445:
                        vulnerabilities.add("SMB service exposed (potential EternalBlue)");
                        break;
                    case 1433:
                        vulnerabilities.add("SQL Server exposed (potential injection)");
                        break;
                    case 3306:
                        vulnerabilities.add("MySQL exposed (check authentication)");
                        break;
                    case 5432:
                        vulnerabilities.add("PostgreSQL exposed (check authentication)");
                        break;
                }
            }
            
            // Add vulnerabilities to host
            for (String vuln : vulnerabilities) {
                host.addService("VULN: " + vuln);
                
                Platform.runLater(() -> {
                    if (progressCallback != null) {
                        progressCallback.accept("Vulnerability found on " + host.getIpAddress() + ": " + vuln);
                    }
                });
            }
        }
    }
    
    private void performNetworkTopologyMapping(List<NetworkHost> hosts, GatewayInfo gatewayInfo, Consumer<String> progressCallback) {
        progressCallback.accept("Mapping network topology (quick scan)...");
        
        // Only trace to gateway and a few key hosts to avoid hanging
        List<NetworkHost> keyHosts = hosts.stream()
            .filter(h -> h.getOpenPorts().size() > 3 || h.getIpAddress().endsWith(".1"))
            .limit(3)  // Only trace to 3 most interesting hosts
            .collect(Collectors.toList());
        
        for (NetworkHost host : keyHosts) {
            if (!scanRunning) break;
            
            try {
                progressCallback.accept("Tracing route to " + host.getIpAddress() + "...");
                List<String> path = performQuickTraceroute(host.getIpAddress());
                host.setNetworkPath(path);
            } catch (Exception e) {
                logger.debug("Failed to trace route to {}", host.getIpAddress(), e);
            }
        }
        
        progressCallback.accept("Network topology mapping completed");
    }
    
    private List<String> performQuickTraceroute(String destination) {
        List<String> hops = new ArrayList<>();
        
        try {
            // For local network, just add gateway and destination
            InetAddress destAddr = InetAddress.getByName(destination);
            if (destAddr.isSiteLocalAddress()) {
                // It's a local address, just add direct path
                hops.add(destination);
                return hops;
            }
            
            // For external addresses, do a quick 3-hop trace
            String os = System.getProperty("os.name").toLowerCase();
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("tracert -h 3 -w 1000 " + destination);
            } else {
                process = Runtime.getRuntime().exec("traceroute -m 3 -w 1 " + destination);
            }
            
            // Very short timeout
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return hops;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern ipPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ipPattern.matcher(line);
                if (matcher.find()) {
                    hops.add(matcher.group(1));
                }
            }
            
        } catch (Exception e) {
            logger.debug("Quick traceroute failed", e);
        }
        
        return hops;
    }
    
    // Helper method to parse target range (reused from original scanner)
    private List<String> parseTargetRange(String targetRange) {
        List<String> ips = new ArrayList<>();
        
        if (targetRange == null || targetRange.trim().isEmpty()) {
            return ips;
        }
        
        targetRange = targetRange.trim();
        
        if (targetRange.contains("/")) {
            ips.addAll(parseCIDR(targetRange));
        } else if (targetRange.contains("-")) {
            ips.addAll(parseIPRange(targetRange));
        } else {
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
            int numHosts = (1 << hostBits) - 2;
            
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
            logger.error("Error parsing CIDR: " + cidr, e);
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
            logger.error("Error parsing IP range: " + range, e);
        }
        
        return ips;
    }
    
    /**
     * Perform ping sweep to discover live hosts
     */
    private Map<String, NetworkHost> performPingSweep(String networkRange, Consumer<String> progressCallback) {
        Map<String, NetworkHost> hosts = new ConcurrentHashMap<>();
        
        try {
            List<String> targetIPs = parseCIDRRange(networkRange);
            progressCallback.accept("Pinging " + targetIPs.size() + " addresses...");
            
            // Use parallel processing for faster scanning
            targetIPs.parallelStream().forEach(ip -> {
                try {
                    InetAddress address = InetAddress.getByName(ip);
                    if (address.isReachable(2000)) { // 2 second timeout
                        NetworkHost host = new NetworkHost(ip);
                        host.setAlive(true);
                        
                        // Try to resolve hostname
                        try {
                            String hostname = address.getCanonicalHostName();
                            if (!hostname.equals(ip)) {
                                host.setHostname(hostname);
                            }
                        } catch (Exception e) {
                            // Hostname resolution failed
                        }
                        
                        hosts.put(ip, host);
                    }
                } catch (Exception e) {
                    // Host unreachable
                }
            });
            
        } catch (Exception e) {
            logger.error("Error in ping sweep", e);
        }
        
        return hosts;
    }
    
    /**
     * Add local network interfaces to discovered hosts
     */
    private void addNetworkInterfaces(Map<String, NetworkHost> hosts, Consumer<String> progressCallback) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                // Get MAC address
                byte[] macBytes = networkInterface.getHardwareAddress();
                String macAddress = null;
                if (macBytes != null) {
                    StringBuilder macBuilder = new StringBuilder();
                    for (int i = 0; i < macBytes.length; i++) {
                        macBuilder.append(String.format("%02x", macBytes[i]));
                        if (i < macBytes.length - 1) {
                            macBuilder.append(":");
                        }
                    }
                    macAddress = macBuilder.toString();
                }
                
                // Get IP addresses
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    if (address.isLoopbackAddress() || address instanceof Inet6Address) {
                        continue;
                    }
                    
                    String ip = address.getHostAddress();
                    
                    if (!hosts.containsKey(ip)) {
                        NetworkHost host = new NetworkHost(ip);
                        host.setAlive(true);
                        host.setHostname("localhost");
                        
                        if (macAddress != null) {
                            host.setMacAddress(macAddress);
                        }
                        
                        hosts.put(ip, host);
                        progressCallback.accept("Added local interface: " + ip);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error enumerating local interfaces", e);
        }
    }
    
    /**
     * Parse CIDR network range into list of IP addresses
     */
    private List<String> parseCIDRRange(String cidr) {
        List<String> ips = new ArrayList<>();
        
        try {
            String[] parts = cidr.split("/");
            String baseIP = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            String[] octets = baseIP.split("\\.");
            int baseAddr = (Integer.parseInt(octets[0]) << 24) |
                          (Integer.parseInt(octets[1]) << 16) |
                          (Integer.parseInt(octets[2]) << 8) |
                          Integer.parseInt(octets[3]);
            
            int mask = 0xFFFFFFFF << (32 - prefixLength);
            int networkAddr = baseAddr & mask;
            int hostBits = 32 - prefixLength;
            int numHosts = (1 << hostBits) - 2;
            
            // Limit to reasonable number for scanning
            if (numHosts > 254) {
                numHosts = 254;
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
            logger.error("Error parsing CIDR range: " + cidr, e);
        }
        
        return ips;
    }

    public void stopScan() {
        scanRunning = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = Executors.newCachedThreadPool();
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
    
    // Supporting classes
    public static class ServiceInfo {
        private final String name;
        private final String bannerPattern;
        private final String versionPattern;
        
        public ServiceInfo(String name, String bannerPattern, String versionPattern) {
            this.name = name;
            this.bannerPattern = bannerPattern;
            this.versionPattern = versionPattern;
        }
        
        public String getName() { return name; }
        public String getBannerPattern() { return bannerPattern; }
        public String getVersionPattern() { return versionPattern; }
    }
    
    public static class GatewayInfo {
        private String defaultGateway;
        private String externalIP;
        private List<RouteEntry> routes = new ArrayList<>();
        private List<String> internetPath = new ArrayList<>();
        
        // Getters and setters
        public String getDefaultGateway() { return defaultGateway; }
        public void setDefaultGateway(String defaultGateway) { this.defaultGateway = defaultGateway; }
        
        public String getExternalIP() { return externalIP; }
        public void setExternalIP(String externalIP) { this.externalIP = externalIP; }
        
        public List<RouteEntry> getRoutes() { return routes; }
        public void setRoutes(List<RouteEntry> routes) { this.routes = routes; }
        
        public List<String> getInternetPath() { return internetPath; }
        public void setInternetPath(List<String> internetPath) { this.internetPath = internetPath; }
    }
    
    public static class RouteEntry {
        private final String destination;
        private final String gateway;
        private final String interface_;
        
        public RouteEntry(String destination, String gateway, String interface_) {
            this.destination = destination;
            this.gateway = gateway;
            this.interface_ = interface_;
        }
        
        public String getDestination() { return destination; }
        public String getGateway() { return gateway; }
        public String getInterface() { return interface_; }
    }
    
    public static class NetworkAnalysisResult {
        private GatewayInfo gatewayInfo;
        private List<NetworkHost> discoveredHosts = new ArrayList<>();
        
        public GatewayInfo getGatewayInfo() { return gatewayInfo; }
        public void setGatewayInfo(GatewayInfo gatewayInfo) { this.gatewayInfo = gatewayInfo; }
        
        public List<NetworkHost> getDiscoveredHosts() { return discoveredHosts; }
        public void setDiscoveredHosts(List<NetworkHost> discoveredHosts) { this.discoveredHosts = discoveredHosts; }
    }
} 