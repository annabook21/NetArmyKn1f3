package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.CapturedPacket;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.util.NifSelector;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.EOFException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Service for capturing and analyzing network packets using pcap4j
 */
public class PacketCaptureService {
    
    private static final Logger logger = Logger.getLogger(PacketCaptureService.class.getName());
    
    private static final int SNAPSHOT_LENGTH = 65536; // 64KB
    private static final int READ_TIMEOUT = 10; // 10ms
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB
    
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final AtomicLong packetIdCounter = new AtomicLong(0);
    private final List<CapturedPacket> capturedPackets = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicLong> protocolCounters = new ConcurrentHashMap<>();
    
    private PcapHandle pcapHandle;
    private ExecutorService captureExecutor;
    private Consumer<CapturedPacket> packetListener;
    private String captureFilter = "";
    private LocalDateTime captureStartTime;
    private ProtocolDissectorService protocolDissector;
    
    public PacketCaptureService() {
        initializeProtocolCounters();
        this.protocolDissector = new ProtocolDissectorService();
    }
    
    private void initializeProtocolCounters() {
        protocolCounters.put("TCP", new AtomicLong(0));
        protocolCounters.put("UDP", new AtomicLong(0));
        protocolCounters.put("ICMP", new AtomicLong(0));
        protocolCounters.put("ARP", new AtomicLong(0));
        protocolCounters.put("HTTP", new AtomicLong(0));
        protocolCounters.put("HTTPS", new AtomicLong(0));
        protocolCounters.put("DNS", new AtomicLong(0));
        protocolCounters.put("DHCP", new AtomicLong(0));
        protocolCounters.put("OTHER", new AtomicLong(0));
    }
    
    /**
     * Get list of available network interfaces
     */
    public List<String> getAvailableInterfaces() {
        List<String> interfaces = new ArrayList<>();
        try {
            List<PcapNetworkInterface> nifs = Pcaps.findAllDevs();
            for (PcapNetworkInterface nif : nifs) {
                String name = nif.getName();
                String description = nif.getDescription();
                if (description != null && !description.isEmpty()) {
                    interfaces.add(name + " (" + description + ")");
                } else {
                    interfaces.add(name);
                }
            }
        } catch (PcapNativeException e) {
            logger.log(Level.SEVERE, "Error getting network interfaces: " + e.getMessage());
        }
        return interfaces;
    }
    
    /**
     * Start packet capture on specified interface
     */
    public boolean startCapture(String interfaceName, String filter) {
        if (capturing.get()) {
            logger.log(Level.WARNING, "Capture already in progress");
            return false;
        }
        
        try {
            // Extract interface name from display string
            String actualInterfaceName = interfaceName.split(" \\(")[0];
            
            // Find the network interface
            PcapNetworkInterface nif = null;
            List<PcapNetworkInterface> nifs = Pcaps.findAllDevs();
            for (PcapNetworkInterface n : nifs) {
                if (n.getName().equals(actualInterfaceName)) {
                    nif = n;
                    break;
                }
            }
            
            if (nif == null) {
                logger.log(Level.SEVERE, "Network interface not found: " + actualInterfaceName);
                return false;
            }
            
            // Open interface for capture
            pcapHandle = nif.openLive(SNAPSHOT_LENGTH, 
                                    PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 
                                    READ_TIMEOUT);
            
            // Buffer size is typically set during interface opening
            
            // Apply filter if specified
            if (filter != null && !filter.trim().isEmpty()) {
                pcapHandle.setFilter(filter.trim(), BpfProgram.BpfCompileMode.OPTIMIZE);
                this.captureFilter = filter.trim();
            } else {
                this.captureFilter = "";
            }
            
            // Clear previous capture data
            capturedPackets.clear();
            protocolCounters.values().forEach(counter -> counter.set(0));
            packetIdCounter.set(0);
            captureStartTime = LocalDateTime.now();
            
            // Start capture thread
            captureExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PacketCapture-" + actualInterfaceName);
                t.setDaemon(true);
                return t;
            });
            
            capturing.set(true);
            
