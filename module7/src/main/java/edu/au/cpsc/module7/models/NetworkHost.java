package edu.au.cpsc.module7.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a discovered network host with its properties and services
 */
public class NetworkHost {
    private String ipAddress;
    private String hostname;
    private String macAddress;
    private String vendor;
    private boolean isAlive;
    private long responseTime;
    private List<Integer> openPorts;
    private List<String> services;
    private String osGuess;
    private double x; // For network map visualization
    private double y; // For network map visualization
    private List<String> networkPath; // Traceroute path to this host
    private List<String> vulnerabilities; // Detected vulnerabilities
    private String lastScanTime;
    private String riskLevel; // Risk assessment level
    
    public NetworkHost(String ipAddress) {
        this.ipAddress = ipAddress;
        this.openPorts = new ArrayList<>();
        this.services = new ArrayList<>();
        this.networkPath = new ArrayList<>();
        this.vulnerabilities = new ArrayList<>();
        this.isAlive = false;
        this.responseTime = -1;
        this.x = 0;
        this.y = 0;
        this.riskLevel = "MINIMAL";
    }
    
    // Getters and setters
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    
    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) { isAlive = alive; }
    
    public long getResponseTime() { return responseTime; }
    public void setResponseTime(long responseTime) { this.responseTime = responseTime; }
    
    public List<Integer> getOpenPorts() { return openPorts; }
    public void setOpenPorts(List<Integer> openPorts) { this.openPorts = openPorts; }
    
    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }
    
    public String getOsGuess() { return osGuess; }
    public void setOsGuess(String osGuess) { this.osGuess = osGuess; }
    
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    
    public List<String> getNetworkPath() { return networkPath; }
    public void setNetworkPath(List<String> networkPath) { this.networkPath = networkPath; }
    
    public List<String> getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(List<String> vulnerabilities) { this.vulnerabilities = vulnerabilities; }
    
    public String getLastScanTime() { return lastScanTime; }
    public void setLastScanTime(String lastScanTime) { this.lastScanTime = lastScanTime; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public void addOpenPort(int port) {
        if (!openPorts.contains(port)) {
            openPorts.add(port);
        }
    }
    
    public void addService(String service) {
        if (!services.contains(service)) {
            services.add(service);
        }
    }
    
    public void addVulnerability(String vulnerability) {
        if (!vulnerabilities.contains(vulnerability)) {
            vulnerabilities.add(vulnerability);
        }
    }
    
    public String getStatusIcon() {
        if (!isAlive) return "❌";
        if (!vulnerabilities.isEmpty()) return "🚨"; // Security risk
        if (openPorts.isEmpty()) return "🟡";
        return openPorts.size() > 5 ? "🔴" : "🟢";
    }
    
    public String getDisplayName() {
        return hostname != null && !hostname.isEmpty() ? hostname : ipAddress;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkHost that = (NetworkHost) o;
        return Objects.equals(ipAddress, that.ipAddress);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ipAddress);
    }
    
    @Override
    public String toString() {
        return String.format("NetworkHost{ip='%s', hostname='%s', alive=%s, ports=%d, responseTime=%dms, risk=%s}",
                ipAddress, hostname, isAlive, openPorts.size(), responseTime, getRiskLevel());
    }
} 