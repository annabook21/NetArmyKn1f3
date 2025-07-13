# NetArmyKn1f3: A Network Analysis & Visualization Suite

**Author**: Anna Booker
**Version**: 1.0.0
**Date**: 2024-07-13

---

## 1. Overview

NetArmyKn1f3 is an advanced, JavaFX-based desktop application engineered for comprehensive network analysis, scanning, and visualization. It serves as an integrated command center for network administrators, cybersecurity professionals, and technology enthusiasts. The suite provides a rich graphical user interface (GUI) to simplify complex networking tasks such as host discovery, port scanning, OS fingerprinting, and visualizing network topologies and traceroute paths.

The application is built on a modular architecture, separating concerns between UI controllers, networking services, and data models. It leverages the power of industry-standard command-line tools like `nmap` and `traceroute`, parsing their output into structured data that powers interactive, D3.js-based visualizations within a JavaFX WebView.

---

## 2. Core Features

The application's functionality is organized into three primary modules, each accessible via a dedicated tab in the main window.

### 2.1. System Information

This module provides a real-time, at-a-glance dashboard of the local machine's network configuration and operating system details.

-   **Network Interface Enumeration**: Lists all detected network interfaces (e.g., `en0`, `lo0`, `eth0`). For each interface, it displays critical information such as IPv4/IPv6 addresses and the hardware MAC address.
-   **Operating System Synopsis**: Displays essential OS details, including name (e.g., macOS, Windows, Linux), version, and system architecture (e.g., `aarch64`, `x86_64`).

### 2.2. Network Probe

The Network Probe module is designed for in-depth analysis of a single, specified remote target. It is the primary tool for visualizing the Layer 3 path that network traffic takes from the local machine to a destination host across the internet.

-   **Target-Specific Analysis**: Accepts a hostname (e.g., `google.com`) or an IP address (e.g., `8.8.8.8`) as input.
-   **Automated Traceroute Execution**: Performs a `traceroute` to the destination, capturing each intermediate router (hop) along the network path.
-   **Dual-View Results Interface**:
    -   **Raw Results View**: Presents the complete, unfiltered command-line output from the underlying network probes for expert review and debugging.
    -   **Topology Map View**: Renders a dynamic, interactive graph of the traceroute path using a **force-directed layout**. This visualization clearly illustrates the journey of packets from the gateway (red node) through intermediate hops (blue nodes) to the final destination (green node).

### 2.3. Network Scanner

This module is a powerful discovery tool for mapping devices and services on the local network.

-   **Flexible Scan Configuration**: Supports CIDR notation (e.g., `192.168.1.0/24`) and IP ranges (e.g., `192.168.1.100-150`) for defining scan targets.
-   **Multiple Scan Modalities**:
    -   **Ping Sweep**: A fast scan to identify active hosts on the network.
    -   **Port Scan**: Scans for common open ports on discovered hosts.
    -   **Full Scan**: A comprehensive deep scan combining host discovery, port scanning, service version detection, and OS fingerprinting.
-   **Advanced Analysis Options**:
    -   **Hostname Resolution**: Resolves IP addresses to hostnames where possible.
    -   **Service & OS Detection**: Identifies services on open ports and estimates the host's operating system.
    -   **Local Traceroute**: Traces the path to discovered hosts.
-   **Interactive Data Visualization**:
    -   **Bidirectional Map-Table Interactivity**: The results table and the network map are bidirectionally linked. Clicking a node on the map highlights the corresponding row in the table, and vice versa. This allows for seamless exploration of scan results.
    -   **Switchable Graph Layouts**: A dropdown menu allows the user to switch between two different D3.js-powered layouts for the network map:
        -   **Ring Layout**: Organizes hosts in clean, concentric circles, providing a structured and orderly view.
        -   **Force-Directed Layout**: Utilizes a physics-based simulation where nodes repel each other, revealing natural network clusters and topology.

---

## 3. Architecture and Design

The application adheres to a modern, modular design pattern, promoting separation of concerns and maintainability.

### 3.1. Technical Stack

