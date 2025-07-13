package edu.au.cpsc.module7.networkprobe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * PublicServerProbe - Probes public servers using standard protocols and services
 * Unlike ProbingYourNetworkClient, this doesn't require custom server software
 */
public class PublicServerProbe {
    
    private static final Map<Integer, String> COMMON_SERVICES = new HashMap<>() {{
        put(21, "FTP"); put(22, "SSH"); put(23, "Telnet"); put(25, "SMTP"); put(53, "DNS");
        put(80, "HTTP"); put(110, "POP3"); put(143, "IMAP"); put(443, "HTTPS"); put(993, "IMAPS");
        put(995, "POP3S"); put(587, "SMTP-TLS"); put(465, "SMTP-SSL"); put(3389, "RDP");
    }};
    
    private static final int[] DEFAULT_PORTS = {21, 22, 23, 25, 53, 80, 110, 143, 443, 993, 995, 587, 465, 3389};
    private static final int DEFAULT_TIMEOUT = 3000; // 3 seconds
    
    private String targetHost;
    private int timeout = DEFAULT_TIMEOUT;
    private boolean verbose = false;
    
    public static void main(String[] args) {
        PublicServerProbe probe = new PublicServerProbe();
        
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            printHelp();
            return;
        }
        
        try {
            probe.parseArgs(args);
            probe.runProbe();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printHelp();
        }
    }
    
    private static void printHelp() {
        System.out.println("Usage: java PublicServerProbe [options]");
        System.out.println("Options:");
        System.out.println("  -h, --host <address>      Target host (required)");
        System.out.println("  -p, --ports <port1,port2> Specific ports to test (default: common ports)");
        System.out.println("  -t, --timeout <ms>        Connection timeout in milliseconds (default: 3000)");
        System.out.println("  -v, --verbose             Verbose output");
        System.out.println("  --ping-only               Only perform ping test");
        System.out.println("  --http-check              Perform HTTP/HTTPS checks");
        System.out.println("  --dns-check               Perform DNS resolution checks");
        System.out.println("  --traceroute              Perform traceroute");
        System.out.println("Examples:");
        System.out.println("  java PublicServerProbe -h google.com");
        System.out.println("  java PublicServerProbe -h 8.8.8.8 --ping-only");
        System.out.println("  java PublicServerProbe -h github.com --http-check");
    }
    
    private void parseArgs(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--host":
                    targetHost = args[++i];
                    break;
                case "-t":
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
                case "-v":
                case "--verbose":
                    verbose = true;
                    break;
                // We'll handle other options in the main probe logic
            }
        }
        
        if (targetHost == null) {
            throw new IllegalArgumentException("Target host is required");
        }
    }
    
    private void runProbe() {
        System.out.println("=== Public Server Probe ===");
        System.out.println("Target: " + targetHost);
        System.out.println("Timeout: " + timeout + "ms");
        System.out.println();
        
        // 1. Basic connectivity test
        testConnectivity();
        
        // 2. DNS resolution test
        testDNSResolution();
        
        // 3. Port scanning for common services
        testCommonPorts();
        
        // 4. HTTP/HTTPS specific tests
        testWebServices();
        
        // 5. Traceroute
        performTraceroute();
        
        System.out.println("\n=== Probe Complete ===");
    }
    
    private void testConnectivity() {
        System.out.println("🔍 Testing basic connectivity...");
        
        try {
            InetAddress addr = InetAddress.getByName(targetHost);
            System.out.println("  IP Address: " + addr.getHostAddress());
            
            // Test reachability (ICMP ping if available)
            long startTime = System.currentTimeMillis();
            boolean reachable = addr.isReachable(timeout);
            long pingTime = System.currentTimeMillis() - startTime;
            
            if (reachable) {
                System.out.println("  ✅ Host is reachable (ping: " + pingTime + "ms)");
            } else {
                System.out.println("  ⚠️ Host ping failed (may be firewalled)");
                
                // Try alternative connectivity test via port 80
                if (testPortConnectivity(80)) {
                    System.out.println("  ✅ Alternative connectivity via port 80 successful");
                } else if (testPortConnectivity(443)) {
                    System.out.println("  ✅ Alternative connectivity via port 443 successful");
                } else {
                    System.out.println("  ❌ No connectivity detected");
                }
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ Connectivity test failed: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private void testDNSResolution() {
        System.out.println("🔍 Testing DNS resolution...");
        
        try {
            InetAddress[] addresses = InetAddress.getAllByName(targetHost);
            System.out.println("  ✅ DNS resolution successful");
            System.out.println("  Resolved addresses:");
            
            for (InetAddress addr : addresses) {
                System.out.println("    - " + addr.getHostAddress() + 
                    (addr.getCanonicalHostName().equals(addr.getHostAddress()) ? "" : 
                     " (" + addr.getCanonicalHostName() + ")"));
            }
            
            // Reverse DNS lookup
            try {
                String reverseDns = addresses[0].getCanonicalHostName();
                if (!reverseDns.equals(addresses[0].getHostAddress())) {
                    System.out.println("  Reverse DNS: " + reverseDns);
                }
            } catch (Exception e) {
                if (verbose) {
                    System.out.println("  Reverse DNS lookup failed: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ DNS resolution failed: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private void testCommonPorts() {
        System.out.println("🔍 Scanning common service ports...");
        
        List<Integer> openPorts = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int port : DEFAULT_PORTS) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (testPortConnectivity(port)) {
                    synchronized (openPorts) {
                        openPorts.add(port);
                    }
                    String service = COMMON_SERVICES.getOrDefault(port, "Unknown");
                    System.out.println("  ✅ Port " + port + " (" + service + ") - OPEN");
                } else if (verbose) {
                    String service = COMMON_SERVICES.getOrDefault(port, "Unknown");
                    System.out.println("  ❌ Port " + port + " (" + service + ") - CLOSED/FILTERED");
                }
            });
            futures.add(future);
        }
        
        // Wait for all port scans to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        Collections.sort(openPorts);
        System.out.println("  Summary: " + openPorts.size() + " open ports found: " + openPorts);
        System.out.println();
    }
    
    private boolean testPortConnectivity(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void testWebServices() {
        System.out.println("🔍 Testing web services...");
        
        // Test HTTP
        testHTTP(80, false);
        
        // Test HTTPS
        testHTTP(443, true);
        
        System.out.println();
    }
    
    private void testHTTP(int port, boolean ssl) {
        String protocol = ssl ? "HTTPS" : "HTTP";
        
        try {
            URL url = new URL((ssl ? "https" : "http") + "://" + targetHost + 
                             (port == (ssl ? 443 : 80) ? "" : ":" + port));
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            
            long startTime = System.currentTimeMillis();
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            System.out.println("  ✅ " + protocol + " (port " + port + ") - Response: " + 
                             responseCode + " (" + responseTime + "ms)");
            
            // Get some headers
            String server = connection.getHeaderField("Server");
            String contentType = connection.getHeaderField("Content-Type");
            
            if (server != null) {
                System.out.println("    Server: " + server);
            }
            if (contentType != null) {
                System.out.println("    Content-Type: " + contentType);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            if (verbose) {
                System.out.println("  ❌ " + protocol + " (port " + port + ") - Failed: " + e.getMessage());
            } else {
                System.out.println("  ❌ " + protocol + " (port " + port + ") - Not available");
            }
        }
    }
    
    private void performTraceroute() {
        System.out.println("🔍 Performing traceroute...");
        
        try {
            String command;
            String[] args;
            
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                command = "tracert";
                args = new String[]{command, "-h", "15", targetHost};
            } else {
                command = "traceroute";
                args = new String[]{command, "-m", "15", targetHost};
            }
            
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int hopCount = 0;
                while ((line = reader.readLine()) != null && hopCount < 15) {
                    System.out.println("  " + line);
                    if (line.trim().matches("^\\s*\\d+.*")) {
                        hopCount++;
                    }
                }
            }
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.out.println("  ⚠️ Traceroute timed out");
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ Traceroute failed: " + e.getMessage());
            
            // Fallback: Simple hop-by-hop ping test
            System.out.println("  Attempting simple connectivity test...");
            for (int ttl = 1; ttl <= 10; ttl++) {
                try {
                    long startTime = System.currentTimeMillis();
                    InetAddress addr = InetAddress.getByName(targetHost);
                    boolean reachable = addr.isReachable(1000);
                    long time = System.currentTimeMillis() - startTime;
                    
                    if (reachable) {
                        System.out.println("  " + ttl + ": " + targetHost + " (" + time + "ms)");
                        break;
                    }
                } catch (Exception ex) {
                    // Continue to next TTL
                }
            }
        }
    }
} 