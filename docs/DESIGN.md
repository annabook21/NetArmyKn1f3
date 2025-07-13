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
    subgraph NetArmyKn1f3 Application
        A[Presentation Layer <br><i>(JavaFX, FXML)</i>]
        B[Service Layer <br><i>(Business Logic)</i>]
        C[Data Models <br><i>(POJOs)</i>]
        D[Visualization Engine <br><i>(JavaFX WebView + D3.js)</i>]
    end

    subgraph External Tools
        E[nmap]
        F[traceroute]
        G[ifconfig / ipconfig]
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

This section will provide a detailed description of each major component in the system.

*(This section is a work in progress.)*

## 5. Data Flow

This section will detail how data moves through the system for a typical use case, like a network scan.

*(This section is a work in progress.)*

## 6. User Interface (UI)

The UI is defined using FXML and styled with CSS. Each major tab or view in the application has a corresponding FXML file and a Controller class.

*(This section is a work in progress.)*

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