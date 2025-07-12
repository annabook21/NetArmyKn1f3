package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.NetworkHost;
import javafx.application.Platform;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.inject.Inject;

/**
 * Advanced ARP scanner for discovering devices on the local network
 * This provides much better device discovery than simple ping scanning
 */
public class ARPScanner {
    // Mock logger implementation for compilation
    private static final MockLogger logger = new MockLogger();
    
    static class MockLogger {
        public void error(String msg, Exception e) { System.err.println(msg + ": " + e.getMessage()); }
        public void debug(String msg, Object... args) { /* debug disabled */ }
        public void info(String msg, Object... args) { System.out.println("INFO: " + msg); }
        public void warn(String msg, Object... args) { System.out.println("WARN: " + msg); }
    }
    
    // OUI (Organizationally Unique Identifier) database for vendor identification
    private static final Map<String, String> VENDOR_DATABASE = new HashMap<>();
    static {
        // Common vendor prefixes (first 3 octets of MAC address)
        VENDOR_DATABASE.put("00:50:56", "VMware");
        VENDOR_DATABASE.put("00:0C:29", "VMware");
        VENDOR_DATABASE.put("00:05:69", "VMware");
        VENDOR_DATABASE.put("00:1C:14", "VMware");
        VENDOR_DATABASE.put("00:50:56", "VMware");
        VENDOR_DATABASE.put("08:00:27", "VirtualBox");
        VENDOR_DATABASE.put("0A:00:27", "VirtualBox");
        VENDOR_DATABASE.put("52:54:00", "QEMU/KVM");
        VENDOR_DATABASE.put("00:16:3E", "Xen");
        VENDOR_DATABASE.put("00:1B:21", "Intel");
        VENDOR_DATABASE.put("00:13:02", "Intel");
        VENDOR_DATABASE.put("00:15:17", "Intel");
        VENDOR_DATABASE.put("00:19:D1", "Intel");
        VENDOR_DATABASE.put("00:1E:67", "Intel");
        VENDOR_DATABASE.put("00:21:6A", "Intel");
        VENDOR_DATABASE.put("00:24:D7", "Intel");
        VENDOR_DATABASE.put("04:0E:3C", "Intel");
        VENDOR_DATABASE.put("0C:8B:FD", "Intel");
        VENDOR_DATABASE.put("10:78:D2", "Intel");
        VENDOR_DATABASE.put("14:13:33", "Intel");
        VENDOR_DATABASE.put("18:03:73", "Intel");
        VENDOR_DATABASE.put("1C:69:7A", "Intel");
        VENDOR_DATABASE.put("20:16:B9", "Intel");
        VENDOR_DATABASE.put("24:77:03", "Intel");
        VENDOR_DATABASE.put("28:D2:44", "Intel");
        VENDOR_DATABASE.put("2C:44:FD", "Intel");
        VENDOR_DATABASE.put("34:13:E8", "Intel");
        VENDOR_DATABASE.put("38:2C:4A", "Intel");
        VENDOR_DATABASE.put("3C:A9:F4", "Intel");
        VENDOR_DATABASE.put("40:B0:34", "Intel");
        VENDOR_DATABASE.put("44:85:00", "Intel");
        VENDOR_DATABASE.put("48:4D:7E", "Intel");
        VENDOR_DATABASE.put("4C:72:B9", "Intel");
        VENDOR_DATABASE.put("50:76:AF", "Intel");
        VENDOR_DATABASE.put("54:27:1E", "Intel");
        VENDOR_DATABASE.put("58:91:CF", "Intel");
        VENDOR_DATABASE.put("5C:E0:C5", "Intel");
        VENDOR_DATABASE.put("60:67:20", "Intel");
        VENDOR_DATABASE.put("64:00:6A", "Intel");
        VENDOR_DATABASE.put("68:05:CA", "Intel");
        VENDOR_DATABASE.put("6C:88:14", "Intel");
        VENDOR_DATABASE.put("70:1A:04", "Intel");
        VENDOR_DATABASE.put("74:E5:F9", "Intel");
        VENDOR_DATABASE.put("78:92:9C", "Intel");
        VENDOR_DATABASE.put("7C:8A:E1", "Intel");
        VENDOR_DATABASE.put("80:19:34", "Intel");
        VENDOR_DATABASE.put("84:38:35", "Intel");
        VENDOR_DATABASE.put("88:75:56", "Intel");
        VENDOR_DATABASE.put("8C:DC:D4", "Intel");
        VENDOR_DATABASE.put("90:48:9A", "Intel");
        VENDOR_DATABASE.put("94:65:9C", "Intel");
        VENDOR_DATABASE.put("98:90:96", "Intel");
        VENDOR_DATABASE.put("9C:B6:54", "Intel");
        VENDOR_DATABASE.put("A0:A8:CD", "Intel");
        VENDOR_DATABASE.put("A4:4C:C8", "Intel");
        VENDOR_DATABASE.put("A8:6D:AA", "Intel");
        VENDOR_DATABASE.put("AC:2B:6E", "Intel");
        VENDOR_DATABASE.put("B0:C0:90", "Intel");
        VENDOR_DATABASE.put("B4:96:91", "Intel");
        VENDOR_DATABASE.put("B8:CA:3A", "Intel");
        VENDOR_DATABASE.put("BC:EE:7B", "Intel");
        VENDOR_DATABASE.put("C0:3F:D5", "Intel");
        VENDOR_DATABASE.put("C4:65:16", "Intel");
        VENDOR_DATABASE.put("C8:5B:76", "Intel");
        VENDOR_DATABASE.put("CC:2F:71", "Intel");
        VENDOR_DATABASE.put("D0:50:99", "Intel");
        VENDOR_DATABASE.put("D4:6D:6D", "Intel");
        VENDOR_DATABASE.put("D8:CB:8A", "Intel");
        VENDOR_DATABASE.put("DC:53:60", "Intel");
        VENDOR_DATABASE.put("E0:DB:55", "Intel");
        VENDOR_DATABASE.put("E4:E7:49", "Intel");
        VENDOR_DATABASE.put("E8:6A:64", "Intel");
        VENDOR_DATABASE.put("EC:A8:6B", "Intel");
        VENDOR_DATABASE.put("F0:76:1C", "Intel");
        VENDOR_DATABASE.put("F4:4D:30", "Intel");
        VENDOR_DATABASE.put("F8:63:3F", "Intel");
        VENDOR_DATABASE.put("FC:AA:14", "Intel");
        
        // Apple devices
        VENDOR_DATABASE.put("00:03:93", "Apple");
        VENDOR_DATABASE.put("00:0A:27", "Apple");
        VENDOR_DATABASE.put("00:0A:95", "Apple");
        VENDOR_DATABASE.put("00:0D:93", "Apple");
        VENDOR_DATABASE.put("00:11:24", "Apple");
        VENDOR_DATABASE.put("00:14:51", "Apple");
        VENDOR_DATABASE.put("00:16:CB", "Apple");
        VENDOR_DATABASE.put("00:17:F2", "Apple");
        VENDOR_DATABASE.put("00:19:E3", "Apple");
        VENDOR_DATABASE.put("00:1B:63", "Apple");
        VENDOR_DATABASE.put("00:1E:C2", "Apple");
        VENDOR_DATABASE.put("00:21:E9", "Apple");
        VENDOR_DATABASE.put("00:23:12", "Apple");
        VENDOR_DATABASE.put("00:23:DF", "Apple");
        VENDOR_DATABASE.put("00:25:00", "Apple");
        VENDOR_DATABASE.put("00:25:4B", "Apple");
        VENDOR_DATABASE.put("00:25:BC", "Apple");
        VENDOR_DATABASE.put("00:26:08", "Apple");
        VENDOR_DATABASE.put("00:26:4A", "Apple");
        VENDOR_DATABASE.put("00:26:B0", "Apple");
        VENDOR_DATABASE.put("00:26:BB", "Apple");
        VENDOR_DATABASE.put("04:0C:CE", "Apple");
        VENDOR_DATABASE.put("04:15:52", "Apple");
        VENDOR_DATABASE.put("04:1E:64", "Apple");
        VENDOR_DATABASE.put("04:26:65", "Apple");
        VENDOR_DATABASE.put("04:54:53", "Apple");
        VENDOR_DATABASE.put("04:69:F8", "Apple");
        VENDOR_DATABASE.put("04:DB:56", "Apple");
        VENDOR_DATABASE.put("04:E5:36", "Apple");
        VENDOR_DATABASE.put("04:F1:3E", "Apple");
        VENDOR_DATABASE.put("04:F7:E4", "Apple");
        VENDOR_DATABASE.put("08:6D:41", "Apple");
        VENDOR_DATABASE.put("08:74:02", "Apple");
        VENDOR_DATABASE.put("0C:3E:9F", "Apple");
        VENDOR_DATABASE.put("0C:4D:E9", "Apple");
        VENDOR_DATABASE.put("0C:74:C2", "Apple");
        VENDOR_DATABASE.put("0C:D2:92", "Apple");
        VENDOR_DATABASE.put("10:40:F3", "Apple");
        VENDOR_DATABASE.put("10:9A:DD", "Apple");
        VENDOR_DATABASE.put("10:DD:B1", "Apple");
        VENDOR_DATABASE.put("14:10:9F", "Apple");
        VENDOR_DATABASE.put("14:20:5E", "Apple");
        VENDOR_DATABASE.put("14:5A:05", "Apple");
        VENDOR_DATABASE.put("14:7D:DA", "Apple");
        VENDOR_DATABASE.put("14:BD:61", "Apple");
        VENDOR_DATABASE.put("18:34:51", "Apple");
        VENDOR_DATABASE.put("18:65:90", "Apple");
        VENDOR_DATABASE.put("18:AF:61", "Apple");
        VENDOR_DATABASE.put("18:E7:F4", "Apple");
        VENDOR_DATABASE.put("1C:1A:C0", "Apple");
        VENDOR_DATABASE.put("1C:36:BB", "Apple");
        VENDOR_DATABASE.put("1C:AB:A7", "Apple");
        VENDOR_DATABASE.put("20:3C:AE", "Apple");
        VENDOR_DATABASE.put("20:A2:E4", "Apple");
        VENDOR_DATABASE.put("20:C9:D0", "Apple");
        VENDOR_DATABASE.put("24:A0:74", "Apple");
        VENDOR_DATABASE.put("24:AB:81", "Apple");
        VENDOR_DATABASE.put("24:F0:94", "Apple");
        VENDOR_DATABASE.put("24:F6:77", "Apple");
        VENDOR_DATABASE.put("28:37:37", "Apple");
        VENDOR_DATABASE.put("28:6A:BA", "Apple");
        VENDOR_DATABASE.put("28:A0:2B", "Apple");
        VENDOR_DATABASE.put("28:E0:2C", "Apple");
        VENDOR_DATABASE.put("28:E7:CF", "Apple");
        VENDOR_DATABASE.put("2C:1F:23", "Apple");
        VENDOR_DATABASE.put("2C:B4:3A", "Apple");
        VENDOR_DATABASE.put("30:10:B3", "Apple");
        VENDOR_DATABASE.put("30:35:AD", "Apple");
        VENDOR_DATABASE.put("30:90:AB", "Apple");
        VENDOR_DATABASE.put("30:F7:C5", "Apple");
        VENDOR_DATABASE.put("34:15:9E", "Apple");
        VENDOR_DATABASE.put("34:36:3B", "Apple");
        VENDOR_DATABASE.put("34:A3:95", "Apple");
        VENDOR_DATABASE.put("34:C0:59", "Apple");
        VENDOR_DATABASE.put("38:0F:4A", "Apple");
        VENDOR_DATABASE.put("38:B5:4D", "Apple");
        VENDOR_DATABASE.put("3C:07:54", "Apple");
        VENDOR_DATABASE.put("3C:15:C2", "Apple");
        VENDOR_DATABASE.put("3C:2E:F9", "Apple");
        VENDOR_DATABASE.put("40:30:04", "Apple");
        VENDOR_DATABASE.put("40:33:1A", "Apple");
        VENDOR_DATABASE.put("40:6C:8F", "Apple");
        VENDOR_DATABASE.put("40:A6:D9", "Apple");
        VENDOR_DATABASE.put("40:B3:95", "Apple");
        VENDOR_DATABASE.put("40:CB:C0", "Apple");
        VENDOR_DATABASE.put("44:00:10", "Apple");
        VENDOR_DATABASE.put("44:2A:60", "Apple");
        VENDOR_DATABASE.put("44:4C:0C", "Apple");
        VENDOR_DATABASE.put("44:D8:84", "Apple");
        VENDOR_DATABASE.put("44:FB:42", "Apple");
        VENDOR_DATABASE.put("48:43:7C", "Apple");
        VENDOR_DATABASE.put("48:74:6E", "Apple");
        VENDOR_DATABASE.put("48:A1:95", "Apple");
        VENDOR_DATABASE.put("48:BF:6B", "Apple");
        VENDOR_DATABASE.put("4C:32:75", "Apple");
        VENDOR_DATABASE.put("4C:57:CA", "Apple");
        VENDOR_DATABASE.put("4C:7C:5F", "Apple");
        VENDOR_DATABASE.put("4C:8D:79", "Apple");
        VENDOR_DATABASE.put("50:DE:06", "Apple");
        VENDOR_DATABASE.put("50:EA:D6", "Apple");
        VENDOR_DATABASE.put("54:26:96", "Apple");
        VENDOR_DATABASE.put("54:72:4F", "Apple");
        VENDOR_DATABASE.put("54:AE:27", "Apple");
        VENDOR_DATABASE.put("54:E4:3A", "Apple");
        VENDOR_DATABASE.put("58:1F:AA", "Apple");
        VENDOR_DATABASE.put("58:40:4E", "Apple");
        VENDOR_DATABASE.put("58:55:CA", "Apple");
        VENDOR_DATABASE.put("5C:59:48", "Apple");
        VENDOR_DATABASE.put("5C:95:AE", "Apple");
        VENDOR_DATABASE.put("5C:F9:38", "Apple");
        VENDOR_DATABASE.put("60:33:4B", "Apple");
        VENDOR_DATABASE.put("60:C5:47", "Apple");
        VENDOR_DATABASE.put("60:F4:45", "Apple");
        VENDOR_DATABASE.put("60:FB:42", "Apple");
        VENDOR_DATABASE.put("64:20:0C", "Apple");
        VENDOR_DATABASE.put("64:76:BA", "Apple");
        VENDOR_DATABASE.put("64:A3:CB", "Apple");
        VENDOR_DATABASE.put("64:B9:E8", "Apple");
        VENDOR_DATABASE.put("68:5B:35", "Apple");
        VENDOR_DATABASE.put("68:96:7B", "Apple");
        VENDOR_DATABASE.put("68:AB:BC", "Apple");
        VENDOR_DATABASE.put("68:D9:3C", "Apple");
        VENDOR_DATABASE.put("6C:19:8F", "Apple");
        VENDOR_DATABASE.put("6C:40:08", "Apple");
        VENDOR_DATABASE.put("6C:72:E7", "Apple");
        VENDOR_DATABASE.put("6C:94:66", "Apple");
        VENDOR_DATABASE.put("6C:AD:F8", "Apple");
        VENDOR_DATABASE.put("70:11:24", "Apple");
        VENDOR_DATABASE.put("70:56:81", "Apple");
        VENDOR_DATABASE.put("70:73:CB", "Apple");
        VENDOR_DATABASE.put("70:CD:60", "Apple");
        VENDOR_DATABASE.put("70:DE:E2", "Apple");
        VENDOR_DATABASE.put("74:E2:F5", "Apple");
        VENDOR_DATABASE.put("74:E7:EA", "Apple");
        VENDOR_DATABASE.put("78:31:C1", "Apple");
        VENDOR_DATABASE.put("78:4F:43", "Apple");
        VENDOR_DATABASE.put("78:67:D0", "Apple");
        VENDOR_DATABASE.put("78:86:6D", "Apple");
        VENDOR_DATABASE.put("78:A3:E4", "Apple");
        VENDOR_DATABASE.put("78:CA:39", "Apple");
        VENDOR_DATABASE.put("78:D7:5F", "Apple");
        VENDOR_DATABASE.put("7C:6D:62", "Apple");
        VENDOR_DATABASE.put("7C:C3:A1", "Apple");
        VENDOR_DATABASE.put("7C:D1:C3", "Apple");
        VENDOR_DATABASE.put("7C:F0:5F", "Apple");
        VENDOR_DATABASE.put("80:06:E0", "Apple");
        VENDOR_DATABASE.put("80:92:9F", "Apple");
        VENDOR_DATABASE.put("80:E6:50", "Apple");
        VENDOR_DATABASE.put("84:38:38", "Apple");
        VENDOR_DATABASE.put("84:78:AC", "Apple");
        VENDOR_DATABASE.put("84:B1:53", "Apple");
        VENDOR_DATABASE.put("84:FC:FE", "Apple");
        VENDOR_DATABASE.put("88:1F:A1", "Apple");
        VENDOR_DATABASE.put("88:53:2E", "Apple");
        VENDOR_DATABASE.put("88:63:DF", "Apple");
        VENDOR_DATABASE.put("88:66:A5", "Apple");
        VENDOR_DATABASE.put("8C:2D:AA", "Apple");
        VENDOR_DATABASE.put("8C:58:77", "Apple");
        VENDOR_DATABASE.put("8C:7C:92", "Apple");
        VENDOR_DATABASE.put("8C:85:90", "Apple");
        VENDOR_DATABASE.put("8C:8E:F2", "Apple");
        VENDOR_DATABASE.put("90:27:E4", "Apple");
        VENDOR_DATABASE.put("90:72:40", "Apple");
        VENDOR_DATABASE.put("90:84:0D", "Apple");
        VENDOR_DATABASE.put("90:B0:ED", "Apple");
        VENDOR_DATABASE.put("90:B2:1F", "Apple");
        VENDOR_DATABASE.put("94:E9:6A", "Apple");
        VENDOR_DATABASE.put("94:F6:A3", "Apple");
        VENDOR_DATABASE.put("98:01:A7", "Apple");
        VENDOR_DATABASE.put("98:5A:EB", "Apple");
        VENDOR_DATABASE.put("98:B8:E3", "Apple");
        VENDOR_DATABASE.put("98:F0:AB", "Apple");
        VENDOR_DATABASE.put("9C:04:EB", "Apple");
        VENDOR_DATABASE.put("9C:20:7B", "Apple");
        VENDOR_DATABASE.put("9C:29:3F", "Apple");
        VENDOR_DATABASE.put("9C:84:BF", "Apple");
        VENDOR_DATABASE.put("9C:F3:87", "Apple");
        VENDOR_DATABASE.put("A0:99:9B", "Apple");
        VENDOR_DATABASE.put("A0:CE:C8", "Apple");
        VENDOR_DATABASE.put("A0:D7:95", "Apple");
        VENDOR_DATABASE.put("A4:5E:60", "Apple");
        VENDOR_DATABASE.put("A4:83:E7", "Apple");
        VENDOR_DATABASE.put("A4:B1:97", "Apple");
        VENDOR_DATABASE.put("A4:C3:61", "Apple");
        VENDOR_DATABASE.put("A4:D1:8C", "Apple");
        VENDOR_DATABASE.put("A8:20:66", "Apple");
        VENDOR_DATABASE.put("A8:60:B6", "Apple");
        VENDOR_DATABASE.put("A8:66:7F", "Apple");
        VENDOR_DATABASE.put("A8:88:08", "Apple");
        VENDOR_DATABASE.put("A8:96:75", "Apple");
        VENDOR_DATABASE.put("A8:BB:CF", "Apple");
        VENDOR_DATABASE.put("A8:FA:D8", "Apple");
        VENDOR_DATABASE.put("AC:1F:74", "Apple");
        VENDOR_DATABASE.put("AC:29:3A", "Apple");
        VENDOR_DATABASE.put("AC:37:43", "Apple");
        VENDOR_DATABASE.put("AC:3C:0B", "Apple");
        VENDOR_DATABASE.put("AC:61:75", "Apple");
        VENDOR_DATABASE.put("AC:87:A3", "Apple");
        VENDOR_DATABASE.put("AC:BC:32", "Apple");
        VENDOR_DATABASE.put("AC:CF:85", "Apple");
        VENDOR_DATABASE.put("B0:65:BD", "Apple");
        VENDOR_DATABASE.put("B0:CA:68", "Apple");
        VENDOR_DATABASE.put("B4:18:D1", "Apple");
        VENDOR_DATABASE.put("B4:F0:AB", "Apple");
        VENDOR_DATABASE.put("B4:F6:1C", "Apple");
        VENDOR_DATABASE.put("B8:09:8A", "Apple");
        VENDOR_DATABASE.put("B8:17:C2", "Apple");
        VENDOR_DATABASE.put("B8:53:AC", "Apple");
        VENDOR_DATABASE.put("B8:63:BC", "Apple");
        VENDOR_DATABASE.put("B8:78:2E", "Apple");
        VENDOR_DATABASE.put("B8:C7:5D", "Apple");
        VENDOR_DATABASE.put("B8:E8:56", "Apple");
        VENDOR_DATABASE.put("B8:FF:61", "Apple");
        VENDOR_DATABASE.put("BC:52:B7", "Apple");
        VENDOR_DATABASE.put("BC:67:1C", "Apple");
        VENDOR_DATABASE.put("BC:92:6B", "Apple");
        VENDOR_DATABASE.put("BC:D0:74", "Apple");
        VENDOR_DATABASE.put("BC:F5:AC", "Apple");
        VENDOR_DATABASE.put("C0:84:7A", "Apple");
        VENDOR_DATABASE.put("C0:9A:D0", "Apple");
        VENDOR_DATABASE.put("C0:CE:CD", "Apple");
        VENDOR_DATABASE.put("C0:D0:12", "Apple");
        VENDOR_DATABASE.put("C4:2C:03", "Apple");
        VENDOR_DATABASE.put("C4:B3:01", "Apple");
        VENDOR_DATABASE.put("C8:1E:E7", "Apple");
        VENDOR_DATABASE.put("C8:2A:14", "Apple");
        VENDOR_DATABASE.put("C8:33:4B", "Apple");
        VENDOR_DATABASE.put("C8:60:00", "Apple");
        VENDOR_DATABASE.put("C8:69:CD", "Apple");
        VENDOR_DATABASE.put("C8:89:F3", "Apple");
        VENDOR_DATABASE.put("C8:BC:C8", "Apple");
        VENDOR_DATABASE.put("C8:E0:EB", "Apple");
        VENDOR_DATABASE.put("CC:08:8D", "Apple");
        VENDOR_DATABASE.put("CC:25:EF", "Apple");
        VENDOR_DATABASE.put("CC:29:F5", "Apple");
        VENDOR_DATABASE.put("CC:78:AB", "Apple");
        VENDOR_DATABASE.put("D0:23:DB", "Apple");
        VENDOR_DATABASE.put("D0:81:7A", "Apple");
        VENDOR_DATABASE.put("D0:A6:37", "Apple");
        VENDOR_DATABASE.put("D4:61:9D", "Apple");
        VENDOR_DATABASE.put("D4:90:9C", "Apple");
        VENDOR_DATABASE.put("D4:9A:20", "Apple");
        VENDOR_DATABASE.put("D4:F4:6F", "Apple");
        VENDOR_DATABASE.put("D8:30:62", "Apple");
        VENDOR_DATABASE.put("D8:96:95", "Apple");
        VENDOR_DATABASE.put("D8:A2:5E", "Apple");
        VENDOR_DATABASE.put("D8:BB:2C", "Apple");
        VENDOR_DATABASE.put("D8:D1:CB", "Apple");
        VENDOR_DATABASE.put("DC:2B:2A", "Apple");
        VENDOR_DATABASE.put("DC:37:45", "Apple");
        VENDOR_DATABASE.put("DC:56:E7", "Apple");
        VENDOR_DATABASE.put("DC:86:D8", "Apple");
        VENDOR_DATABASE.put("DC:A4:CA", "Apple");
        VENDOR_DATABASE.put("DC:A9:04", "Apple");
        VENDOR_DATABASE.put("DC:D3:A2", "Apple");
        VENDOR_DATABASE.put("E0:AC:CB", "Apple");
        VENDOR_DATABASE.put("E0:B9:BA", "Apple");
        VENDOR_DATABASE.put("E0:C9:7A", "Apple");
        VENDOR_DATABASE.put("E0:F5:C6", "Apple");
        VENDOR_DATABASE.put("E0:F8:47", "Apple");
        VENDOR_DATABASE.put("E4:25:E7", "Apple");
        VENDOR_DATABASE.put("E4:8B:7F", "Apple");
        VENDOR_DATABASE.put("E4:9A:79", "Apple");
        VENDOR_DATABASE.put("E4:B3:18", "Apple");
        VENDOR_DATABASE.put("E4:CE:8F", "Apple");
        VENDOR_DATABASE.put("E8:06:88", "Apple");
        VENDOR_DATABASE.put("E8:2A:EA", "Apple");
        VENDOR_DATABASE.put("E8:40:F2", "Apple");
        VENDOR_DATABASE.put("E8:80:2E", "Apple");
        VENDOR_DATABASE.put("E8:B2:AC", "Apple");
        VENDOR_DATABASE.put("E8:E0:B7", "Apple");
        VENDOR_DATABASE.put("EC:35:86", "Apple");
        VENDOR_DATABASE.put("EC:89:14", "Apple");
        VENDOR_DATABASE.put("EC:8A:4C", "Apple");
        VENDOR_DATABASE.put("F0:18:98", "Apple");
        VENDOR_DATABASE.put("F0:2F:74", "Apple");
        VENDOR_DATABASE.put("F0:4D:A2", "Apple");
        VENDOR_DATABASE.put("F0:7B:CB", "Apple");
        VENDOR_DATABASE.put("F0:B4:79", "Apple");
        VENDOR_DATABASE.put("F0:D1:A9", "Apple");
        VENDOR_DATABASE.put("F0:DB:E2", "Apple");
        VENDOR_DATABASE.put("F0:DC:E2", "Apple");
        VENDOR_DATABASE.put("F4:0F:24", "Apple");
        VENDOR_DATABASE.put("F4:1B:A1", "Apple");
        VENDOR_DATABASE.put("F4:37:B7", "Apple");
        VENDOR_DATABASE.put("F4:5C:89", "Apple");
        VENDOR_DATABASE.put("F4:F1:5A", "Apple");
        VENDOR_DATABASE.put("F4:F9:51", "Apple");
        VENDOR_DATABASE.put("F8:1E:DF", "Apple");
        VENDOR_DATABASE.put("F8:27:93", "Apple");
        VENDOR_DATABASE.put("F8:2F:A8", "Apple");
        VENDOR_DATABASE.put("F8:4F:57", "Apple");
        VENDOR_DATABASE.put("F8:A9:D0", "Apple");
        VENDOR_DATABASE.put("F8:E9:4E", "Apple");
        VENDOR_DATABASE.put("F8:FF:C2", "Apple");
        VENDOR_DATABASE.put("FC:25:3F", "Apple");
        VENDOR_DATABASE.put("FC:E9:98", "Apple");
        VENDOR_DATABASE.put("FC:FC:48", "Apple");
        
        // Common router/network device vendors
        VENDOR_DATABASE.put("00:01:42", "Cisco");
        VENDOR_DATABASE.put("00:01:43", "Cisco");
        VENDOR_DATABASE.put("00:01:64", "Cisco");
        VENDOR_DATABASE.put("00:01:96", "Cisco");
        VENDOR_DATABASE.put("00:01:97", "Cisco");
        VENDOR_DATABASE.put("00:01:C7", "Cisco");
        VENDOR_DATABASE.put("00:01:C9", "Cisco");
        VENDOR_DATABASE.put("00:02:16", "Cisco");
        VENDOR_DATABASE.put("00:02:17", "Cisco");
        VENDOR_DATABASE.put("00:02:3D", "Cisco");
        VENDOR_DATABASE.put("00:02:4A", "Cisco");
        VENDOR_DATABASE.put("00:02:4B", "Cisco");
        VENDOR_DATABASE.put("00:02:7D", "Cisco");
        VENDOR_DATABASE.put("00:02:7E", "Cisco");
        VENDOR_DATABASE.put("00:02:B9", "Cisco");
        VENDOR_DATABASE.put("00:02:BA", "Cisco");
        VENDOR_DATABASE.put("00:02:FC", "Cisco");
        VENDOR_DATABASE.put("00:02:FD", "Cisco");
        VENDOR_DATABASE.put("00:03:31", "Cisco");
        VENDOR_DATABASE.put("00:03:32", "Cisco");
        VENDOR_DATABASE.put("00:03:6B", "Cisco");
        VENDOR_DATABASE.put("00:03:6C", "Cisco");
        VENDOR_DATABASE.put("00:03:A0", "Cisco");
        VENDOR_DATABASE.put("00:03:E3", "Cisco");
        VENDOR_DATABASE.put("00:03:FD", "Cisco");
        VENDOR_DATABASE.put("00:03:FE", "Cisco");
        VENDOR_DATABASE.put("00:04:27", "Cisco");
        VENDOR_DATABASE.put("00:04:28", "Cisco");
        VENDOR_DATABASE.put("00:04:4D", "Cisco");
        VENDOR_DATABASE.put("00:04:6D", "Cisco");
        VENDOR_DATABASE.put("00:04:9A", "Cisco");
        VENDOR_DATABASE.put("00:04:C0", "Cisco");
        VENDOR_DATABASE.put("00:04:C1", "Cisco");
        VENDOR_DATABASE.put("00:04:DD", "Cisco");
        VENDOR_DATABASE.put("00:05:00", "Cisco");
        VENDOR_DATABASE.put("00:05:01", "Cisco");
        VENDOR_DATABASE.put("00:05:31", "Cisco");
        VENDOR_DATABASE.put("00:05:32", "Cisco");
        VENDOR_DATABASE.put("00:05:5E", "Cisco");
        VENDOR_DATABASE.put("00:05:73", "Cisco");
        VENDOR_DATABASE.put("00:05:74", "Cisco");
        VENDOR_DATABASE.put("00:05:9A", "Cisco");
        VENDOR_DATABASE.put("00:05:DC", "Cisco");
        VENDOR_DATABASE.put("00:05:DD", "Cisco");
        VENDOR_DATABASE.put("00:06:28", "Cisco");
        VENDOR_DATABASE.put("00:06:2A", "Cisco");
        VENDOR_DATABASE.put("00:06:52", "Cisco");
        VENDOR_DATABASE.put("00:06:53", "Cisco");
        VENDOR_DATABASE.put("00:06:7C", "Cisco");
        VENDOR_DATABASE.put("00:06:C1", "Cisco");
        VENDOR_DATABASE.put("00:06:D6", "Cisco");
        VENDOR_DATABASE.put("00:06:D7", "Cisco");
        VENDOR_DATABASE.put("00:06:F6", "Cisco");
        VENDOR_DATABASE.put("00:07:0D", "Cisco");
        VENDOR_DATABASE.put("00:07:0E", "Cisco");
        VENDOR_DATABASE.put("00:07:4F", "Cisco");
        VENDOR_DATABASE.put("00:07:50", "Cisco");
        VENDOR_DATABASE.put("00:07:84", "Cisco");
        VENDOR_DATABASE.put("00:07:85", "Cisco");
        VENDOR_DATABASE.put("00:07:B3", "Cisco");
        VENDOR_DATABASE.put("00:07:B4", "Cisco");
        VENDOR_DATABASE.put("00:07:EB", "Cisco");
        VENDOR_DATABASE.put("00:07:EC", "Cisco");
        VENDOR_DATABASE.put("00:08:20", "Cisco");
        VENDOR_DATABASE.put("00:08:21", "Cisco");
        VENDOR_DATABASE.put("00:08:30", "Cisco");
        VENDOR_DATABASE.put("00:08:31", "Cisco");
        VENDOR_DATABASE.put("00:08:7C", "Cisco");
        VENDOR_DATABASE.put("00:08:A3", "Cisco");
        VENDOR_DATABASE.put("00:08:C2", "Cisco");
        VENDOR_DATABASE.put("00:08:E2", "Cisco");
        VENDOR_DATABASE.put("00:08:E3", "Cisco");
        VENDOR_DATABASE.put("00:09:11", "Cisco");
        VENDOR_DATABASE.put("00:09:12", "Cisco");
        VENDOR_DATABASE.put("00:09:43", "Cisco");
        VENDOR_DATABASE.put("00:09:44", "Cisco");
        VENDOR_DATABASE.put("00:09:7B", "Cisco");
        VENDOR_DATABASE.put("00:09:B7", "Cisco");
        VENDOR_DATABASE.put("00:09:E8", "Cisco");
        VENDOR_DATABASE.put("00:09:E9", "Cisco");
        VENDOR_DATABASE.put("00:0A:41", "Cisco");
        VENDOR_DATABASE.put("00:0A:42", "Cisco");
        VENDOR_DATABASE.put("00:0A:8A", "Cisco");
        VENDOR_DATABASE.put("00:0A:8B", "Cisco");
        VENDOR_DATABASE.put("00:0A:B7", "Cisco");
        VENDOR_DATABASE.put("00:0A:B8", "Cisco");
        VENDOR_DATABASE.put("00:0A:F3", "Cisco");
        VENDOR_DATABASE.put("00:0A:F4", "Cisco");
        VENDOR_DATABASE.put("00:0B:45", "Cisco");
        VENDOR_DATABASE.put("00:0B:46", "Cisco");
        VENDOR_DATABASE.put("00:0B:5F", "Cisco");
        VENDOR_DATABASE.put("00:0B:60", "Cisco");
        VENDOR_DATABASE.put("00:0B:85", "Cisco");
        VENDOR_DATABASE.put("00:0B:BE", "Cisco");
        VENDOR_DATABASE.put("00:0B:BF", "Cisco");
        VENDOR_DATABASE.put("00:0B:FC", "Cisco");
        VENDOR_DATABASE.put("00:0B:FD", "Cisco");
        VENDOR_DATABASE.put("00:0C:30", "Cisco");
        VENDOR_DATABASE.put("00:0C:31", "Cisco");
        VENDOR_DATABASE.put("00:0C:41", "Cisco");
        VENDOR_DATABASE.put("00:0C:85", "Cisco");
        VENDOR_DATABASE.put("00:0C:CE", "Cisco");
        VENDOR_DATABASE.put("00:0C:CF", "Cisco");
        VENDOR_DATABASE.put("00:0D:28", "Cisco");
        VENDOR_DATABASE.put("00:0D:29", "Cisco");
        VENDOR_DATABASE.put("00:0D:BC", "Cisco");
        VENDOR_DATABASE.put("00:0D:BD", "Cisco");
        VENDOR_DATABASE.put("00:0D:EC", "Cisco");
        VENDOR_DATABASE.put("00:0D:ED", "Cisco");
        VENDOR_DATABASE.put("00:0E:08", "Cisco");
        VENDOR_DATABASE.put("00:0E:38", "Cisco");
        VENDOR_DATABASE.put("00:0E:39", "Cisco");
        VENDOR_DATABASE.put("00:0E:83", "Cisco");
        VENDOR_DATABASE.put("00:0E:84", "Cisco");
        VENDOR_DATABASE.put("00:0E:D6", "Cisco");
        VENDOR_DATABASE.put("00:0E:D7", "Cisco");
        VENDOR_DATABASE.put("00:0F:23", "Cisco");
        VENDOR_DATABASE.put("00:0F:24", "Cisco");
        VENDOR_DATABASE.put("00:0F:34", "Cisco");
        VENDOR_DATABASE.put("00:0F:35", "Cisco");
        VENDOR_DATABASE.put("00:0F:66", "Cisco");
        VENDOR_DATABASE.put("00:0F:8F", "Cisco");
        VENDOR_DATABASE.put("00:0F:90", "Cisco");
        VENDOR_DATABASE.put("00:0F:F7", "Cisco");
        VENDOR_DATABASE.put("00:0F:F8", "Cisco");
        VENDOR_DATABASE.put("00:10:07", "Cisco");
        VENDOR_DATABASE.put("00:10:11", "Cisco");
        VENDOR_DATABASE.put("00:10:29", "Cisco");
        VENDOR_DATABASE.put("00:10:2F", "Cisco");
        VENDOR_DATABASE.put("00:10:54", "Cisco");
        VENDOR_DATABASE.put("00:10:79", "Cisco");
        VENDOR_DATABASE.put("00:10:7B", "Cisco");
        VENDOR_DATABASE.put("00:10:A6", "Cisco");
        VENDOR_DATABASE.put("00:10:F6", "Cisco");
        VENDOR_DATABASE.put("00:11:20", "Cisco");
        VENDOR_DATABASE.put("00:11:21", "Cisco");
        VENDOR_DATABASE.put("00:11:5C", "Cisco");
        VENDOR_DATABASE.put("00:11:5D", "Cisco");
        VENDOR_DATABASE.put("00:11:92", "Cisco");
        VENDOR_DATABASE.put("00:11:93", "Cisco");
        VENDOR_DATABASE.put("00:11:BB", "Cisco");
        VENDOR_DATABASE.put("00:11:BC", "Cisco");
        VENDOR_DATABASE.put("00:12:00", "Cisco");
        VENDOR_DATABASE.put("00:12:01", "Cisco");
        VENDOR_DATABASE.put("00:12:43", "Cisco");
        VENDOR_DATABASE.put("00:12:44", "Cisco");
        VENDOR_DATABASE.put("00:12:7F", "Cisco");
        VENDOR_DATABASE.put("00:12:80", "Cisco");
        VENDOR_DATABASE.put("00:12:D9", "Cisco");
        VENDOR_DATABASE.put("00:12:DA", "Cisco");
        VENDOR_DATABASE.put("00:13:1A", "Cisco");
        VENDOR_DATABASE.put("00:13:5F", "Cisco");
        VENDOR_DATABASE.put("00:13:60", "Cisco");
        VENDOR_DATABASE.put("00:13:7F", "Cisco");
        VENDOR_DATABASE.put("00:13:80", "Cisco");
        VENDOR_DATABASE.put("00:13:C3", "Cisco");
        VENDOR_DATABASE.put("00:13:C4", "Cisco");
        VENDOR_DATABASE.put("00:14:1B", "Cisco");
        VENDOR_DATABASE.put("00:14:1C", "Cisco");
        VENDOR_DATABASE.put("00:14:69", "Cisco");
        VENDOR_DATABASE.put("00:14:6A", "Cisco");
        VENDOR_DATABASE.put("00:14:A8", "Cisco");
        VENDOR_DATABASE.put("00:14:A9", "Cisco");
        VENDOR_DATABASE.put("00:14:BF", "Cisco");
        VENDOR_DATABASE.put("00:14:F1", "Cisco");
        VENDOR_DATABASE.put("00:14:F2", "Cisco");
        VENDOR_DATABASE.put("00:15:2B", "Cisco");
        VENDOR_DATABASE.put("00:15:2C", "Cisco");
        VENDOR_DATABASE.put("00:15:62", "Cisco");
        VENDOR_DATABASE.put("00:15:63", "Cisco");
        VENDOR_DATABASE.put("00:15:C6", "Cisco");
        VENDOR_DATABASE.put("00:15:C7", "Cisco");
        VENDOR_DATABASE.put("00:15:F9", "Cisco");
        VENDOR_DATABASE.put("00:15:FA", "Cisco");
        VENDOR_DATABASE.put("00:16:46", "Cisco");
        VENDOR_DATABASE.put("00:16:47", "Cisco");
        VENDOR_DATABASE.put("00:16:9C", "Cisco");
        VENDOR_DATABASE.put("00:16:9D", "Cisco");
        VENDOR_DATABASE.put("00:16:B6", "Cisco");
        VENDOR_DATABASE.put("00:16:C7", "Cisco");
        VENDOR_DATABASE.put("00:17:0E", "Cisco");
        VENDOR_DATABASE.put("00:17:33", "Cisco");
        VENDOR_DATABASE.put("00:17:34", "Cisco");
        VENDOR_DATABASE.put("00:17:59", "Cisco");
        VENDOR_DATABASE.put("00:17:5A", "Cisco");
        VENDOR_DATABASE.put("00:17:94", "Cisco");
        VENDOR_DATABASE.put("00:17:95", "Cisco");
        VENDOR_DATABASE.put("00:17:DF", "Cisco");
        VENDOR_DATABASE.put("00:17:E0", "Cisco");
        VENDOR_DATABASE.put("00:18:18", "Cisco");
        VENDOR_DATABASE.put("00:18:19", "Cisco");
        VENDOR_DATABASE.put("00:18:39", "Cisco");
        VENDOR_DATABASE.put("00:18:68", "Cisco");
        VENDOR_DATABASE.put("00:18:73", "Cisco");
        VENDOR_DATABASE.put("00:18:74", "Cisco");
        VENDOR_DATABASE.put("00:18:B9", "Cisco");
        VENDOR_DATABASE.put("00:18:BA", "Cisco");
        VENDOR_DATABASE.put("00:19:06", "Cisco");
        VENDOR_DATABASE.put("00:19:07", "Cisco");
        VENDOR_DATABASE.put("00:19:2F", "Cisco");
        VENDOR_DATABASE.put("00:19:30", "Cisco");
        VENDOR_DATABASE.put("00:19:55", "Cisco");
        VENDOR_DATABASE.put("00:19:56", "Cisco");
        VENDOR_DATABASE.put("00:19:A9", "Cisco");
        VENDOR_DATABASE.put("00:19:AA", "Cisco");
        VENDOR_DATABASE.put("00:19:E7", "Cisco");
        VENDOR_DATABASE.put("00:19:E8", "Cisco");
        VENDOR_DATABASE.put("00:1A:2F", "Cisco");
        VENDOR_DATABASE.put("00:1A:30", "Cisco");
        VENDOR_DATABASE.put("00:1A:6C", "Cisco");
        VENDOR_DATABASE.put("00:1A:6D", "Cisco");
        VENDOR_DATABASE.put("00:1A:A1", "Cisco");
        VENDOR_DATABASE.put("00:1A:A2", "Cisco");
        VENDOR_DATABASE.put("00:1A:E2", "Cisco");
        VENDOR_DATABASE.put("00:1A:E3", "Cisco");
        VENDOR_DATABASE.put("00:1B:0C", "Cisco");
        VENDOR_DATABASE.put("00:1B:0D", "Cisco");
        VENDOR_DATABASE.put("00:1B:2A", "Cisco");
        VENDOR_DATABASE.put("00:1B:2B", "Cisco");
        VENDOR_DATABASE.put("00:1B:53", "Cisco");
        VENDOR_DATABASE.put("00:1B:54", "Cisco");
        VENDOR_DATABASE.put("00:1B:67", "Cisco");
        VENDOR_DATABASE.put("00:1B:8F", "Cisco");
        VENDOR_DATABASE.put("00:1B:90", "Cisco");
        VENDOR_DATABASE.put("00:1B:D4", "Cisco");
        VENDOR_DATABASE.put("00:1B:D5", "Cisco");
        VENDOR_DATABASE.put("00:1C:0E", "Cisco");
        VENDOR_DATABASE.put("00:1C:0F", "Cisco");
        VENDOR_DATABASE.put("00:1C:57", "Cisco");
        VENDOR_DATABASE.put("00:1C:58", "Cisco");
        VENDOR_DATABASE.put("00:1C:B0", "Cisco");
        VENDOR_DATABASE.put("00:1C:B1", "Cisco");
        VENDOR_DATABASE.put("00:1C:F6", "Cisco");
        VENDOR_DATABASE.put("00:1C:F9", "Cisco");
        VENDOR_DATABASE.put("00:1D:45", "Cisco");
        VENDOR_DATABASE.put("00:1D:46", "Cisco");
        VENDOR_DATABASE.put("00:1D:70", "Cisco");
        VENDOR_DATABASE.put("00:1D:71", "Cisco");
        VENDOR_DATABASE.put("00:1D:A1", "Cisco");
        VENDOR_DATABASE.put("00:1D:A2", "Cisco");
        VENDOR_DATABASE.put("00:1D:E5", "Cisco");
        VENDOR_DATABASE.put("00:1D:E6", "Cisco");
        VENDOR_DATABASE.put("00:1E:13", "Cisco");
        VENDOR_DATABASE.put("00:1E:14", "Cisco");
        VENDOR_DATABASE.put("00:1E:49", "Cisco");
        VENDOR_DATABASE.put("00:1E:4A", "Cisco");
        VENDOR_DATABASE.put("00:1E:6B", "Cisco");
        VENDOR_DATABASE.put("00:1E:79", "Cisco");
        VENDOR_DATABASE.put("00:1E:7A", "Cisco");
        VENDOR_DATABASE.put("00:1E:BD", "Cisco");
        VENDOR_DATABASE.put("00:1E:BE", "Cisco");
        VENDOR_DATABASE.put("00:1E:F6", "Cisco");
        VENDOR_DATABASE.put("00:1E:F7", "Cisco");
        VENDOR_DATABASE.put("00:1F:26", "Cisco");
        VENDOR_DATABASE.put("00:1F:27", "Cisco");
        VENDOR_DATABASE.put("00:1F:6C", "Cisco");
        VENDOR_DATABASE.put("00:1F:6D", "Cisco");
        VENDOR_DATABASE.put("00:1F:9D", "Cisco");
        VENDOR_DATABASE.put("00:1F:9E", "Cisco");
        VENDOR_DATABASE.put("00:1F:CA", "Cisco");
        VENDOR_DATABASE.put("00:1F:CB", "Cisco");
        VENDOR_DATABASE.put("00:21:1B", "Cisco");
        VENDOR_DATABASE.put("00:21:1C", "Cisco");
        VENDOR_DATABASE.put("00:21:55", "Cisco");
        VENDOR_DATABASE.put("00:21:56", "Cisco");
        VENDOR_DATABASE.put("00:21:A0", "Cisco");
        VENDOR_DATABASE.put("00:21:A1", "Cisco");
        VENDOR_DATABASE.put("00:21:D7", "Cisco");
        VENDOR_DATABASE.put("00:21:D8", "Cisco");
        VENDOR_DATABASE.put("00:22:0C", "Cisco");
        VENDOR_DATABASE.put("00:22:0D", "Cisco");
        VENDOR_DATABASE.put("00:22:55", "Cisco");
        VENDOR_DATABASE.put("00:22:56", "Cisco");
        VENDOR_DATABASE.put("00:22:90", "Cisco");
        VENDOR_DATABASE.put("00:22:91", "Cisco");
        VENDOR_DATABASE.put("00:22:BD", "Cisco");
        VENDOR_DATABASE.put("00:22:BE", "Cisco");
        VENDOR_DATABASE.put("00:23:04", "Cisco");
        VENDOR_DATABASE.put("00:23:05", "Cisco");
        VENDOR_DATABASE.put("00:23:33", "Cisco");
        VENDOR_DATABASE.put("00:23:34", "Cisco");
        VENDOR_DATABASE.put("00:23:5D", "Cisco");
        VENDOR_DATABASE.put("00:23:5E", "Cisco");
        VENDOR_DATABASE.put("00:23:AB", "Cisco");
        VENDOR_DATABASE.put("00:23:AC", "Cisco");
        VENDOR_DATABASE.put("00:23:BE", "Cisco");
        VENDOR_DATABASE.put("00:23:EA", "Cisco");
        VENDOR_DATABASE.put("00:23:EB", "Cisco");
        VENDOR_DATABASE.put("00:24:13", "Cisco");
        VENDOR_DATABASE.put("00:24:14", "Cisco");
        VENDOR_DATABASE.put("00:24:50", "Cisco");
        VENDOR_DATABASE.put("00:24:51", "Cisco");
        VENDOR_DATABASE.put("00:24:97", "Cisco");
        VENDOR_DATABASE.put("00:24:98", "Cisco");
        VENDOR_DATABASE.put("00:24:C3", "Cisco");
        VENDOR_DATABASE.put("00:24:C4", "Cisco");
        VENDOR_DATABASE.put("00:24:F7", "Cisco");
        VENDOR_DATABASE.put("00:24:F9", "Cisco");
        VENDOR_DATABASE.put("00:25:2E", "Cisco");
        VENDOR_DATABASE.put("00:25:45", "Cisco");
        VENDOR_DATABASE.put("00:25:46", "Cisco");
        VENDOR_DATABASE.put("00:25:83", "Cisco");
        VENDOR_DATABASE.put("00:25:84", "Cisco");
        VENDOR_DATABASE.put("00:25:B4", "Cisco");
        VENDOR_DATABASE.put("00:25:B5", "Cisco");
        VENDOR_DATABASE.put("00:26:0A", "Cisco");
        VENDOR_DATABASE.put("00:26:0B", "Cisco");
        VENDOR_DATABASE.put("00:26:51", "Cisco");
        VENDOR_DATABASE.put("00:26:52", "Cisco");
        VENDOR_DATABASE.put("00:26:98", "Cisco");
        VENDOR_DATABASE.put("00:26:99", "Cisco");
        VENDOR_DATABASE.put("00:26:CA", "Cisco");
        VENDOR_DATABASE.put("00:26:CB", "Cisco");
        VENDOR_DATABASE.put("00:30:19", "Cisco");
        VENDOR_DATABASE.put("00:30:24", "Cisco");
        VENDOR_DATABASE.put("00:30:40", "Cisco");
        VENDOR_DATABASE.put("00:30:71", "Cisco");
        VENDOR_DATABASE.put("00:30:78", "Cisco");
        VENDOR_DATABASE.put("00:30:7B", "Cisco");
        VENDOR_DATABASE.put("00:30:80", "Cisco");
        VENDOR_DATABASE.put("00:30:85", "Cisco");
        VENDOR_DATABASE.put("00:30:94", "Cisco");
        VENDOR_DATABASE.put("00:30:96", "Cisco");
        VENDOR_DATABASE.put("00:30:A3", "Cisco");
        VENDOR_DATABASE.put("00:30:B6", "Cisco");
        VENDOR_DATABASE.put("00:30:F2", "Cisco");
        VENDOR_DATABASE.put("00:60:2F", "Cisco");
        VENDOR_DATABASE.put("00:60:3E", "Cisco");
        VENDOR_DATABASE.put("00:60:47", "Cisco");
        VENDOR_DATABASE.put("00:60:5C", "Cisco");
        VENDOR_DATABASE.put("00:60:70", "Cisco");
        VENDOR_DATABASE.put("00:60:83", "Cisco");
        VENDOR_DATABASE.put("00:90:0C", "Cisco");
        VENDOR_DATABASE.put("00:90:21", "Cisco");
        VENDOR_DATABASE.put("00:90:2B", "Cisco");
        VENDOR_DATABASE.put("00:90:5F", "Cisco");
        VENDOR_DATABASE.put("00:90:6D", "Cisco");
        VENDOR_DATABASE.put("00:90:86", "Cisco");
        VENDOR_DATABASE.put("00:90:92", "Cisco");
        VENDOR_DATABASE.put("00:90:A6", "Cisco");
        VENDOR_DATABASE.put("00:90:AB", "Cisco");
        VENDOR_DATABASE.put("00:90:B1", "Cisco");
        VENDOR_DATABASE.put("00:90:BF", "Cisco");
        VENDOR_DATABASE.put("00:90:D9", "Cisco");
        VENDOR_DATABASE.put("00:90:F2", "Cisco");
        VENDOR_DATABASE.put("00:A0:C9", "Cisco");
        VENDOR_DATABASE.put("00:B0:64", "Cisco");
        VENDOR_DATABASE.put("00:D0:06", "Cisco");
        VENDOR_DATABASE.put("00:D0:58", "Cisco");
        VENDOR_DATABASE.put("00:D0:79", "Cisco");
        VENDOR_DATABASE.put("00:D0:90", "Cisco");
        VENDOR_DATABASE.put("00:D0:97", "Cisco");
        VENDOR_DATABASE.put("00:D0:BA", "Cisco");
        VENDOR_DATABASE.put("00:D0:BB", "Cisco");
        VENDOR_DATABASE.put("00:D0:BC", "Cisco");
        VENDOR_DATABASE.put("00:D0:C0", "Cisco");
        VENDOR_DATABASE.put("00:D0:D3", "Cisco");
        VENDOR_DATABASE.put("00:D0:E4", "Cisco");
        VENDOR_DATABASE.put("00:D0:FF", "Cisco");
        VENDOR_DATABASE.put("00:E0:14", "Cisco");
        VENDOR_DATABASE.put("00:E0:1E", "Cisco");
        VENDOR_DATABASE.put("00:E0:34", "Cisco");
        VENDOR_DATABASE.put("00:E0:4F", "Cisco");
        VENDOR_DATABASE.put("00:E0:A3", "Cisco");
        VENDOR_DATABASE.put("00:E0:B0", "Cisco");
        VENDOR_DATABASE.put("00:E0:F7", "Cisco");
        VENDOR_DATABASE.put("00:E0:F9", "Cisco");
        VENDOR_DATABASE.put("00:E0:FE", "Cisco");
        
        // Netgear
        VENDOR_DATABASE.put("00:09:5B", "Netgear");
        VENDOR_DATABASE.put("00:0F:B5", "Netgear");
        VENDOR_DATABASE.put("00:14:6C", "Netgear");
        VENDOR_DATABASE.put("00:18:4D", "Netgear");
        VENDOR_DATABASE.put("00:1B:2F", "Netgear");
        VENDOR_DATABASE.put("00:1E:2A", "Netgear");
        VENDOR_DATABASE.put("00:22:3F", "Netgear");
        VENDOR_DATABASE.put("00:24:B2", "Netgear");
        VENDOR_DATABASE.put("00:26:F2", "Netgear");
        VENDOR_DATABASE.put("20:4E:7F", "Netgear");
        VENDOR_DATABASE.put("28:C6:8E", "Netgear");
        VENDOR_DATABASE.put("2C:30:33", "Netgear");
        VENDOR_DATABASE.put("30:46:9A", "Netgear");
        VENDOR_DATABASE.put("44:94:FC", "Netgear");
        VENDOR_DATABASE.put("4C:60:DE", "Netgear");
        VENDOR_DATABASE.put("84:1B:5E", "Netgear");
        VENDOR_DATABASE.put("A0:21:B7", "Netgear");
        VENDOR_DATABASE.put("B0:39:56", "Netgear");
        VENDOR_DATABASE.put("C0:3F:0E", "Netgear");
        VENDOR_DATABASE.put("E0:46:9A", "Netgear");
        
        // TP-Link
        VENDOR_DATABASE.put("00:25:86", "TP-Link");
        VENDOR_DATABASE.put("14:CF:92", "TP-Link");
        VENDOR_DATABASE.put("1C:BD:B9", "TP-Link");
        VENDOR_DATABASE.put("50:C7:BF", "TP-Link");
        VENDOR_DATABASE.put("60:E3:27", "TP-Link");
        VENDOR_DATABASE.put("64:70:02", "TP-Link");
        VENDOR_DATABASE.put("74:DA:38", "TP-Link");
        VENDOR_DATABASE.put("84:16:F9", "TP-Link");
        VENDOR_DATABASE.put("A4:2B:B0", "TP-Link");
        VENDOR_DATABASE.put("B0:48:7A", "TP-Link");
        VENDOR_DATABASE.put("C4:6E:1F", "TP-Link");
        VENDOR_DATABASE.put("E8:DE:27", "TP-Link");
        VENDOR_DATABASE.put("F4:F2:6D", "TP-Link");
        
        // D-Link
        VENDOR_DATABASE.put("00:05:5D", "D-Link");
        VENDOR_DATABASE.put("00:0D:88", "D-Link");
        VENDOR_DATABASE.put("00:0F:3D", "D-Link");
        VENDOR_DATABASE.put("00:13:46", "D-Link");
        VENDOR_DATABASE.put("00:15:E9", "D-Link");
        VENDOR_DATABASE.put("00:17:9A", "D-Link");
        VENDOR_DATABASE.put("00:19:5B", "D-Link");
        VENDOR_DATABASE.put("00:1B:11", "D-Link");
        VENDOR_DATABASE.put("00:1C:F0", "D-Link");
        VENDOR_DATABASE.put("00:1E:58", "D-Link");
        VENDOR_DATABASE.put("00:21:91", "D-Link");
        VENDOR_DATABASE.put("00:22:B0", "D-Link");
        VENDOR_DATABASE.put("00:24:01", "D-Link");
        VENDOR_DATABASE.put("00:26:5A", "D-Link");
        VENDOR_DATABASE.put("14:D6:4D", "D-Link");
        VENDOR_DATABASE.put("1C:7E:E5", "D-Link");
        VENDOR_DATABASE.put("28:10:7B", "D-Link");
        VENDOR_DATABASE.put("34:08:04", "D-Link");
        VENDOR_DATABASE.put("5C:D9:98", "D-Link");
        VENDOR_DATABASE.put("78:54:2E", "D-Link");
        VENDOR_DATABASE.put("84:C9:B2", "D-Link");
        VENDOR_DATABASE.put("90:94:E4", "D-Link");
        VENDOR_DATABASE.put("B8:A3:86", "D-Link");
        VENDOR_DATABASE.put("C8:BE:19", "D-Link");
        VENDOR_DATABASE.put("C8:D3:A3", "D-Link");
        VENDOR_DATABASE.put("CC:B2:55", "D-Link");
        VENDOR_DATABASE.put("E4:6F:13", "D-Link");
        
        // Linksys
        VENDOR_DATABASE.put("00:06:25", "Linksys");
        VENDOR_DATABASE.put("00:0C:41", "Linksys");
        VENDOR_DATABASE.put("00:0E:08", "Linksys");
        VENDOR_DATABASE.put("00:12:17", "Linksys");
        VENDOR_DATABASE.put("00:13:10", "Linksys");
        VENDOR_DATABASE.put("00:14:BF", "Linksys");
        VENDOR_DATABASE.put("00:16:B6", "Linksys");
        VENDOR_DATABASE.put("00:18:39", "Linksys");
        VENDOR_DATABASE.put("00:18:F8", "Linksys");
        VENDOR_DATABASE.put("00:1A:70", "Linksys");
        VENDOR_DATABASE.put("00:1C:10", "Linksys");
        VENDOR_DATABASE.put("00:1D:7E", "Linksys");
        VENDOR_DATABASE.put("00:1E:E5", "Linksys");
        VENDOR_DATABASE.put("00:20:A6", "Linksys");
        VENDOR_DATABASE.put("00:21:29", "Linksys");
        VENDOR_DATABASE.put("00:22:6B", "Linksys");
        VENDOR_DATABASE.put("00:23:69", "Linksys");
        VENDOR_DATABASE.put("00:25:9C", "Linksys");
        VENDOR_DATABASE.put("48:F8:B3", "Linksys");
        VENDOR_DATABASE.put("58:6D:8F", "Linksys");
        VENDOR_DATABASE.put("C0:56:27", "Linksys");
        VENDOR_DATABASE.put("E8:9F:80", "Linksys");
        
        // ASUS
        VENDOR_DATABASE.put("00:0E:A6", "ASUS");
        VENDOR_DATABASE.put("00:11:2F", "ASUS");
        VENDOR_DATABASE.put("00:13:D4", "ASUS");
        VENDOR_DATABASE.put("00:15:F2", "ASUS");
        VENDOR_DATABASE.put("00:17:31", "ASUS");
        VENDOR_DATABASE.put("00:19:DB", "ASUS");
        VENDOR_DATABASE.put("00:1B:FC", "ASUS");
        VENDOR_DATABASE.put("00:1E:8C", "ASUS");
        VENDOR_DATABASE.put("00:22:15", "ASUS");
        VENDOR_DATABASE.put("00:24:8C", "ASUS");
        VENDOR_DATABASE.put("00:26:18", "ASUS");
        VENDOR_DATABASE.put("04:D4:C4", "ASUS");
        VENDOR_DATABASE.put("08:60:6E", "ASUS");
        VENDOR_DATABASE.put("10:C3:7B", "ASUS");
        VENDOR_DATABASE.put("14:DD:A9", "ASUS");
        VENDOR_DATABASE.put("1C:87:2C", "ASUS");
        VENDOR_DATABASE.put("20:CF:30", "ASUS");
        VENDOR_DATABASE.put("2C:56:DC", "ASUS");
        VENDOR_DATABASE.put("30:5A:3A", "ASUS");
        VENDOR_DATABASE.put("38:D5:47", "ASUS");
        VENDOR_DATABASE.put("40:16:7E", "ASUS");
        VENDOR_DATABASE.put("50:46:5D", "ASUS");
        VENDOR_DATABASE.put("54:04:A6", "ASUS");
        VENDOR_DATABASE.put("60:45:CB", "ASUS");
        VENDOR_DATABASE.put("70:4D:7B", "ASUS");
        VENDOR_DATABASE.put("74:D0:2B", "ASUS");
        VENDOR_DATABASE.put("88:D7:F6", "ASUS");
        VENDOR_DATABASE.put("9C:5C:8E", "ASUS");
        VENDOR_DATABASE.put("AC:9E:17", "ASUS");
        VENDOR_DATABASE.put("B0:6E:BF", "ASUS");
        VENDOR_DATABASE.put("BC:AE:C5", "ASUS");
        VENDOR_DATABASE.put("D0:17:C2", "ASUS");
        VENDOR_DATABASE.put("E0:3F:49", "ASUS");
        VENDOR_DATABASE.put("F4:6D:04", "ASUS");
        
        // Samsung
        VENDOR_DATABASE.put("00:02:78", "Samsung");
        VENDOR_DATABASE.put("00:07:AB", "Samsung");
        VENDOR_DATABASE.put("00:09:18", "Samsung");
        VENDOR_DATABASE.put("00:0D:E5", "Samsung");
        VENDOR_DATABASE.put("00:12:FB", "Samsung");
        VENDOR_DATABASE.put("00:13:77", "Samsung");
        VENDOR_DATABASE.put("00:15:99", "Samsung");
        VENDOR_DATABASE.put("00:16:32", "Samsung");
        VENDOR_DATABASE.put("00:16:6B", "Samsung");
        VENDOR_DATABASE.put("00:16:DB", "Samsung");
        VENDOR_DATABASE.put("00:17:C9", "Samsung");
        VENDOR_DATABASE.put("00:17:D5", "Samsung");
        VENDOR_DATABASE.put("00:18:AF", "Samsung");
        VENDOR_DATABASE.put("00:1A:8A", "Samsung");
        VENDOR_DATABASE.put("00:1B:98", "Samsung");
        VENDOR_DATABASE.put("00:1D:25", "Samsung");
        VENDOR_DATABASE.put("00:1E:7D", "Samsung");
        VENDOR_DATABASE.put("00:1F:CC", "Samsung");
        VENDOR_DATABASE.put("00:21:19", "Samsung");
        VENDOR_DATABASE.put("00:21:D1", "Samsung");
        VENDOR_DATABASE.put("00:23:39", "Samsung");
        VENDOR_DATABASE.put("00:23:C2", "Samsung");
        VENDOR_DATABASE.put("00:24:54", "Samsung");
        VENDOR_DATABASE.put("00:24:E9", "Samsung");
        VENDOR_DATABASE.put("00:26:37", "Samsung");
        VENDOR_DATABASE.put("08:08:C2", "Samsung");
        VENDOR_DATABASE.put("08:37:3D", "Samsung");
        VENDOR_DATABASE.put("08:D4:2B", "Samsung");
        VENDOR_DATABASE.put("0C:14:20", "Samsung");
        VENDOR_DATABASE.put("0C:89:10", "Samsung");
        VENDOR_DATABASE.put("10:1D:C0", "Samsung");
        VENDOR_DATABASE.put("18:3A:2D", "Samsung");
        VENDOR_DATABASE.put("18:3D:A2", "Samsung");
        VENDOR_DATABASE.put("18:CF:5E", "Samsung");
        VENDOR_DATABASE.put("1C:5A:3E", "Samsung");
        VENDOR_DATABASE.put("20:64:32", "Samsung");
        VENDOR_DATABASE.put("20:A5:BF", "Samsung");
        VENDOR_DATABASE.put("24:4B:81", "Samsung");
        VENDOR_DATABASE.put("24:5E:BE", "Samsung");
        VENDOR_DATABASE.put("28:BA:B5", "Samsung");
        VENDOR_DATABASE.put("28:CC:01", "Samsung");
        VENDOR_DATABASE.put("2C:8A:72", "Samsung");
        VENDOR_DATABASE.put("34:BE:00", "Samsung");
        VENDOR_DATABASE.put("38:AA:3C", "Samsung");
        VENDOR_DATABASE.put("38:E7:D8", "Samsung");
        VENDOR_DATABASE.put("3C:5A:B4", "Samsung");
        VENDOR_DATABASE.put("40:0E:85", "Samsung");
        VENDOR_DATABASE.put("40:5D:82", "Samsung");
        VENDOR_DATABASE.put("44:4E:6D", "Samsung");
        VENDOR_DATABASE.put("44:5E:F3", "Samsung");
        VENDOR_DATABASE.put("48:5A:3F", "Samsung");
        VENDOR_DATABASE.put("4C:3C:16", "Samsung");
        VENDOR_DATABASE.put("4C:66:41", "Samsung");
        VENDOR_DATABASE.put("50:01:BB", "Samsung");
        VENDOR_DATABASE.put("50:32:37", "Samsung");
        VENDOR_DATABASE.put("50:B7:C3", "Samsung");
        VENDOR_DATABASE.put("54:88:0E", "Samsung");
        VENDOR_DATABASE.put("58:21:DB", "Samsung");
        VENDOR_DATABASE.put("5C:0A:5B", "Samsung");
        VENDOR_DATABASE.put("5C:F6:DC", "Samsung");
        VENDOR_DATABASE.put("60:6B:BD", "Samsung");
        VENDOR_DATABASE.put("60:A1:0A", "Samsung");
        VENDOR_DATABASE.put("60:D0:A9", "Samsung");
        VENDOR_DATABASE.put("64:B8:53", "Samsung");
        VENDOR_DATABASE.put("68:EB:C5", "Samsung");
        VENDOR_DATABASE.put("6C:2F:2C", "Samsung");
        VENDOR_DATABASE.put("6C:F3:73", "Samsung");
        VENDOR_DATABASE.put("70:F9:27", "Samsung");
        VENDOR_DATABASE.put("74:45:8A", "Samsung");
        VENDOR_DATABASE.put("78:1F:DB", "Samsung");
        VENDOR_DATABASE.put("78:25:AD", "Samsung");
        VENDOR_DATABASE.put("78:47:1D", "Samsung");
        VENDOR_DATABASE.put("78:59:5E", "Samsung");
        VENDOR_DATABASE.put("78:9E:D0", "Samsung");
        VENDOR_DATABASE.put("7C:1C:4E", "Samsung");
        VENDOR_DATABASE.put("7C:61:66", "Samsung");
        VENDOR_DATABASE.put("80:18:A7", "Samsung");
        VENDOR_DATABASE.put("80:57:19", "Samsung");
        VENDOR_DATABASE.put("84:25:3F", "Samsung");
        VENDOR_DATABASE.put("84:A4:66", "Samsung");
        VENDOR_DATABASE.put("88:32:9B", "Samsung");
        VENDOR_DATABASE.put("8C:77:12", "Samsung");
        VENDOR_DATABASE.put("8C:C8:CD", "Samsung");
        VENDOR_DATABASE.put("90:18:7C", "Samsung");
        VENDOR_DATABASE.put("94:51:03", "Samsung");
        VENDOR_DATABASE.put("94:E9:79", "Samsung");
        VENDOR_DATABASE.put("98:52:3D", "Samsung");
        VENDOR_DATABASE.put("98:E7:43", "Samsung");
        VENDOR_DATABASE.put("9C:02:98", "Samsung");
        VENDOR_DATABASE.put("9C:3A:AF", "Samsung");
        VENDOR_DATABASE.put("A0:0B:BA", "Samsung");
        VENDOR_DATABASE.put("A0:75:91", "Samsung");
        VENDOR_DATABASE.put("A0:82:1F", "Samsung");
        VENDOR_DATABASE.put("A0:B4:A5", "Samsung");
        VENDOR_DATABASE.put("A4:EB:D3", "Samsung");
        VENDOR_DATABASE.put("A8:F2:74", "Samsung");
        VENDOR_DATABASE.put("AC:36:13", "Samsung");
        VENDOR_DATABASE.put("AC:5F:3E", "Samsung");
        VENDOR_DATABASE.put("B4:62:93", "Samsung");
        VENDOR_DATABASE.put("B4:EF:39", "Samsung");
        VENDOR_DATABASE.put("B8:5E:7B", "Samsung");
        VENDOR_DATABASE.put("BC:20:A4", "Samsung");
        VENDOR_DATABASE.put("BC:72:B1", "Samsung");
        VENDOR_DATABASE.put("BC:85:1F", "Samsung");
        VENDOR_DATABASE.put("BC:F5:AC", "Samsung");
        VENDOR_DATABASE.put("C0:BD:D1", "Samsung");
        VENDOR_DATABASE.put("C4:42:02", "Samsung");
        VENDOR_DATABASE.put("C4:57:6E", "Samsung");
        VENDOR_DATABASE.put("C8:19:F7", "Samsung");
        VENDOR_DATABASE.put("C8:21:58", "Samsung");
        VENDOR_DATABASE.put("C8:3A:6B", "Samsung");
        VENDOR_DATABASE.put("CC:07:AB", "Samsung");
        VENDOR_DATABASE.put("CC:F9:E8", "Samsung");
        VENDOR_DATABASE.put("D0:22:BE", "Samsung");
        VENDOR_DATABASE.put("D0:59:E4", "Samsung");
        VENDOR_DATABASE.put("D4:87:D8", "Samsung");
        VENDOR_DATABASE.put("D4:E8:B2", "Samsung");
        VENDOR_DATABASE.put("D8:31:CF", "Samsung");
        VENDOR_DATABASE.put("D8:90:E8", "Samsung");
        VENDOR_DATABASE.put("DC:71:44", "Samsung");
        VENDOR_DATABASE.put("E0:91:F5", "Samsung");
        VENDOR_DATABASE.put("E4:40:E2", "Samsung");
        VENDOR_DATABASE.put("E8:50:8B", "Samsung");
        VENDOR_DATABASE.put("EC:1F:72", "Samsung");
        VENDOR_DATABASE.put("EC:9B:F3", "Samsung");
        VENDOR_DATABASE.put("F0:25:B7", "Samsung");
        VENDOR_DATABASE.put("F4:7B:5E", "Samsung");
        VENDOR_DATABASE.put("F8:04:2E", "Samsung");
        VENDOR_DATABASE.put("F8:D0:BD", "Samsung");
        VENDOR_DATABASE.put("FC:00:12", "Samsung");
        VENDOR_DATABASE.put("FC:A6:21", "Samsung");
        VENDOR_DATABASE.put("FC:C2:DE", "Samsung");
    }
    