            captureExecutor.submit(() -> {
                logger.info("Starting packet capture on interface: " + actualInterfaceName);
                
                try {
                    while (capturing.get()) {
                        try {
                            Packet packet = pcapHandle.getNextPacket();
                            if (packet != null) {
                                processPacket(packet);
                            }
                        } catch (Exception e) {
                            if (capturing.get()) {
                                logger.log(Level.SEVERE, "Error capturing packet", e);
                            }
                        }
                    }
                } finally {
                    logger.info("Packet capture stopped");
                }
            });
            
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error starting packet capture: " + e.getMessage());
            stopCapture();
            return false;
        }
    }
    
    /**
     * Stop packet capture
     */
    public void stopCapture() {
        if (!capturing.get()) {
            return;
        }
        
        capturing.set(false);
        
        try {
            if (pcapHandle != null) {
                pcapHandle.breakLoop();
                pcapHandle.close();
                pcapHandle = null;
            }
            
            if (captureExecutor != null) {
                captureExecutor.shutdown();
                if (!captureExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    captureExecutor.shutdownNow();
                }
                captureExecutor = null;
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error stopping packet capture: " + e.getMessage());
        }
        
        logger.info("Packet capture stopped. Total packets captured: " + capturedPackets.size());
    }
    
    /**
     * Process captured packet and extract information
     */
    private void processPacket(Packet packet) {
        try {
            long packetId = packetIdCounter.incrementAndGet();
            LocalDateTime timestamp = LocalDateTime.now();
            
            // Extract basic packet information
            String sourceAddress = "";
            String destinationAddress = "";
            int sourcePort = 0;
            int destinationPort = 0;
            String protocol = "UNKNOWN";
            String info = "";
            
            // Process Ethernet frame
            if (packet.contains(EthernetPacket.class)) {
                EthernetPacket ethernetPacket = packet.get(EthernetPacket.class);
                
                // Process IP packet
                if (ethernetPacket.getHeader().getType() == EtherType.IPV4) {
                    IpV4Packet ipv4Packet = ethernetPacket.get(IpV4Packet.class);
                    if (ipv4Packet != null) {
                        sourceAddress = ipv4Packet.getHeader().getSrcAddr().getHostAddress();
                        destinationAddress = ipv4Packet.getHeader().getDstAddr().getHostAddress();
                        
                        // Process transport layer
                        if (ipv4Packet.getHeader().getProtocol() == IpNumber.TCP) {
                            TcpPacket tcpPacket = ipv4Packet.get(TcpPacket.class);
                            if (tcpPacket != null) {
                                sourcePort = tcpPacket.getHeader().getSrcPort().valueAsInt();
                                destinationPort = tcpPacket.getHeader().getDstPort().valueAsInt();
                                protocol = "TCP";
                                info = String.format("TCP %d -> %d", sourcePort, destinationPort);
                                
                                // Check for HTTP/HTTPS
                                if (sourcePort == 80 || destinationPort == 80) {
                                    protocol = "HTTP";
                                    protocolCounters.get("HTTP").incrementAndGet();
                                } else if (sourcePort == 443 || destinationPort == 443) {
                                    protocol = "HTTPS";
                                    protocolCounters.get("HTTPS").incrementAndGet();
                                } else {
                                    protocolCounters.get("TCP").incrementAndGet();
                                }
                            }
                        } else if (ipv4Packet.getHeader().getProtocol() == IpNumber.UDP) {
                            UdpPacket udpPacket = ipv4Packet.get(UdpPacket.class);
                            if (udpPacket != null) {
                                sourcePort = udpPacket.getHeader().getSrcPort().valueAsInt();
                                destinationPort = udpPacket.getHeader().getDstPort().valueAsInt();
                                protocol = "UDP";
                                info = String.format("UDP %d -> %d", sourcePort, destinationPort);
                                
                                // Check for DNS
                                if (sourcePort == 53 || destinationPort == 53) {
                                    protocol = "DNS";
                                    protocolCounters.get("DNS").incrementAndGet();
                                } else if ((sourcePort == 67 || destinationPort == 67) || 
                                          (sourcePort == 68 || destinationPort == 68)) {
                                    protocol = "DHCP";
                                    protocolCounters.get("DHCP").incrementAndGet();
                                } else {
                                    protocolCounters.get("UDP").incrementAndGet();
                                }
                            }
                        } else if (ipv4Packet.getHeader().getProtocol() == IpNumber.ICMPV4) {
                            protocol = "ICMP";
                            info = "ICMP";
                            protocolCounters.get("ICMP").incrementAndGet();
                        }
                    }
                } else if (ethernetPacket.getHeader().getType() == EtherType.ARP) {
                    ArpPacket arpPacket = ethernetPacket.get(ArpPacket.class);
                    if (arpPacket != null) {
                        sourceAddress = arpPacket.getHeader().getSrcProtocolAddr().getHostAddress();
                        destinationAddress = arpPacket.getHeader().getDstProtocolAddr().getHostAddress();
                        protocol = "ARP";
                        info = String.format("ARP %s -> %s", sourceAddress, destinationAddress);
                        protocolCounters.get("ARP").incrementAndGet();
                    }
                }
            }
            
            // If we couldn't determine the protocol, count as OTHER
            if ("UNKNOWN".equals(protocol)) {
                protocolCounters.get("OTHER").incrementAndGet();
            }
            
            // Create captured packet
            CapturedPacket capturedPacket = new CapturedPacket(
                packetId, timestamp, sourceAddress, destinationAddress,
                sourcePort, destinationPort, protocol, packet.length(),
                packet.getRawData(), info
            );
            
            // Add additional protocol-specific analysis
            analyzeProtocolSpecifics(capturedPacket, packet);
            
            // Perform deep protocol analysis
            protocolDissector.performDeepAnalysis(capturedPacket, packet);
            
            // Store packet
            capturedPackets.add(capturedPacket);
            
            // Notify listener
            if (packetListener != null) {
                packetListener.accept(capturedPacket);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing packet", e);
        }
    }
    
    /**
     * Analyze protocol-specific details
     */
    private void analyzeProtocolSpecifics(CapturedPacket capturedPacket, Packet packet) {
        try {
            // HTTP analysis
            if (capturedPacket.isHTTP()) {
                analyzeHTTP(capturedPacket, packet);
            }
            
            // DNS analysis
            if (capturedPacket.isDNS()) {
                analyzeDNS(capturedPacket, packet);
            }
            
            // ARP analysis
            if (capturedPacket.isARP()) {
                analyzeARP(capturedPacket, packet);
            }
            
        } catch (Exception e) {
            logger.log(Level.FINE, "Error analyzing protocol specifics", e);
        }
    }
    
    private void analyzeHTTP(CapturedPacket capturedPacket, Packet packet) {
        try {
            // Extract HTTP information from payload
            String payload = new String(packet.getRawData());
            
            // Simple HTTP method detection
            if (payload.contains("GET ")) {
                capturedPacket.setHttpMethod("GET");
            } else if (payload.contains("POST ")) {
                capturedPacket.setHttpMethod("POST");
            } else if (payload.contains("PUT ")) {
                capturedPacket.setHttpMethod("PUT");
            } else if (payload.contains("DELETE ")) {
                capturedPacket.setHttpMethod("DELETE");
            }
            
            // Extract User-Agent
            if (payload.contains("User-Agent:")) {
                int start = payload.indexOf("User-Agent:") + 11;
                int end = payload.indexOf("\r\n", start);
                if (end > start) {
                    capturedPacket.setHttpUserAgent(payload.substring(start, end).trim());
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.FINE, "Error analyzing HTTP packet", e);
        }
    }
    
    private void analyzeDNS(CapturedPacket capturedPacket, Packet packet) {
        try {
            // Basic DNS analysis - would need more sophisticated parsing for full DNS analysis
            capturedPacket.addProtocolDetail("dns_analyzed", true);
        } catch (Exception e) {
            logger.log(Level.FINE, "Error analyzing DNS packet", e);
        }
    }
    
    private void analyzeARP(CapturedPacket capturedPacket, Packet packet) {
        try {
            if (packet.contains(ArpPacket.class)) {
                ArpPacket arpPacket = packet.get(ArpPacket.class);
                if (arpPacket != null) {
                    capturedPacket.setArpOperation(arpPacket.getHeader().getOperation().toString());
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Error analyzing ARP packet", e);
        }
    }
    
    // Getters and utility methods
    public boolean isCapturing() {
        return capturing.get();
    }
    
    public List<CapturedPacket> getCapturedPackets() {
        return new ArrayList<>(capturedPackets);
    }
    
    public Map<String, Long> getProtocolStatistics() {
        Map<String, Long> stats = new HashMap<>();
        protocolCounters.forEach((protocol, counter) -> stats.put(protocol, counter.get()));
        return stats;
    }
    
    public long getTotalPacketCount() {
        return capturedPackets.size();
    }
    
    public LocalDateTime getCaptureStartTime() {
        return captureStartTime;
    }
    
    public String getCaptureFilter() {
        return captureFilter;
    }
    
    public void setPacketListener(Consumer<CapturedPacket> listener) {
        this.packetListener = listener;
    }
    
    public void clearPackets() {
        capturedPackets.clear();
        protocolCounters.values().forEach(counter -> counter.set(0));
        packetIdCounter.set(0);
    }
    
    /**
     * Get capture duration in seconds
     */
    public long getCaptureDurationSeconds() {
        if (captureStartTime == null) return 0;
        return java.time.Duration.between(captureStartTime, LocalDateTime.now()).getSeconds();
    }
    
    /**
     * Export packets to PCAP format
     */
    public void exportToPcap(String filename) throws Exception {
        // Implementation would write packets to PCAP file format
        // This is a placeholder for the actual implementation
        logger.info("Exporting " + capturedPackets.size() + " packets to PCAP file: " + filename);
    }
} 