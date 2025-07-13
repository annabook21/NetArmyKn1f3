package edu.au.cpsc.module7.models;

import java.util.List;
import java.util.Map;

/**
 * Configuration for AWS firewall testing
 */
public class AwsFirewallConfiguration {
    
    public enum TestType {
        NETWORK_FIREWALL_RULES,
        WAF_RULES,
        BOTH
    }
    
    public enum PayloadCategory {
        SQL_INJECTION,
        XSS,
        COMMAND_INJECTION,
        PATH_TRAVERSAL,
        MALICIOUS_USER_AGENT,
        SUSPICIOUS_HEADERS,
        LARGE_PAYLOAD,
        TLS_FRAGMENTATION,
        CUSTOM
    }
    
    private final String configurationName;
    private final TestType testType;
    private final String awsRegion;
    private final String targetResource;
    private final List<String> firewallArns;
    private final List<PayloadCategory> payloadCategories;
    private final Map<String, String> customPayloads;
    private final int timeoutSeconds;
    private final int maxConcurrentTests;
    private final boolean enableCloudWatchLogs;
    private final boolean enableResponseAnalysis;
    private final List<String> targetPorts;
    private final String awsAccessKeyId;
    private final String awsSecretAccessKey;
    private final String awsSessionToken;
    
    public AwsFirewallConfiguration(Builder builder) {
        this.configurationName = builder.configurationName;
        this.testType = builder.testType;
        this.awsRegion = builder.awsRegion;
        this.targetResource = builder.targetResource;
        this.firewallArns = builder.firewallArns;
        this.payloadCategories = builder.payloadCategories;
        this.customPayloads = builder.customPayloads;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxConcurrentTests = builder.maxConcurrentTests;
        this.enableCloudWatchLogs = builder.enableCloudWatchLogs;
        this.enableResponseAnalysis = builder.enableResponseAnalysis;
        this.targetPorts = builder.targetPorts;
        this.awsAccessKeyId = builder.awsAccessKeyId;
        this.awsSecretAccessKey = builder.awsSecretAccessKey;
        this.awsSessionToken = builder.awsSessionToken;
    }
    
    // Getters
    public String getConfigurationName() { return configurationName; }
    public TestType getTestType() { return testType; }
    public String getAwsRegion() { return awsRegion; }
    public String getTargetResource() { return targetResource; }
    public List<String> getFirewallArns() { return firewallArns; }
    public List<PayloadCategory> getPayloadCategories() { return payloadCategories; }
    public Map<String, String> getCustomPayloads() { return customPayloads; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxConcurrentTests() { return maxConcurrentTests; }
    public boolean isEnableCloudWatchLogs() { return enableCloudWatchLogs; }
    public boolean isEnableResponseAnalysis() { return enableResponseAnalysis; }
    public List<String> getTargetPorts() { return targetPorts; }
    public String getAwsAccessKeyId() { return awsAccessKeyId; }
    public String getAwsSecretAccessKey() { return awsSecretAccessKey; }
    public String getAwsSessionToken() { return awsSessionToken; }
    
    public static class Builder {
        private String configurationName;
        private TestType testType = TestType.BOTH;
        private String awsRegion = "us-east-1";
        private String targetResource;
        private List<String> firewallArns;
        private List<PayloadCategory> payloadCategories;
        private Map<String, String> customPayloads;
        private int timeoutSeconds = 30;
        private int maxConcurrentTests = 5;
        private boolean enableCloudWatchLogs = true;
        private boolean enableResponseAnalysis = true;
        private List<String> targetPorts;
        private String awsAccessKeyId;
        private String awsSecretAccessKey;
        private String awsSessionToken;
        
        public Builder configurationName(String configurationName) { this.configurationName = configurationName; return this; }
        public Builder testType(TestType testType) { this.testType = testType; return this; }
        public Builder awsRegion(String awsRegion) { this.awsRegion = awsRegion; return this; }
        public Builder targetResource(String targetResource) { this.targetResource = targetResource; return this; }
        public Builder firewallArns(List<String> firewallArns) { this.firewallArns = firewallArns; return this; }
        public Builder payloadCategories(List<PayloadCategory> payloadCategories) { this.payloadCategories = payloadCategories; return this; }
        public Builder customPayloads(Map<String, String> customPayloads) { this.customPayloads = customPayloads; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
        public Builder maxConcurrentTests(int maxConcurrentTests) { this.maxConcurrentTests = maxConcurrentTests; return this; }
        public Builder enableCloudWatchLogs(boolean enableCloudWatchLogs) { this.enableCloudWatchLogs = enableCloudWatchLogs; return this; }
        public Builder enableResponseAnalysis(boolean enableResponseAnalysis) { this.enableResponseAnalysis = enableResponseAnalysis; return this; }
        public Builder targetPorts(List<String> targetPorts) { this.targetPorts = targetPorts; return this; }
        public Builder awsAccessKeyId(String awsAccessKeyId) { this.awsAccessKeyId = awsAccessKeyId; return this; }
        public Builder awsSecretAccessKey(String awsSecretAccessKey) { this.awsSecretAccessKey = awsSecretAccessKey; return this; }
        public Builder awsSessionToken(String awsSessionToken) { this.awsSessionToken = awsSessionToken; return this; }
        
        public AwsFirewallConfiguration build() {
            return new AwsFirewallConfiguration(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AwsFirewallConfiguration{name='%s', type=%s, region='%s', target='%s'}", 
                configurationName, testType, awsRegion, targetResource);
    }
} 