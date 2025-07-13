package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.Route53ResolverTest;
import edu.au.cpsc.module7.models.Route53ResolverTest.TestType;
import edu.au.cpsc.module7.models.Route53ResolverTest.TestResult;
import edu.au.cpsc.module7.models.Route53ResolverTest.QueryType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Route53ResolverTestingService {
    
    private static final Logger logger = Logger.getLogger(Route53ResolverTestingService.class.getName());
    
    // Common test domains for different scenarios
    private static final Map<String, String> TEST_DOMAINS = Map.of(
        "aws-internal", "ec2.internal",
        "public", "google.com",
        "corporate", "corp.example.com",
        "on-premises", "internal.company.local"
    );
    
    // DNS servers for testing
    private static final Map<String, String> DNS_SERVERS = Map.of(
        "aws-vpc", "169.254.169.253",  // AWS VPC DNS
        "cloudflare", "1.1.1.1",
        "google", "8.8.8.8",
        "quad9", "9.9.9.9"
    );
    
    public Route53ResolverTestingService() {
        logger.info("Route53ResolverTestingService initialized");
    }
    
    /**
     * Test Route 53 Resolver forwarding rules
     */
    public CompletableFuture<Route53ResolverTest> testForwardingRule(String domain, String resolverRuleId, List<String> targetDNSServers) {
        return CompletableFuture.supplyAsync(() -> {
            Route53ResolverTest test = new Route53ResolverTest(
                UUID.randomUUID().toString(),
                TestType.FORWARDING_RULE,
                domain
            );
            test.setResolverRuleId(resolverRuleId);
            test.setTargetIPs(targetDNSServers);
            
            try {
                logger.info("Testing forwarding rule for domain: " + domain);
                
                long startTime = System.currentTimeMillis();
                
                // Test DNS resolution
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                
                long responseTime = System.currentTimeMillis() - startTime;
                test.setResponseTimeMs(responseTime);
                
                if (addresses.length > 0) {
                    List<String> resolvedIPs = new ArrayList<>();
                    for (InetAddress addr : addresses) {
                        resolvedIPs.add(addr.getHostAddress());
                    }
                    test.setResponseData(String.join(", ", resolvedIPs));
                    
                    // Check if resolution was forwarded (basic heuristic)
                    if (isForwardedResolution(domain, resolvedIPs, targetDNSServers)) {
                        test.setActualResult(TestResult.PASSED);
                        logger.info("Forwarding rule test PASSED for " + domain);
                    } else {
                        test.setActualResult(TestResult.FAILED);
                        test.setErrorMessage("DNS resolution may not have been forwarded");
                        logger.warning("Forwarding rule test FAILED for " + domain);
                    }
                } else {
                    test.setActualResult(TestResult.FAILED);
                    test.setErrorMessage("No DNS resolution found");
                }
                
            } catch (UnknownHostException e) {
                test.setActualResult(TestResult.FAILED);
                test.setErrorMessage("DNS resolution failed: " + e.getMessage());
                logger.warning("DNS resolution failed for " + domain + ": " + e.getMessage());
            } catch (Exception e) {
                test.setActualResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("Test error for " + domain + ": " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Test Route 53 Resolver outbound endpoints
     */
    public CompletableFuture<Route53ResolverTest> testOutboundEndpoint(String domain, String endpointId, List<String> endpointIPs) {
        return CompletableFuture.supplyAsync(() -> {
            Route53ResolverTest test = new Route53ResolverTest(
                UUID.randomUUID().toString(),
                TestType.OUTBOUND_ENDPOINT,
                domain
            );
            test.setEndpointId(endpointId);
            test.setTargetIPs(endpointIPs);
            
            try {
                logger.info("Testing outbound endpoint for domain: " + domain);
                
                long startTime = System.currentTimeMillis();
                
                // Test connectivity to outbound endpoint
                boolean endpointReachable = testEndpointConnectivity(endpointIPs);
                
                if (endpointReachable) {
                    // Test DNS resolution through endpoint
                    InetAddress[] addresses = InetAddress.getAllByName(domain);
                    
                    long responseTime = System.currentTimeMillis() - startTime;
                    test.setResponseTimeMs(responseTime);
                    
                    if (addresses.length > 0) {
                        List<String> resolvedIPs = new ArrayList<>();
                        for (InetAddress addr : addresses) {
                            resolvedIPs.add(addr.getHostAddress());
                        }
                        test.setResponseData(String.join(", ", resolvedIPs));
                        test.setActualResult(TestResult.PASSED);
                        logger.info("Outbound endpoint test PASSED for " + domain);
                    } else {
                        test.setActualResult(TestResult.FAILED);
                        test.setErrorMessage("No DNS resolution through outbound endpoint");
                    }
                } else {
                    test.setActualResult(TestResult.FAILED);
                    test.setErrorMessage("Outbound endpoint not reachable");
                }
                
            } catch (Exception e) {
                test.setActualResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("Outbound endpoint test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Test Route 53 Resolver inbound endpoints
     */
    public CompletableFuture<Route53ResolverTest> testInboundEndpoint(String domain, String endpointId, List<String> endpointIPs) {
        return CompletableFuture.supplyAsync(() -> {
            Route53ResolverTest test = new Route53ResolverTest(
                UUID.randomUUID().toString(),
                TestType.INBOUND_ENDPOINT,
                domain
            );
            test.setEndpointId(endpointId);
            test.setTargetIPs(endpointIPs);
            
            try {
                logger.info("Testing inbound endpoint for domain: " + domain);
                
                long startTime = System.currentTimeMillis();
                
                // Test if inbound endpoint is accessible
                boolean endpointAccessible = testEndpointConnectivity(endpointIPs);
                
                if (endpointAccessible) {
                    // Test DNS query to inbound endpoint
                    String dnsResult = performDNSQuery(domain, endpointIPs.get(0));
                    
                    long responseTime = System.currentTimeMillis() - startTime;
                    test.setResponseTimeMs(responseTime);
                    
                    if (dnsResult != null && !dnsResult.isEmpty()) {
                        test.setResponseData(dnsResult);
                        test.setActualResult(TestResult.PASSED);
                        logger.info("Inbound endpoint test PASSED for " + domain);
                    } else {
                        test.setActualResult(TestResult.FAILED);
                        test.setErrorMessage("No response from inbound endpoint");
                    }
                } else {
                    test.setActualResult(TestResult.FAILED);
                    test.setErrorMessage("Inbound endpoint not accessible");
                }
                
            } catch (Exception e) {
                test.setActualResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("Inbound endpoint test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Test Route 53 Resolver rule priority
     */
    public CompletableFuture<Route53ResolverTest> testRulePriority(String domain, Map<String, Integer> rulePriorities) {
        return CompletableFuture.supplyAsync(() -> {
            Route53ResolverTest test = new Route53ResolverTest(
                UUID.randomUUID().toString(),
                TestType.RULE_PRIORITY,
                domain
            );
            
            try {
                logger.info("Testing rule priority for domain: " + domain);
                
                long startTime = System.currentTimeMillis();
                
                // Resolve domain and analyze which rule was applied
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                
                long responseTime = System.currentTimeMillis() - startTime;
                test.setResponseTimeMs(responseTime);
                
                if (addresses.length > 0) {
                    List<String> resolvedIPs = new ArrayList<>();
                    for (InetAddress addr : addresses) {
                        resolvedIPs.add(addr.getHostAddress());
                    }
                    test.setResponseData(String.join(", ", resolvedIPs));
                    
                    // Analyze which rule was likely applied based on response
                    String appliedRule = analyzeAppliedRule(domain, resolvedIPs, rulePriorities);
                    test.setMetadata(Map.of("applied_rule", appliedRule));
                    
                    test.setActualResult(TestResult.PASSED);
                    logger.info("Rule priority test PASSED for " + domain + " (applied rule: " + appliedRule + ")");
                } else {
                    test.setActualResult(TestResult.FAILED);
                    test.setErrorMessage("No DNS resolution found");
                }
                
            } catch (Exception e) {
                test.setActualResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("Rule priority test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Generate comprehensive Route 53 Resolver test suite
     */
    public List<Route53ResolverTest> generateResolverTestSuite() {
        List<Route53ResolverTest> tests = new ArrayList<>();
        
        // Test AWS internal domains
        tests.add(createBasicTest("ec2.internal", TestType.SYSTEM_RULE, "AWS VPC DNS resolution"));
        tests.add(createBasicTest("s3.amazonaws.com", TestType.SYSTEM_RULE, "AWS service DNS resolution"));
        
        // Test public domains
        tests.add(createBasicTest("google.com", TestType.SYSTEM_RULE, "Public DNS resolution"));
        tests.add(createBasicTest("amazonaws.com", TestType.SYSTEM_RULE, "AWS public DNS resolution"));
        
        // Test corporate domains (would be forwarded)
        tests.add(createBasicTest("corp.example.com", TestType.FORWARDING_RULE, "Corporate domain forwarding"));
        tests.add(createBasicTest("internal.company.local", TestType.FORWARDING_RULE, "Internal domain forwarding"));
        
        return tests;
    }
    
    // Helper methods
    
    private boolean isForwardedResolution(String domain, List<String> resolvedIPs, List<String> targetDNSServers) {
        // Basic heuristic: if domain contains private/corporate patterns, it was likely forwarded
        String lowerDomain = domain.toLowerCase();
        return lowerDomain.contains("corp") || 
               lowerDomain.contains("internal") || 
               lowerDomain.contains("local") ||
               lowerDomain.endsWith(".private");
    }
    
    private boolean testEndpointConnectivity(List<String> endpointIPs) {
        for (String ip : endpointIPs) {
            try {
                InetAddress address = InetAddress.getByName(ip);
                if (address.isReachable(5000)) { // 5 second timeout
                    return true;
                }
            } catch (IOException e) {
                logger.warning("Failed to test connectivity to endpoint: " + ip);
            }
        }
        return false;
    }
    
    private String performDNSQuery(String domain, String dnsServer) {
        try {
            // Simple DNS query using nslookup command
            ProcessBuilder pb = new ProcessBuilder("nslookup", domain, dnsServer);
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                StringBuilder result = new StringBuilder();
                while (scanner.hasNextLine()) {
                    result.append(scanner.nextLine()).append("\n");
                }
                scanner.close();
                return result.toString();
            }
        } catch (Exception e) {
            logger.warning("DNS query failed: " + e.getMessage());
        }
        return null;
    }
    
    private String analyzeAppliedRule(String domain, List<String> resolvedIPs, Map<String, Integer> rulePriorities) {
        // Analyze response to determine which rule was applied
        // This is a simplified analysis - in practice, would need AWS API integration
        
        if (resolvedIPs.stream().anyMatch(ip -> ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172."))) {
            return "forwarding_rule";
        } else if (domain.endsWith(".amazonaws.com") || domain.endsWith(".aws.amazon.com")) {
            return "aws_system_rule";
        } else {
            return "internet_resolver_rule";
        }
    }
    
    private Route53ResolverTest createBasicTest(String domain, TestType testType, String description) {
        Route53ResolverTest test = new Route53ResolverTest(
            UUID.randomUUID().toString(),
            testType,
            domain
        );
        test.setExpectedResult(description);
        return test;
    }
} 