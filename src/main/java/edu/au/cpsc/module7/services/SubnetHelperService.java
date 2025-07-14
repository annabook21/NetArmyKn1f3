package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.SubnetCalculation;
import com.google.inject.Singleton;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for subnet calculations and IP address validation.
 * Converts Python subnet helper logic to Java with enhanced features.
 */
@Singleton
public class SubnetHelperService {

    /**
     * Finds the closest valid IP address to the given input.
     */
    public String getClosestValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return "192.168.1.1";
        }

        String[] segments = ip.split("\\.");
        if (segments.length != 4) {
            return "192.168.1.1";
        }

        StringBuilder validIp = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            try {
                int segment = Integer.parseInt(segments[i].trim());
                segment = Math.max(0, Math.min(255, segment));
                validIp.append(segment);
                if (i < segments.length - 1) {
                    validIp.append(".");
                }
            } catch (NumberFormatException e) {
                validIp.append("0");
                if (i < segments.length - 1) {
                    validIp.append(".");
                }
            }
        }
        return validIp.toString();
    }

    /**
     * Validates if the given string is a valid IP address.
     */
    public boolean isValidIp(String ip) {
        try {
            InetAddress.getByName(ip);
            String[] segments = ip.split("\\.");
            if (segments.length != 4) return false;
            
            for (String segment : segments) {
                int value = Integer.parseInt(segment);
                if (value < 0 || value > 255) return false;
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    /**
     * Gets the closest valid CIDR prefix length.
     */
    public int getClosestValidCidr(String mask) {
        try {
            int prefixLength = Integer.parseInt(mask);
            return Math.max(0, Math.min(32, prefixLength));
        } catch (NumberFormatException e) {
            return 24; // Default fallback
        }
    }

    /**
     * Validates if the given string is a valid CIDR prefix length.
     */
    public boolean isValidCidr(String cidr) {
        try {
            int prefix = Integer.parseInt(cidr);
            return prefix >= 0 && prefix <= 32;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Converts CIDR prefix length to subnet mask.
     */
    public String cidrToSubnetMask(int prefixLength) {
        int mask = 0xffffffff << (32 - prefixLength);
        return String.format("%d.%d.%d.%d",
                (mask >> 24) & 0xff,
                (mask >> 16) & 0xff,
                (mask >> 8) & 0xff,
                mask & 0xff);
    }

    /**
     * Calculates the number of hosts per subnet.
     */
    public long calculateHostsPerSubnet(int prefixLength) {
        if (prefixLength >= 31) return 0;
        return (1L << (32 - prefixLength)) - 2;
    }

    /**
     * Calculates the network address from IP and CIDR.
     */
    public String calculateNetworkAddress(String ip, int prefixLength) {
        try {
            String[] segments = ip.split("\\.");
            int[] ipBytes = new int[4];
            for (int i = 0; i < 4; i++) {
                ipBytes[i] = Integer.parseInt(segments[i]);
            }

            int mask = 0xffffffff << (32 - prefixLength);
            int networkInt = (ipBytes[0] << 24) | (ipBytes[1] << 16) | (ipBytes[2] << 8) | ipBytes[3];
            networkInt &= mask;

            return String.format("%d.%d.%d.%d",
                    (networkInt >> 24) & 0xff,
                    (networkInt >> 16) & 0xff,
                    (networkInt >> 8) & 0xff,
                    networkInt & 0xff);
        } catch (Exception e) {
            return ip;
        }
    }

    /**
     * Generates subnets based on the given parameters.
     */
    public SubnetCalculation generateSubnets(String baseIp, int prefixLength, int numSubnetsRequested) {
        SubnetCalculation result = new SubnetCalculation();
        result.setBaseIp(baseIp);
        result.setRequestedSubnets(numSubnetsRequested);
        result.setPrefixLength(prefixLength);

        try {
            // Validate inputs
            if (!isValidIp(baseIp)) {
                String suggestedIp = getClosestValidIp(baseIp);
                result.setSuggestedIp(suggestedIp);
                result.setErrorMessage("Invalid IP address. Using suggested IP: " + suggestedIp);
                baseIp = suggestedIp;
            } else {
                result.setSuggestedIp(baseIp);
            }

            if (prefixLength < 0 || prefixLength > 32) {
                prefixLength = getClosestValidCidr(String.valueOf(prefixLength));
                result.setPrefixLength(prefixLength);
                result.setErrorMessage("Invalid CIDR. Using suggested CIDR: /" + prefixLength);
            }

            result.setSubnetMask(cidrToSubnetMask(prefixLength));

            // Calculate required bits for subnets
            int bitsNeeded = Integer.toBinaryString(numSubnetsRequested - 1).length();
            if (numSubnetsRequested <= 1) bitsNeeded = 1;

            int actualNumSubnets = 1 << bitsNeeded;
            int newPrefixLength = prefixLength + bitsNeeded;

            if (newPrefixLength > 32) {
                result.setValid(false);
                result.setErrorMessage("Requested number of subnets exceeds available address space.");
                return result;
            }

            // Generate subnets
            String networkAddress = calculateNetworkAddress(baseIp, prefixLength);
            List<String> subnets = new ArrayList<>();
            
            long subnetSize = 1L << (32 - newPrefixLength);
            String[] networkSegments = networkAddress.split("\\.");
            long networkLong = (Long.parseLong(networkSegments[0]) << 24) |
                              (Long.parseLong(networkSegments[1]) << 16) |
                              (Long.parseLong(networkSegments[2]) << 8) |
                              Long.parseLong(networkSegments[3]);

            for (int i = 0; i < actualNumSubnets && i < numSubnetsRequested * 2; i++) {
                long subnetNetwork = networkLong + (i * subnetSize);
                String subnetIp = String.format("%d.%d.%d.%d",
                        (subnetNetwork >> 24) & 0xff,
                        (subnetNetwork >> 16) & 0xff,
                        (subnetNetwork >> 8) & 0xff,
                        subnetNetwork & 0xff);
                subnets.add(subnetIp + "/" + newPrefixLength);
            }

            result.setGeneratedSubnets(subnets);
            result.setExplanation(String.format(
                    "Requested %d subnet(s); due to binary subdivision, generated %d non-overlapping subnets.",
                    numSubnetsRequested, actualNumSubnets));
            result.setValid(true);

        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage("Error generating subnets: " + e.getMessage());
        }

        return result;
    }

    /**
     * Calculates subnet information for display.
     */
    public String getSubnetInfo(String ip, int prefixLength) {
        try {
            String networkAddress = calculateNetworkAddress(ip, prefixLength);
            long hostsPerSubnet = calculateHostsPerSubnet(prefixLength);
            String subnetMask = cidrToSubnetMask(prefixLength);
            
            // Calculate broadcast address
            String[] networkSegments = networkAddress.split("\\.");
            long networkLong = (Long.parseLong(networkSegments[0]) << 24) |
                              (Long.parseLong(networkSegments[1]) << 16) |
                              (Long.parseLong(networkSegments[2]) << 8) |
                              Long.parseLong(networkSegments[3]);
            
            long broadcastLong = networkLong + (1L << (32 - prefixLength)) - 1;
            String broadcastAddress = String.format("%d.%d.%d.%d",
                    (broadcastLong >> 24) & 0xff,
                    (broadcastLong >> 16) & 0xff,
                    (broadcastLong >> 8) & 0xff,
                    broadcastLong & 0xff);

            return String.format(
                    "Network: %s/%d\nSubnet Mask: %s\nBroadcast: %s\nHosts: %d",
                    networkAddress, prefixLength, subnetMask, broadcastAddress, hostsPerSubnet);
        } catch (Exception e) {
            return "Error calculating subnet information";
        }
    }
} 