package edu.au.cpsc.module7.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Route53ResolverTest {
    
    public enum TestType {
        FORWARDING_RULE,
        SYSTEM_RULE,
        OUTBOUND_ENDPOINT,
        INBOUND_ENDPOINT,
        RULE_PRIORITY
    }
    
    public enum TestResult {
        PASSED,
        FAILED,
        BLOCKED,
        TIMEOUT,
        UNKNOWN
    }
    
    private String testId;
    private TestType testType;
    private String targetDomain;
    private String resolverRuleId;
    private String endpointId;
    private List<String> targetIPs;
    private String expectedResult;
    private TestResult actualResult;
    private String responseData;
    private long responseTimeMs;
    private LocalDateTime timestamp;
    private Map<String, String> metadata;
    private String errorMessage;
    
    // DNS Query Types
    public enum QueryType {
        A, AAAA, CNAME, MX, TXT, SOA, NS, PTR, SRV
    }
    
    private QueryType queryType = QueryType.A;
    
    public Route53ResolverTest() {
        this.timestamp = LocalDateTime.now();
    }
    
    public Route53ResolverTest(String testId, TestType testType, String targetDomain) {
        this();
        this.testId = testId;
        this.testType = testType;
        this.targetDomain = targetDomain;
    }
    
    // Getters and Setters
    public String getTestId() { return testId; }
    public void setTestId(String testId) { this.testId = testId; }
    
    public TestType getTestType() { return testType; }
    public void setTestType(TestType testType) { this.testType = testType; }
    
    public String getTargetDomain() { return targetDomain; }
    public void setTargetDomain(String targetDomain) { this.targetDomain = targetDomain; }
    
    public String getResolverRuleId() { return resolverRuleId; }
    public void setResolverRuleId(String resolverRuleId) { this.resolverRuleId = resolverRuleId; }
    
    public String getEndpointId() { return endpointId; }
    public void setEndpointId(String endpointId) { this.endpointId = endpointId; }
    
    public List<String> getTargetIPs() { return targetIPs; }
    public void setTargetIPs(List<String> targetIPs) { this.targetIPs = targetIPs; }
    
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    
    public TestResult getActualResult() { return actualResult; }
    public void setActualResult(TestResult actualResult) { this.actualResult = actualResult; }
    
    public String getResponseData() { return responseData; }
    public void setResponseData(String responseData) { this.responseData = responseData; }
    
    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public QueryType getQueryType() { return queryType; }
    public void setQueryType(QueryType queryType) { this.queryType = queryType; }
    
    @Override
    public String toString() {
        return String.format("Route53ResolverTest{testId='%s', testType=%s, targetDomain='%s', result=%s, responseTime=%dms}", 
                           testId, testType, targetDomain, actualResult, responseTimeMs);
    }
} 