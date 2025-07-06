package edu.au.cpsc.module7.services;

import com.yourname.alwaysdns.models.QueryType;
import com.yourname.alwaysdns.models.SystemInfo;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages system DNS tools - detection, installation, and validation
 * Provides automatic installation capabilities for missing tools
 */
public class SystemToolsManager {
    private static final Logger LOGGER = Logger.getLogger(SystemToolsManager.class.getName());

    private final SystemInfo systemInfo;
    private final Map<String, Boolean> toolAvailability = new HashMap<>();
    private final Map<String, String> toolPaths = new HashMap<>();

    // Tool installation URLs and commands by platform
    private static final Map<String, Map<String, String>> INSTALL_COMMANDS = new HashMap<>();
    private static final Map<String, String> PORTABLE_TOOLS_URLS = new HashMap<>();

    static {
        // Initialize installation commands for different platforms
        initializeInstallCommands();
        initializePortableToolsUrls();
    }

    public SystemToolsManager() {
        this.systemInfo = SystemInfo.detect();
        LOGGER.info("Detected system: " + systemInfo.getOsName() + " " + systemInfo.getArchitecture());
    }

    /**
     * Checks availability of all required DNS tools
     * @return Map of tool names to availability status
     */
    public Map<String, Boolean> checkToolAvailability() {
        toolAvailability.clear();
        toolPaths.clear();

        for (QueryType queryType : QueryType.values()) {
            String toolName = queryType.getCommand();
            boolean available = isToolAvailable(toolName);
            toolAvailability.put(toolName, available);

            if (available) {
                toolPaths.put(toolName, getToolPath(toolName));
            }

            LOGGER.info(String.format("Tool '%s': %s", toolName, available ? "Available" : "Missing"));
        }

        return new HashMap<>(toolAvailability);
    }

