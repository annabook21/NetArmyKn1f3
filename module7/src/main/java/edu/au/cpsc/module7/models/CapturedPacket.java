package edu.au.cpsc.module7.models;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a captured network packet with detailed information for analysis
 */
public class CapturedPacket {
    
    private final long id;
    private final LocalDateTime timestamp;
    private final String sourceAddress;
    private final String destinationAddress;
    private final int sourcePort;
    private final int destinationPort;
    private final String protocol;
    private final int length;
    private final byte[] rawData;
    private final String info;
    private final Map<String, Object> protocolDetails;
    
    // Protocol-specific fields
    private String httpMethod;
    private String httpUrl;
    private String httpUserAgent;
    private String dnsQuery;
    private String dnsResponse;
    private String dhcpMessageType;
    private String arpOperation;
    
    public CapturedPacket(long id, LocalDateTime timestamp, String sourceAddress, 
                         String destinationAddress, int sourcePort, int destinationPort,
                         String protocol, int length, byte[] rawData, String info) {
        this.id = id;
        this.timestamp = timestamp;
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.protocol = protocol;
        this.length = length;
        this.rawData = rawData != null ? Arrays.copyOf(rawData, rawData.length) : new byte[0];
        this.info = info;
        this.protocolDetails = new HashMap<>();
    }
    
    // Getters
    public long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSourceAddress() { return sourceAddress; }
    public String getDestinationAddress() { return destinationAddress; }
    public int getSourcePort() { return sourcePort; }
    public int getDestinationPort() { return destinationPort; }
    public String getProtocol() { return protocol; }
    public int getLength() { return length; }
    public byte[] getRawData() { return Arrays.copyOf(rawData, rawData.length); }
    public String getInfo() { return info; }
    public Map<String, Object> getProtocolDetails() { return new HashMap<>(protocolDetails); }
    
    // Protocol-specific getters
    public String getHttpMethod() { return httpMethod; }
    public String getHttpUrl() { return httpUrl; }
    public String getHttpUserAgent() { return httpUserAgent; }
    public String getDnsQuery() { return dnsQuery; }
    public String getDnsResponse() { return dnsResponse; }
    public String getDhcpMessageType() { return dhcpMessageType; }
    public String getArpOperation() { return arpOperation; }
    
    // Protocol-specific setters
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public void setHttpUrl(String httpUrl) { this.httpUrl = httpUrl; }
    public void setHttpUserAgent(String httpUserAgent) { this.httpUserAgent = httpUserAgent; }
    public void setDnsQuery(String dnsQuery) { this.dnsQuery = dnsQuery; }
    public void setDnsResponse(String dnsResponse) { this.dnsResponse = dnsResponse; }
    public void setDhcpMessageType(String dhcpMessageType) { this.dhcpMessageType = dhcpMessageType; }
    public void setArpOperation(String arpOperation) { this.arpOperation = arpOperation; }
    
    // Protocol detail management
    public void addProtocolDetail(String key, Object value) {
        protocolDetails.put(key, value);
    }
    
    public Object getProtocolDetail(String key) {
        return protocolDetails.get(key);
    }
    
    // Utility methods
    public String getFormattedTimestamp() {
        return timestamp.toString().substring(11, 19); // HH:MM:SS format
    }
    
    public String getSourceEndpoint() {
        return sourcePort > 0 ? sourceAddress + ":" + sourcePort : sourceAddress;
    }
    
    public String getDestinationEndpoint() {
        return destinationPort > 0 ? destinationAddress + ":" + destinationPort : destinationAddress;
    }
    
    public String getHexData() {
        if (rawData.length == 0) return "";
        
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < rawData.length; i++) {
            if (i > 0 && i % 16 == 0) {
                hex.append("\n");
            } else if (i > 0 && i % 8 == 0) {
                hex.append("  ");
            } else if (i > 0) {
                hex.append(" ");
            }
            hex.append(String.format("%02x", rawData[i] & 0xFF));
        }
        return hex.toString();
    }
    
    public boolean isHTTP() {
        return "HTTP".equalsIgnoreCase(protocol) || sourcePort == 80 || destinationPort == 80;
    }
    
    public boolean isHTTPS() {
        return "HTTPS".equalsIgnoreCase(protocol) || sourcePort == 443 || destinationPort == 443;
    }
    
    public boolean isDNS() {
        return "DNS".equalsIgnoreCase(protocol) || sourcePort == 53 || destinationPort == 53;
    }
    
    public boolean isDHCP() {
        return "DHCP".equalsIgnoreCase(protocol) || sourcePort == 67 || destinationPort == 67 || 
               sourcePort == 68 || destinationPort == 68;
    }
    
    public boolean isARP() {
        return "ARP".equalsIgnoreCase(protocol);
    }
    
    public boolean isTCP() {
        return "TCP".equalsIgnoreCase(protocol);
    }
    
    public boolean isUDP() {
        return "UDP".equalsIgnoreCase(protocol);
    }
    
    public boolean isICMP() {
        return "ICMP".equalsIgnoreCase(protocol);
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s -> %s [%s] %d bytes: %s",
                getFormattedTimestamp(), getSourceEndpoint(), getDestinationEndpoint(),
                protocol, length, info);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CapturedPacket that = (CapturedPacket) obj;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
} 