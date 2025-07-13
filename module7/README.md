# NetArmyKn1f3 - Network Analysis & Monitoring Tool

NetArmyKn1f3 is a comprehensive JavaFX-based desktop application for network analysis, scanning, and visualization. It provides a suite of tools to help network administrators and security enthusiasts discover hosts, scan ports, and visualize network topologies in an intuitive graphical interface.

![Main Window](docs/main-window.png)

---

## Features

The application is organized into three main tabs, each providing a distinct set of networking tools.

### 1. System Information

This tab provides a quick, at-a-glance overview of your local system's network configuration.

- **Network Interface Details:** Lists all available network interfaces (e.g., `en0`, `lo0`) along with their assigned IP addresses (both IPv4 and IPv6) and MAC addresses.
- **System Information:** Displays key details about your operating system, including its name, version, and architecture.

### 2. Network Probe

The Network Probe is designed for analyzing a single, specific target, which can be a public website (like `github.com`) or an IP address (like `8.8.8.8`). It is the primary tool for visualizing the path your traffic takes across the internet.

- **Single-Target Analysis:** Perform detailed lookups on a single host.
- **Traceroute Execution:** Automatically performs a `traceroute` to the target destination to identify the intermediate routers (hops) along the path.
- **Dual-View Results:**
    - **Raw Results Tab:** Displays the raw, command-line output from the probe and traceroute for expert analysis.
    - **Topology Map Tab:** Presents a rich, interactive visualization of the traceroute path.

#### Understanding the Probe's Topology Map

When you probe a public target, this map shows:
- **Your Gateway:** The red node representing your local network's gateway.
- **Intermediate Hops:** A series of blue nodes, each representing a router or server that your connection travels through to reach the destination.
- **The Final Host:** The green node representing the public server you are probing.
- **The Path:** Lines connect the nodes to illustrate the exact path of your connection across the internet. The map uses a **Force-Directed Layout** to organize the nodes in an organic, easy-to-understand structure.

### 3. Network Scanner

The Network Scanner is a powerful tool designed for discovering and mapping devices on your **local network**.

- **Flexible Target Ranges:** Scan your network using CIDR notation (e.g., `192.168.1.0/24`) or a specific IP range (e.g., `192.168.1.100-150`).
- **Multiple Scan Types:**
    - **Ping Sweep:** A quick scan to discover which hosts are online.
    - **Port Scan:** Scans a list of common ports on specified hosts.
    - **Full Scan:** A comprehensive analysis that combines host discovery, port scanning, service detection, and OS fingerprinting.
- **Advanced Scanning Options:**
    - **Resolve Hostnames:** Attempts to find the hostname for discovered IPs.
    - **Detect Services:** Identifies the services running on open ports (e.g., HTTP, SSH).
    - **Detect Operating System:** Provides a best-guess estimate of the host's OS.
    - **Perform Traceroute:** Maps the path to hosts on the local network. (Note: This is most effective when scanning across different subnets).

#### Understanding the Scanner's Network Map

This map provides a bird's-eye view of your local network scan results.

- **Interactive Table and Map:** The map is directly linked to the "Host Results" table.
    - Click a node on the map to instantly select and scroll to that host in the table.
    - Click a row in the table to highlight the corresponding node on the map.
- **Switchable Layouts:** Use the "Layout" dropdown menu to choose how the map is organized:
    - **Ring Layout:** Arranges hosts in clean, concentric circles around your gateway. Ideal for getting an organized overview of a large number of devices.
    - **Force Layout:** Uses a physics-based simulation where nodes repel each other. This often reveals the natural clustering of devices on the network.

---

## How to Build and Run

The project is managed with Maven.

1.  **Prerequisites:**
    -   Java JDK 17 or higher.
    -   Apache Maven.

2.  **Build and Run:**
    -   Open a terminal in the root directory of the project.
    -   Run the following command to clean the project, compile the code, and start the application:
    ```sh
    mvn clean javafx:run
    ```
