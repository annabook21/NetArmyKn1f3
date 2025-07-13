package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.AwsFirewallConfiguration;
import edu.au.cpsc.module7.models.AwsFirewallConfiguration.PayloadCategory;
import edu.au.cpsc.module7.models.FirewallTestResult;
import edu.au.cpsc.module7.models.FirewallTestResult.TestStatus;
import edu.au.cpsc.module7.models.FirewallTestResult.FirewallType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.concurrent.Task;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service for testing AWS firewall rules and analyzing responses
 */
@Singleton
public class AwsFirewallTestingService {
    
    private static final Logger LOGGER = Logger.getLogger(AwsFirewallTestingService.class.getName());
    
    private final FirewallPayloadGenerator payloadGenerator;
    private final ExecutorService executorService;
    
    @Inject
    public AwsFirewallTestingService(FirewallPayloadGenerator payloadGenerator) {
        this.payloadGenerator = payloadGenerator;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Execute firewall tests based on configuration
     */
    public Task<List<FirewallTestResult>> executeFirewallTests(AwsFirewallConfiguration config) {
        return new Task<List<FirewallTestResult>>() {
            @Override
            protected List<FirewallTestResult> call() throws Exception {
                List<FirewallTestResult> results = new ArrayList<>();
                
                updateMessage("Starting AWS firewall tests...");
                updateProgress(0, 100);
                
                // Generate test payloads
                List<TestPayload> testPayloads = generateTestPayloads(config);
                
                updateMessage("Generated " + testPayloads.size() + " test payloads");
                updateProgress(10, 100);
                
                // Execute tests
                List<Future<FirewallTestResult>> futures = new ArrayList<>();
                
                for (int i = 0; i < testPayloads.size(); i++) {
                    if (isCancelled()) {
                        break;
                    }
                    
                    TestPayload payload = testPayloads.get(i);
                    Future<FirewallTestResult> future = executorService.submit(() -> 
                        executeTest(payload, config));
                    futures.add(future);
                    
                    // Progress update
                    int progress = 10 + (i * 80 / testPayloads.size());
                    updateProgress(progress, 100);
                    updateMessage("Executing test " + (i + 1) + " of " + testPayloads.size());
                }
                
                // Collect results
                for (Future<FirewallTestResult> future : futures) {
                    try {
                        FirewallTestResult result = future.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
                        results.add(result);
                    } catch (TimeoutException e) {
                        LOGGER.warning("Test timed out");
                        results.add(createTimeoutResult(config));
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Test execution failed", e);
                        results.add(createErrorResult(config, e.getMessage()));
                    }
                }
                
                updateMessage("Completed " + results.size() + " tests");
                updateProgress(100, 100);
                
                return results;
            }
        };
    }
    
    /**
     * Generate test payloads based on configuration
     */
    private List<TestPayload> generateTestPayloads(AwsFirewallConfiguration config) {
        List<TestPayload> testPayloads = new ArrayList<>();
        
        for (PayloadCategory category : config.getPayloadCategories()) {
            List<String> payloads = payloadGenerator.generatePayloads(category, 5); // Generate 5 base payloads with variations
            
            for (String payload : payloads) {
                testPayloads.add(new TestPayload(category.name(), payload, 
                    payloadGenerator.getCategoryDescription(category)));
            }
            
            // Add encoded variants
            List<String> urlEncoded = payloadGenerator.generateUrlEncodedPayloads(category);
            for (String payload : urlEncoded) {
                testPayloads.add(new TestPayload(category.name() + "_URL_ENCODED", payload, 
                    "URL encoded " + payloadGenerator.getCategoryDescription(category)));
            }
        }
        
        // Add custom payloads
        if (config.getCustomPayloads() != null) {
            for (Map.Entry<String, String> entry : config.getCustomPayloads().entrySet()) {
                testPayloads.add(new TestPayload("CUSTOM_" + entry.getKey(), entry.getValue(), 
                    "Custom payload: " + entry.getKey()));
            }
        }
        
        return testPayloads;
    }
    
    /**
     * Execute a single test
     */
    private FirewallTestResult executeTest(TestPayload payload, AwsFirewallConfiguration config) {
        String testId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        try {
            // Determine test method based on target resource
            if (config.getTargetResource().startsWith("http://") || 
                config.getTargetResource().startsWith("https://")) {
                return executeHttpTest(testId, payload, config, startTime);
            } else {
                return executeTcpTest(testId, payload, config, startTime);
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            LOGGER.log(Level.SEVERE, "Test execution failed for payload: " + payload.getPayload(), e);
            
            return new FirewallTestResult.Builder()
                .testId(testId)
                .targetResource(config.getTargetResource())
                .payloadType(payload.getType())
                .payload(payload.getPayload())
                .status(TestStatus.ERROR)
                .firewallType(FirewallType.UNKNOWN)
                .responseTimeMs(responseTime)
                .errorMessage(e.getMessage())
                .detectionMethods(Arrays.asList("Exception"))
                .build();
        }
    }
    
    /**
     * Execute HTTP-based test
     */
    private FirewallTestResult executeHttpTest(String testId, TestPayload payload, 
                                             AwsFirewallConfiguration config, long startTime) {
        try {
            URL url = new URL(config.getTargetResource());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(config.getTimeoutSeconds() * 1000);
            connection.setReadTimeout(config.getTimeoutSeconds() * 1000);
            connection.setDoOutput(true);
            
            // Add suspicious headers if testing header-based rules
            if (payload.getType().contains("SUSPICIOUS_HEADERS")) {
                Map<String, String> headers = payloadGenerator.generateSuspiciousHeaders();
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            
            // Add malicious user agent if testing user agent rules
            if (payload.getType().contains("MALICIOUS_USER_AGENTS")) {
                connection.setRequestProperty("User-Agent", payload.getPayload());
            } else {
                connection.setRequestProperty("User-Agent", "NetArmy-FirewallTester/1.0");
            }
            
            // Send payload
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getPayload().getBytes("UTF-8"));
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            String responseBody = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                responseBody = sb.toString();
            }
            
            // Analyze response
            TestStatus status = analyzeHttpResponse(responseCode, responseBody, responseTime);
            List<String> detectionMethods = getDetectionMethods(responseCode, responseBody, responseTime);
            
            return new FirewallTestResult.Builder()
                .testId(testId)
                .targetResource(config.getTargetResource())
                .payloadType(payload.getType())
                .payload(payload.getPayload())
                .status(status)
                .firewallType(FirewallType.AWS_WAF)
                .responseTimeMs(responseTime)
                .httpStatusCode(responseCode)
                .responseBody(responseBody)
                .detectionMethods(detectionMethods)
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Connection refused or timeout might indicate blocking
            TestStatus status = TestStatus.ERROR;
            if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
                status = TestStatus.BLOCKED;
            }
            
            return new FirewallTestResult.Builder()
                .testId(testId)
                .targetResource(config.getTargetResource())
                .payloadType(payload.getType())
                .payload(payload.getPayload())
                .status(status)
                .firewallType(FirewallType.AWS_NETWORK_FIREWALL)
                .responseTimeMs(responseTime)
                .errorMessage(e.getMessage())
                .detectionMethods(Arrays.asList("Connection Exception"))
                .build();
        }
    }
    
