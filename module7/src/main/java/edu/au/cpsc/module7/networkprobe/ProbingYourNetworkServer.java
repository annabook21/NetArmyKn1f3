package edu.au.cpsc.module7.networkprobe;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.text.SimpleDateFormat;

public class ProbingYourNetworkServer {
    private static final int BUFFER_SIZE = 1470; // Max UDP packet size
    private static final int DEFAULT_THREAD_POOL_SIZE = 10;
    
    // Statistics tracking
    private static final Map<String, ClientStats> clientStats = new ConcurrentHashMap<>();
    private static final AtomicLong totalUdpPackets = new AtomicLong(0);
    private static final AtomicLong totalTcpPackets = new AtomicLong(0);
    private static boolean verbose = false;
    private static boolean running = true;
    
    public static void main(String[] args) {
        int udpPort = 5001;
        int tcpPort = 5002;
        int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            try {
                switch (args[i]) {
                    case "-u":
                    case "--udp-port":
                        udpPort = Integer.parseInt(args[++i]);
                        break;
                    case "-t":
                    case "--tcp-port":
                        tcpPort = Integer.parseInt(args[++i]);
                        break;
                    case "-p":
                    case "--pool-size":
                        threadPoolSize = Integer.parseInt(args[++i]);
                        break;
                    case "-v":
                    case "--verbose":
                        verbose = true;
                        break;
                    case "-h":
                    case "--help":
                        printHelp();
                        return;
                    default:
                        System.err.println("Unknown option: " + args[i]);
                        printHelp();
                        return;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number for option: " + args[i-1]);
                printHelp();
                return;
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("Missing value for option: " + args[i]);
                printHelp();
                return;
            }
        }
        
        // Create thread pools
        ExecutorService mainExecutor = Executors.newFixedThreadPool(2);
        ThreadPoolExecutor clientThreadPool = new ThreadPoolExecutor(
                threadPoolSize, threadPoolSize * 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            running = false;
            
            mainExecutor.shutdownNow();
            clientThreadPool.shutdownNow();
            
            try {
                if (!mainExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Main executor did not terminate");
                }
                if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Client thread pool did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            printFinalStats();
        }));
        
        // Start the status reporter thread
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(ProbingYourNetworkServer::printStats, 10, 10, TimeUnit.SECONDS);
        
        // Start server components
        System.out.println("Starting ProbingYourNetworkServer...");
        System.out.println("UDP Server Port: " + udpPort);
        System.out.println("TCP Server Port: " + tcpPort);
        System.out.println("Thread Pool Size: " + threadPoolSize);
        System.out.println("Verbose Mode: " + (verbose ? "ON" : "OFF"));
        System.out.println("Press Ctrl+C to stop the server");

        final int finalUdpPort = udpPort;
        final int finalTcpPort = tcpPort;

        // Submit server tasks
        Future<?> udpFuture = mainExecutor.submit(() -> runUDPServer(finalUdpPort));
        Future<?> tcpFuture = mainExecutor.submit(() -> runTCPServer(finalTcpPort, clientThreadPool));
        