    /**
     * Gets list of missing tools
     * @return List of missing tool names
     */
    public List<String> getMissingTools() {
        return toolAvailability.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Creates a task to automatically install missing tools
     * @param missingTools List of tools to install
     * @param usePortable Whether to use portable versions when possible
     * @return Installation task
     */
    public Task<Boolean> createInstallationTask(List<String> missingTools, boolean usePortable) {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                updateMessage("Preparing to install missing DNS tools...");
                updateProgress(0, missingTools.size());

                boolean allSuccess = true;
                int completed = 0;

                for (String tool : missingTools) {
                    if (isCancelled()) {
                        break;
                    }

                    updateMessage(String.format("Installing %s...", tool));

                    try {
                        boolean success;
                        if (usePortable && supportsPortableInstall(tool)) {
                            success = installPortableTool(tool);
                        } else {
                            success = installSystemTool(tool);
                        }

                        if (!success) {
                            allSuccess = false;
                            updateMessage(getMessage() + String.format("\nFailed to install %s", tool));
                        } else {
                            updateMessage(getMessage() + String.format("\n✓ %s installed successfully", tool));
                        }

                    } catch (Exception e) {
                        allSuccess = false;
                        String errorMsg = String.format("Error installing %s: %s", tool, e.getMessage());
                        updateMessage(getMessage() + "\n" + errorMsg);
                        LOGGER.log(Level.WARNING, errorMsg, e);
                    }

                    updateProgress(++completed, missingTools.size());
                }

                if (!isCancelled()) {
                    updateMessage(getMessage() + "\n\nInstallation process completed.");

                    // Re-check tool availability
                    Platform.runLater(() -> {
                        checkToolAvailability();
                    });
                }

                return allSuccess;
            }
        };
    }

    /**
     * Installs a tool using the system package manager
     */
    private boolean installSystemTool(String tool) throws Exception {
        Map<String, String> platformCommands = INSTALL_COMMANDS.get(systemInfo.getOsName().toLowerCase());
        if (platformCommands == null) {
            throw new UnsupportedOperationException("Automatic installation not supported on " + systemInfo.getOsName());
        }

        String installCommand = platformCommands.get(tool);
        if (installCommand == null) {
            throw new IllegalArgumentException("No installation command found for tool: " + tool);
        }

        // Check if we need sudo/admin privileges
        boolean needsElevation = needsElevatedPrivileges();
        if (needsElevation && !hasElevatedPrivileges()) {
            return requestElevatedInstallation(tool, installCommand);
        }

        return executeInstallCommand(installCommand);
    }

    /**
     * Installs a portable version of the tool
     */
    private boolean installPortableTool(String tool) throws Exception {
        String downloadUrl = PORTABLE_TOOLS_URLS.get(tool + "_" + systemInfo.getOsName().toLowerCase());
        if (downloadUrl == null) {
            throw new UnsupportedOperationException("Portable version not available for " + tool + " on " + systemInfo.getOsName());
        }

        // Create portable tools directory
        Path portableDir = getPortableToolsDirectory();
        Files.createDirectories(portableDir);

        // Download and extract tool
        Path toolPath = downloadAndExtractTool(downloadUrl, tool, portableDir);

        // Make executable (Unix-like systems)
        if (!systemInfo.isWindows()) {
            Files.setPosixFilePermissions(toolPath,
                    Set.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        }

        // Update tool paths
        toolPaths.put(tool, toolPath.toString());

        return true;
    }

    /**
     * Downloads and extracts a portable tool
     */
    private Path downloadAndExtractTool(String downloadUrl, String toolName, Path targetDir) throws Exception {
        LOGGER.info("Downloading " + toolName + " from " + downloadUrl);

        // Download file
        Path downloadPath = targetDir.resolve(toolName + "_download");
        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, downloadPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Extract if needed (for zip/tar files)
        Path toolPath = targetDir.resolve(toolName + (systemInfo.isWindows() ? ".exe" : ""));

        if (downloadUrl.endsWith(".zip")) {
            extractZip(downloadPath, toolPath);
        } else if (downloadUrl.endsWith(".tar.gz") || downloadUrl.endsWith(".tgz")) {
            extractTarGz(downloadPath, toolPath);
        } else {
            // Direct binary
            Files.move(downloadPath, toolPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Clean up download file
        Files.deleteIfExists(downloadPath);

        return toolPath;
    }

    /**
     * Executes an installation command
     */
    private boolean executeInstallCommand(String command) throws Exception {
        LOGGER.info("Executing install command: " + command);

        ProcessBuilder pb = new ProcessBuilder();

        if (systemInfo.isWindows()) {
            pb.command("cmd", "/c", command);
        } else {
            pb.command("sh", "-c", command);
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("Install output: " + line);
            }
        }

        boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5 minute timeout
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Installation command timed out");
        }

        return process.exitValue() == 0;
    }

    /**
     * Requests elevated installation through system dialogs
     */
    private boolean requestElevatedInstallation(String tool, String command) throws Exception {
        if (systemInfo.isWindows()) {
            return requestWindowsElevation(tool, command);
        } else if (systemInfo.isMacOS()) {
            return requestMacOSElevation(tool, command);
        } else {
            return requestLinuxElevation(tool, command);
        }
    }

    private boolean requestWindowsElevation(String tool, String command) throws Exception {
        // Create a PowerShell script for elevation
        Path scriptPath = Files.createTempFile("install_" + tool, ".ps1");
        try {
            String script = String.format(
                    "Start-Process -FilePath 'cmd' -ArgumentList '/c', '%s' -Verb RunAs -Wait",
                    command.replace("'", "''")
            );
            Files.write(scriptPath, script.getBytes());

            ProcessBuilder pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toString());
            Process process = pb.start();

            return process.waitFor() == 0;
        } finally {
            Files.deleteIfExists(scriptPath);
        }
    }

    private boolean requestMacOSElevation(String tool, String command) throws Exception {
        // Use osascript for privilege escalation
        String script = String.format(
                "do shell script \"%s\" with administrator privileges",
                command.replace("\"", "\\\"")
        );

        ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
        Process process = pb.start();

        return process.waitFor() == 0;
    }

    private boolean requestLinuxElevation(String tool, String command) throws Exception {
        // Try different elevation methods
        String[] elevationCommands = {"pkexec", "gksudo", "kdesudo"};

        for (String elevationCmd : elevationCommands) {
            if (isToolAvailable(elevationCmd)) {
                ProcessBuilder pb = new ProcessBuilder(elevationCmd, "sh", "-c", command);
                Process process = pb.start();

                if (process.waitFor() == 0) {
                    return true;
                }
            }
        }

        // Fallback to terminal sudo
        ProcessBuilder pb = new ProcessBuilder("x-terminal-emulator", "-e", "sudo " + command);
        Process process = pb.start();

        return process.waitFor() == 0;
    }

    // Utility methods
    private boolean isToolAvailable(String toolName) {
        try {
            ProcessBuilder pb = new ProcessBuilder();

            if (systemInfo.isWindows()) {
                pb.command("where", toolName);
            } else {
                pb.command("which", toolName);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking tool availability: " + toolName, e);
            return false;
        }
    }

    private String getToolPath(String toolName) {
        // Check if we have a custom path (e.g., portable installation)
        if (toolPaths.containsKey(toolName)) {
            return toolPaths.get(toolName);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();

            if (systemInfo.isWindows()) {
                pb.command("where", toolName);
            } else {
                pb.command("which", toolName);
            }

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String path = reader.readLine();
                if (path != null && !path.trim().isEmpty()) {
                    return path.trim();
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting tool path: " + toolName, e);
        }

        return toolName; // Fallback to tool name
    }

    private boolean supportsPortableInstall(String tool) {
        String key = tool + "_" + systemInfo.getOsName().toLowerCase();
        return PORTABLE_TOOLS_URLS.containsKey(key);
    }

    private boolean needsElevatedPrivileges() {
        return !systemInfo.isWindows() || !hasWriteAccessToSystemDirs();
    }

    private boolean hasElevatedPrivileges() {
        if (systemInfo.isWindows()) {
            // Check if running as administrator
            try {
                ProcessBuilder pb = new ProcessBuilder("net", "session");
                Process process = pb.start();
                return process.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        } else {
            // Check if running as root
            return System.getProperty("user.name").equals("root");
        }
    }

    private boolean hasWriteAccessToSystemDirs() {
        try {
            Path systemDir = systemInfo.isWindows() ?
                    Paths.get("C:\\Windows\\System32") :
                    Paths.get("/usr/local/bin");

            return Files.isWritable(systemDir);
        } catch (Exception e) {
            return false;
        }
    }

    private Path getPortableToolsDirectory() {
        Path homeDir = Paths.get(System.getProperty("user.home"));
        return homeDir.resolve(".alwaysdns").resolve("tools");
    }

    // Helper methods for extraction
    private void extractZip(Path zipFile, Path targetFile) throws Exception {
        // Implement ZIP extraction logic
        // This is a simplified version - you might want to use a library like Apache Commons Compress
        throw new UnsupportedOperationException("ZIP extraction not yet implemented");
    }

    private void extractTarGz(Path tarGzFile, Path targetFile) throws Exception {
        // Implement TAR.GZ extraction logic
        throw new UnsupportedOperationException("TAR.GZ extraction not yet implemented");
    }

    // Static initialization methods
    private static void initializeInstallCommands() {
        // Windows (using Chocolatey, Scoop, or manual downloads)
        Map<String, String> windowsCommands = new HashMap<>();
        windowsCommands.put("dig", "choco install bind-toolsonly -y");
        windowsCommands.put("nslookup", ""); // Usually pre-installed
        windowsCommands.put("whois", "choco install whois -y");
        windowsCommands.put("host", "choco install bind-toolsonly -y");
        INSTALL_COMMANDS.put("windows", windowsCommands);

        // macOS (using Homebrew)
        Map<String, String> macCommands = new HashMap<>();
        macCommands.put("dig", "brew install bind");
        macCommands.put("nslookup", "brew install bind");
        macCommands.put("whois", "brew install whois");
        macCommands.put("host", "brew install bind");
        INSTALL_COMMANDS.put("macos", macCommands);

        // Ubuntu/Debian
        Map<String, String> ubuntuCommands = new HashMap<>();
        ubuntuCommands.put("dig", "apt-get update && apt-get install -y dnsutils");
        ubuntuCommands.put("nslookup", "apt-get update && apt-get install -y dnsutils");
        ubuntuCommands.put("whois", "apt-get update && apt-get install -y whois");
        ubuntuCommands.put("host", "apt-get update && apt-get install -y dnsutils");
        INSTALL_COMMANDS.put("ubuntu", ubuntuCommands);
        INSTALL_COMMANDS.put("debian", ubuntuCommands);

        // CentOS/RHEL/Fedora
        Map<String, String> rhelCommands = new HashMap<>();
        rhelCommands.put("dig", "yum install -y bind-utils || dnf install -y bind-utils");
        rhelCommands.put("nslookup", "yum install -y bind-utils || dnf install -y bind-utils");
        rhelCommands.put("whois", "yum install -y whois || dnf install -y whois");
        rhelCommands.put("host", "yum install -y bind-utils || dnf install -y bind-utils");
        INSTALL_COMMANDS.put("centos", rhelCommands);
        INSTALL_COMMANDS.put("rhel", rhelCommands);
        INSTALL_COMMANDS.put("fedora", rhelCommands);
    }

    private static void initializePortableToolsUrls() {
        // These would be real URLs to portable versions of the tools
        // For demonstration purposes, these are placeholder URLs
        PORTABLE_TOOLS_URLS.put("dig_windows", "https://downloads.isc.org/isc/bind9/9.18.0/BIND9.18.0.x64.zip");
        PORTABLE_TOOLS_URLS.put("whois_windows", "https://download.sysinternals.com/files/WhoIs.zip");

        // Note: In a real implementation, you would need to:
        // 1. Host your own portable binaries or use official distribution URLs
        // 2. Implement proper extraction logic for different archive formats
        // 3. Handle different architectures (x86, x64, ARM)
        // 4. Verify checksums for security
    }

    public Map<String, String> getToolPaths() {
        return new HashMap<>(toolPaths);
    }

    public SystemInfo getSystemInfo() {
        return systemInfo;
    }
}