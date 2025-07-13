package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.CapturedPacket;
import org.pcap4j.packet.*;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IpNumber;

import java.util.logging.Logger;

/**
 * Simplified protocol dissector for basic packet analysis
 */
public class ProtocolDissectorService {
    
    private static final Logger logger = Logger.getLogger(ProtocolDissectorService.class.getName());
    
    /**
     * Perform basic protocol analysis on a captured packet
     */
    public void performDeepAnalysis(CapturedPacket capturedPacket, Packet rawPacket) {
        try {
            // Basic analysis without complex API calls
            analyzeBasicProtocol(capturedPacket, rawPacket);
        } catch (Exception e) {
            // Silently ignore errors in protocol analysis
        }
    }
    
    private void analyzeBasicProtocol(CapturedPacket packet, Packet rawPacket) {
        // Add basic protocol information
        packet.addProtocolDetail("packet_length", rawPacket.length());
        packet.addProtocolDetail("raw_data_available", rawPacket.getRawData() != null);
        
        // Ethernet layer analysis
        if (rawPacket.contains(EthernetPacket.class)) {
            packet.addProtocolDetail("has_ethernet", true);
            analyzeEthernet(packet, rawPacket.get(EthernetPacket.class));
        }
        
        // IP layer analysis
        if (rawPacket.contains(IpV4Packet.class)) {
            packet.addProtocolDetail("has_ipv4", true);
            analyzeIPv4(packet, rawPacket.get(IpV4Packet.class));
        }
        
        // Transport layer analysis
        if (rawPacket.contains(TcpPacket.class)) {
            packet.addProtocolDetail("has_tcp", true);
            analyzeTCP(packet, rawPacket.get(TcpPacket.class));
        } else if (rawPacket.contains(UdpPacket.class)) {
            packet.addProtocolDetail("has_udp", true);
            analyzeUDP(packet, rawPacket.get(UdpPacket.class));
        }
        
        // ARP analysis
        if (rawPacket.contains(ArpPacket.class)) {
            packet.addProtocolDetail("has_arp", true);
            analyzeARP(packet, rawPacket.get(ArpPacket.class));
        }
    }
    
    private void analyzeEthernet(CapturedPacket packet, EthernetPacket ethPacket) {
        try {
            EthernetPacket.EthernetHeader header = ethPacket.getHeader();
            packet.addProtocolDetail("eth_src_mac", header.getSrcAddr().toString());
            packet.addProtocolDetail("eth_dst_mac", header.getDstAddr().toString());
            packet.addProtocolDetail("eth_type", header.getType().toString());
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    private void analyzeIPv4(CapturedPacket packet, IpV4Packet ipPacket) {
        try {
            IpV4Packet.IpV4Header header = ipPacket.getHeader();
            packet.addProtocolDetail("ip_src", header.getSrcAddr().getHostAddress());
            packet.addProtocolDetail("ip_dst", header.getDstAddr().getHostAddress());
            packet.addProtocolDetail("ip_protocol", header.getProtocol().value());
            packet.addProtocolDetail("ip_total_length", header.getTotalLength());
            packet.addProtocolDetail("ip_ttl", header.getTtl());
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    private void analyzeTCP(CapturedPacket packet, TcpPacket tcpPacket) {
        try {
            TcpPacket.TcpHeader header = tcpPacket.getHeader();
            packet.addProtocolDetail("tcp_src_port", header.getSrcPort().valueAsInt());
            packet.addProtocolDetail("tcp_dst_port", header.getDstPort().valueAsInt());
            packet.addProtocolDetail("tcp_seq", header.getSequenceNumber());
            packet.addProtocolDetail("tcp_ack", header.getAcknowledgmentNumber());
            packet.addProtocolDetail("tcp_window", header.getWindow());
            
            // Basic flags
            packet.addProtocolDetail("tcp_syn", header.getSyn());
            packet.addProtocolDetail("tcp_ack_flag", header.getAck());
            packet.addProtocolDetail("tcp_fin", header.getFin());
            packet.addProtocolDetail("tcp_rst", header.getRst());
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    private void analyzeUDP(CapturedPacket packet, UdpPacket udpPacket) {
        try {
            UdpPacket.UdpHeader header = udpPacket.getHeader();
            packet.addProtocolDetail("udp_src_port", header.getSrcPort().valueAsInt());
            packet.addProtocolDetail("udp_dst_port", header.getDstPort().valueAsInt());
            packet.addProtocolDetail("udp_length", header.getLength());
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    private void analyzeARP(CapturedPacket packet, ArpPacket arpPacket) {
        try {
            ArpPacket.ArpHeader header = arpPacket.getHeader();
            packet.addProtocolDetail("arp_operation", header.getOperation().value());
            packet.addProtocolDetail("arp_sender_mac", header.getSrcHardwareAddr().toString());
            packet.addProtocolDetail("arp_sender_ip", header.getSrcProtocolAddr().getHostAddress());
            packet.addProtocolDetail("arp_target_mac", header.getDstHardwareAddr().toString());
            packet.addProtocolDetail("arp_target_ip", header.getDstProtocolAddr().getHostAddress());
        } catch (Exception e) {
            // Ignore errors
        }
    }
} 