-   **Language**: Java 17
-   **Framework**: JavaFX (via OpenJFX)
-   **Dependency Injection**: Google Guice
-   **Build System**: Apache Maven
-   **Visualization**: D3.js v7, rendered within a JavaFX WebView
-   **Core Networking Tools**: `nmap`, `traceroute`, `ifconfig`/`ip` (interfaced via `SystemToolsManager`)

### 3.2. High-Level Architecture

The architecture is composed of three main layers:

1.  **Presentation Layer (Controllers & FXML)**: Manages the user interface, defines the layout in FXML, and handles user input. Controllers are responsible for orchestrating calls to the service layer and updating the view with results.
2.  **Service Layer**: Encapsulates the core business logic. Services like `NetworkScannerService` and `SystemToolsManager` are responsible for executing external processes, parsing their output, and returning structured data models.
3.  **Data Layer (Models)**: A set of POJOs (Plain Old Java Objects) like `NetworkHost` and `ScanConfiguration` that represent the application's data structures.

### 3.3. Key Components

-   **`AppModule` (Dependency Injection)**: Configures bindings for Google Guice, ensuring that dependencies are automatically injected throughout the application. This promotes loose coupling.
-   **`*Controller` Classes**: (e.g., `NetworkScannerController`, `NetworkProbeController`) Mediate between the FXML views and the backend services. They handle UI events, trigger scans, and populate UI components with data.
-   **`SystemToolsManager`**: A critical service that acts as a wrapper for executing command-line tools (`nmap`, `traceroute`). It handles process creation, captures `stdout` and `stderr`, and provides a unified interface for running system commands.
-   **`NetworkVisualizationService`**: The bridge between the Java backend and the D3.js frontend. It loads the HTML/JS/CSS for the visualization into a `WebView`, generates the required JSON data from the scan results, and uses the `JSObject` bridge to pass data to the JavaScript environment and render the graphs.
-   **`NetworkScannerService`**: Orchestrates network scans by constructing the correct `nmap` commands based on user configuration, executing them via `SystemToolsManager`, and parsing the XML output into a list of `NetworkHost` objects.

### 3.4. JavaFX-JavaScript Bridge

A key architectural feature is the communication between the JavaFX application and the D3.js visualization running inside a `WebView`.

-   **Java to JavaScript**: Java calls JavaScript functions using `webEngine.executeScript()`. This is how the `NetworkVisualizationService` passes the network graph data (as a JSON string) to D3.js for rendering.
-   **JavaScript to Java**: A Java object (an instance of a `JavaBridge` class) is exposed to the JavaScript environment using `webEngine.getLoadWorker().stateProperty().addListener(...)` and `JSObject.setMember()`. This allows JavaScript event handlers (e.g., `onClick` on a D3 node) to call Java methods, enabling features like map-to-table selection.

---

## 4. How to Build and Run

The project is managed with Maven, simplifying the build process.

### 4.1. Prerequisites

-   **Java JDK 17** or newer.
-   **Apache Maven** 3.6 or newer.
-   **System Tools**: Ensure that `nmap` and `traceroute` are installed on your system and accessible in your system's PATH.
    -   **macOS (Homebrew)**: `brew install nmap`
    -   **Debian/Ubuntu**: `sudo apt-get install nmap traceroute`
    -   **Windows**: Download and install Nmap from the official website (which includes `ncat` for traceroute functionality).

### 4.2. Build & Execution

1.  Clone the repository:
    ```sh
    git clone https://github.com/annabook21/NetArmyKn1f3.git
    cd NetArmyKn1f3
    ```

2.  Build the project and run the application using the Maven JavaFX plugin:
    ```sh
    mvn clean javafx:run
    ```

This command will compile the source code, resolve dependencies, and launch the application.

---

## 5. Future Enhancements

-   **Packet Analysis Module**: Implement live packet capture and dissection using a library like Pcap4j.
-   **Persistent Scan History**: Save scan results to a local database (e.g., SQLite) to allow for historical comparison and analysis.
-   **Customizable Nmap Scripts**: Allow users to leverage the Nmap Scripting Engine (NSE) for more advanced vulnerability and discovery scans.
-   **Export Functionality**: Add options to export scan results and visualizations to common formats (e.g., CSV, PDF, PNG).