    @Inject
    public ARPScanner() {}
    
    /**
     * Performs comprehensive ARP-based device discovery on the local network
     */
    public Map<String, NetworkHost> performARPScan(String networkRange, Consumer<String> progressCallback) {
        Map<String, NetworkHost> discoveredDevices = new ConcurrentHashMap<>();
        
        try {
            progressCallback.accept("Starting ARP-based device discovery...");
            
            // Method 1: Parse system ARP table
            Map<String, NetworkHost> arpTableDevices = parseSystemARPTable();
            discoveredDevices.putAll(arpTableDevices);
            progressCallback.accept("Found " + arpTableDevices.size() + " devices in ARP table");
            
            // Method 2: Active ARP scanning (if tools available)
            Map<String, NetworkHost> activeARPDevices = performActiveARPScan(networkRange, progressCallback);
            
            // Merge results, preferring ARP table data
            for (Map.Entry<String, NetworkHost> entry : activeARPDevices.entrySet()) {
                String ip = entry.getKey();
                NetworkHost newHost = entry.getValue();
                
                if (discoveredDevices.containsKey(ip)) {
                    // Merge data - prefer ARP table MAC address if available
                    NetworkHost existingHost = discoveredDevices.get(ip);
                    if (existingHost.getMacAddress() == null || existingHost.getMacAddress().isEmpty()) {
                        existingHost.setMacAddress(newHost.getMacAddress());
                        existingHost.setVendor(newHost.getVendor());
                    }
                } else {
                    discoveredDevices.put(ip, newHost);
                }
            }
            
            // Method 3: Network interface enumeration
            addLocalInterfaces(discoveredDevices, progressCallback);
            
            // Enhance all discovered devices with additional information
            enhanceDeviceInformation(discoveredDevices, progressCallback);
            
            progressCallback.accept("ARP scan completed. Found " + discoveredDevices.size() + " total devices");
            
        } catch (Exception e) {
            logger.error("Error during ARP scanning", e);
            progressCallback.accept("ARP scan error: " + e.getMessage());
        }
        
        return discoveredDevices;
    }
    