        // Wait for servers to exit (which should only happen on errors)
        try {
            udpFuture.get();
            tcpFuture.get();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            // Clean shutdown
            running = false;
            scheduler.shutdownNow();
            mainExecutor.shutdownNow();
            clientThreadPool.shutdownNow();
        }
    }
    
    private static void printHelp() {
        System.out.println("Usage: java ProbingYourNetworkServer [options]");
        System.out.println("Options:");
        System.out.println("  -u, --udp-port <port>     UDP port (default: 5001)");
        System.out.println("  -t, --tcp-port <port>     TCP port (default: 5002)");
        System.out.println("  -p, --pool-size <size>    Thread pool size (default: 10)");
        System.out.println("  -v, --verbose             Enable verbose logging");
        System.out.println("  -h, --help                Show this help message");
    }
    
    private static void runUDPServer(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(1000); // FIX: Set a 1-second timeout
            System.out.println("UDP Server listening on port " + port);
            byte[] buffer = new byte[BUFFER_SIZE];
            
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);  // Block until a packet is received
                    
                    // Update statistics
                    totalUdpPackets.incrementAndGet();
                    updateClientStats(packet.getAddress().getHostAddress(), "UDP", packet.getLength());
                    
                    if (verbose) {
                        System.out.println("UDP packet received from " + packet.getAddress() + ":" + 
                                packet.getPort() + " (" + packet.getLength() + " bytes)");
                    }
                    
                    // Echo back the data
                    socket.send(new DatagramPacket(
                            packet.getData(), 
                            packet.getLength(), 
                            packet.getAddress(), 
                            packet.getPort()));
                    
                } catch (SocketTimeoutException e) {
                    // Timeout is normal, just continue to check the 'running' flag
                } catch (IOException e) {
                    if (running) {
                        System.err.println("UDP Server error: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Failed to start UDP server on port " + port + ": " + e.getMessage());
        }
    }
    
    private static void runTCPServer(int port, ExecutorService clientThreadPool) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server listening on port " + port);
            serverSocket.setSoTimeout(1000); // Allow checking running flag periodically
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientThreadPool.submit(() -> handleTCPClient(clientSocket));
                } catch (SocketTimeoutException e) {
                    // Timeout is normal, just continue
                } catch (IOException e) {
                    if (running) {
                        System.err.println("TCP Server accept error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start TCP server on port " + port + ": " + e.getMessage());
        }
    }
    
    private static void handleTCPClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        
        try (
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream()
        ) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;
            
            if (verbose) {
                System.out.println("TCP client connected: " + clientAddress);
            }
            
            while (running && (bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);  // Echo back
                out.flush();
                
                // Update statistics
                totalTcpPackets.incrementAndGet();
                totalBytes += bytesRead;
                updateClientStats(clientAddress, "TCP", bytesRead);
                
                if (verbose) {
                    System.out.println("TCP packet from " + clientAddress + " (" + bytesRead + " bytes)");
                }
            }
            
            if (verbose) {
                System.out.println("TCP client disconnected: " + clientAddress + 
                        " (total bytes: " + totalBytes + ")");
            }
        } catch (IOException e) {
            if (running) {
                 System.err.println("TCP client error for " + clientAddress + ": " + e.getMessage());
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    private static void updateClientStats(String clientAddress, String protocol, int packetSize) {
        ClientStats stats = clientStats.computeIfAbsent(clientAddress, k -> new ClientStats(clientAddress));
        
        if ("TCP".equals(protocol)) {
            stats.tcpPackets++;
            stats.tcpBytes += packetSize;
        } else {
            stats.udpPackets++;
            stats.udpBytes += packetSize;
        }
        
        stats.lastSeen = System.currentTimeMillis();
    }
    
    private static void printStats() {
        if (!verbose) return;
        
        System.out.println("\n=== Server Statistics ===");
        System.out.println("Active since: " + formatTime(System.currentTimeMillis()));
        System.out.println("Total UDP packets: " + totalUdpPackets.get());
        System.out.println("Total TCP packets: " + totalTcpPackets.get());
        System.out.println("Connected clients: " + clientStats.size());
        
        // Clean up old clients (inactive for more than 5 minutes)
        long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000);
        clientStats.entrySet().removeIf(entry -> entry.getValue().lastSeen < cutoffTime);
    }
    
    private static void printFinalStats() {
        System.out.println("\n=== Final Server Statistics ===");
        System.out.println("Total UDP packets processed: " + totalUdpPackets.get());
        System.out.println("Total TCP packets processed: " + totalTcpPackets.get());
        System.out.println("Unique clients served: " + clientStats.size());
        
        if (!clientStats.isEmpty()) {
            System.out.println("\nClient Details:");
            clientStats.values().stream()
                    .sorted(Comparator.comparing(stats -> stats.clientAddress))
                    .forEach(stats -> {
                        System.out.println("  " + stats.clientAddress + ":");
                        System.out.println("    UDP: " + stats.udpPackets + " packets (" + formatBytes(stats.udpBytes) + ")");
                        System.out.println("    TCP: " + stats.tcpPackets + " packets (" + formatBytes(stats.tcpBytes) + ")");
                        System.out.println("    Last seen: " + formatTime(stats.lastSeen));
                    });
        }
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private static String formatTime(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timeMillis));
    }
    
    static class ClientStats {
        final String clientAddress;
        long udpPackets = 0;
        long tcpPackets = 0;
        long udpBytes = 0;
        long tcpBytes = 0;
        long lastSeen;
        
        ClientStats(String clientAddress) {
            this.clientAddress = clientAddress;
            this.lastSeen = System.currentTimeMillis();
        }
    }
} 