package edu.au.cpsc.module7.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of testing AWS firewall rules
 */
public class FirewallTestResult {
    
    public enum TestStatus {
        BLOCKED,        // Traffic was blocked by firewall
        ALLOWED,        // Traffic was allowed through firewall
        TIMEOUT,        // Request timed out (possible blocking)
        ERROR,          // Error occurred during testing
        UNKNOWN         // Unable to determine result
    }
    
    public enum FirewallType {
        AWS_NETWORK_FIREWALL,
        AWS_WAF,
        UNKNOWN
    }
    
    private final String testId;
    private final LocalDateTime timestamp;
    private final String targetResource;
    private final String payloadType;
    private final String payload;
    private final TestStatus status;
    private final FirewallType firewallType;
    private final String firewallArn;
    private final String ruleName;
    private final long responseTimeMs;
    private final int httpStatusCode;
    private final String responseBody;
    private final String errorMessage;
    private final Map<String, String> additionalMetadata;
    private final List<String> detectionMethods;
    
    public FirewallTestResult(Builder builder) {
        this.testId = builder.testId;
        this.timestamp = builder.timestamp;
        this.targetResource = builder.targetResource;
        this.payloadType = builder.payloadType;
        this.payload = builder.payload;
        this.status = builder.status;
        this.firewallType = builder.firewallType;
        this.firewallArn = builder.firewallArn;
        this.ruleName = builder.ruleName;
        this.responseTimeMs = builder.responseTimeMs;
        this.httpStatusCode = builder.httpStatusCode;
        this.responseBody = builder.responseBody;
        this.errorMessage = builder.errorMessage;
        this.additionalMetadata = builder.additionalMetadata;
        this.detectionMethods = builder.detectionMethods;
    }
    
    // Getters
    public String getTestId() { return testId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getTargetResource() { return targetResource; }
    public String getPayloadType() { return payloadType; }
    public String getPayload() { return payload; }
    public TestStatus getStatus() { return status; }
    public FirewallType getFirewallType() { return firewallType; }
    public String getFirewallArn() { return firewallArn; }
    public String getRuleName() { return ruleName; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public int getHttpStatusCode() { return httpStatusCode; }
    public String getResponseBody() { return responseBody; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, String> getAdditionalMetadata() { return additionalMetadata; }
    public List<String> getDetectionMethods() { return detectionMethods; }
    
    public static class Builder {
        private String testId;
        private LocalDateTime timestamp = LocalDateTime.now();
        private String targetResource;
        private String payloadType;
        private String payload;
        private TestStatus status;
        private FirewallType firewallType;
        private String firewallArn;
        private String ruleName;
        private long responseTimeMs;
        private int httpStatusCode;
        private String responseBody;
        private String errorMessage;
        private Map<String, String> additionalMetadata;
        private List<String> detectionMethods;
        
        public Builder testId(String testId) { this.testId = testId; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder targetResource(String targetResource) { this.targetResource = targetResource; return this; }
        public Builder payloadType(String payloadType) { this.payloadType = payloadType; return this; }
        public Builder payload(String payload) { this.payload = payload; return this; }
        public Builder status(TestStatus status) { this.status = status; return this; }
        public Builder firewallType(FirewallType firewallType) { this.firewallType = firewallType; return this; }
        public Builder firewallArn(String firewallArn) { this.firewallArn = firewallArn; return this; }
        public Builder ruleName(String ruleName) { this.ruleName = ruleName; return this; }
        public Builder responseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; return this; }
        public Builder httpStatusCode(int httpStatusCode) { this.httpStatusCode = httpStatusCode; return this; }
        public Builder responseBody(String responseBody) { this.responseBody = responseBody; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder additionalMetadata(Map<String, String> additionalMetadata) { this.additionalMetadata = additionalMetadata; return this; }
        public Builder detectionMethods(List<String> detectionMethods) { this.detectionMethods = detectionMethods; return this; }
        
        public FirewallTestResult build() {
            return new FirewallTestResult(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("FirewallTestResult{testId='%s', status=%s, target='%s', payload='%s', responseTime=%dms}", 
                testId, status, targetResource, payloadType, responseTimeMs);
    }
} 