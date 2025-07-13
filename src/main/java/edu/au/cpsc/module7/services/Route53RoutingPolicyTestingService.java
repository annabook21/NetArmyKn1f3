package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.Route53RoutingPolicyTest;
import edu.au.cpsc.module7.models.Route53RoutingPolicyTest.RoutingPolicyType;
import edu.au.cpsc.module7.models.Route53RoutingPolicyTest.TestResult;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Route53RoutingPolicyTestingService {
    
    private static final Logger logger = Logger.getLogger(Route53RoutingPolicyTestingService.class.getName());
    
    private final TorProxyService torProxyService;
    
    // AWS IP ranges for region detection (subset of commonly used ranges)
    private static final Map<String, List<String>> AWS_IP_RANGES = Map.of(
        "us-east-1", List.of("3.208.0.0/12", "3.224.0.0/12", "52.0.0.0/11", "54.144.0.0/14", "54.208.0.0/13", "54.224.0.0/15"),
        "us-west-1", List.of("13.52.0.0/14", "13.56.0.0/14", "50.18.0.0/16", "54.153.0.0/16", "54.183.0.0/16", "54.241.0.0/16"),
        "us-west-2", List.of("34.192.0.0/12", "35.160.0.0/13", "52.24.0.0/14", "52.32.0.0/15", "54.68.0.0/14", "54.244.0.0/16"),
        "eu-west-1", List.of("18.200.0.0/16", "34.240.0.0/12", "52.16.0.0/15", "52.48.0.0/14", "54.154.0.0/16", "54.170.0.0/15"),
        "eu-central-1", List.of("3.64.0.0/12", "18.184.0.0/15", "35.156.0.0/14", "52.28.0.0/16", "52.57.0.0/17", "18.192.0.0/12"),
        "ap-southeast-1", List.of("13.212.0.0/15", "13.228.0.0/15", "13.250.0.0/15", "52.74.0.0/16", "54.151.0.0/17", "54.169.0.0/16"),
        "ap-northeast-1", List.of("13.112.0.0/14", "13.230.0.0/15", "18.176.0.0/13", "52.68.0.0/15", "54.64.0.0/15", "54.92.0.0/17")
    );
    
    // Cloud provider IP range patterns for general detection
    private static final Map<String, List<String>> CLOUD_PROVIDER_RANGES = Map.of(
        "AWS", List.of("3.", "13.", "15.", "18.", "34.", "35.", "50.", "52.", "54."),
        "Google", List.of("8.8.", "8.34.", "34.64.", "34.65.", "34.66.", "34.67.", "35.184.", "35.185."),
        "Microsoft", List.of("13.64.", "13.65.", "13.66.", "13.67.", "20.36.", "20.37.", "40.64.", "52.224."),
        "Cloudflare", List.of("1.1.", "1.0.", "104.16.", "104.17.", "172.64.", "172.65.", "173.245.", "188.114.")
    );
    
    private static final Map<String, String> AWS_REGIONS = Map.of(
        "us-east-1", "US East (N. Virginia)",
        "us-west-2", "US West (Oregon)", 
        "eu-west-1", "Europe (Ireland)",
        "ap-southeast-1", "Asia Pacific (Singapore)",
        "ap-northeast-1", "Asia Pacific (Tokyo)"
    );
    
    // IP geolocation service endpoints
    private static final String[] GEOLOCATION_SERVICES = {
        "https://ipapi.co/json/",
        "https://ipinfo.io/json",
        "https://api.ipify.org?format=json"
    };
    
    @Inject
    public Route53RoutingPolicyTestingService(TorProxyService torProxyService) {
        this.torProxyService = torProxyService;
        logger.info("Route53RoutingPolicyTestingService initialized with Tor support: " + torProxyService.isTorAvailable());
    }
    
    /**
     * Test geolocation routing policy using Tor for geographic diversity
     */
    public CompletableFuture<Route53RoutingPolicyTest> testGeolocationRoutingWithTor(String domain, int numLocations) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.GEOLOCATION, domain);
            
            if (!torProxyService.isTorAvailable()) {
                test.setResult(TestResult.FAILED);
                test.setErrorMessage("Tor not available for geographic testing. " + torProxyService.getTorSetupInstructions());
                return test;
            }
            
            StringBuilder results = new StringBuilder();
            results.append("Testing geolocation routing from multiple locations via Tor:\n\n");
            
            Map<String, String> locationResults = new HashMap<>();
            long totalTime = 0;
            
            for (int i = 0; i < numLocations; i++) {
                try {
                    // Get current exit node location
                    String exitNodeIP = torProxyService.getCurrentExitNodeIP().get();
                    
                    long startTime = System.currentTimeMillis();
                    
                    // Perform DNS lookup through Tor
                    String resolvedIP = torProxyService.performDNSLookupThroughTor(domain).get();
                    
                    long responseTime = System.currentTimeMillis() - startTime;
                    totalTime += responseTime;
                    
                    locationResults.put(exitNodeIP, resolvedIP);
                    
                    results.append("Location ").append(i + 1).append(":\n");
                    results.append("  Exit Node IP: ").append(exitNodeIP).append("\n");
                    results.append("  Resolved IP: ").append(resolvedIP).append("\n");
                    results.append("  Response Time: ").append(responseTime).append("ms\n\n");
                    
                    // Request new circuit for next test
                    if (i < numLocations - 1) {
                        torProxyService.requestNewCircuit().get();
                        Thread.sleep(2000); // Wait for circuit to establish
                    }
                    
                } catch (Exception e) {
                    results.append("Location ").append(i + 1).append(" failed: ").append(e.getMessage()).append("\n\n");
                }
            }
            
            test.setResponseTimeMs(totalTime / numLocations);
            test.setErrorMessage(results.toString());
            
            // Analyze results
            Set<String> uniqueResolutions = new HashSet<>(locationResults.values());
            if (uniqueResolutions.size() > 1) {
                test.setResult(TestResult.PASSED);
                test.setActualEndpoint("Geographic routing detected - " + uniqueResolutions.size() + " different endpoints");
                logger.info("Geolocation routing test PASSED with Tor - multiple endpoints detected");
            } else if (uniqueResolutions.size() == 1) {
                test.setResult(TestResult.PARTIAL);
                test.setActualEndpoint("Single endpoint returned from all locations");
                logger.warning("Geolocation routing test PARTIAL - same endpoint from all locations");
            } else {
                test.setResult(TestResult.FAILED);
                test.setActualEndpoint("No successful resolutions");
            }
            
            return test;
        });
    }
    
    /**
     * Test geolocation routing policy using AWS best practices
     * Follows AWS troubleshooting guide for geolocation routing
     */
    public CompletableFuture<Route53RoutingPolicyTest> testGeolocationRouting(String domain, String expectedRegion) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.GEOLOCATION, domain);
            test.setExpectedEndpoint(expectedRegion);
            
            try {
                logger.info("Testing geolocation routing for domain: " + domain + " using AWS best practices");
                
                StringBuilder detailReport = new StringBuilder();
                detailReport.append("AWS Route 53 Geolocation Routing Test\n");
                detailReport.append("======================================\n\n");
                
                // Step 1: Check resource record configuration and default location
                detailReport.append("Step 1: Checking Resource Record Configuration\n");
                GeolocationConfig config = checkGeolocationResourceRecords(domain);
                detailReport.append("Has Default Location: ").append(config.hasDefaultLocation ? "YES" : "NO").append("\n");
                detailReport.append("Configuration Status: ").append(config.configStatus).append("\n\n");
                
                if (!config.hasDefaultLocation) {
                    detailReport.append("⚠️  WARNING: No default location specified in geolocation routing configuration\n");
                    detailReport.append("   This may cause NOERROR responses with no ANSWER section for non-matching geolocations\n\n");
                }
                
                // Step 2: Check DNS resolver IP address range using CloudFront
                detailReport.append("Step 2: Testing DNS Resolver IP Range (CloudFront)\n");
                List<String> resolverIPs = testDNSResolverIPRange();
                detailReport.append("DNS Resolver IPs detected: ").append(String.join(", ", resolverIPs)).append("\n");
                detailReport.append("Resolver Stability: ").append(resolverIPs.size() == 1 ? "STABLE" : "VARIES").append("\n\n");
                
                // Step 3: Test EDNS0-Client-Subnet support (reuse from latency testing)
                detailReport.append("Step 3: Testing EDNS0-Client-Subnet Support\n");
                boolean ednsSupport = testEdns0ClientSubnetSupport();
                detailReport.append("EDNS0-CLIENT-SUBNET Support: ").append(ednsSupport ? "SUPPORTED" : "NOT SUPPORTED").append("\n\n");
                
                if (!ednsSupport) {
                    detailReport.append("⚠️  WARNING: DNS resolver doesn't support edns0-client-subnet\n");
                    detailReport.append("   Route 53 will use resolver IP location instead of client location\n");
                    detailReport.append("   Recommendation: Use Google DNS (8.8.8.8) or OpenDNS (208.67.222.222)\n\n");
                }
                
                // Step 4: Get client IP and test with Route 53 authoritative servers
                detailReport.append("Step 4: Testing with Route 53 Authoritative Servers\n");
                String clientIP = getCurrentPublicIP();
                detailReport.append("Client IP: ").append(clientIP).append("\n");
                
                List<String> authoritativeNS = getAuthoritativeNameServers(domain);
                detailReport.append("Authoritative NS: ").append(String.join(", ", authoritativeNS)).append("\n");
                
                Map<String, GeolocationTestResult> geoResults = new HashMap<>();
                
                for (String ns : authoritativeNS) {
                    GeolocationTestResult result = testGeolocationWithSubnet(domain, clientIP, ns);
                    geoResults.put(ns, result);
                    
                    detailReport.append("\nTesting with NS: ").append(ns).append("\n");
                    detailReport.append("  Resolved IP: ").append(result.resolvedIP).append("\n");
                    detailReport.append("  Response Time: ").append(result.responseTime).append("ms\n");
                    detailReport.append("  Has Answer: ").append(result.hasAnswer ? "YES" : "NO (Authority only)").append("\n");
                    detailReport.append("  TTL: ").append(result.ttl).append(" seconds\n");
                }
                detailReport.append("\n");
                
                // Step 5: Check geographic location using MaxMind-style lookup
                detailReport.append("Step 5: Geographic Location Verification\n");
                String clientGeoLocation = getGeoLocationForIP(clientIP);
                detailReport.append("Client IP Geographic Location: ").append(clientGeoLocation).append("\n");
                
                // Compare with resolver location if no EDNS support
                if (!ednsSupport && !resolverIPs.isEmpty()) {
                    String resolverGeoLocation = getGeoLocationForIP(resolverIPs.get(0));
                    detailReport.append("DNS Resolver Geographic Location: ").append(resolverGeoLocation).append("\n");
                    
                    if (!clientGeoLocation.equals(resolverGeoLocation)) {
                        detailReport.append("⚠️  Geographic mismatch between client and resolver\n");
                        detailReport.append("   This may cause incorrect geolocation routing\n");
                    }
                }
                detailReport.append("\n");
                
                // Step 6: Test with different DNS resolvers for comparison
                detailReport.append("Step 6: Testing with Different DNS Resolvers\n");
                String[] testResolvers = {"8.8.8.8", "1.1.1.1", "208.67.222.222"};
                Map<String, String> resolverResults = new HashMap<>();
                
                for (String resolver : testResolvers) {
                    String result = testWithDNSResolver(domain, resolver);
                    resolverResults.put(resolver, result);
                    detailReport.append("DNS Resolver ").append(resolver).append(": ").append(result).append("\n");
                }
                detailReport.append("\n");
                
                // Step 7: Check DNS propagation status
                detailReport.append("Step 7: DNS Propagation Check\n");
                boolean propagationComplete = checkDNSPropagation(domain, authoritativeNS);
                detailReport.append("DNS Propagation Status: ").append(propagationComplete ? "COMPLETE" : "IN PROGRESS").append("\n\n");
                
                // Step 8: Check health checks and ETH if applicable
                detailReport.append("Step 8: Health Check Analysis\n");
                HealthCheckStatus healthStatus = analyzeHealthChecks(geoResults);
                detailReport.append("Health Check Status: ").append(healthStatus.status).append("\n");
                detailReport.append("ETH Analysis: ").append(healthStatus.ethAnalysis).append("\n\n");
                
                // Step 9: Analysis and recommendations
                detailReport.append("Step 9: Analysis and Recommendations\n");
                String analysis = analyzeGeolocationResults(config, ednsSupport, geoResults, resolverResults, clientGeoLocation);
                detailReport.append(analysis).append("\n");
                
                // Set test results
                test.setErrorMessage(detailReport.toString());
                test.setSourceLocation(clientGeoLocation);
                test.setResponseTimeMs(calculateAverageGeolocationResponseTime(geoResults));
                
                // Determine overall test result
                if (config.hasDefaultLocation && ednsSupport && !geoResults.isEmpty()) {
                    Set<String> uniqueResponses = new HashSet<>(resolverResults.values());
                    if (uniqueResponses.size() > 1) {
                        test.setResult(TestResult.PASSED);
                        test.setActualEndpoint("Proper geolocation routing detected");
                        logger.info("Geolocation routing test PASSED for " + domain);
                    } else {
                        test.setResult(TestResult.PARTIAL);
                        test.setActualEndpoint("Limited geolocation routing capability");
                        logger.warning("Geolocation routing test PARTIAL for " + domain);
                    }
                } else if (!geoResults.isEmpty()) {
                    test.setResult(TestResult.PARTIAL);
                    test.setActualEndpoint("Geolocation routing with configuration issues");
                    logger.warning("Geolocation routing test PARTIAL - configuration issues detected");
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setActualEndpoint("Geolocation routing not functioning");
                    logger.warning("Geolocation routing test FAILED for " + domain);
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("Geolocation test error: " + e.getMessage());
                logger.severe("Geolocation routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Check geolocation resource record configuration
     * Looks for default location and proper configuration
     */
    private GeolocationConfig checkGeolocationResourceRecords(String domain) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dig", domain);
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                boolean hasAnswer = false;
                boolean hasAuthority = false;
                String configStatus = "Unknown";
                
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    
                    if (line.contains("ANSWER SECTION")) {
                        hasAnswer = true;
                    } else if (line.contains("AUTHORITY SECTION")) {
                        hasAuthority = true;
                    } else if (line.contains("ANSWER: 0") && hasAuthority) {
                        configStatus = "No default location - returns NOERROR with Authority only";
                    } else if (hasAnswer) {
                        configStatus = "Has answer section - likely has default location";
                    }
                }
                scanner.close();
                
                // If we get answer section, assume default location exists
                return new GeolocationConfig(hasAnswer, configStatus);
            }
        } catch (Exception e) {
            logger.warning("Failed to check geolocation resource records: " + e.getMessage());
        }
        
        return new GeolocationConfig(false, "Unable to determine configuration");
    }
    
    /**
     * Test DNS resolver IP range using CloudFront resolver-identity
     * Equivalent to: for i in {1..10}; do dig +short resolver-identity.cloudfront.net; sleep 11; done;
     */
    private List<String> testDNSResolverIPRange() {
        List<String> resolverIPs = new ArrayList<>();
        
        try {
            for (int i = 0; i < 5; i++) { // Reduced iterations for efficiency
                ProcessBuilder pb = new ProcessBuilder("dig", "+short", "resolver-identity.cloudfront.net");
                Process process = pb.start();
                
                if (process.waitFor(10, TimeUnit.SECONDS)) {
                    Scanner scanner = new Scanner(process.getInputStream());
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            resolverIPs.add(line);
                        }
                    }
                    scanner.close();
                }
                
                if (i < 4) Thread.sleep(2000); // Reduced sleep time
            }
        } catch (Exception e) {
            logger.warning("Failed to test DNS resolver IP range: " + e.getMessage());
        }
        
        return resolverIPs.isEmpty() ? List.of("Unknown") : resolverIPs;
    }
    
    /**
     * Test geolocation routing with client subnet parameter
     * Equivalent to: dig geo.example.com +subnet=<Client IP>/24 @ns-xx.awsdns-xxx.com +short
     */
    private GeolocationTestResult testGeolocationWithSubnet(String domain, String clientIP, String nameServer) {
        try {
            String subnet = clientIP + "/24";
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "+subnet=" + subnet, "@" + nameServer);
            Process process = pb.start();
            
            long startTime = System.currentTimeMillis();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                long responseTime = System.currentTimeMillis() - startTime;
                
                Scanner scanner = new Scanner(process.getInputStream());
                String resolvedIP = "";
                boolean hasAnswer = false;
                int ttl = 0;
                
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    
                    if (line.contains("ANSWER SECTION")) {
                        hasAnswer = true;
                    } else if (hasAnswer && line.matches(".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) {
                        String[] parts = line.split("\\s+");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                                resolvedIP = parts[i];
                                // Try to extract TTL
                                if (i > 0 && parts[i - 1].matches("\\d+")) {
                                    ttl = Integer.parseInt(parts[i - 1]);
                                }
                                break;
                            }
                        }
                    }
                }
                scanner.close();
                
                return new GeolocationTestResult(resolvedIP, responseTime, hasAnswer, ttl);
            }
        } catch (Exception e) {
            logger.warning("Failed to test geolocation with subnet: " + e.getMessage());
        }
        
        return new GeolocationTestResult("", 0, false, 0);
    }
    
    /**
     * Get geographic location for IP address using simple IP geolocation
     * In production, would use MaxMind GeoIP database
     */
    private String getGeoLocationForIP(String ip) {
        try {
            // Use a simple IP geolocation service
            URL url = new URL("https://ipapi.co/" + ip + "/json/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                return extractLocationFromJson(response.toString());
            }
        } catch (Exception e) {
            logger.warning("Failed to get geolocation for IP " + ip + ": " + e.getMessage());
        }
        
        return "Unknown location";
    }
    
    /**
     * Check DNS propagation status across name servers
     */
    private boolean checkDNSPropagation(String domain, List<String> nameServers) {
        Set<String> responses = new HashSet<>();
        
        for (String ns : nameServers) {
            try {
                String result = testWithSpecificNameServer(domain, ns);
                if (!result.isEmpty()) {
                    responses.add(result);
                }
            } catch (Exception e) {
                logger.warning("Failed to check propagation with NS " + ns + ": " + e.getMessage());
            }
        }
        
        // If all name servers return the same result, propagation is complete
        return responses.size() <= 1;
    }
    
    /**
     * Test with specific name server
     */
    private String testWithSpecificNameServer(String domain, String nameServer) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "@" + nameServer, "+short");
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                        return line;
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            logger.warning("Failed to test with name server " + nameServer + ": " + e.getMessage());
        }
        return "";
    }
    
    /**
     * Analyze health checks and ETH status
     */
    private HealthCheckStatus analyzeHealthChecks(Map<String, GeolocationTestResult> geoResults) {
        // Simplified analysis - in production would integrate with AWS APIs
        boolean hasHealthyEndpoints = geoResults.values().stream()
            .anyMatch(result -> !result.resolvedIP.isEmpty());
        
        String status = hasHealthyEndpoints ? "Healthy endpoints detected" : "No healthy endpoints detected";
        String ethAnalysis = "ETH status would require AWS API integration to determine";
        
        return new HealthCheckStatus(status, ethAnalysis);
    }
    
    /**
     * Analyze geolocation test results and provide recommendations
     */
    private String analyzeGeolocationResults(GeolocationConfig config, boolean ednsSupport, 
                                           Map<String, GeolocationTestResult> geoResults,
                                           Map<String, String> resolverResults,
                                           String clientGeoLocation) {
        StringBuilder analysis = new StringBuilder();
        
        if (!config.hasDefaultLocation) {
            analysis.append("❌ No default location configured\n");
            analysis.append("   Recommendation: Add a default location to your geolocation routing configuration\n");
            analysis.append("   This prevents NOERROR responses with no ANSWER section\n\n");
        }
        
        if (!ednsSupport) {
            analysis.append("⚠️  DNS resolver doesn't support edns0-client-subnet\n");
            analysis.append("   Route 53 will use resolver location instead of client location\n");
            analysis.append("   Recommendation: Use Google DNS (8.8.8.8) or OpenDNS (208.67.222.222)\n\n");
        }
        
        // Check for geographic routing effectiveness
        Set<String> uniqueResponses = new HashSet<>(resolverResults.values());
        if (uniqueResponses.size() > 1) {
            analysis.append("✅ Different DNS resolvers returned different IP addresses\n");
            analysis.append("   This indicates geolocation routing is working\n\n");
        } else {
            analysis.append("⚠️  All DNS resolvers returned the same IP address\n");
            analysis.append("   This suggests geolocation routing may not be properly configured\n");
            analysis.append("   Or all test resolvers are in the same geographic region\n\n");
        }
        
        // Check TTL values for caching issues
        boolean hasOptimalTTL = geoResults.values().stream()
            .anyMatch(result -> result.ttl == 60);
        
        if (hasOptimalTTL) {
            analysis.append("✅ Fresh DNS responses detected (TTL = 60 seconds)\n");
        } else {
            analysis.append("⚠️  Responses may be cached (TTL ≠ 60 seconds)\n");
            analysis.append("   Recommendation: Wait and retry to get fresh responses\n");
        }
        
        return analysis.toString();
    }
    
    /**
     * Calculate average response time for geolocation tests
     */
    private long calculateAverageGeolocationResponseTime(Map<String, GeolocationTestResult> geoResults) {
        return Math.round(geoResults.values().stream()
            .mapToLong(result -> result.responseTime)
            .average()
            .orElse(0.0));
    }
    
    /**
     * Helper classes for geolocation testing
     */
    private static class GeolocationConfig {
        final boolean hasDefaultLocation;
        final String configStatus;
        
        GeolocationConfig(boolean hasDefaultLocation, String configStatus) {
            this.hasDefaultLocation = hasDefaultLocation;
            this.configStatus = configStatus;
        }
    }
    
    private static class GeolocationTestResult {
        final String resolvedIP;
        final long responseTime;
        final boolean hasAnswer;
        final int ttl;
        
        GeolocationTestResult(String resolvedIP, long responseTime, boolean hasAnswer, int ttl) {
            this.resolvedIP = resolvedIP;
            this.responseTime = responseTime;
            this.hasAnswer = hasAnswer;
            this.ttl = ttl;
        }
    }
    
    private static class HealthCheckStatus {
        final String status;
        final String ethAnalysis;
        
        HealthCheckStatus(String status, String ethAnalysis) {
            this.status = status;
            this.ethAnalysis = ethAnalysis;
        }
    }
    
    /**
     * Test weighted routing policy
     */
    public CompletableFuture<Route53RoutingPolicyTest> testWeightedRouting(String domain, Map<String, Integer> expectedWeights, int iterations) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.WEIGHTED, domain);
            test.setWeights(expectedWeights);
            test.setTestIterations(iterations);
            
            try {
                logger.info("Testing weighted routing for domain: " + domain + " with " + iterations + " iterations");
                
                Map<String, Integer> actualDistribution = new HashMap<>();
                long totalTime = 0;
                
                for (int i = 0; i < iterations; i++) {
                    long startTime = System.currentTimeMillis();
                    
                    InetAddress[] addresses = InetAddress.getAllByName(domain);
                    
                    totalTime += System.currentTimeMillis() - startTime;
                    
                    if (addresses.length > 0) {
                        String resolvedIP = addresses[0].getHostAddress();
                        String endpoint = detectEndpointFromIP(resolvedIP);
                        actualDistribution.put(endpoint, actualDistribution.getOrDefault(endpoint, 0) + 1);
                    }
                    
                    // Small delay between requests - reduce for high volume testing
                    if (iterations > 5000) {
                        Thread.sleep(5); // Faster for high volume tests
                    } else {
                    Thread.sleep(100);
                    }
                }
                
                test.setResponseTimeMs(totalTime / iterations);
                test.setActualDistribution(actualDistribution);
                
                // Validate weighted distribution
                if (validateWeightedDistribution(expectedWeights, actualDistribution, iterations)) {
                    test.setResult(TestResult.PASSED);
                    logger.info("Weighted routing test PASSED for " + domain);
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setErrorMessage("Actual distribution doesn't match expected weights");
                    logger.warning("Weighted routing test FAILED for " + domain);
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("Weighted routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * High-volume DNS testing method similar to the bash script approach
     * Performs 10,000+ queries and tracks detailed distribution statistics
     */
    public CompletableFuture<Route53RoutingPolicyTest> testHighVolumeWeightedRouting(String domain, 
                                                                                    Map<String, Integer> expectedWeights, 
                                                                                    int iterations,
                                                                                    String dnsServer) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.WEIGHTED, domain);
            test.setWeights(expectedWeights);
            test.setTestIterations(iterations);
            
            try {
                logger.info("Starting high-volume weighted routing test for domain: " + domain + " with " + iterations + " iterations");
                
                Map<String, Integer> actualDistribution = new HashMap<>();
                List<Long> responseTimes = new ArrayList<>();
                List<String> rawResults = new ArrayList<>();
                int successCount = 0;
                int failureCount = 0;
                
                // Use system's dig command for more accurate results (similar to bash script)
                for (int i = 0; i < iterations; i++) {
                    try {
                        long startTime = System.currentTimeMillis();
                        
                        String result = performDNSQueryWithDig(domain, dnsServer);
                        
                        long responseTime = System.currentTimeMillis() - startTime;
                        responseTimes.add(responseTime);
                        
                        if (result != null && !result.isEmpty()) {
                            String endpoint = extractEndpointFromDigResult(result);
                            actualDistribution.put(endpoint, actualDistribution.getOrDefault(endpoint, 0) + 1);
                            rawResults.add(String.format("Query %d: %s -> %s (%dms)", i + 1, domain, endpoint, responseTime));
                            successCount++;
                        } else {
                            rawResults.add(String.format("Query %d: FAILED", i + 1));
                            failureCount++;
                        }
                        
                        // Very minimal delay for high-volume testing
                        if (iterations > 10000) {
                            Thread.sleep(1);
                        } else {
                            Thread.sleep(5);
                        }
                        
                    } catch (Exception e) {
                        failureCount++;
                        rawResults.add(String.format("Query %d: ERROR - %s", i + 1, e.getMessage()));
                    }
                }
                
                // Calculate statistics
                long averageResponseTime = responseTimes.isEmpty() ? 0 : 
                    responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
                
                test.setResponseTimeMs(averageResponseTime);
                test.setActualDistribution(actualDistribution);
                
                // Create detailed result message
                StringBuilder detailMessage = new StringBuilder();
                detailMessage.append("High-Volume Weighted Routing Test Results\n");
                detailMessage.append("========================================\n");
                detailMessage.append("Domain: ").append(domain).append("\n");
                detailMessage.append("Total Queries: ").append(iterations).append("\n");
                detailMessage.append("Successful Queries: ").append(successCount).append("\n");
                detailMessage.append("Failed Queries: ").append(failureCount).append("\n");
                detailMessage.append("Success Rate: ").append(String.format("%.2f%%", (successCount * 100.0) / iterations)).append("\n");
                detailMessage.append("Average Response Time: ").append(averageResponseTime).append("ms\n\n");
                
                // Distribution analysis
                detailMessage.append("Endpoint Distribution:\n");
                int totalWeight = expectedWeights.values().stream().mapToInt(Integer::intValue).sum();
                for (Map.Entry<String, Integer> entry : actualDistribution.entrySet()) {
                    String endpoint = entry.getKey();
                    int count = entry.getValue();
                    double actualPercentage = (count * 100.0) / successCount;
                    
                    Integer expectedWeight = expectedWeights.get(endpoint);
                    if (expectedWeight != null) {
                        double expectedPercentage = (expectedWeight * 100.0) / totalWeight;
                        double deviation = Math.abs(actualPercentage - expectedPercentage);
                        detailMessage.append(String.format("  %s: %d queries (%.2f%%) - Expected: %.2f%% - Deviation: %.2f%%\n", 
                            endpoint, count, actualPercentage, expectedPercentage, deviation));
                    } else {
                        detailMessage.append(String.format("  %s: %d queries (%.2f%%) - Unexpected endpoint\n", 
                            endpoint, count, actualPercentage));
                    }
                }
                
                // Add sample raw results
                detailMessage.append("\nSample Raw Results (first 10 queries):\n");
                for (int i = 0; i < Math.min(10, rawResults.size()); i++) {
                    detailMessage.append(rawResults.get(i)).append("\n");
                }
                
                test.setErrorMessage(detailMessage.toString());
                
                // Validate weighted distribution with tighter tolerance for high-volume tests
                double tolerance = iterations > 10000 ? 0.05 : 0.10; // 5% tolerance for >10k queries, 10% otherwise
                if (validateWeightedDistributionWithTolerance(expectedWeights, actualDistribution, iterations, tolerance)) {
                    test.setResult(TestResult.PASSED);
                    logger.info("High-volume weighted routing test PASSED for " + domain);
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setErrorMessage(test.getErrorMessage() + "\nTest FAILED: Actual distribution doesn't match expected weights within tolerance");
                    logger.warning("High-volume weighted routing test FAILED for " + domain);
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("High-volume test error: " + e.getMessage());
                logger.severe("High-volume weighted routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Perform DNS query using dig command (similar to bash script)
     */
    private String performDNSQueryWithDig(String domain, String dnsServer) {
        try {
            ProcessBuilder pb;
            if (dnsServer != null && !dnsServer.isEmpty() && !dnsServer.equals("System Default")) {
                String serverIP = extractDNSServerIP(dnsServer);
                pb = new ProcessBuilder("dig", domain, "A", "@" + serverIP, "+short");
            } else {
                pb = new ProcessBuilder("dig", domain, "A", "+short");
            }
            
            Process process = pb.start();
            
            if (process.waitFor(5, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                StringBuilder result = new StringBuilder();
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        result.append(line).append("\n");
                    }
                }
                scanner.close();
                return result.toString().trim();
            }
        } catch (Exception e) {
            logger.warning("Dig command failed: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract DNS server IP from combo box selection
     */
    private String extractDNSServerIP(String dnsServerSelection) {
        if (dnsServerSelection.contains("8.8.8.8")) return "8.8.8.8";
        if (dnsServerSelection.contains("1.1.1.1")) return "1.1.1.1";
        if (dnsServerSelection.contains("9.9.9.9")) return "9.9.9.9";
        if (dnsServerSelection.contains("169.254.169.253")) return "169.254.169.253";
        return "8.8.8.8"; // Default
    }
    
    /**
     * Extract endpoint from dig command result
     */
    private String extractEndpointFromDigResult(String digResult) {
        // Parse dig output to extract IP addresses
        String[] lines = digResult.split("\n");
        for (String line : lines) {
            line = line.trim();
            // Check if line contains an IP address
            if (line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                return line;
            }
        }
        return digResult; // Return as-is if no IP found
    }
    
    /**
     * Validate weighted distribution with configurable tolerance
     */
    private boolean validateWeightedDistributionWithTolerance(Map<String, Integer> expectedWeights, 
                                                             Map<String, Integer> actualDistribution, 
                                                             int iterations, 
                                                             double tolerance) {
        int totalWeight = expectedWeights.values().stream().mapToInt(Integer::intValue).sum();
        int totalActualQueries = actualDistribution.values().stream().mapToInt(Integer::intValue).sum();
        
        for (Map.Entry<String, Integer> entry : expectedWeights.entrySet()) {
            String endpoint = entry.getKey();
            double expectedPercentage = (entry.getValue() * 100.0) / totalWeight;
            int actualCount = actualDistribution.getOrDefault(endpoint, 0);
            double actualPercentage = (actualCount * 100.0) / totalActualQueries;
            
            if (Math.abs(expectedPercentage - actualPercentage) > tolerance * 100) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test latency-based routing policy using AWS best practices
     * Follows AWS troubleshooting guide for latency-based routing
     */
    public CompletableFuture<Route53RoutingPolicyTest> testLatencyBasedRouting(String domain, Map<String, String> regionEndpoints) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.LATENCY, domain);
            
            try {
                logger.info("Testing latency-based routing for domain: " + domain + " using AWS best practices");
                
                StringBuilder detailReport = new StringBuilder();
                detailReport.append("AWS Route 53 Latency-Based Routing Test\n");
                detailReport.append("==========================================\n\n");
                
                // Step 1: Check DNS resolver Anycast support
                detailReport.append("Step 1: Testing DNS Resolver Anycast Support\n");
                boolean anycastSupport = testDNSResolverAnycastSupport();
                detailReport.append("Anycast Support: ").append(anycastSupport ? "SUPPORTED" : "NOT SUPPORTED").append("\n\n");
                
                // Step 2: Get client IP address
                detailReport.append("Step 2: Determining Client IP Address\n");
                String clientIP = getCurrentPublicIP();
                detailReport.append("Client IP: ").append(clientIP).append("\n\n");
                
                // Step 3: Test edns0-client-subnet support
                detailReport.append("Step 3: Testing edns0-client-subnet Support\n");
                boolean ednsSupport = testEdns0ClientSubnetSupport();
                detailReport.append("EDNS0-CLIENT-SUBNET Support: ").append(ednsSupport ? "SUPPORTED" : "NOT SUPPORTED").append("\n\n");
                
                // Step 4: Get Route 53 authoritative name servers
                detailReport.append("Step 4: Finding Route 53 Authoritative Name Servers\n");
                List<String> authoritativeNS = getAuthoritativeNameServers(domain);
                detailReport.append("Authoritative Name Servers: ").append(String.join(", ", authoritativeNS)).append("\n\n");
                
                // Step 5: Test latency-based routing with client subnet
                detailReport.append("Step 5: Testing Latency-Based Routing with Client Subnet\n");
                Map<String, LatencyTestResult> latencyResults = new HashMap<>();
                
                for (String ns : authoritativeNS) {
                    LatencyTestResult result = testLatencyRoutingWithSubnet(domain, clientIP, ns);
                    latencyResults.put(ns, result);
                    
                    detailReport.append("Testing with NS: ").append(ns).append("\n");
                    detailReport.append("  Resolved IP: ").append(result.resolvedIP).append("\n");
                    detailReport.append("  Response Time: ").append(result.responseTime).append("ms\n");
                    detailReport.append("  TTL: ").append(result.ttl).append(" seconds\n");
                    detailReport.append("  Fresh Response: ").append(result.ttl == 60 ? "YES" : "CACHED").append("\n\n");
                }
                
                // Step 6: Test with different DNS resolvers for comparison
                detailReport.append("Step 6: Testing with Different DNS Resolvers\n");
                String[] testResolvers = {"8.8.8.8", "1.1.1.1", "9.9.9.9"};
                Map<String, String> resolverResults = new HashMap<>();
                
                for (String resolver : testResolvers) {
                    String result = testWithDNSResolver(domain, resolver);
                    resolverResults.put(resolver, result);
                    detailReport.append("DNS Resolver ").append(resolver).append(": ").append(result).append("\n");
                }
                detailReport.append("\n");
                
                // Step 7: Analyze results and determine compliance
                detailReport.append("Step 7: Analysis and Recommendations\n");
                String analysis = analyzeLatencyTestResults(latencyResults, resolverResults, ednsSupport, anycastSupport);
                detailReport.append(analysis).append("\n");
                
                // Set test results
                test.setErrorMessage(detailReport.toString());
                test.setResponseTimeMs(calculateAverageResponseTime(latencyResults));
                
                // Determine overall test result
                if (ednsSupport && anycastSupport && !latencyResults.isEmpty()) {
                        test.setResult(TestResult.PASSED);
                    test.setActualLowestLatencyRegion("Proper latency-based routing detected");
                        logger.info("Latency-based routing test PASSED for " + domain);
                } else if (!latencyResults.isEmpty()) {
                        test.setResult(TestResult.PARTIAL);
                    test.setActualLowestLatencyRegion("Limited latency-based routing capability");
                        logger.warning("Latency-based routing test PARTIAL for " + domain);
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setActualLowestLatencyRegion("Latency-based routing not functioning");
                    logger.warning("Latency-based routing test FAILED for " + domain);
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("Latency test error: " + e.getMessage());
                logger.severe("Latency-based routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Test DNS resolver Anycast support
     * Runs dig command in loop to check for changing IP addresses
     */
    private boolean testDNSResolverAnycastSupport() {
        try {
            Set<String> observedIPs = new HashSet<>();
            
            // Run dig command multiple times as per AWS guide
            for (int i = 0; i < 5; i++) {
                ProcessBuilder pb = new ProcessBuilder("dig", "TXT", "o-o.myaddr.l.google.com", "+short");
                Process process = pb.start();
                
                if (process.waitFor(10, TimeUnit.SECONDS)) {
                    Scanner scanner = new Scanner(process.getInputStream());
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (!line.isEmpty() && line.matches(".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) {
                            observedIPs.add(line);
                        }
                    }
                    scanner.close();
                }
                
                Thread.sleep(1000); // Wait between queries
            }
            
            // If we see multiple different IPs, Anycast is supported
            return observedIPs.size() > 1;
            
        } catch (Exception e) {
            logger.warning("Failed to test Anycast support: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Test edns0-client-subnet support
     */
    private boolean testEdns0ClientSubnetSupport() {
        try {
            ProcessBuilder pb = new ProcessBuilder("dig", "+nocl", "TXT", "o-o.myaddr.l.google.com", "+short");
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                List<String> lines = new ArrayList<>();
                while (scanner.hasNextLine()) {
                    lines.add(scanner.nextLine().trim());
                }
                scanner.close();
                
                // If there are 2+ TXT records, edns0-client-subnet is supported
                return lines.size() >= 2;
            }
        } catch (Exception e) {
            logger.warning("Failed to test EDNS0-CLIENT-SUBNET support: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Get authoritative name servers for domain
     */
    private List<String> getAuthoritativeNameServers(String domain) {
        List<String> nameServers = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("dig", "NS", domain, "+short");
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty() && line.endsWith(".")) {
                        nameServers.add(line);
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            logger.warning("Failed to get authoritative name servers: " + e.getMessage());
            // Fallback to common AWS Route 53 name server pattern
            nameServers.add("ns-1.awsdns-01.com.");
            nameServers.add("ns-2.awsdns-02.net.");
        }
        
        return nameServers.isEmpty() ? List.of("ns-1.awsdns-01.com.") : nameServers;
    }
    
    /**
     * Test latency-based routing with client subnet parameter
     * Follows AWS guide: dig domain +subnet=<Client IP>/24 @ns-xx.awsdns-xxx.com +short
     */
    private LatencyTestResult testLatencyRoutingWithSubnet(String domain, String clientIP, String nameServer) {
        try {
            String subnet = clientIP + "/24";
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "+subnet=" + subnet, "@" + nameServer, "+short");
            Process process = pb.start();
            
            long startTime = System.currentTimeMillis();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                long responseTime = System.currentTimeMillis() - startTime;
                
                Scanner scanner = new Scanner(process.getInputStream());
                String resolvedIP = "";
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                        resolvedIP = line;
                        break;
                    }
                }
                scanner.close();
                
                // Get TTL by running detailed query
                int ttl = getTTLForDomain(domain, nameServer);
                
                return new LatencyTestResult(resolvedIP, responseTime, ttl);
            }
        } catch (Exception e) {
            logger.warning("Failed to test latency routing with subnet: " + e.getMessage());
        }
        
        return new LatencyTestResult("", 0, 0);
    }
    
    /**
     * Get TTL value for domain response
     */
    private int getTTLForDomain(String domain, String nameServer) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "@" + nameServer);
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains(domain) && line.contains("A\t")) {
                        String[] parts = line.split("\\s+");
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (parts[i].equals(domain) && parts[i + 1].matches("\\d+")) {
                                return Integer.parseInt(parts[i + 1]);
                            }
                        }
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            logger.warning("Failed to get TTL: " + e.getMessage());
        }
        return 300; // Default
    }
    
    /**
     * Test with different DNS resolver
     */
    private String testWithDNSResolver(String domain, String resolver) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "@" + resolver, "+short");
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                        return line;
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            logger.warning("Failed to test with DNS resolver " + resolver + ": " + e.getMessage());
        }
        return "No response";
    }
    
    /**
     * Analyze latency test results and provide recommendations
     */
    private String analyzeLatencyTestResults(Map<String, LatencyTestResult> latencyResults, 
                                           Map<String, String> resolverResults, 
                                           boolean ednsSupport, 
                                           boolean anycastSupport) {
        StringBuilder analysis = new StringBuilder();
        
        if (!ednsSupport) {
            analysis.append("⚠️  DNS resolver doesn't support edns0-client-subnet\n");
            analysis.append("   Recommendation: Switch to a DNS resolver that supports edns0-client-subnet\n");
            analysis.append("   (e.g., Google DNS 8.8.8.8 or OpenDNS 208.67.222.222)\n\n");
        }
        
        if (!anycastSupport) {
            analysis.append("⚠️  DNS resolver doesn't support Anycast\n");
            analysis.append("   This may result in unexpected latency-based routing behavior\n\n");
        }
        
        // Check for consistent responses
        Set<String> uniqueResponses = new HashSet<>(resolverResults.values());
        if (uniqueResponses.size() == 1) {
            analysis.append("⚠️  All DNS resolvers returned the same IP address\n");
            analysis.append("   This suggests latency-based routing may not be properly configured\n\n");
        } else {
            analysis.append("✅ Different DNS resolvers returned different IP addresses\n");
            analysis.append("   This indicates latency-based routing is working\n\n");
        }
        
        // Check TTL values
        boolean hasOptimalTTL = latencyResults.values().stream()
            .anyMatch(result -> result.ttl == 60);
        
        if (hasOptimalTTL) {
            analysis.append("✅ Fresh DNS responses detected (TTL = 60 seconds)\n");
        } else {
            analysis.append("⚠️  All responses appear to be cached (TTL ≠ 60 seconds)\n");
            analysis.append("   Recommendation: Wait and retry to get fresh responses\n");
        }
        
        return analysis.toString();
    }
    
    /**
     * Calculate average response time from latency results
     */
    private long calculateAverageResponseTime(Map<String, LatencyTestResult> latencyResults) {
        return Math.round(latencyResults.values().stream()
            .mapToLong(result -> result.responseTime)
            .average()
            .orElse(0.0));
    }
    
    /**
     * Helper class for latency test results
     */
    private static class LatencyTestResult {
        final String resolvedIP;
        final long responseTime;
        final int ttl;
        
        LatencyTestResult(String resolvedIP, long responseTime, int ttl) {
            this.resolvedIP = resolvedIP;
            this.responseTime = responseTime;
            this.ttl = ttl;
        }
    }
    
    /**
     * Test failover routing policy with user-specified A record health status
     * Performs simple DNS lookup and reports which A record is returned
     */
    public CompletableFuture<Route53RoutingPolicyTest> testFailoverRouting(String domain, String primaryEndpoint, String secondaryEndpoint) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.FAILOVER, domain);
            test.setPrimaryEndpoint(primaryEndpoint);
            test.setSecondaryEndpoint(secondaryEndpoint);
            
            try {
                logger.info("Testing failover routing for domain: " + domain);
                
                StringBuilder detailReport = new StringBuilder();
                detailReport.append("AWS Route 53 Failover Routing Test\n");
                detailReport.append("==================================\n\n");
                
                // Step 1: Record user-specified health status
                detailReport.append("Step 1: User-Specified A Record Configuration\n");
                if (primaryEndpoint != null && !primaryEndpoint.isEmpty()) {
                    detailReport.append("Primary A Record: ").append(primaryEndpoint).append(" (specified as primary / used when healthy)\n");
                }
                if (secondaryEndpoint != null && !secondaryEndpoint.isEmpty()) {
                    detailReport.append("Secondary A Record: ").append(secondaryEndpoint).append(" (specified as secondary / used when unhealthy)\n");
                }
                detailReport.append("\n");
                
                // Step 2: Perform DNS lookup
                detailReport.append("Step 2: DNS Lookup Results\n");
                long startTime = System.currentTimeMillis();
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                long responseTime = System.currentTimeMillis() - startTime;
                
                test.setResponseTimeMs(responseTime);
                detailReport.append("DNS Resolution Time: ").append(responseTime).append("ms\n");
                detailReport.append("Number of A Records Returned: ").append(addresses.length).append("\n\n");
                
                // Step 3: Log all returned A records
                detailReport.append("Step 3: Returned A Records\n");
                List<String> returnedIPs = new ArrayList<>();
                
                for (int i = 0; i < addresses.length; i++) {
                    String ip = addresses[i].getHostAddress();
                    returnedIPs.add(ip);
                    detailReport.append("A Record ").append(i + 1).append(": ").append(ip).append("\n");
                }
                detailReport.append("\n");
                
                // Step 4: Analyze failover behavior
                detailReport.append("Step 4: Failover Analysis\n");
                String primaryIP = null;
                String secondaryIP = null;
                
                // Try to resolve user-specified endpoints to IPs for comparison
                if (primaryEndpoint != null && !primaryEndpoint.isEmpty()) {
                    try {
                        InetAddress primaryAddr = InetAddress.getByName(primaryEndpoint);
                        primaryIP = primaryAddr.getHostAddress();
                    } catch (Exception e) {
                        // If it's already an IP, use it directly
                        if (primaryEndpoint.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            primaryIP = primaryEndpoint;
                        }
                    }
                }
                
                if (secondaryEndpoint != null && !secondaryEndpoint.isEmpty()) {
                    try {
                        InetAddress secondaryAddr = InetAddress.getByName(secondaryEndpoint);
                        secondaryIP = secondaryAddr.getHostAddress();
                    } catch (Exception e) {
                        // If it's already an IP, use it directly
                        if (secondaryEndpoint.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            secondaryIP = secondaryEndpoint;
                        }
                    }
                }
                
                // Determine which record was returned
                boolean primaryReturned = primaryIP != null && returnedIPs.contains(primaryIP);
                boolean secondaryReturned = secondaryIP != null && returnedIPs.contains(secondaryIP);
                
                if (primaryIP != null && secondaryIP != null) {
                    // User specified both records
                    detailReport.append("Primary A Record IP: ").append(primaryIP).append("\n");
                    detailReport.append("Secondary A Record IP: ").append(secondaryIP).append("\n");
                    detailReport.append("Primary Record Returned: ").append(primaryReturned ? "YES" : "NO").append("\n");
                    detailReport.append("Secondary Record Returned: ").append(secondaryReturned ? "YES" : "NO").append("\n\n");
                    
                    if (primaryReturned && !secondaryReturned) {
                        detailReport.append("RESULT: Primary A record returned (expected when primary is healthy)\n");
                        test.setResult(TestResult.PASSED);
                        test.setFailoverTriggered(false);
                        test.setActualEndpoint(primaryIP);
                    } else if (!primaryReturned && secondaryReturned) {
                        detailReport.append("RESULT: Secondary A record returned (failover triggered - primary unhealthy)\n");
                        test.setResult(TestResult.PASSED);
                        test.setFailoverTriggered(true);
                        test.setActualEndpoint(secondaryIP);
                    } else if (primaryReturned && secondaryReturned) {
                        detailReport.append("RESULT: Both A records returned (possible multivalue or round-robin)\n");
                        test.setResult(TestResult.PARTIAL);
                        test.setFailoverTriggered(false);
                        test.setActualEndpoint(returnedIPs.get(0)); // Use first returned IP
                    } else {
                        detailReport.append("RESULT: Neither specified A record returned\n");
                        test.setResult(TestResult.FAILED);
                        test.setFailoverTriggered(false);
                        test.setActualEndpoint(returnedIPs.isEmpty() ? "none" : returnedIPs.get(0));
                    }
                } else {
                    // User didn't specify which records are which, just report what was returned
                    detailReport.append("User did not specify primary/secondary A record configuration\n");
                    detailReport.append("RESULT: Returned A records without failover classification:\n");
                    for (int i = 0; i < returnedIPs.size(); i++) {
                        detailReport.append("  - ").append(returnedIPs.get(i)).append("\n");
                    }
                    test.setResult(TestResult.PARTIAL);
                    test.setFailoverTriggered(false);
                    test.setActualEndpoint(returnedIPs.isEmpty() ? "none" : String.join(", ", returnedIPs));
                }
                
                // Step 5: Additional DNS information
                detailReport.append("\nStep 5: Additional DNS Information\n");
                detailReport.append("Domain: ").append(domain).append("\n");
                detailReport.append("Query Type: A\n");
                detailReport.append("Response Time: ").append(responseTime).append("ms\n");
                detailReport.append("Total A Records: ").append(addresses.length).append("\n");
                
                // Set test results
                test.setErrorMessage(detailReport.toString());
                
                if (addresses.length > 0) {
                    logger.info("Failover routing test completed for " + domain + " - returned " + addresses.length + " A records");
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setErrorMessage("No A records returned for domain: " + domain);
                    logger.warning("Failover routing test FAILED for " + domain + " - no A records returned");
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("Failover test error: " + e.getMessage());
                logger.severe("Failover routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Test IP-based routing policy
     */
    public CompletableFuture<Route53RoutingPolicyTest> testIPBasedRouting(String domain, Map<String, String> ipRangeEndpoints) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.IP_BASED, domain);
            
            try {
                logger.info("Testing IP-based routing for domain: " + domain);
                
                // Get current public IP
                String currentIP = getCurrentPublicIP();
                test.setSourceLocation(currentIP);
                
                long startTime = System.currentTimeMillis();
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                long responseTime = System.currentTimeMillis() - startTime;
                
                test.setResponseTimeMs(responseTime);
                
                if (addresses.length > 0) {
                    String resolvedIP = addresses[0].getHostAddress();
                    test.setActualEndpoint(resolvedIP);
                    
                    // Validate IP-based routing
                    String expectedEndpoint = findExpectedEndpointForIP(currentIP, ipRangeEndpoints);
                    test.setExpectedEndpoint(expectedEndpoint);
                    
                    if (resolvedIP.equals(expectedEndpoint)) {
                        test.setResult(TestResult.PASSED);
                        logger.info("IP-based routing test PASSED for " + domain);
                    } else {
                        test.setResult(TestResult.FAILED);
                        test.setErrorMessage("Unexpected endpoint returned for IP: " + currentIP);
                        logger.warning("IP-based routing test FAILED for " + domain);
                    }
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setErrorMessage("No DNS resolution found");
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("IP-based routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Test multivalue answer routing policy
     */
    public CompletableFuture<Route53RoutingPolicyTest> testMultiValueAnswerRouting(String domain, List<String> expectedEndpoints) {
        return CompletableFuture.supplyAsync(() -> {
            Route53RoutingPolicyTest test = new Route53RoutingPolicyTest(RoutingPolicyType.MULTIVALUE_ANSWER, domain);
            test.setExpectedEndpoint(String.join(", ", expectedEndpoints));
            
            try {
                logger.info("Testing multivalue answer routing for domain: " + domain);
                
                long startTime = System.currentTimeMillis();
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                long responseTime = System.currentTimeMillis() - startTime;
                
                test.setResponseTimeMs(responseTime);
                
                if (addresses.length > 0) {
                    List<String> resolvedIPs = new ArrayList<>();
                    for (InetAddress addr : addresses) {
                        resolvedIPs.add(addr.getHostAddress());
                    }
                    
                    test.setActualEndpoint(String.join(", ", resolvedIPs));
                    
                    // Validate multivalue response
                    if (addresses.length >= 2) {
                        test.setResult(TestResult.PASSED);
                        logger.info("Multivalue answer routing test PASSED - multiple IPs returned");
                    } else {
                        test.setResult(TestResult.PARTIAL);
                        test.setErrorMessage("Only single IP returned, expected multiple values");
                        logger.warning("Multivalue answer routing test PARTIAL for " + domain);
                    }
                } else {
                    test.setResult(TestResult.FAILED);
                    test.setErrorMessage("No DNS resolution found");
                }
                
            } catch (Exception e) {
                test.setResult(TestResult.UNKNOWN);
                test.setErrorMessage("Test error: " + e.getMessage());
                logger.severe("Multivalue answer routing test error: " + e.getMessage());
            }
            
            return test;
        });
    }
    
    /**
     * Generate comprehensive routing policy test suite
     */
    public List<Route53RoutingPolicyTest> generateRoutingPolicyTestSuite(String baseDomain) {
        List<Route53RoutingPolicyTest> tests = new ArrayList<>();
        
        // Geolocation routing tests
        tests.add(new Route53RoutingPolicyTest(RoutingPolicyType.GEOLOCATION, "geo." + baseDomain));
        
        // Weighted routing tests
        Route53RoutingPolicyTest weightedTest = new Route53RoutingPolicyTest(RoutingPolicyType.WEIGHTED, "weighted." + baseDomain);
        weightedTest.setWeights(Map.of("endpoint1", 70, "endpoint2", 30));
        weightedTest.setTestIterations(100);
        tests.add(weightedTest);
        
        // Latency-based routing tests
        tests.add(new Route53RoutingPolicyTest(RoutingPolicyType.LATENCY, "latency." + baseDomain));
        
        // IP-based routing tests
        tests.add(new Route53RoutingPolicyTest(RoutingPolicyType.IP_BASED, "ipbased." + baseDomain));
        
        // Failover routing tests
        Route53RoutingPolicyTest failoverTest = new Route53RoutingPolicyTest(RoutingPolicyType.FAILOVER, "failover." + baseDomain);
        failoverTest.setPrimaryEndpoint("primary." + baseDomain);
        failoverTest.setSecondaryEndpoint("secondary." + baseDomain);
        tests.add(failoverTest);
        
        // Multivalue answer tests
        tests.add(new Route53RoutingPolicyTest(RoutingPolicyType.MULTIVALUE_ANSWER, "multi." + baseDomain));
        
        return tests;
    }
    
    // Helper methods
    
    private String getCurrentLocation() {
        try {
            for (String service : GEOLOCATION_SERVICES) {
                try {
                    URL url = new URL(service);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        // Parse location from response (simplified)
                        String locationData = response.toString();
                        if (locationData.contains("country")) {
                            return extractLocationFromJson(locationData);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to get location from " + service + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to determine current location: " + e.getMessage());
        }
        return "Unknown";
    }
    
    private String extractLocationFromJson(String json) {
        // Simple JSON parsing for location
        Pattern countryPattern = Pattern.compile("\"country\":\\s*\"([^\"]+)\"");
        Pattern regionPattern = Pattern.compile("\"region\":\\s*\"([^\"]+)\"");
        
        Matcher countryMatcher = countryPattern.matcher(json);
        Matcher regionMatcher = regionPattern.matcher(json);
        
        String country = countryMatcher.find() ? countryMatcher.group(1) : "Unknown";
        String region = regionMatcher.find() ? regionMatcher.group(1) : "";
        
        return region.isEmpty() ? country : country + "/" + region;
    }
    
    /**
     * Detect AWS region from IP address using CIDR matching
     * Enhanced with proper AWS IP range detection
     */
    private String detectRegionFromIP(String ip) {
        try {
            // First check against known AWS IP ranges
            for (Map.Entry<String, List<String>> entry : AWS_IP_RANGES.entrySet()) {
                String region = entry.getKey();
                List<String> cidrs = entry.getValue();
                
                for (String cidr : cidrs) {
                    if (isIPInCIDR(ip, cidr)) {
                        logger.info("IP " + ip + " detected in AWS region: " + region + " (CIDR: " + cidr + ")");
                        return region;
                    }
                }
            }
            
            // Fallback: Check cloud provider and make educated guess
            String provider = detectCloudProvider(ip);
            if ("AWS".equals(provider)) {
                // Use geographic heuristics for unknown AWS IPs
                String geoRegion = detectRegionByGeography(ip);
                if (geoRegion != null) {
                    logger.info("IP " + ip + " detected as AWS but unknown range, using geographic heuristic: " + geoRegion);
                    return geoRegion;
                }
                return "aws-unknown-region";
            } else if (provider != null) {
                logger.info("IP " + ip + " detected as " + provider + " (non-AWS)");
                return provider.toLowerCase() + "-region";
            }
            
            // Last resort: Geographic detection for any IP
            String geoLocation = getGeoLocationForIP(ip);
            if (geoLocation.contains("US")) {
                return "us-east-1"; // Default US region
            } else if (geoLocation.contains("EU") || geoLocation.contains("Europe")) {
                return "eu-west-1"; // Default EU region
            } else if (geoLocation.contains("Asia") || geoLocation.contains("AP")) {
                return "ap-southeast-1"; // Default Asia region
            }
            
            logger.warning("Could not determine region for IP: " + ip + ", using unknown-region");
        return "unknown-region";
            
        } catch (Exception e) {
            logger.warning("Error detecting region for IP " + ip + ": " + e.getMessage());
            return "error-region";
        }
    }
    
    /**
     * Enhanced endpoint detection with better logic
     */
    private String detectEndpointFromIP(String ip) {
        try {
            // Check if it's a well-known service IP
            String knownService = detectKnownService(ip);
            if (knownService != null) {
                return knownService;
            }
            
            // Check cloud provider and create meaningful endpoint name
            String provider = detectCloudProvider(ip);
            String region = detectRegionFromIP(ip);
            
            if (provider != null && region != null) {
                // Create endpoint name: provider-region-subnet
                String subnet = getSubnetIdentifier(ip);
                return provider.toLowerCase() + "-" + region + "-" + subnet;
            }
            
            // Fallback: Use geographic location + IP pattern
            String geoLocation = getGeoLocationForIP(ip);
            String subnet = getSubnetIdentifier(ip);
            
            if (geoLocation.contains("US")) {
                return "us-endpoint-" + subnet;
            } else if (geoLocation.contains("EU")) {
                return "eu-endpoint-" + subnet;
            } else if (geoLocation.contains("Asia")) {
                return "asia-endpoint-" + subnet;
            }
            
            // Final fallback: IP-based identifier
            return "endpoint-" + ip.replace(".", "-");
            
        } catch (Exception e) {
            logger.warning("Error detecting endpoint for IP " + ip + ": " + e.getMessage());
            return "unknown-endpoint-" + ip.hashCode();
        }
    }
    
    /**
     * Enhanced IP range matching with proper CIDR support
     */
    private String findExpectedEndpointForIP(String currentIP, Map<String, String> ipRangeEndpoints) {
        try {
            // First pass: Exact IP match
            if (ipRangeEndpoints.containsKey(currentIP)) {
                logger.info("Exact IP match found for " + currentIP);
                return ipRangeEndpoints.get(currentIP);
            }
            
            // Second pass: CIDR range matching
            for (Map.Entry<String, String> entry : ipRangeEndpoints.entrySet()) {
                String ipRange = entry.getKey();
                String endpoint = entry.getValue();
                
                if (ipRange.contains("/")) {
                    // CIDR notation
                    if (isIPInCIDR(currentIP, ipRange)) {
                        logger.info("IP " + currentIP + " matches CIDR range " + ipRange + " -> " + endpoint);
                        return endpoint;
                    }
                } else if (ipRange.contains("-")) {
                    // Range notation (e.g., 192.168.1.1-192.168.1.100)
                    if (isIPInRange(currentIP, ipRange)) {
                        logger.info("IP " + currentIP + " matches IP range " + ipRange + " -> " + endpoint);
                        return endpoint;
                    }
                } else if (ipRange.contains("*")) {
                    // Wildcard notation (e.g., 192.168.1.*)
                    if (matchesWildcard(currentIP, ipRange)) {
                        logger.info("IP " + currentIP + " matches wildcard " + ipRange + " -> " + endpoint);
                        return endpoint;
                    }
                }
            }
            
            // Third pass: Subnet matching (first 3 octets)
            for (Map.Entry<String, String> entry : ipRangeEndpoints.entrySet()) {
                String ipRange = entry.getKey();
                String endpoint = entry.getValue();
                
                if (!ipRange.contains("/") && !ipRange.contains("-") && !ipRange.contains("*")) {
                    // Assume it's a single IP, try subnet matching
                    if (isSameSubnet(currentIP, ipRange, 24)) { // /24 subnet
                        logger.info("IP " + currentIP + " in same subnet as " + ipRange + " -> " + endpoint);
                        return endpoint;
                    }
                }
            }
            
            logger.info("No matching endpoint found for IP " + currentIP + ", using default");
            return "default-endpoint";
            
        } catch (Exception e) {
            logger.warning("Error finding endpoint for IP " + currentIP + ": " + e.getMessage());
            return "error-endpoint";
        }
    }
    
    // CIDR and IP utility methods
    
    /**
     * Check if an IP address is within a CIDR range
     */
    private boolean isIPInCIDR(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            long ipLong = ipToLong(ip);
            long cidrIP = ipToLong(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            
            return (ipLong & mask) == (cidrIP & mask);
        } catch (Exception e) {
            logger.warning("Error checking CIDR match for " + ip + " in " + cidr + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if an IP is in a range (e.g., 192.168.1.1-192.168.1.100)
     */
    private boolean isIPInRange(String ip, String range) {
        try {
            String[] parts = range.split("-");
            if (parts.length != 2) {
                return false;
            }
            
            long ipLong = ipToLong(ip);
            long startIP = ipToLong(parts[0].trim());
            long endIP = ipToLong(parts[1].trim());
            
            return ipLong >= startIP && ipLong <= endIP;
        } catch (Exception e) {
            logger.warning("Error checking IP range for " + ip + " in " + range + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if an IP matches a wildcard pattern (e.g., 192.168.1.*)
     */
    private boolean matchesWildcard(String ip, String pattern) {
        try {
            String regex = pattern.replace(".", "\\.").replace("*", "\\d+");
            return ip.matches(regex);
        } catch (Exception e) {
            logger.warning("Error checking wildcard match for " + ip + " against " + pattern + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if two IPs are in the same subnet
     */
    private boolean isSameSubnet(String ip1, String ip2, int prefixLength) {
        try {
            long ip1Long = ipToLong(ip1);
            long ip2Long = ipToLong(ip2);
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            
            return (ip1Long & mask) == (ip2Long & mask);
        } catch (Exception e) {
            logger.warning("Error checking subnet match for " + ip1 + " and " + ip2 + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Convert IP address string to long
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ip);
        }
        
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result += Long.parseLong(parts[i]) << (8 * (3 - i));
        }
        return result & 0xFFFFFFFFL;
    }
    
    /**
     * Detect cloud provider from IP address
     */
    private String detectCloudProvider(String ip) {
        for (Map.Entry<String, List<String>> entry : CLOUD_PROVIDER_RANGES.entrySet()) {
            String provider = entry.getKey();
            List<String> prefixes = entry.getValue();
            
            for (String prefix : prefixes) {
                if (ip.startsWith(prefix)) {
                    return provider;
                }
            }
        }
        return null;
    }
    
    /**
     * Detect region by geographic heuristics
     */
    private String detectRegionByGeography(String ip) {
        // First two octets can give geographic hints for some ranges
        String[] parts = ip.split("\\.");
        if (parts.length >= 2) {
            int firstOctet = Integer.parseInt(parts[0]);
            int secondOctet = Integer.parseInt(parts[1]);
            
            // Some heuristics based on common AWS patterns
            if (firstOctet == 3 && secondOctet >= 208) return "us-east-1";
            if (firstOctet == 34 && secondOctet >= 192) return "us-west-2";
            if (firstOctet == 18 && secondOctet >= 200) return "eu-west-1";
            if (firstOctet == 13 && secondOctet >= 112) return "ap-northeast-1";
        }
        return null;
    }
    
    /**
     * Detect known services by IP
     */
    private String detectKnownService(String ip) {
        // Common known service IPs
        if (ip.equals("8.8.8.8") || ip.equals("8.8.4.4")) {
            return "google-dns";
        }
        if (ip.equals("1.1.1.1") || ip.equals("1.0.0.1")) {
            return "cloudflare-dns";
        }
        if (ip.equals("9.9.9.9") || ip.equals("149.112.112.112")) {
            return "quad9-dns";
        }
        if (ip.equals("169.254.169.253")) {
            return "aws-vpc-dns";
        }
        return null;
    }
    
    /**
     * Get subnet identifier for endpoint naming
     */
    private String getSubnetIdentifier(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "-" + parts[1] + "-" + parts[2];
        }
        return "unknown-subnet";
    }
    
    private boolean validateGeolocationRouting(String currentLocation, String detectedRegion, String expectedRegion) {
        // Simplified validation - in practice would use comprehensive geolocation mapping
        return detectedRegion.equals(expectedRegion) || 
               (currentLocation.contains("US") && detectedRegion.contains("us-")) ||
               (currentLocation.contains("EU") && detectedRegion.contains("eu-"));
    }
    
    private boolean validateWeightedDistribution(Map<String, Integer> expectedWeights, Map<String, Integer> actualDistribution, int iterations) {
        // Calculate expected vs actual percentages with tolerance
        double tolerance = 0.15; // 15% tolerance
        
        for (Map.Entry<String, Integer> entry : expectedWeights.entrySet()) {
            String endpoint = entry.getKey();
            double expectedPercentage = entry.getValue() / 100.0;
            int actualCount = actualDistribution.getOrDefault(endpoint, 0);
            double actualPercentage = (double) actualCount / iterations;
            
            if (Math.abs(expectedPercentage - actualPercentage) > tolerance) {
                return false;
            }
        }
        return true;
    }
    
    private long measureLatency(String endpoint) {
        try {
            // Use ping command for more accurate latency measurement
            ProcessBuilder pb = new ProcessBuilder("ping", "-c", "3", endpoint);
            Process process = pb.start();
            
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                Scanner scanner = new Scanner(process.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    // Look for average latency in ping output
                    if (line.contains("avg") || line.contains("average")) {
                        // Parse ping statistics line
                        String[] parts = line.split("[=/]");
                        for (String part : parts) {
                            try {
                                if (part.trim().matches("\\d+\\.\\d+")) {
                                    return Math.round(Double.parseDouble(part.trim()));
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                scanner.close();
            }
            
            // Fallback to basic method if ping fails
            long startTime = System.currentTimeMillis();
            InetAddress address = InetAddress.getByName(endpoint);
            address.isReachable(5000);
            return System.currentTimeMillis() - startTime;
            
        } catch (Exception e) {
            logger.warning("Failed to measure latency to " + endpoint + ": " + e.getMessage());
            return Long.MAX_VALUE;
        }
    }
    
    private boolean checkEndpointHealth(String endpoint) {
        try {
            InetAddress address = InetAddress.getByName(endpoint);
            return address.isReachable(5000);
        } catch (Exception e) {
            logger.warning("Health check failed for " + endpoint + ": " + e.getMessage());
            return false;
        }
    }
    
    private String getCurrentPublicIP() {
        try {
            // Use multiple services to get public IP
            String[] ipServices = {
                "https://api.ipify.org",
                "https://ifconfig.me/ip",
                "https://icanhazip.com"
            };
            
            for (String service : ipServices) {
                try {
                    URL url = new URL(service);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String ip = reader.readLine().trim();
                        reader.close();
                        
                        if (ip != null && !ip.isEmpty()) {
                            return ip;
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to get IP from " + service + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to determine public IP: " + e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * Discover all A records for a domain and related failover subdomains
     * This helps users identify available records for failover configuration including backup subdomains
     */
    public CompletableFuture<List<DiscoveredARecord>> discoverARecordsForDomain(String domain) {
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredARecord> allRecords = new ArrayList<>();
            
            try {
                logger.info("Discovering A records for domain: " + domain + " and related failover subdomains");
                
                // Generate list of domains to check (primary + common failover patterns)
                List<String> domainsToCheck = generateFailoverDomainList(domain);
                
                for (String domainToCheck : domainsToCheck) {
                    logger.info("Checking domain: " + domainToCheck);
                    List<DiscoveredARecord> records = discoverARecordsForSingleDomain(domainToCheck);
                    
                    // Mark records with their source domain for clarity
                    for (DiscoveredARecord record : records) {
                        record.setSourceDomain(domainToCheck);
                        
                        // Auto-suggest designation based on subdomain patterns
                        if (domainToCheck.equals(domain)) {
                            record.setSuggestedRole("Primary (main domain)");
                        } else if (isBackupSubdomain(domainToCheck)) {
                            record.setSuggestedRole("Secondary (backup subdomain)");
                        } else {
                            record.setSuggestedRole("Unassigned (related subdomain)");
                        }
                    }
                    
                    allRecords.addAll(records);
                    
                    if (!records.isEmpty()) {
                        logger.info("Found " + records.size() + " A records for " + domainToCheck);
                    }
                }
                
                logger.info("Total A records discovered across all domains: " + allRecords.size());
                
            } catch (Exception e) {
                logger.severe("Error discovering A records for " + domain + " and related domains: " + e.getMessage());
            }
            
            return allRecords;
        });
    }
    
    /**
     * Generate list of domain variations to check for failover configurations
     */
    private List<String> generateFailoverDomainList(String baseDomain) {
        List<String> domains = new ArrayList<>();
        
        // Always check the original domain first
        domains.add(baseDomain);
        
        // Extract base domain components
        String[] parts = baseDomain.split("\\.");
        if (parts.length < 2) {
            return domains; // Invalid domain format
        }
        
        // For subdomains like "api.example.com", generate backup patterns
        if (parts.length >= 3) {
            String subdomain = parts[0];
            String rootDomain = String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
            
            // Common backup subdomain patterns
            List<String> backupPatterns = List.of(
                subdomain + "-backup." + rootDomain,      // api-backup.example.com
                subdomain + "-failover." + rootDomain,    // api-failover.example.com
                subdomain + "-secondary." + rootDomain,   // api-secondary.example.com
                subdomain + "2." + rootDomain,            // api2.example.com
                "backup-" + subdomain + "." + rootDomain, // backup-api.example.com
                "failover-" + subdomain + "." + rootDomain, // failover-api.example.com
                "secondary-" + subdomain + "." + rootDomain, // secondary-api.example.com
                "bak-" + subdomain + "." + rootDomain,    // bak-api.example.com
                subdomain + "-dr." + rootDomain,          // api-dr.example.com (disaster recovery)
                subdomain + "-hot." + rootDomain,         // api-hot.example.com (hot standby)
                subdomain + "-standby." + rootDomain      // api-standby.example.com
            );
            
            domains.addAll(backupPatterns);
        } else {
            // For root domains like "example.com", generate backup patterns
            String rootDomain = baseDomain;
            
            List<String> backupPatterns = List.of(
                "backup." + rootDomain,                  // backup.example.com
                "failover." + rootDomain,                // failover.example.com
                "secondary." + rootDomain,               // secondary.example.com
                "www-backup." + rootDomain,              // www-backup.example.com
                "www2." + rootDomain,                    // www2.example.com
                "dr." + rootDomain,                      // dr.example.com
                "standby." + rootDomain,                 // standby.example.com
                "hot." + rootDomain,                     // hot.example.com
                "fallback." + rootDomain,                // fallback.example.com
                "mirror." + rootDomain                   // mirror.example.com
            );
            
            domains.addAll(backupPatterns);
        }
        
        return domains;
    }
    
    /**
     * Check if a domain appears to be a backup/secondary subdomain
     */
    private boolean isBackupSubdomain(String domain) {
        String lowerDomain = domain.toLowerCase();
        return lowerDomain.contains("backup") || 
               lowerDomain.contains("failover") || 
               lowerDomain.contains("secondary") || 
               lowerDomain.contains("standby") || 
               lowerDomain.contains("dr") || 
               lowerDomain.contains("hot") || 
               lowerDomain.contains("fallback") || 
               lowerDomain.contains("mirror") ||
               lowerDomain.contains("bak-") ||
               lowerDomain.matches(".*\\d+\\..*"); // Like api2.example.com
    }
    
    /**
     * Discover A records for a single domain (original method logic)
     */
    private List<DiscoveredARecord> discoverARecordsForSingleDomain(String domain) {
        List<DiscoveredARecord> records = new ArrayList<>();
        
        try {
            // Use dig to get all A records
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "A", "+short");
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and CNAME records (dig +short can return CNAMEs first)
                if (line.isEmpty() || !line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                    continue;
                }
                
                // Create discovered record with additional info
                DiscoveredARecord record = new DiscoveredARecord();
                record.setIpAddress(line);
                record.setCloudProvider(detectCloudProvider(line));
                record.setAwsRegion(detectRegionFromIP(line));
                record.setEndpointName(detectEndpointFromIP(line));
                
                // Test reachability
                record.setReachable(checkEndpointHealth(line));
                
                // Measure response time
                record.setResponseTime(measureLatency(line));
                
                records.add(record);
            }
            
            process.waitFor();
            
            // If no A records found with +short, try detailed query
            if (records.isEmpty()) {
                records = discoverARecordsDetailedSingle(domain);
            }
            
        } catch (Exception e) {
            logger.warning("Error discovering A records for " + domain + ": " + e.getMessage());
            
            // Fallback: try Java DNS resolution
            try {
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                for (InetAddress addr : addresses) {
                    if (addr instanceof java.net.Inet4Address) { // IPv4 only
                        DiscoveredARecord record = new DiscoveredARecord();
                        record.setIpAddress(addr.getHostAddress());
                        record.setCloudProvider(detectCloudProvider(addr.getHostAddress()));
                        record.setAwsRegion(detectRegionFromIP(addr.getHostAddress()));
                        record.setEndpointName(detectEndpointFromIP(addr.getHostAddress()));
                        record.setReachable(true); // Assume reachable if resolved
                        record.setResponseTime(0L); // Unknown
                        records.add(record);
                    }
                }
            } catch (Exception fallbackException) {
                // Silent failure for individual domains
            }
        }
        
        return records;
    }
    
    /**
     * Discover A records using detailed dig output for a single domain
     */
    private List<DiscoveredARecord> discoverARecordsDetailedSingle(String domain) {
        List<DiscoveredARecord> records = new ArrayList<>();
        
        try {
            // Use detailed dig output
            ProcessBuilder pb = new ProcessBuilder("dig", domain, "A");
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean inAnswerSection = false;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                if (line.startsWith(";; ANSWER SECTION:")) {
                    inAnswerSection = true;
                    continue;
                }
                
                if (line.startsWith(";;") || line.isEmpty()) {
                    if (inAnswerSection && line.startsWith(";;")) {
                        break; // End of answer section
                    }
                    continue;
                }
                
                if (inAnswerSection && line.contains(" A ")) {
                    // Parse line like: "example.com. 300 IN A 192.168.1.1"
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5 && "A".equals(parts[3])) {
                        String ip = parts[4];
                        
                        if (ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            DiscoveredARecord record = new DiscoveredARecord();
                            record.setIpAddress(ip);
                            record.setTtl(Integer.parseInt(parts[1]));
                            record.setCloudProvider(detectCloudProvider(ip));
                            record.setAwsRegion(detectRegionFromIP(ip));
                            record.setEndpointName(detectEndpointFromIP(ip));
                            record.setReachable(checkEndpointHealth(ip));
                            record.setResponseTime(measureLatency(ip));
                            records.add(record);
                        }
                    }
                }
            }
            
            process.waitFor();
            
        } catch (Exception e) {
            logger.warning("Error in detailed A record discovery for " + domain + ": " + e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Data class for discovered A records with additional metadata
     */
    public static class DiscoveredARecord {
        private String ipAddress;
        private String cloudProvider;
        private String awsRegion;
        private String endpointName;
        private boolean reachable;
        private long responseTime;
        private int ttl;
        private boolean isPrimary;
        private boolean isSecondary;
        private String sourceDomain;
        private String suggestedRole;
        
        // Constructors
        public DiscoveredARecord() {}
        
        public DiscoveredARecord(String ipAddress) {
            this.ipAddress = ipAddress;
        }
        
        // Getters and setters
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public String getCloudProvider() { return cloudProvider; }
        public void setCloudProvider(String cloudProvider) { this.cloudProvider = cloudProvider; }
        
        public String getAwsRegion() { return awsRegion; }
        public void setAwsRegion(String awsRegion) { this.awsRegion = awsRegion; }
        
        public String getEndpointName() { return endpointName; }
        public void setEndpointName(String endpointName) { this.endpointName = endpointName; }
        
        public boolean isReachable() { return reachable; }
        public void setReachable(boolean reachable) { this.reachable = reachable; }
        
        public long getResponseTime() { return responseTime; }
        public void setResponseTime(long responseTime) { this.responseTime = responseTime; }
        
        public int getTtl() { return ttl; }
        public void setTtl(int ttl) { this.ttl = ttl; }
        
        public boolean isPrimary() { return isPrimary; }
        public void setPrimary(boolean primary) { isPrimary = primary; }
        
        public boolean isSecondary() { return isSecondary; }
        public void setSecondary(boolean secondary) { isSecondary = secondary; }
        
        public String getDesignation() {
            if (isPrimary) return "Primary";
            if (isSecondary) return "Secondary";
            return "Unassigned";
        }
        
        public String getStatus() {
            if (reachable && responseTime < 1000) return "Healthy";
            if (reachable && responseTime < 5000) return "Slow";
            if (reachable) return "Very Slow";
            return "Unreachable";
        }
        
        public String getSourceDomain() { return sourceDomain; }
        public void setSourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; }
        
        public String getSuggestedRole() { return suggestedRole; }
        public void setSuggestedRole(String suggestedRole) { this.suggestedRole = suggestedRole; }
        
        @Override
        public String toString() {
            return String.format("%s from %s (%s, %s, %s, %dms)", 
                ipAddress, sourceDomain != null ? sourceDomain : "unknown", 
                awsRegion, cloudProvider, getStatus(), responseTime);
        }
    }
} 