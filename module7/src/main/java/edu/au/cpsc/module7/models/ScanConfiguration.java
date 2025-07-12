package edu.au.cpsc.module7.models;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for network scanning operations
 */
public class ScanConfiguration {
    public enum ScanType {
        PING_SWEEP("Ping Sweep"),
        PORT_SCAN("Port Scan"),
        FULL_SCAN("Full Scan"),
        CUSTOM("Custom");
        
        private final String displayName;
        
        ScanType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum PortScanType {
        TCP_CONNECT("TCP Connect"),
        TCP_SYN("TCP SYN"),
        UDP("UDP"),
        COMPREHENSIVE("Comprehensive");
        
        private final String displayName;
        
        PortScanType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private String targetRange;
    private ScanType scanType;
    private PortScanType portScanType;
    private List<Integer> ports;
    private int timeout;
    private int threads;
    private boolean resolveHostnames;
    private boolean detectServices;
    private boolean detectOS;
    private boolean performTraceroute;
    
    // Default configuration
    public ScanConfiguration() {
        this.scanType = ScanType.PING_SWEEP;
        this.portScanType = PortScanType.TCP_CONNECT;
        this.ports = getCommonPorts();
        this.timeout = 3000; // 3 seconds
        this.threads = 50;
        this.resolveHostnames = true;
        this.detectServices = true;
        this.detectOS = false;
        this.performTraceroute = false;
    }
    
    public static List<Integer> getCommonPorts() {
        return Arrays.asList(
            21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 993, 995, 1723, 3306, 3389, 5432, 5900, 8080
        );
    }
    
    public static List<Integer> getAllPorts() {
        return Arrays.asList(
            20, 21, 22, 23, 25, 53, 67, 68, 69, 79, 80, 88, 102, 110, 111, 113, 119, 135, 137, 138, 139, 143, 161, 162, 
            179, 389, 427, 443, 445, 465, 513, 514, 515, 543, 544, 548, 554, 587, 631, 636, 646, 873, 990, 993, 995, 
            1025, 1026, 1027, 1028, 1029, 1110, 1433, 1720, 1723, 1755, 1900, 2000, 2001, 2049, 2121, 2717, 3000, 3128, 
            3306, 3389, 3690, 3986, 4899, 5000, 5009, 5051, 5060, 5101, 5190, 5357, 5432, 5631, 5666, 5800, 5900, 6000, 
            6001, 6646, 7000, 7070, 7937, 7938, 8000, 8002, 8008, 8080, 8443, 8888, 9100, 9999, 10000, 32768, 49152, 49153, 49154, 49155, 49156, 49157
        );
    }
    
    // Getters and setters
    public String getTargetRange() { return targetRange; }
    public void setTargetRange(String targetRange) { this.targetRange = targetRange; }
    
    public ScanType getScanType() { return scanType; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }
    
    public PortScanType getPortScanType() { return portScanType; }
    public void setPortScanType(PortScanType portScanType) { this.portScanType = portScanType; }
    
    public List<Integer> getPorts() { return ports; }
    public void setPorts(List<Integer> ports) { this.ports = ports; }
    
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    
    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
    
    public boolean isResolveHostnames() { return resolveHostnames; }
    public void setResolveHostnames(boolean resolveHostnames) { this.resolveHostnames = resolveHostnames; }
    
    public boolean isDetectServices() { return detectServices; }
    public void setDetectServices(boolean detectServices) { this.detectServices = detectServices; }
    
    public boolean isDetectOS() { return detectOS; }
    public void setDetectOS(boolean detectOS) { this.detectOS = detectOS; }
    
    public boolean isPerformTraceroute() { return performTraceroute; }
    public void setPerformTraceroute(boolean performTraceroute) { this.performTraceroute = performTraceroute; }
} 