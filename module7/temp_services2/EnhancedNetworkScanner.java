package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.NetworkHost;
import edu.au.cpsc.module7.models.ScanConfiguration;
// Logging removed for compilation

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Enhanced network scanner that combines multiple discovery methods:
 * 1. Passive packet capture (pcap4j)
 * 2. Active ARP scanning
 * 3. Traditional ping sweeps
 * 4. Service fingerprinting
 * 
 * This provides the most comprehensive network discovery available.
 */
public class EnhancedNetworkScanner {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedNetworkScanner.class);
    private static EnhancedNetworkScanner instance;
    
    private final PacketCaptureService packetCapture;
    private final ARPScanner arpScanner;
    private final AdvancedNetworkScanner advancedScanner;
    
    private EnhancedNetworkScanner() {
        this.packetCapture = PacketCaptureService.getInstance();
        this.arpScanner = ARPScanner.getInstance();
        this.advancedScanner = AdvancedNetworkScanner.getInstance();
    }
    
    public static EnhancedNetworkScanner getInstance() {
        if (instance == null) {
            instance = new EnhancedNetworkScanner();
        }
        return instance;
    }
    
    /**
     * Perform comprehensive network discovery using all available methods
     */
    public Map<String, NetworkHost> performComprehensiveDiscovery(String networkRange, Consumer<String> progressCallback) {
        Map<String, NetworkHost> allHosts = new ConcurrentHashMap<>();
        
        try {
            progressCallback.accept("🚀 Starting comprehensive network discovery...");
            
            // Phase 1: Check if packet capture is available
            boolean packetCaptureAvailable = packetCapture.isPacketCaptureSupported();
            
            if (packetCaptureAvailable) {
                progressCallback.accept("✅ Packet capture available - using advanced discovery");
                
                // Method 1: Passive packet capture (most comprehensive)
                progressCallback.accept("🔍 Phase 1: Passive packet capture discovery...");
                Map<String, NetworkHost> passiveHosts = packetCapture.performPassiveDiscovery(15, progressCallback);
                mergeHosts(allHosts, passiveHosts, "Packet Capture");
                progressCallback.accept("📊 Passive discovery found " + passiveHosts.size() + " devices");
                
                // Method 2: Active reconnaissance with packet crafting
                progressCallback.accept("🎯 Phase 2: Active reconnaissance...");
                Map<String, NetworkHost> activeHosts = packetCapture.performActiveReconnaissance(networkRange, progressCallback);
                mergeHosts(allHosts, activeHosts, "Active Recon");
                progressCallback.accept("📊 Active reconnaissance found " + activeHosts.size() + " devices");
                
            } else {
                progressCallback.accept("⚠️ Packet capture not available - using traditional methods");
            }
            
            // Method 3: ARP-based discovery (always available)
            progressCallback.accept("📡 Phase 3: ARP-based discovery...");
            Map<String, NetworkHost> arpHosts = arpScanner.performARPScan(networkRange, progressCallback);
            mergeHosts(allHosts, arpHosts, "ARP Scan");
            progressCallback.accept("📊 ARP discovery found " + arpHosts.size() + " devices");
            
            // Method 4: Traditional ping sweep (fallback)
            progressCallback.accept("🏓 Phase 4: Traditional ping sweep...");
            Map<String, NetworkHost> pingHosts = advancedScanner.discoverHosts(networkRange, progressCallback);
            mergeHosts(allHosts, pingHosts, "Ping Sweep");
            progressCallback.accept("📊 Ping sweep found " + pingHosts.size() + " devices");
            
            // Phase 5: Enhanced analysis of discovered hosts
            progressCallback.accept("🔬 Phase 5: Enhanced host analysis...");
            enhanceHostInformation(allHosts, progressCallback);
            
            // Phase 6: Security assessment
            progressCallback.accept("🛡️ Phase 6: Security assessment...");
            performSecurityAssessment(allHosts, progressCallback);
            
            progressCallback.accept("✅ Comprehensive discovery completed!");
            progressCallback.accept("🎉 Total devices discovered: " + allHosts.size());
            
            // Display summary
            displayDiscoverySummary(allHosts, progressCallback);
            
        } catch (Exception e) {
            logger.error("Error during comprehensive discovery", e);
            progressCallback.accept("❌ Discovery error: " + e.getMessage());
        }
        
        return allHosts;
    }
    
    /**
     * Merge hosts from different discovery methods
     */
    private void mergeHosts(Map<String, NetworkHost> allHosts, Map<String, NetworkHost> newHosts, String method) {
        for (Map.Entry<String, NetworkHost> entry : newHosts.entrySet()) {
            String ip = entry.getKey();
            NetworkHost newHost = entry.getValue();
            
            if (allHosts.containsKey(ip)) {
                // Merge with existing host
                NetworkHost existingHost = allHosts.get(ip);
                mergeHostData(existingHost, newHost, method);
            } else {
                // Add new host
                newHost.addService("Discovered via " + method);
                allHosts.put(ip, newHost);
            }
        }
    }
    
    /**
     * Merge data from two NetworkHost objects
     */
    private void mergeHostData(NetworkHost existing, NetworkHost newHost, String method) {
        // Merge MAC address (prefer non-null)
        if (existing.getMacAddress() == null && newHost.getMacAddress() != null) {
            existing.setMacAddress(newHost.getMacAddress());
        }
        
        // Merge vendor (prefer non-null)
        if (existing.getVendor() == null && newHost.getVendor() != null) {
            existing.setVendor(newHost.getVendor());
        }
        
        // Merge hostname (prefer non-null)
        if (existing.getHostname() == null && newHost.getHostname() != null) {
            existing.setHostname(newHost.getHostname());
        }
        
        // Merge OS guess (prefer non-null)
        if (existing.getOsGuess() == null && newHost.getOsGuess() != null) {
            existing.setOsGuess(newHost.getOsGuess());
        }
        
        // Merge response time (prefer faster)
        if (existing.getResponseTime() <= 0 && newHost.getResponseTime() > 0) {
            existing.setResponseTime(newHost.getResponseTime());
        } else if (newHost.getResponseTime() > 0 && newHost.getResponseTime() < existing.getResponseTime()) {
            existing.setResponseTime(newHost.getResponseTime());
        }
        
        // Merge open ports
        for (Integer port : newHost.getOpenPorts()) {
            existing.addOpenPort(port);
        }
        
        // Merge services
        for (String service : newHost.getServices()) {
            existing.addService(service);
        }
        
        // Merge vulnerabilities
        for (String vuln : newHost.getVulnerabilities()) {
            existing.addVulnerability(vuln);
        }
        
        // Merge network path
        if (existing.getNetworkPath().isEmpty() && !newHost.getNetworkPath().isEmpty()) {
            existing.setNetworkPath(newHost.getNetworkPath());
        }
        
        // Add discovery method
        existing.addService("Also found via " + method);
        
        // Set alive status
        existing.setAlive(existing.isAlive() || newHost.isAlive());
    }
    
    /**
     * Enhance host information with additional analysis
     */
    private void enhanceHostInformation(Map<String, NetworkHost> hosts, Consumer<String> progressCallback) {
        int processed = 0;
        int total = hosts.size();
        
        for (NetworkHost host : hosts.values()) {
            try {
                // Enhanced device classification
                classifyDevice(host);
                
                // Enhanced service analysis
                analyzeServices(host);
                
                // Network role detection
                detectNetworkRole(host);
                
                processed++;
                if (processed % 5 == 0) {
                    progressCallback.accept("📈 Enhanced analysis: " + processed + "/" + total + " hosts");
                }
                
            } catch (Exception e) {
                logger.debug("Error enhancing host information for {}", host.getIpAddress(), e);
            }
        }
    }
    
    /**
     * Classify device type based on all available information
     */
    private void classifyDevice(NetworkHost host) {
        String vendor = host.getVendor();
        String hostname = host.getHostname();
        Set<String> services = new HashSet<>(host.getServices());
        Set<Integer> ports = new HashSet<>(host.getOpenPorts());
        
        // Router/Gateway detection
        if (host.getIpAddress().endsWith(".1") || host.getIpAddress().endsWith(".254")) {
            host.addService("🌐 Likely Gateway/Router");
        }
        
        // Vendor-based classification
        if (vendor != null) {
            String vendorLower = vendor.toLowerCase();
            if (vendorLower.contains("cisco") || vendorLower.contains("netgear") || 
                vendorLower.contains("tp-link") || vendorLower.contains("linksys") ||
                vendorLower.contains("d-link") || vendorLower.contains("asus")) {
                host.addService("🔧 Network Equipment (" + vendor + ")");
            } else if (vendorLower.contains("apple")) {
                host.addService("🍎 Apple Device");
            } else if (vendorLower.contains("samsung")) {
                host.addService("📱 Samsung Device");
            } else if (vendorLower.contains("intel")) {
                host.addService("💻 Intel-based Device");
            } else if (vendorLower.contains("vmware") || vendorLower.contains("virtualbox")) {
                host.addService("🖥️ Virtual Machine");
            }
        }
        
        // Hostname-based classification
        if (hostname != null) {
            String hostnameLower = hostname.toLowerCase();
            if (hostnameLower.contains("router") || hostnameLower.contains("gateway")) {
                host.addService("🌐 Router/Gateway");
            } else if (hostnameLower.contains("printer")) {
                host.addService("🖨️ Network Printer");
            } else if (hostnameLower.contains("camera") || hostnameLower.contains("cam")) {
                host.addService("📹 IP Camera");
            } else if (hostnameLower.contains("nas") || hostnameLower.contains("storage")) {
                host.addService("💾 Network Storage");
            }
        }
        
        // Service-based classification
        if (services.stream().anyMatch(s -> s.toLowerCase().contains("dhcp"))) {
            host.addService("🔧 DHCP Server");
        }
        if (services.stream().anyMatch(s -> s.toLowerCase().contains("dns"))) {
            host.addService("🔧 DNS Server");
        }
        if (services.stream().anyMatch(s -> s.toLowerCase().contains("web") || s.toLowerCase().contains("http"))) {
            host.addService("🌐 Web Server");
        }
        
        // Port-based classification
        if (ports.contains(3389)) {
            host.addService("🖥️ Windows RDP Server");
        }
        if (ports.contains(22)) {
            host.addService("🔒 SSH Server");
        }
        if (ports.contains(5900)) {
            host.addService("🖥️ VNC Server");
        }
        if (ports.contains(631)) {
            host.addService("🖨️ CUPS Printer");
        }
    }
    
    /**
     * Analyze services for additional insights
     */
    private void analyzeServices(NetworkHost host) {
        Set<Integer> ports = new HashSet<>(host.getOpenPorts());
        
        // Database servers
        if (ports.contains(3306)) {
            host.addService("🗄️ MySQL Database");
        }
        if (ports.contains(5432)) {
            host.addService("🗄️ PostgreSQL Database");
        }
        if (ports.contains(1433)) {
            host.addService("🗄️ SQL Server Database");
        }
        
        // Mail servers
        if (ports.contains(25)) {
            host.addService("📧 SMTP Mail Server");
        }
        if (ports.contains(110)) {
            host.addService("📧 POP3 Mail Server");
        }
        if (ports.contains(143)) {
            host.addService("📧 IMAP Mail Server");
        }
        
        // File servers
        if (ports.contains(21)) {
            host.addService("📁 FTP Server");
        }
        if (ports.contains(445)) {
            host.addService("📁 SMB File Server");
        }
        if (ports.contains(2049)) {
            host.addService("📁 NFS Server");
        }
        
        // Media servers
        if (ports.contains(8080)) {
            host.addService("🎵 Media Server (Alt HTTP)");
        }
        if (ports.contains(32400)) {
            host.addService("🎵 Plex Media Server");
        }
    }
    
    /**
     * Detect network role based on characteristics
     */
    private void detectNetworkRole(NetworkHost host) {
        String ip = host.getIpAddress();
        Set<String> services = new HashSet<>(host.getServices());
        
        // Critical infrastructure detection
        if (services.stream().anyMatch(s -> s.toLowerCase().contains("dhcp")) ||
            services.stream().anyMatch(s -> s.toLowerCase().contains("dns")) ||
            ip.endsWith(".1") || ip.endsWith(".254")) {
            host.addService("⚡ Critical Network Infrastructure");
        }
        
        // Server detection
        if (host.getOpenPorts().size() > 5) {
            host.addService("🖥️ Server (Multiple Services)");
        }
        
        // Workstation detection
        if (host.getOpenPorts().size() <= 3 && host.getVendor() != null &&
            (host.getVendor().toLowerCase().contains("intel") || 
             host.getVendor().toLowerCase().contains("apple"))) {
            host.addService("💻 Workstation/Client");
        }
    }
    
    /**
     * Perform security assessment of discovered hosts
     */
    private void performSecurityAssessment(Map<String, NetworkHost> hosts, Consumer<String> progressCallback) {
        int vulnerableHosts = 0;
        int criticalHosts = 0;
        
        for (NetworkHost host : hosts.values()) {
            List<String> vulnerabilities = new ArrayList<>();
            int riskScore = 0;
            
            // Check for insecure services
            for (String service : host.getServices()) {
                String serviceLower = service.toLowerCase();
                if (serviceLower.contains("telnet")) {
                    vulnerabilities.add("🚨 Insecure Telnet service");
                    riskScore += 40;
                }
                if (serviceLower.contains("ftp") && !serviceLower.contains("sftp")) {
                    vulnerabilities.add("⚠️ Insecure FTP service");
                    riskScore += 30;
                }
                if (serviceLower.contains("http") && !serviceLower.contains("https")) {
                    vulnerabilities.add("⚠️ Unencrypted HTTP service");
                    riskScore += 20;
                }
                if (serviceLower.contains("smtp") && !serviceLower.contains("secure")) {
                    vulnerabilities.add("⚠️ Unencrypted SMTP service");
                    riskScore += 15;
                }
            }
            
            // Check for excessive open ports
            if (host.getOpenPorts().size() > 15) {
                vulnerabilities.add("⚠️ Excessive open ports (" + host.getOpenPorts().size() + ")");
                riskScore += 25;
            }
            
            // Check for dangerous ports
            Set<Integer> dangerousPorts = Set.of(23, 135, 139, 445, 1433, 3389, 5900);
            for (Integer port : host.getOpenPorts()) {
                if (dangerousPorts.contains(port)) {
                    vulnerabilities.add("🚨 Dangerous port exposed: " + port);
                    riskScore += 20;
                }
            }
            
            // Set risk level and vulnerabilities
            if (riskScore >= 60) {
                host.setRiskLevel("CRITICAL");
                criticalHosts++;
            } else if (riskScore >= 40) {
                host.setRiskLevel("HIGH");
                vulnerableHosts++;
            } else if (riskScore >= 20) {
                host.setRiskLevel("MEDIUM");
                vulnerableHosts++;
            } else if (riskScore >= 10) {
                host.setRiskLevel("LOW");
            } else {
                host.setRiskLevel("MINIMAL");
            }
            
            host.setVulnerabilities(vulnerabilities);
        }
        
        progressCallback.accept("🛡️ Security assessment completed");
        progressCallback.accept("⚠️ Vulnerable hosts: " + vulnerableHosts);
        progressCallback.accept("🚨 Critical hosts: " + criticalHosts);
    }
    
    /**
     * Display discovery summary
     */
    private void displayDiscoverySummary(Map<String, NetworkHost> hosts, Consumer<String> progressCallback) {
        progressCallback.accept("📊 === DISCOVERY SUMMARY ===");
        
        // Count by device types
        Map<String, Long> deviceTypes = hosts.values().stream()
            .flatMap(h -> h.getServices().stream())
            .filter(s -> s.contains("🔧") || s.contains("🌐") || s.contains("💻") || 
                        s.contains("🖥️") || s.contains("📱") || s.contains("🍎"))
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        
        progressCallback.accept("Device Types Found:");
        deviceTypes.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> progressCallback.accept("  " + entry.getKey() + ": " + entry.getValue()));
        
        // Count by vendor
        Map<String, Long> vendors = hosts.values().stream()
            .filter(h -> h.getVendor() != null && !h.getVendor().equals("Unknown"))
            .collect(Collectors.groupingBy(NetworkHost::getVendor, Collectors.counting()));
        
        if (!vendors.isEmpty()) {
            progressCallback.accept("Top Vendors:");
            vendors.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> progressCallback.accept("  " + entry.getKey() + ": " + entry.getValue()));
        }
        
        // Risk summary
        Map<String, Long> riskLevels = hosts.values().stream()
            .collect(Collectors.groupingBy(NetworkHost::getRiskLevel, Collectors.counting()));
        
        progressCallback.accept("Risk Assessment:");
        riskLevels.forEach((risk, count) -> 
            progressCallback.accept("  " + risk + ": " + count + " hosts"));
    }
    
    /**
     * Stop all scanning operations
     */
    public void stopAllScanning() {
        packetCapture.stopCapture();
        advancedScanner.stopScan();
    }
    
    /**
     * Get traffic statistics from packet capture
     */
    public Map<String, Long> getTrafficStatistics() {
        return packetCapture.getTrafficStatistics();
    }
    
    /**
     * Check if advanced packet capture is available
     */
    public boolean isAdvancedScanningAvailable() {
        return packetCapture.isPacketCaptureSupported();
    }
} 