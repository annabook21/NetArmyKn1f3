# NetArmyKn1f3: Software Design Document

**Author**: Anna Booker
**Status**: In Progress
**Version**: 1.0

---

## 1. Overview

This document outlines the software design for NetArmyKn1f3, a comprehensive network analysis and visualization suite. The application is a JavaFX-based desktop tool designed for network administrators, cybersecurity professionals, and students. It provides a graphical user interface for complex networking tasks, including network scanning, probing, and topology visualization. The core design philosophy emphasizes modularity, extensibility, and a clean separation of concerns.

## 2. Goals and Non-Goals

### 2.1. Goals

*   **Provide an Integrated Toolset**: Offer a unified interface for common network reconnaissance tasks (host discovery, port scanning, traceroute).
*   **Intuitive Visualization**: Translate complex, text-based network data into interactive, easy-to-understand graphical visualizations.
*   **Cross-Platform Support**: Function consistently across major desktop operating systems (Windows, macOS, Linux).
*   **Extensibility**: Design a modular architecture that allows for the future addition of new tools and features (e.g., live packet capture).
*   **Secure by Default**: Ensure that interactions with the underlying system and network are handled securely.

### 2.2. Non-Goals

*   **Replace Professional Pentesting Tools**: This tool is for analysis and visualization, not for advanced penetration testing or exploitation.
*   **Full Command-Line Equivalence**: The GUI will not expose every possible flag or option of the underlying tools (like `nmap`). It will focus on the most common and useful configurations.
*   **Cloud-Native Operation**: This is designed as a standalone desktop application, not a SaaS or cloud-based service.

## 3. System Architecture

### 3.1. High-Level View

NetArmyKn1f3 is a monolithic desktop application with a modular internal structure. It relies on external, industry-standard command-line tools for its core networking capabilities.

```mermaid
graph TD
    subgraph "NetArmyKn1f3 Application"
        A["Presentation Layer <br><i>(JavaFX, FXML)</i>"]
        B["Service Layer <br><i>(Business Logic)</i>"]
        C["Data Models <br><i>(POJOs)</i>"]
        D["Visualization Engine <br><i>(JavaFX WebView + D3.js)</i>"]
    end

    subgraph "External Tools"
        E[nmap]
        F[traceroute]
        G["ifconfig / ipconfig"]
    end

    A -->|User Input| B
    B -->|Executes| E
    B -->|Executes| F
    B -->|Executes| G
    E -->|Output| B
    F -->|Output| B
    G -->|Output| B
    B -->|Updates| C
    B -->|Renders Via| D
    C -->|Data For| D
    D -->|Displays To| A

```

### 3.2. Technology Stack

-   **Core Language**: Java 17
-   **UI Framework**: JavaFX 21 (via OpenJFX)
-   **Dependency Injection**: Google Guice 5.1.0
-   **Build & Dependency Management**: Apache Maven
-   **Visualization Library**: D3.js v7
-   **JSON Processing**: Jackson Databind 2.15.2
-   **Testing**: JUnit 5, Mockito

### 3.3. Directory Structure

The project follows the standard Maven directory layout.

-   `src/main/java`: Core Java source code.
    -   `controllers`: UI logic, mediating between views and services.
    -   `di`: Dependency injection configuration (Guice modules).
    -   `models`: Plain Old Java Objects (POJOs) representing the application's data.
    -   `services`: Core application logic, process management, and data parsing.
    -   `networkprobe`: Classes related to the network probe functionality.
-   `src/main/resources`: Non-code assets.
    -   `styles/fxml`: FXML files defining the UI layout.
    -   `styles/css`: CSS files for styling the application.
-   `pom.xml`: The Maven Project Object Model file, defining the project's dependencies, plugins, and build profiles.

## 4. Component Deep-Dive

This section breaks down the core classes of the application.

#### 4.1. Controllers (`src/main/java/.../controllers`)

