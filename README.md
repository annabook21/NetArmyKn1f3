<div align="center">
    <img src="net_icon.png" alt="NetArmyKn1f3 Logo" width="450"/>
</div>

# NetArmyKn1f3: A Network Analysis & Security Testing Suite

**Author**: Anna Booker
**Version**: 1.0.0
**Date**: 2025-07-13

---

## 1. Overview

NetArmyKn1f3 is an advanced, JavaFX-based desktop application engineered for comprehensive network analysis, scanning, visualization, and security testing. It serves as an integrated command center for network administrators, cybersecurity professionals, penetration testers, and technology enthusiasts. The suite provides a rich graphical user interface (GUI) to simplify complex networking tasks such as host discovery, port scanning, OS fingerprinting, firewall rule testing, and visualizing network topologies and traceroute paths.

The application is built on a modular architecture, separating concerns between UI controllers, networking services, and data models. It leverages the power of industry-standard command-line tools like `nmap` and `traceroute`, while incorporating cutting-edge security testing capabilities including sophisticated payload generation and firewall effectiveness analysis.

---

## 2. Core Features

The application's functionality is organized into four primary modules, each accessible via a dedicated tab in the main window.

### 2.1. System Information

This module provides a real-time, at-a-glance dashboard of the local machine's network configuration and operating system details.

-   **Network Interface Enumeration**: Lists all detected network interfaces (e.g., `en0`, `lo0`, `eth0`). For each interface, it displays critical information such as IPv4/IPv6 addresses and the hardware MAC address.
-   **Operating System Synopsis**: Displays essential OS details, including name (e.g., macOS, Windows, Linux), version, and system architecture (e.g., `aarch64`, `x86_64`).

### 2.2. Network Probe

The Network Probe module is designed for in-depth analysis of a single, specified remote target. It is the primary tool for visualizing the Layer 3 path that network traffic takes from the local machine to a destination host across the internet.

-   **Target-Specific Analysis**: Accepts a hostname (e.g., `google.com`) or an IP address (e.g., `8.8.8.8`) as input.
-   **Automated Traceroute Execution**: Performs a `traceroute` to the destination, capturing each intermediate router (hop) along the network path.
-   **Automated Path Analysis**: Performs a `traceroute` or `mtr` (My Traceroute) to the destination. `mtr` provides a richer, real-time view of packet loss and latency at each hop.
-   **Dual-View Results Interface**:
    -   **Raw Results View**: Presents the complete, unfiltered command-line output from the underlying network probes for expert review and debugging.
    -   **Topology Map View**: Renders a dynamic, interactive graph of the traceroute path using a **force-directed layout**. This visualization clearly illustrates the journey of packets from the gateway (red node) through intermediate hops (blue nodes) to the final destination (green node).

### 2.3. Network Scanner

This module is a powerful discovery tool for mapping devices and services on the local network.

-   **Flexible Scan Configuration**: Supports CIDR notation (e.g., `192.168.1.0/24`) and IP ranges (e.g., `192.168.1.100-150`) for defining scan targets.
-   **Multiple Scan Modalities**:
    -   **Ping Sweep**: A fast scan to identify active hosts on the network.
    -   **Port Scan**: Scans for common open ports on discovered hosts.

### 2.4. Firewall Rule Tester ⭐ **NEW**

An advanced security testing module designed to evaluate firewall rule effectiveness through sophisticated payload generation and response analysis.

