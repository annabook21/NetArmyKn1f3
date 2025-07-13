package edu.au.cpsc.module7.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Route53RoutingPolicyTest {
    
    public enum RoutingPolicyType {
        SIMPLE,
        FAILOVER,
        GEOLOCATION,
        GEOPROXIMITY,
        LATENCY,
        IP_BASED,
        MULTIVALUE_ANSWER,
        WEIGHTED
    }
    
    public enum TestResult {
        PASSED,
        FAILED,
        PARTIAL,
        TIMEOUT,
        UNKNOWN
    }
    
    private String testId;
    private RoutingPolicyType policyType;
    private String domainName;
    private String recordType; // A, AAAA, CNAME, etc.
    private String sourceLocation; // For geolocation testing
    private String expectedEndpoint;
    private String actualEndpoint;
    private TestResult result;
    private long responseTimeMs;
    private LocalDateTime timestamp;
    private Map<String, String> metadata;
    private String errorMessage;
    
    // Geolocation specific fields
    private String continent;
    private String country;
    private String subdivision;
    
    // Weighted routing fields
    private Map<String, Integer> weights;
    private int testIterations;
    private Map<String, Integer> actualDistribution;
    
    // Latency-based routing fields
    private Map<String, Long> regionLatencies;
    private String expectedLowestLatencyRegion;
    private String actualLowestLatencyRegion;
    
    // Failover routing fields
    private String primaryEndpoint;
    private String secondaryEndpoint;
    private boolean primaryHealthy;
    private boolean failoverTriggered;
    
    public Route53RoutingPolicyTest() {
        this.timestamp = LocalDateTime.now();
        this.testId = java.util.UUID.randomUUID().toString();
    }
    
    public Route53RoutingPolicyTest(RoutingPolicyType policyType, String domainName) {
        this();
        this.policyType = policyType;
        this.domainName = domainName;
    }
    
    // Getters and Setters
    public String getTestId() { return testId; }
    public void setTestId(String testId) { this.testId = testId; }
    
    public RoutingPolicyType getPolicyType() { return policyType; }
    public void setPolicyType(RoutingPolicyType policyType) { this.policyType = policyType; }
    
    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
    
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    
    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }
    
    public String getExpectedEndpoint() { return expectedEndpoint; }
    public void setExpectedEndpoint(String expectedEndpoint) { this.expectedEndpoint = expectedEndpoint; }
    
    public String getActualEndpoint() { return actualEndpoint; }
    public void setActualEndpoint(String actualEndpoint) { this.actualEndpoint = actualEndpoint; }
    
    public TestResult getResult() { return result; }
    public void setResult(TestResult result) { this.result = result; }
    
    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    // Geolocation getters/setters
    public String getContinent() { return continent; }
    public void setContinent(String continent) { this.continent = continent; }
    
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    
    public String getSubdivision() { return subdivision; }
    public void setSubdivision(String subdivision) { this.subdivision = subdivision; }
    
    // Weighted routing getters/setters
    public Map<String, Integer> getWeights() { return weights; }
    public void setWeights(Map<String, Integer> weights) { this.weights = weights; }
    
    public int getTestIterations() { return testIterations; }
    public void setTestIterations(int testIterations) { this.testIterations = testIterations; }
    
    public Map<String, Integer> getActualDistribution() { return actualDistribution; }
    public void setActualDistribution(Map<String, Integer> actualDistribution) { this.actualDistribution = actualDistribution; }
    
    // Latency-based routing getters/setters
    public Map<String, Long> getRegionLatencies() { return regionLatencies; }
    public void setRegionLatencies(Map<String, Long> regionLatencies) { this.regionLatencies = regionLatencies; }
    
    public String getExpectedLowestLatencyRegion() { return expectedLowestLatencyRegion; }
    public void setExpectedLowestLatencyRegion(String expectedLowestLatencyRegion) { this.expectedLowestLatencyRegion = expectedLowestLatencyRegion; }
    
    public String getActualLowestLatencyRegion() { return actualLowestLatencyRegion; }
    public void setActualLowestLatencyRegion(String actualLowestLatencyRegion) { this.actualLowestLatencyRegion = actualLowestLatencyRegion; }
    
    // Failover routing getters/setters
    public String getPrimaryEndpoint() { return primaryEndpoint; }
    public void setPrimaryEndpoint(String primaryEndpoint) { this.primaryEndpoint = primaryEndpoint; }
    
    public String getSecondaryEndpoint() { return secondaryEndpoint; }
    public void setSecondaryEndpoint(String secondaryEndpoint) { this.secondaryEndpoint = secondaryEndpoint; }
    
    public boolean isPrimaryHealthy() { return primaryHealthy; }
    public void setPrimaryHealthy(boolean primaryHealthy) { this.primaryHealthy = primaryHealthy; }
    
    public boolean isFailoverTriggered() { return failoverTriggered; }
    public void setFailoverTriggered(boolean failoverTriggered) { this.failoverTriggered = failoverTriggered; }
    
    @Override
    public String toString() {
        return String.format("Route53RoutingPolicyTest{testId='%s', policyType=%s, domain='%s', result=%s, responseTime=%dms}", 
                           testId, policyType, domainName, result, responseTimeMs);
    }
    
    /**
     * Get a human-readable description of the test
     */
    public String getTestDescription() {
        switch (policyType) {
            case GEOLOCATION:
                return String.format("Geolocation routing test for %s from %s", domainName, sourceLocation);
            case WEIGHTED:
                return String.format("Weighted routing test for %s (%d iterations)", domainName, testIterations);
            case LATENCY:
                return String.format("Latency-based routing test for %s", domainName);
            case FAILOVER:
                return String.format("Failover routing test for %s (primary: %s)", domainName, primaryEndpoint);
            case GEOPROXIMITY:
                return String.format("Geoproximity routing test for %s from %s", domainName, sourceLocation);
            case MULTIVALUE_ANSWER:
                return String.format("Multivalue answer routing test for %s", domainName);
            default:
                return String.format("Simple routing test for %s", domainName);
        }
    }
} 