    /**
     * Parse the system ARP table to find devices
     */
    private Map<String, NetworkHost> parseSystemARPTable() {
        Map<String, NetworkHost> devices = new HashMap<>();
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("arp -a");
            } else if (os.contains("mac")) {
                process = Runtime.getRuntime().exec("arp -a");
            } else {
                process = Runtime.getRuntime().exec("arp -a");
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            // Different regex patterns for different OS formats
            Pattern windowsPattern = Pattern.compile("\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F-]{17})\\s+.*");
            Pattern unixPattern = Pattern.compile(".*\\((\\d+\\.\\d+\\.\\d+\\.\\d+)\\)\\s+at\\s+([0-9a-fA-F:]{17}).*");
            Pattern macPattern = Pattern.compile(".*\\((\\d+\\.\\d+\\.\\d+\\.\\d+)\\)\\s+at\\s+([0-9a-fA-F:]{17}).*");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = null;
                String ip = null;
                String mac = null;
                
                if (os.contains("win")) {
                    matcher = windowsPattern.matcher(line);
                    if (matcher.matches()) {
                        ip = matcher.group(1);
                        mac = matcher.group(2).replace("-", ":");
                    }
                } else {
                    matcher = unixPattern.matcher(line);
                    if (matcher.matches()) {
                        ip = matcher.group(1);
                        mac = matcher.group(2);
                    }
                }
                
                if (ip != null && mac != null && !mac.equals("ff:ff:ff:ff:ff:ff")) {
                    NetworkHost host = new NetworkHost(ip);
                    host.setMacAddress(mac.toLowerCase());
                    host.setVendor(getVendorFromMAC(mac));
                    host.setAlive(true);
                    devices.put(ip, host);
                    
                    logger.debug("Found device in ARP table: {} -> {}", ip, mac);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error parsing ARP table", e);
        }
        
        return devices;
    }
    