    /**
     * Execute TCP-based test
     */
    private FirewallTestResult executeTcpTest(String testId, TestPayload payload, 
                                            AwsFirewallConfiguration config, long startTime) {
        try {
            String[] hostPort = config.getTargetResource().split(":");
            String host = hostPort[0];
            int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 80;
            
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), config.getTimeoutSeconds() * 1000);
                
                // Send payload
                try (PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                    writer.println(payload.getPayload());
                }
                
                // Try to read response
                String response = "";
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    response = reader.readLine();
                }
                
                long responseTime = System.currentTimeMillis() - startTime;
                
                return new FirewallTestResult.Builder()
                    .testId(testId)
                    .targetResource(config.getTargetResource())
                    .payloadType(payload.getType())
                    .payload(payload.getPayload())
                    .status(TestStatus.ALLOWED)
                    .firewallType(FirewallType.AWS_NETWORK_FIREWALL)
                    .responseTimeMs(responseTime)
                    .responseBody(response)
                    .detectionMethods(Arrays.asList("TCP Connection Success"))
                    .build();
            }
                    
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return new FirewallTestResult.Builder()
                .testId(testId)
                .targetResource(config.getTargetResource())
                .payloadType(payload.getType())
                .payload(payload.getPayload())
                .status(TestStatus.BLOCKED)
                .firewallType(FirewallType.AWS_NETWORK_FIREWALL)
                .responseTimeMs(responseTime)
                .errorMessage(e.getMessage())
                .detectionMethods(Arrays.asList("TCP Connection Failed"))
                .build();
        }
    }
    
    /**
     * Analyze HTTP response to determine if request was blocked
     */
    private TestStatus analyzeHttpResponse(int responseCode, String responseBody, long responseTime) {
        // Common blocking response codes
        if (responseCode == 403 || responseCode == 429 || responseCode == 503) {
            return TestStatus.BLOCKED;
        }
        
        // Check for common WAF blocking messages
        String bodyLower = responseBody.toLowerCase();
        if (bodyLower.contains("blocked") || bodyLower.contains("forbidden") || 
            bodyLower.contains("access denied") || bodyLower.contains("waf")) {
            return TestStatus.BLOCKED;
        }
        
        // Very slow responses might indicate filtering
        if (responseTime > 10000) { // 10 seconds
            return TestStatus.TIMEOUT;
        }
        
        // Success codes indicate allowed
        if (responseCode >= 200 && responseCode < 300) {
            return TestStatus.ALLOWED;
        }
        
        return TestStatus.UNKNOWN;
    }
    
    /**
     * Get detection methods used to determine the result
     */
    private List<String> getDetectionMethods(int responseCode, String responseBody, long responseTime) {
        List<String> methods = new ArrayList<>();
        
        if (responseCode == 403 || responseCode == 429 || responseCode == 503) {
            methods.add("HTTP Status Code (" + responseCode + ")");
        }
        
        String bodyLower = responseBody.toLowerCase();
        if (bodyLower.contains("blocked") || bodyLower.contains("forbidden") || 
            bodyLower.contains("access denied") || bodyLower.contains("waf")) {
            methods.add("Response Body Analysis");
        }
        
        if (responseTime > 10000) {
            methods.add("Response Time Analysis");
        }
        
        if (methods.isEmpty()) {
            methods.add("Standard HTTP Response");
        }
        
        return methods;
    }
    
    /**
     * Create timeout result
     */
    private FirewallTestResult createTimeoutResult(AwsFirewallConfiguration config) {
        return new FirewallTestResult.Builder()
            .testId(UUID.randomUUID().toString())
            .targetResource(config.getTargetResource())
            .payloadType("TIMEOUT")
            .payload("N/A")
            .status(TestStatus.TIMEOUT)
            .firewallType(FirewallType.UNKNOWN)
            .responseTimeMs(config.getTimeoutSeconds() * 1000L)
            .errorMessage("Test timed out")
            .detectionMethods(Arrays.asList("Timeout"))
            .build();
    }
    
    /**
     * Create error result
     */
    private FirewallTestResult createErrorResult(AwsFirewallConfiguration config, String errorMessage) {
        return new FirewallTestResult.Builder()
            .testId(UUID.randomUUID().toString())
            .targetResource(config.getTargetResource())
            .payloadType("ERROR")
            .payload("N/A")
            .status(TestStatus.ERROR)
            .firewallType(FirewallType.UNKNOWN)
            .responseTimeMs(0)
            .errorMessage(errorMessage)
            .detectionMethods(Arrays.asList("Error"))
            .build();
    }
    
    /**
     * Test payload container
     */
    private static class TestPayload {
        private final String type;
        private final String payload;
        private final String description;
        
        public TestPayload(String type, String payload, String description) {
            this.type = type;
            this.payload = payload;
            this.description = description;
        }
        
        public String getType() { return type; }
        public String getPayload() { return payload; }
        public String getDescription() { return description; }
    }
    
    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
} 