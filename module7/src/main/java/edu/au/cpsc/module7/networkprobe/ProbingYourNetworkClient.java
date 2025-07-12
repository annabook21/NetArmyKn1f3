package edu.au.cpsc.module7.networkprobe;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProbingYourNetworkClient {
    // Configuration constants
    private static final int[] DEFAULT_PACKET_SIZES = {64, 512, 1024, 1470}; // in bytes
    private static final int DEFAULT_TEST_DURATION = 30; // seconds
    private static final int DEFAULT_BURST_SIZE = 10;
    private static final int DEFAULT_MAX_TTL = 30; // for traceroute
    private static final int DEFAULT_TIMEOUT = 1000; // ms
    
    // Test parameters
    private String serverAddress;
    private int udpPort;
    private int tcpPort;
    private int duration = DEFAULT_TEST_DURATION;
    private int burstSize = DEFAULT_BURST_SIZE;
    private int[] packetSizes = DEFAULT_PACKET_SIZES;
    private boolean continuous = false;
    private boolean runTraceroute = true;
    private int maxTTL = DEFAULT_MAX_TTL;
    private int timeout = DEFAULT_TIMEOUT;
    private String[] args;
    
    // Test results storage
    private Map<String, TestResult> results = new LinkedHashMap<>();
    private AtomicBoolean stopRequested = new AtomicBoolean(false);
    
    public static void main(String[] args) {
        ProbingYourNetworkClient client = new ProbingYourNetworkClient();
        client.args = args;
        
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            printHelp();
            return;
        }
        
        try {
            // Parse command line arguments
            client.parseArgs(args);
            
            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down... (Press Ctrl+C again to force exit)");
                client.stopRequested.set(true);
            }));
            
            // Run the tests
            client.runTests();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printHelp();
        }
    }
    
    private static void printHelp() {
        System.out.println("Usage: java ProbingYourNetworkClient [options]");
        System.out.println("Options:");
        System.out.println("  -s, --server <address>    Server address (required)");
        System.out.println("  -u, --udp-port <port>     UDP port (required)");
        System.out.println("  -t, --tcp-port <port>     TCP port (required)");
        System.out.println("  -p, --protocol <tcp|udp>  Protocol to test (default: both)");
        System.out.println("  -d, --duration <seconds>  Test duration in seconds (default: 30)");
        System.out.println("  -b, --burst <size>        Burst size (default: 10)");
        System.out.println("  -ps, --packet-sizes <s1,s2,...>  Packet sizes in bytes (default: 64,512,1024,1470)");
        System.out.println("  -c, --continuous          Run tests continuously");
        System.out.println("  --no-traceroute           Skip traceroute test");
        System.out.println("  --timeout <ms>            Socket timeout in milliseconds (default: 1000)");
        System.out.println("Examples:");
        System.out.println("  java ProbingYourNetworkClient -s example.com -u 5001 -t 5002 -p tcp");
        System.out.println("  java ProbingYourNetworkClient -s 192.168.1.1 -u 5001 -t 5002 -d 60 -c");
    }
    
    private void parseArgs(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-s":
                case "--server":
                    serverAddress = args[++i];
                    break;
                case "-u":
                case "--udp-port":
                    udpPort = Integer.parseInt(args[++i]);
                    break;
                case "-t":
                case "--tcp-port":
                    tcpPort = Integer.parseInt(args[++i]);
                    break;
                case "-p":
                case "--protocol":
                    // Protocol will be handled in the test execution
                    i++; // consume the value
                    break;
                case "-d":
                case "--duration":
                    duration = Integer.parseInt(args[++i]);
                    break;
                case "-b":
                case "--burst":
                    burstSize = Integer.parseInt(args[++i]);
                    break;
                case "-ps":
                case "--packet-sizes":
                    packetSizes = Arrays.stream(args[++i].split(","))
                            .mapToInt(Integer::parseInt)
                            .toArray();
                    break;
                case "-c":
                case "--continuous":
                    continuous = true;
                    break;
                case "--no-traceroute":
                    runTraceroute = false;
                    break;
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        
        // Validate required parameters
        if (serverAddress == null) {
            throw new IllegalArgumentException("Server address is required");
        }
        if (udpPort <= 0) {
            throw new IllegalArgumentException("Valid UDP port is required");
        }
        if (tcpPort <= 0) {
            throw new IllegalArgumentException("Valid TCP port is required");
        }
    }
    
    private void runTests() {
        boolean runUdp = true;
        boolean runTcp = true;
        
        // Check if a specific protocol was requested
        for (int i = 0; i < this.args.length - 1; i++) {
            if ("-p".equals(this.args[i]) || "--protocol".equals(this.args[i])) {
                String protocol = this.args[i + 1].toLowerCase();
                if ("udp".equals(protocol)) {
                    runTcp = false;
                } else if ("tcp".equals(protocol)) {
                    runUdp = false;
                }
                break;
            }
        }
        
        do {
            System.out.println("=== Starting Network Tests ===");
            System.out.println("Server: " + serverAddress);
            System.out.println("Duration: " + duration + " seconds per test");
            System.out.println("Packet sizes: " + Arrays.toString(packetSizes));
            System.out.println();
            
            // Run TCP tests if requested
            if (runTcp) {
                for (int packetSize : packetSizes) {
                    if (stopRequested.get()) break;
                    
                    String testId = "TCP-" + packetSize;
                    System.out.println("Running TCP test with " + packetSize + " byte packets...");
                    TestResult result = runTCPTest(packetSize);
                    results.put(testId, result);
                    printTestResult(testId, result);
                }
            }
            
            // Run UDP tests if requested
            if (runUdp) {
                for (int packetSize : packetSizes) {
                    if (stopRequested.get()) break;
                    
                    String testId = "UDP-" + packetSize;
                    System.out.println("Running UDP test with " + packetSize + " byte packets...");
                    TestResult result = runUDPTest(packetSize);
                    results.put(testId, result);
                    printTestResult(testId, result);
                }
                
                // Run traceroute after UDP tests if requested
                if (runTraceroute && !stopRequested.get()) {
                    System.out.println("\n=== UDP Traceroute ===");
                    runRealUDPTraceroute();
                }
            }
            
            if (continuous && !stopRequested.get()) {
                System.out.println("\n=== Test cycle completed, starting next cycle... ===\n");
                try {
                    Thread.sleep(5000); // 5-second pause between cycles
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopRequested.set(true);
                }
            }
            
        } while (continuous && !stopRequested.get());
        
        if (results.size() > 0) {
            System.out.println("\n=== Summary of All Tests ===");
            results.forEach(this::printTestResult);
        }
    }
    
    private TestResult runTCPTest(int packetSize) {
        TestResult result = new TestResult(packetSize);
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverAddress, tcpPort), timeout);
            socket.setSoTimeout(timeout);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            
            runTest(packetSize, result, (data) -> {
                out.write(data);
                out.flush();
                
                byte[] receiveData = new byte[packetSize];
                int bytesRead = 0;
                int totalRead = 0;
                
                // Read until we get the full packet or timeout
                while (totalRead < packetSize) {
                    bytesRead = in.read(receiveData, totalRead, packetSize - totalRead);
                    if (bytesRead == -1) break;
                    totalRead += bytesRead;
                }
                
                return totalRead == packetSize;
            });
            
        } catch (Exception e) {
            System.err.println("TCP Test Error: " + e.getMessage());
            result.errors.add(e.toString());
        }
        
        return result;
    }
    
    private TestResult runUDPTest(int packetSize) {
        TestResult result = new TestResult(packetSize);
        
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeout);
            InetAddress serverAddr = InetAddress.getByName(serverAddress);
            
            runTest(packetSize, result, (data) -> {
                DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, udpPort);
                socket.send(packet);
                
                byte[] receiveData = new byte[packetSize];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                
                try {
                    socket.receive(receivePacket);
                    return receivePacket.getLength() == packetSize;
                } catch (SocketTimeoutException e) {
                    return false;
                }
            });
            
        } catch (Exception e) {
            System.err.println("UDP Test Error: " + e.getMessage());
            result.errors.add(e.toString());
        }
        
        return result;
    }
    
    private void runTest(int packetSize, TestResult result, PacketSender packetSender) throws Exception {
        byte[] data = new byte[packetSize];
        Arrays.fill(data, (byte) 'A');
        
        // Used to track per-packet metrics
        long previousRtt = 0;
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (duration * 1000);
        
        while (System.currentTimeMillis() < endTime && !stopRequested.get()) {
            // Progress indicator
            double progress = (double)(System.currentTimeMillis() - startTime) / (duration * 1000);
            if (result.packetsSent % 50 == 0) {
                printProgressBar(progress);
            }
            
            // Burst mode
            for (int i = 0; i < burstSize && System.currentTimeMillis() < endTime && !stopRequested.get(); i++) {
                long sendTime = System.nanoTime();
                boolean received = false;
                
                try {
                    received = packetSender.sendAndReceive(data);
                } catch (Exception e) {
                    result.errors.add("Packet send/receive error: " + e.getMessage());
                }
                
                result.packetsSent++;
                result.totalBytesSent += packetSize;
                
                if (received) {
                    long rtt = (System.nanoTime() - sendTime) / 1_000_000; // Convert to ms
                    result.roundTripTimes.add(rtt);
                    result.packetsReceived++;
                    
                    // Calculate jitter for consecutive successful packets
                    if (previousRtt > 0) {
                        result.jitterSamples.add((double) Math.abs(rtt - previousRtt));
                    }
                    previousRtt = rtt;
                }
            }
            
            // Small delay between bursts to avoid overwhelming the network
            try {
                Thread.sleep(50); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Complete the progress bar
        printProgressBar(1.0);
        System.out.println();
        
        // Update final test metrics
        result.testDurationMs = System.currentTimeMillis() - startTime;
    }
    
    private void printProgressBar(double progress) {
        final int barLength = 40;
        int completed = (int) (progress * barLength);
        System.out.print("\r[");
        for (int i = 0; i < barLength; i++) {
            System.out.print(i < completed ? "=" : " ");
        }
        System.out.print("] " + (int) (progress * 100) + "%");
    }
    
    private void printTestResult(String testId, TestResult result) {
        System.out.println("\n=== Results for " + testId + " ===");
        System.out.println("Packet Size: " + result.packetSize + " bytes");
        System.out.println("Test Duration: " + (result.testDurationMs / 1000.0) + " seconds");
        System.out.println("Packets Sent: " + result.packetsSent);
        System.out.println("Packets Received: " + result.packetsReceived);
        
        if (result.packetsSent > 0) {
            double packetLossRate = 100.0 * (result.packetsSent - result.packetsReceived) / result.packetsSent;
            System.out.println("Packet Loss Rate: " + String.format("%.2f%%", packetLossRate));
        }
        
        if (!result.roundTripTimes.isEmpty()) {
            Collections.sort(result.roundTripTimes);
            double avgRtt = result.roundTripTimes.stream().mapToLong(Long::longValue).average().getAsDouble();
            long minRtt = result.roundTripTimes.get(0);
            long maxRtt = result.roundTripTimes.get(result.roundTripTimes.size() - 1);
            long medianRtt = result.roundTripTimes.get(result.roundTripTimes.size() / 2);
            
            System.out.println("RTT (min/avg/max/median): " + 
                    minRtt + "/" + 
                    String.format("%.2f", avgRtt) + "/" + 
                    maxRtt + "/" + 
                    medianRtt + " ms");
            
            if (!result.jitterSamples.isEmpty()) {
                double avgJitter = result.jitterSamples.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
                System.out.println("Jitter: " + String.format("%.2f", avgJitter) + " ms");
            }
        }
        
        // Calculate throughput based on received data
        double throughputMbps = (result.packetsReceived * result.packetSize * 8.0) / (result.testDurationMs * 1000.0);
        System.out.println("Throughput: " + String.format("%.2f", throughputMbps) + " Mbps");
        
        if (!result.errors.isEmpty()) {
            System.out.println("Errors: " + result.errors.size());
            // Only print the first few errors to avoid overwhelming output
            for (int i = 0; i < Math.min(3, result.errors.size()); i++) {
                System.out.println("  - " + result.errors.get(i));
            }
            if (result.errors.size() > 3) {
                System.out.println("  - (" + (result.errors.size() - 3) + " more errors)");
            }
        }
    }
    
    /**
     * Runs a more reliable UDP traceroute using the ProcessBuilder to access system traceroute
     * Falls back to a simpler implementation if the system command isn't available
     */
    private void runRealUDPTraceroute() {
        boolean useSystemCommand = false;
        
        // Check if we can use the system's traceroute command
        try {
            Process process = new ProcessBuilder("which", "traceroute").start();
            useSystemCommand = process.waitFor() == 0;
        } catch (Exception e) {
            // Silently fail and use our implementation
        }
        
        if (useSystemCommand) {
            runSystemTraceroute();
        } else {
            runJavaTraceroute();
        }
    }
    
    private void runSystemTraceroute() {
        try {
            String command = "traceroute";
            // On Windows, use tracert instead
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                command = "tracert";
            }
            
            ProcessBuilder pb = new ProcessBuilder(command, "-m", String.valueOf(maxTTL), 
                    "-p", String.valueOf(udpPort), serverAddress);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read and display the output in real-time
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            
            process.waitFor(2, TimeUnit.MINUTES);
            
        } catch (Exception e) {
            System.err.println("System traceroute failed: " + e.getMessage());
            // Fall back to Java implementation
            runJavaTraceroute();
        }
    }
    
    private void runJavaTraceroute() {
        System.out.println("Running Java UDP traceroute to " + serverAddress + ":" + udpPort);
        System.out.println("Note: This is a best-effort implementation and may not show accurate hops.");
        
        try {
            // Use InetAddress.isReachable with decreasing TTL
            InetAddress destAddr = InetAddress.getByName(serverAddress);
            
            for (int ttl = 1; ttl <= maxTTL; ttl++) {
                System.out.print(ttl + ": ");
                
                try {
                    // We use ping with specific TTL to trace the route
                    long startTime = System.currentTimeMillis();
                    
                    // On Unix-based systems, we can use ping with TTL option
                    Process process;
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        process = Runtime.getRuntime().exec("ping -n 1 -i " + ttl + " " + serverAddress);
                    } else {
                        process = Runtime.getRuntime().exec("ping -c 1 -t " + ttl + " " + serverAddress);
                    }
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    String hopAddress = null;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("TTL expired") || line.contains("Time to live exceeded")) {
                            // Extract IP address
                            String[] parts = line.split(" ");
                            for (String part : parts) {
                                if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                    hopAddress = part;
                                    break;
                                }
                            }
                        }
                    }
                    
                    process.waitFor();
                    long endTime = System.currentTimeMillis();
                    
                    if (hopAddress != null) {
                        System.out.println(hopAddress + " (" + (endTime - startTime) + " ms)");
                    } else {
                        System.out.println("* * *");
                    }
                    
                    // If we've reached the destination, exit
                    if (hopAddress != null && hopAddress.equals(destAddr.getHostAddress())) {
                        break;
                    }
                    
                } catch (Exception e) {
                    System.out.println("* * * (Error: " + e.getMessage() + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("Error in traceroute: " + e.getMessage());
        }
    }
    
    @FunctionalInterface
    interface PacketSender {
        boolean sendAndReceive(byte[] data) throws IOException;
    }
    
    /**
     * Class to store test results
     */
    static class TestResult {
        int packetSize;
        int packetsSent = 0;
        int packetsReceived = 0;
        long totalBytesSent = 0;
        long testDurationMs = 0;
        List<Long> roundTripTimes = new ArrayList<>();
        List<Double> jitterSamples = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        TestResult(int packetSize) {
            this.packetSize = packetSize;
        }
    }
} 