    /**
     * Perform active ARP scanning using ping sweep
     */
    private Map<String, NetworkHost> performActiveARPScan(String networkRange, Consumer<String> progressCallback) {
        Map<String, NetworkHost> devices = new HashMap<>();
        
        try {
            // Parse CIDR network range
            List<String> targetIPs = parseCIDRRange(networkRange);
            progressCallback.accept("Performing active scan of " + targetIPs.size() + " addresses...");
            
            // Ping all addresses to populate ARP table
            for (String ip : targetIPs) {
                try {
                    InetAddress address = InetAddress.getByName(ip);
                    if (address.isReachable(1000)) { // 1 second timeout
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
                        
                        devices.put(ip, host);
                    }
                } catch (Exception e) {
                    // Host unreachable
                }
            }
            
            // After pinging, re-parse ARP table to get MAC addresses
            if (!devices.isEmpty()) {
                Map<String, NetworkHost> arpTableUpdate = parseSystemARPTable();
                for (Map.Entry<String, NetworkHost> entry : devices.entrySet()) {
                    String ip = entry.getKey();
                    NetworkHost host = entry.getValue();
                    
                    if (arpTableUpdate.containsKey(ip)) {
                        NetworkHost arpHost = arpTableUpdate.get(ip);
                        host.setMacAddress(arpHost.getMacAddress());
                        host.setVendor(arpHost.getVendor());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in active ARP scanning", e);
        }
        
        return devices;
    }
    
    /**
     * Add local network interfaces to the device list
     */
    private void addLocalInterfaces(Map<String, NetworkHost> devices, Consumer<String> progressCallback) {
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
                    
                    if (!devices.containsKey(ip)) {
                        NetworkHost host = new NetworkHost(ip);
                        host.setAlive(true);
                        host.setHostname("localhost");
                        
                        if (macAddress != null) {
                            host.setMacAddress(macAddress);
                            host.setVendor(getVendorFromMAC(macAddress));
                        }
                        
                        devices.put(ip, host);
                        progressCallback.accept("Added local interface: " + ip);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error enumerating local interfaces", e);
        }
    }
    
    /**
     * Enhance device information with additional details
     */
    private void enhanceDeviceInformation(Map<String, NetworkHost> devices, Consumer<String> progressCallback) {
        for (NetworkHost host : devices.values()) {
            try {
                // Enhance hostname resolution
                if (host.getHostname() == null || host.getHostname().isEmpty()) {
                    try {
                        InetAddress address = InetAddress.getByName(host.getIpAddress());
                        String hostname = address.getCanonicalHostName();
                        if (!hostname.equals(host.getIpAddress())) {
                            host.setHostname(hostname);
                        }
                    } catch (Exception e) {
                        // Hostname resolution failed
                    }
                }
                
                // Set device type based on vendor and other characteristics
                if (host.getVendor() != null) {
                    String vendor = host.getVendor().toLowerCase();
                    if (vendor.contains("cisco") || vendor.contains("netgear") || 
                        vendor.contains("tp-link") || vendor.contains("d-link") || 
                        vendor.contains("linksys") || vendor.contains("asus")) {
                        host.addService("Network Device (" + host.getVendor() + ")");
                    } else if (vendor.contains("apple")) {
                        host.addService("Apple Device");
                    } else if (vendor.contains("samsung")) {
                        host.addService("Samsung Device");
                    } else if (vendor.contains("intel")) {
                        host.addService("Intel-based Device");
                    }
                }
                
                // Try to determine if it's likely a gateway
                if (host.getIpAddress().endsWith(".1") || host.getIpAddress().endsWith(".254")) {
                    host.addService("Likely Gateway/Router");
                }
                
            } catch (Exception e) {
                logger.debug("Error enhancing device information for {}", host.getIpAddress(), e);
            }
        }
    }
    
    /**
     * Get vendor information from MAC address
     */
    private String getVendorFromMAC(String macAddress) {
        if (macAddress == null || macAddress.length() < 8) {
            return "Unknown";
        }
        
        // Get first 3 octets (OUI)
        String oui = macAddress.substring(0, 8).toUpperCase();
        
        String vendor = VENDOR_DATABASE.get(oui);
        return vendor != null ? vendor : "Unknown";
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
} 