-   **`MainWindowController.java`**: The primary controller for the main application window. It manages the tab pane and orchestrates the initialization of the other controllers.
-   **`NetworkScannerController.java`**: Governs the "Network Scanner" tab. It captures user input for scan configurations (CIDR/IP range, scan type), initiates scans via the `NetworkScannerService`, and populates the results table and visualization with the returned `NetworkHost` data. It also handles the bidirectional communication between the results table and the D3.js map.
-   **`NetworkProbeController.java`**: Manages the "Network Probe" tab. It takes a target hostname/IP, executes a traceroute via the `SystemToolsManager`, and passes the resulting hop data to the `NetworkVisualizationService` to render the network path.
-   **`SystemInformationController.java`**: (Implicitly from `SystemInformation.fxml`) Backs the "System Information" tab. It queries the `SystemToolsManager` to get local network interface and OS details and displays them in the UI.
-   **`SettingsDialogController.java`**: Handles the settings dialog, allowing users to configure application-level settings, such as paths to external tools (`nmap`, `traceroute`). It interacts with the `SettingsService` to persist these settings.

#### 4.2. Services (`src/main/java/.../services`)

-   **`SystemToolsManager.java`**: A critical service acting as an abstraction layer over command-line executables. It provides a unified API to run external processes like `nmap` and `traceroute`, capturing their `stdout` and `stderr` streams, managing timeouts, and returning the results as a `QueryResult` object. This isolates the rest of the application from the complexities of process management.
-   **`NetworkScannerService.java`**: Orchestrates the entire network scanning process. It receives a `ScanConfiguration` from the controller, constructs the appropriate `nmap` command-line arguments, executes the command via `SystemToolsManager`, and then parses the resulting XML output into a list of `NetworkHost` model objects.
-   **`NetworkVisualizationService.java`**: The bridge between the Java backend and the D3.js frontend. It loads the HTML/JS/CSS for the visualizations into a `JavaFX WebView`. Its primary role is to serialize Java model objects (like `List<NetworkHost>`) into a JSON string and pass this data to the JavaScript environment to be rendered by D3.js.
-   **`SettingsService.java`**: Manages the loading and saving of application settings. It handles the `settings.properties` file, providing a simple key-value store for persisting configuration data across application sessions.
-   **`PacketCaptureService.java` & `TcpdumpPacketCaptureService.java`**: Represents the foundation for the future "Packet Analyzer" module. It defines an interface for live packet capture and provides an initial implementation using `tcpdump`.

#### 4.3. Models (`src/main/java/.../models`)

-   **`NetworkHost.java`**: A data class representing a single host discovered on the network. It contains fields for IP address, hostname, MAC address, open ports, OS, and status. This is the primary data structure used by the Network Scanner.
-   **`ScanConfiguration.java`**: A model that holds all the user-selected options for a network scan, such as the target specification (CIDR), scan type (Ping, Port, Full), and other boolean flags (e.g., `resolveHostnames`).
-   **`QueryResult.java`**: A simple record used to encapsulate the result of executing an external command, containing the exit code, standard output, and standard error.
-   **`SystemInfo.java`**: A model for storing details about the local system's OS and network interfaces.

## 5. Data Flow

This section details how data moves through the system for a typical use case.

### 5.1. Use Case: Network Scan Execution

This sequence describes the data flow when a user initiates a "Full Scan" from the Network Scanner tab.