-   **Professional Payload Generation**: 
    -   **SQL Injection**: 20+ advanced SQL injection payloads with time-based variants
    -   **Cross-Site Scripting (XSS)**: 20+ XSS payloads targeting modern browsers
    -   **Command Injection**: 20+ OS command injection techniques for multiple platforms
    -   **Path Traversal**: 15+ directory traversal attacks with encoding variations
    -   **TLS Fragmentation**: Tests for post-quantum crypto readiness ([tldr.fail](https://tldr.fail/))
    -   **Large Payloads**: Buffer overflow and DoS protection testing
    -   **Malicious User Agents**: 15+ attack tool signatures and exploit strings

-   **Advanced Encoding Techniques**:
    -   URL Encoding (single and double)
    -   Base64 encoding
    -   Hexadecimal encoding
    -   Unicode encoding
    -   HTML entity encoding
    -   Case variation attacks
    -   Comment injection bypasses

-   **Intelligent Response Analysis**:
    -   HTTP status code detection (403, 429, 503)
    -   Response body keyword analysis
    -   Connection behavior monitoring
    -   Response timing analysis
    -   Effectiveness scoring and statistics

-   **Real-Time Testing Dashboard**:
    -   Live test execution with progress tracking
    -   Concurrent payload testing (configurable threads)
    -   Detailed results table with filtering
    -   Export capabilities for reporting
    -   Custom payload support

---

## 3. Architecture and Design

The application adheres to a modern, modular design pattern, promoting separation of concerns and maintainability.

### 3.1. Technical Stack

-   **Language**: Java 17
-   **Framework**: JavaFX (via OpenJFX)
-   **Dependency Injection**: Google Guice
-   **Build System**: Apache Maven
-   **Visualization**: D3.js v7, rendered within a JavaFX WebView
-   **Security Testing**: Custom payload generation with enterprise-grade encoding
-   **Core Networking Tools**: `nmap`, `traceroute`, `mtr`, `hping3`, `ifconfig`/`ip`

### 3.2. High-Level Architecture

The architecture is composed of four main layers:

1.  **Presentation Layer (Controllers & FXML)**: Manages the user interface and handles user input
2.  **Service Layer**: Encapsulates core business logic including security testing services
3.  **Data Layer (Models)**: POJOs representing application data structures
4.  **Security Layer**: Advanced payload generation and response analysis engines

### 3.3. Key Security Components

-   **`FirewallPayloadGenerator`**: Generates sophisticated attack payloads with multiple encoding variations
-   **`AwsFirewallTestingService`**: Orchestrates security tests with concurrent execution and response analysis
-   **`AwsFirewallTesterController`**: Provides real-time testing dashboard with live progress tracking

---

## 4. How to Build and Run

### 4.1. Prerequisites

-   **Java JDK 17** or newer
-   **Apache Maven** 3.6 or newer
-   **System Tools**: `nmap`, `traceroute`, `mtr`, `hping3` (optional, for network modules)

### 4.2. Quick Start

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/annabook21/NetArmyKn1f3.git
    cd NetArmyKn1f3
    ```

2.  **Run the application:**
    ```bash
    mvn exec:java -Dexec.mainClass="edu.au.cpsc.module7.App"
    ```

3.  **Or use the launcher script (macOS/Linux):**
    ```bash
    ./run-netarmy.sh
    ```

---

## 5. Running the Application

### 5.1. Quick Start

**Using the launcher script:**
```bash
./NetArmyKn1f3.sh
```

**Direct Maven execution:**
```bash
mvn exec:java -Dexec.mainClass="edu.au.cpsc.module7.App"
```

### 5.2. Prerequisites

✅ **Java 21 or later**: Required for JavaFX support  
✅ **Maven 3.6+**: For dependency management and execution  
✅ **Network Tools**: `nmap`, `traceroute` (auto-installed if missing)  

---

## 6. Security Testing Capabilities

### 6.1. Payload Categories

| Category | Payloads | Encoding Variants | Use Case |
|----------|----------|-------------------|----------|
| **SQL Injection** | 20+ | 8+ encodings | Database security testing |
| **XSS** | 20+ | Multiple contexts | Web application security |
| **Command Injection** | 20+ | Cross-platform | System command filtering |
| **Path Traversal** | 15+ | Directory attacks | File access controls |
| **TLS Fragmentation** | 10+ | Post-quantum ready | Modern crypto migration |
| **Large Payloads** | 5+ | Buffer overflow | DoS protection testing |

### 6.2. Response Analysis

-   **HTTP Status Codes**: 403, 429, 503 (blocked), 200, 404 (allowed)
-   **Content Analysis**: Keywords like "blocked", "forbidden", "access denied"
-   **Timing Analysis**: Response time patterns indicating filtering
-   **Connection Behavior**: Reset vs. timeout patterns

### 6.3. Educational Integration

-   **TLS Fragmentation Testing**: References [tldr.fail](https://tldr.fail/) for post-quantum crypto education
-   **Real-World Scenarios**: Payloads based on actual attack techniques
-   **Professional Tools Comparison**: Techniques from OWASP ZAP and GoTestWAF

---

## 7. Future Enhancements

-   **Advanced Reporting**: PDF/HTML reports with detailed analysis
-   **Custom Rule Testing**: User-defined firewall rules validation
-   **API Integration**: REST API for automated testing
-   **Machine Learning**: AI-powered payload generation
-   **Compliance Testing**: OWASP Top 10, PCI DSS validation
-   **Team Collaboration**: Shared test configurations and results

---

## 8. Contributing

Contributions are welcome! Please read our contributing guidelines and submit pull requests for any improvements.

## 9. License

This project is licensed under the MIT License - see the LICENSE file for details.

---

**NetArmyKn1f3** - Empowering network professionals with comprehensive analysis and security testing capabilities.