1.  **User Interaction**: The user enters a CIDR range (e.g., `192.168.1.0/24`), selects "Full Scan", and clicks the "Start Scan" button.
2.  **Controller Action (`NetworkScannerController`)**: The `onScanButtonClick()` event handler is triggered. The controller reads the values from the UI input fields and constructs a `ScanConfiguration` object.
3.  **Service Invocation**: The controller calls the `performScan(config)` method on the injected `NetworkScannerService`, passing the configuration object. To provide immediate feedback, the UI is updated to show a "Scanning..." state (e.g., progress indicator is shown, scan button is disabled).
4.  **Command Construction (`NetworkScannerService`)**: The service interprets the `ScanConfiguration` object and builds a valid `nmap` command string (e.g., `nmap -sV -O -oX - 192.168.1.0/24`). The `-oX -` flag is crucial as it directs `nmap` to output the results in XML format to standard output.
5.  **Process Execution (`SystemToolsManager`)**: The `NetworkScannerService` invokes the `SystemToolsManager` to execute the constructed `nmap` command. The manager creates a new `Process`, captures its `stdout` and `stderr` streams, and waits for it to complete. It returns the raw XML output and any errors inside a `QueryResult` object.
6.  **XML Parsing (`NetworkScannerService`)**: The service takes the XML string from the `QueryResult` and parses it. It iterates through the XML nodes corresponding to each host, extracting details like IP address, status, ports, and OS information. For each host, it creates and populates a `NetworkHost` object.
7.  **Return to Controller**: The `NetworkScannerService` completes its `Task` and returns a `List<NetworkHost>` to the `NetworkScannerController`.
8.  **UI Update (`NetworkScannerController`)**: The controller receives the list of hosts.
    a.  It populates the `TableView` with the data, creating a new row for each `NetworkHost`.
    b.  It invokes the `NetworkVisualizationService`, passing it the list of hosts.
9.  **Visualization (`NetworkVisualizationService`)**:
    a.  The service serializes the `List<NetworkHost>` into a JSON array string.
    b.  It calls a JavaScript function inside the `WebView` (e.g., `renderGraph(jsonData)`) via `webEngine.executeScript()`.
10. **D3.js Rendering**: The JavaScript code within the `WebView` receives the JSON data. The D3.js library uses this data to render the interactive network map, creating nodes for each host and applying the selected layout (e.g., force-directed).

## 6. User Interface (UI)

The application's UI is partitioned into several FXML files, each representing a distinct view or component. This separation aligns with the Model-View-Controller (MVC) pattern.

-   **`MainWindow.fxml`**: The main application container. It defines the primary window structure, including the main menu and a `TabPane` that holds the other modules. Its controller is `MainWindowController`.
-   **`SystemInformation.fxml`**: The view for the "System Information" tab. It contains labels and text areas to display the local machine's OS and network interface data.
-   **`NetworkScanner.fxml`**: The view for the "Network Scanner" tab. This is the most complex view, containing input fields for scan configuration, a `TableView` for results, a `WebView` for the D3.js visualization, and controls for interacting with the map. Its controller is `NetworkScannerController`.
-   **`NetworkProbe.fxml`**: The view for the "Network Probe" tab. It includes a text field for the target host, a "Start Probe" button, a `WebView` for the traceroute visualization, and a `TextArea` for raw output. Its controller is `NetworkProbeController`.
-   **`PacketAnalyzer.fxml`**: The view for the future "Packet Analyzer" module. It currently serves as a placeholder.
-   **`SettingsDialog.fxml`**: A dialog window for application settings. It provides fields for users to specify paths to required command-line tools. Its controller is `SettingsDialogController`.
-   **`ToolInstallationDialog.fxml`**: A helper dialog used to guide the user through the process of installing required tools like `nmap` if they are not found on the system.

## 7. Key Architectural Patterns

### 7.1. Dependency Injection (DI)

The application uses Google Guice for dependency injection. This pattern decouples components, making the application easier to test, maintain, and extend. The central configuration is in `di/AppModule.java`, where interfaces are bound to their concrete implementations.

### 7.2. JavaFX-JavaScript Bridge

A critical architectural feature is the communication between the Java backend and the D3.js visualization running inside a `JavaFX WebView`.

-   **Java to JavaScript**: Java calls JavaScript functions using `webEngine.executeScript()`. This is the mechanism by which the `NetworkVisualizationService` passes graph data (as a JSON string) to D3.js for rendering.
-   **JavaScript to Java**: A Java object is exposed to the JavaScript environment via `JSObject.setMember()`. This allows JavaScript event handlers (e.g., `onClick` on a D3 node) to call back into Java methods, enabling features like map-to-table selection synchronization.

## 8. Security Considerations

*(This section is a work in progress.)*

## 9. Build and Deployment

The project is built using Apache Maven. It is configured to produce self-contained native installers for Windows, macOS, and Linux using the `jpackage` tool. For detailed instructions, see the main `README.md` file.

--- 