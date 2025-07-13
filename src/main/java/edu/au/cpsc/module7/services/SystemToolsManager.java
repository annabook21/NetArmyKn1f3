package edu.au.cpsc.module7.services;

import com.google.inject.Inject;
import edu.au.cpsc.module7.models.QueryResult;
import edu.au.cpsc.module7.models.ScanConfiguration;
import edu.au.cpsc.module7.models.SystemInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.nio.file.attribute.PosixFilePermission;
import javafx.application.Platform;
import javafx.concurrent.Task;

public class SystemToolsManager {
    private static final Logger LOGGER = Logger.getLogger(SystemToolsManager.class.getName());

    private final SettingsService settingsService;
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

    @Inject
    public SystemToolsManager(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.systemInfo = SystemInfo.detect();
        LOGGER.info("Detected system: " + systemInfo.getOsName() + " " + systemInfo.getArchitecture());
        // Initialize tool paths and check availability
        checkToolAvailability();
    }

    public SystemInfo getSystemInfo() {
        return systemInfo;
    }

    public Map<String, Boolean> checkToolAvailability() {
        toolAvailability.clear();
        checkTool("nmap", settingsService.getNmapPath());
        checkTool("traceroute", getTracerouteCommand());
        checkTool("mtr", settingsService.getMtrPath());
        checkTool("hping3", settingsService.getHping3Path());
        
        return new HashMap<>(toolAvailability);
    }

    private boolean checkToolDirect(String toolName, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        
        try {
            // First, try to check if the command exists using 'which' or 'where'
            String checkCommand = systemInfo.getOsName().toLowerCase().contains("windows") ? "where" : "which";
            Process checkProcess = new ProcessBuilder(checkCommand, path).start();
            boolean commandExists = checkProcess.waitFor(3, TimeUnit.SECONDS) && checkProcess.exitValue() == 0;
            
            if (!commandExists) {
                // If 'which'/'where' fails, try direct execution
                try {
                    Process testProcess = new ProcessBuilder(path).start();
                    testProcess.destroyForcibly(); // Kill immediately
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
            
            // Command exists, now try version check
            String[] versionCommand = new String[]{path, "--version"};
            
            Process process = new ProcessBuilder(versionCommand).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (finished) {
                // Many tools return 0 for --version, but some return 1. Accept both.
                int exitCode = process.exitValue();
                return exitCode == 0 || exitCode == 1;
            } else {
                // Timeout - assume tool exists but is hanging
                process.destroyForcibly();
                return true;
            }
            
        } catch (IOException | InterruptedException e) {
            // Final fallback: try basic execution
            try {
                Process process = new ProcessBuilder(path).start();
                process.destroyForcibly();
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    private String getTracerouteCommand() {
        String os = systemInfo.getOsName().toLowerCase();
        if (os.contains("windows")) {
            return "tracert"; // Windows uses 'tracert' instead of 'traceroute'
        } else {
            return settingsService.getTraceroutePath();
        }
    }

    private void checkTool(String toolName, String path) {
        if (path == null || path.isBlank()) {
            toolAvailability.put(toolName, false);
            return;
        }
        
        try {
            // First, try to check if the command exists using 'which' or 'where'
            String checkCommand = systemInfo.getOsName().toLowerCase().contains("windows") ? "where" : "which";
            Process checkProcess = new ProcessBuilder(checkCommand, path).start();
            boolean commandExists = checkProcess.waitFor(3, TimeUnit.SECONDS) && checkProcess.exitValue() == 0;
            
            if (!commandExists) {
                // If 'which'/'where' fails, try direct execution
                try {
                    Process testProcess = new ProcessBuilder(path).start();
                    testProcess.destroyForcibly(); // Kill immediately
                    toolAvailability.put(toolName, true);
                    return;
                } catch (IOException e) {
                    toolAvailability.put(toolName, false);
                    return;
                }
            }
            
            // Command exists, now try version check
            String[] versionCommand;
            if (toolName.equals("traceroute") && systemInfo.getOsName().toLowerCase().contains("windows")) {
                versionCommand = new String[]{path, "/?"};
            } else if (toolName.equals("nmap")) {
                versionCommand = new String[]{path, "--version"};
            } else if (toolName.equals("mtr")) {
                versionCommand = new String[]{path, "--version"};
            } else if (toolName.equals("hping3")) {
                versionCommand = new String[]{path, "--version"};
            } else {
                versionCommand = new String[]{path, "--version"};
            }
            
            Process process = new ProcessBuilder(versionCommand).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (finished) {
                // Many tools return 0 for --version, but some return 1. Accept both.
                int exitCode = process.exitValue();
                toolAvailability.put(toolName, exitCode == 0 || exitCode == 1);
            } else {
                // Timeout - assume tool exists but is hanging
                process.destroyForcibly();
                toolAvailability.put(toolName, true);
            }
            
        } catch (IOException | InterruptedException e) {
            // Final fallback: try basic execution
            try {
                Process process = new ProcessBuilder(path).start();
                process.destroyForcibly();
                toolAvailability.put(toolName, true);
            } catch (IOException ex) {
                toolAvailability.put(toolName, false);
            }
        }
    }

    public List<String> getMissingTools() {
        return toolAvailability.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public boolean hasHping3Alternative() {
        // Check if nping is available as hping3 alternative on macOS
        if (systemInfo.getOsName().toLowerCase().contains("mac")) {
            return checkToolDirect("nping", "nping");
        }
        return false;
    }

    public Task<Boolean> createInstallationTask(List<String> toolsToInstall, boolean usePortable) {
        return new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                updateMessage("Starting installation...");
                long totalSteps = toolsToInstall.size();
                long workDone = 0;
                StringBuilder fullLog = new StringBuilder();
                int successfulInstalls = 0;
                int manualInstalls = 0;
                int failedInstalls = 0;

                for (String tool : toolsToInstall) {
                    if (isCancelled()) {
                        fullLog.append("Installation cancelled.");
                        updateMessage(fullLog.toString());
                        return false;
                    }

                    updateProgress(workDone, totalSteps);
                    fullLog.append("Installing ").append(tool).append("...\n");
                    updateMessage(fullLog.toString());

                    String installCommand = getInstallCommand(tool);
                    if (installCommand == null) {
                        fullLog.append("No installation command found for ").append(tool).append(" on ").append(systemInfo.getOsName()).append("\n");
                        updateMessage(fullLog.toString());
                        failedInstalls++;
                        workDone++;
                        continue;
                    }

                    if (installCommand.isEmpty()) {
                        if (tool.equals("traceroute")) {
                            fullLog.append(tool).append(" is built into the system, no installation needed.\n");
                            successfulInstalls++;
                        } else {
                            fullLog.append(tool).append(" installation not available via package manager on this system.\n");
                            manualInstalls++;
                        }
                        updateMessage(fullLog.toString());
                        workDone++;
                        continue;
                    }

                    try {
                        ProcessBuilder pb = new ProcessBuilder(installCommand.split(" "));
                        Process process = pb.start();

                        // Capture output for logging
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                fullLog.append(line).append("\n");
                                updateMessage(fullLog.toString());
                            }
                        }
                        
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                fullLog.append("ERROR: ").append(line).append("\n");
                                updateMessage(fullLog.toString());
                            }
                        }

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            fullLog.append("Failed to install ").append(tool).append(". Exit code: ").append(exitCode).append("\n");
                            failedInstalls++;
                        } else {
                            fullLog.append(tool).append(" installed successfully.\n");
                            successfulInstalls++;
                        }
                        updateMessage(fullLog.toString());

                    } catch (IOException | InterruptedException e) {
                        fullLog.append("Error installing ").append(tool).append(": ").append(e.getMessage()).append("\n");
                        updateMessage(fullLog.toString());
                        failedInstalls++;
                    }
                    workDone++;
                }

                updateProgress(totalSteps, totalSteps);
                
                // Provide accurate summary
                fullLog.append("\n=== Installation Summary ===\n");
                fullLog.append("Successfully installed: ").append(successfulInstalls).append(" tools\n");
                if (manualInstalls > 0) {
                    fullLog.append("Require manual installation: ").append(manualInstalls).append(" tools\n");
                }
                if (failedInstalls > 0) {
                    fullLog.append("Failed to install: ").append(failedInstalls).append(" tools\n");
                }
                
                if (successfulInstalls > 0) {
                    fullLog.append("\nPlease restart the application to refresh tool status.\n");
                }
                
                updateMessage(fullLog.toString());
                
                // Return true only if we actually installed something automatically
                return successfulInstalls > 0;
            }
        };
    }

    private String getInstallCommand(String tool) {
        String os = systemInfo.getOsName().toLowerCase();
        
        if (os.contains("mac") || os.contains("darwin")) {
            Map<String, String> macCommands = INSTALL_COMMANDS.get("mac");
            String command = macCommands.get(tool);
            
            // Special case for hping3 on macOS - use third-party tap that actually works
            if (tool.equals("hping3")) {
                return "brew install draftbrew/tap/hping";
            }
            
            return command;
        } else if (os.contains("windows")) {
            // Windows doesn't have a built-in package manager, suggest manual installation
            return getWindowsInstallCommand(tool);
        } else if (os.contains("linux") || os.contains("unix")) {
            // Try to detect Linux distribution
            return getLinuxInstallCommand(tool);
        }
        
        return null; // Unsupported OS
    }
    
    private String getWindowsInstallCommand(String tool) {
        // For Windows, we can suggest Chocolatey, Scoop, or manual installation
        switch (tool) {
            case "nmap":
                return "choco install nmap"; // Or suggest manual download
            case "traceroute":
                return ""; // Built into Windows as 'tracert'
            case "mtr":
                return "choco install winmtr"; // WinMTR is the Windows equivalent
            case "hping3":
                return "choco install hping3"; // May not be available, suggest manual
            default:
                return null;
        }
    }
    
    private String getLinuxInstallCommand(String tool) {
        // Try to detect the Linux distribution
        try {
            // Check if we can read /etc/os-release
            if (java.nio.file.Files.exists(java.nio.file.Paths.get("/etc/os-release"))) {
                String osRelease = java.nio.file.Files.readString(java.nio.file.Paths.get("/etc/os-release"));
                
                if (osRelease.contains("ubuntu") || osRelease.contains("debian")) {
                    return INSTALL_COMMANDS.get("debian").get(tool);
                } else if (osRelease.contains("fedora") || osRelease.contains("rhel") || osRelease.contains("centos")) {
                    return INSTALL_COMMANDS.get("fedora").get(tool);
                } else if (osRelease.contains("arch")) {
                    return getArchInstallCommand(tool);
                } else if (osRelease.contains("alpine")) {
                    return getAlpineInstallCommand(tool);
                }
            }
            
            // Fallback: try to detect package manager by checking if commands exist
            if (commandExists("apt-get")) {
                return INSTALL_COMMANDS.get("debian").get(tool);
            } else if (commandExists("dnf")) {
                return INSTALL_COMMANDS.get("fedora").get(tool);
            } else if (commandExists("yum")) {
                return getYumInstallCommand(tool);
            } else if (commandExists("pacman")) {
                return getArchInstallCommand(tool);
            } else if (commandExists("apk")) {
                return getAlpineInstallCommand(tool);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not detect Linux distribution", e);
        }
        
        // Default to debian commands as a fallback
        return INSTALL_COMMANDS.get("debian").get(tool);
    }
    
    private String getArchInstallCommand(String tool) {
        switch (tool) {
            case "nmap": return "sudo pacman -S nmap";
            case "traceroute": return "sudo pacman -S traceroute";
            case "mtr": return "sudo pacman -S mtr";
            case "hping3": return "sudo pacman -S hping";
            default: return null;
        }
    }
    
    private String getAlpineInstallCommand(String tool) {
        switch (tool) {
            case "nmap": return "sudo apk add nmap";
            case "traceroute": return "sudo apk add traceroute";
            case "mtr": return "sudo apk add mtr";
            case "hping3": return "sudo apk add hping3";
            default: return null;
        }
    }
    
    private String getYumInstallCommand(String tool) {
        switch (tool) {
            case "nmap": return "sudo yum install -y nmap";
            case "traceroute": return "sudo yum install -y traceroute";
            case "mtr": return "sudo yum install -y mtr";
            case "hping3": return "sudo yum install -y hping3";
            default: return null;
        }
    }
    
    private boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("which", command).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public QueryResult executeTraceroute(String host) {
        String command = getTracerouteCommand();
        return executeCommand(command, host);
    }

    public QueryResult executeMtr(String host) {
        // Use -j for JSON output, -c 1 to send one packet per hop
        String mtrPath = settingsService.getMtrPath();
        return executeCommand(mtrPath, "-j", "-c", "1", host);
    }

    public QueryResult executeHping3(List<String> args) {
        String hping3Path = settingsService.getHping3Path();
        
        // On macOS, if hping3 is not available, try using nping as alternative
        if (systemInfo.getOsName().toLowerCase().contains("mac")) {
            try {
                Process checkProcess = new ProcessBuilder("which", hping3Path).start();
                boolean hping3Exists = checkProcess.waitFor(3, TimeUnit.SECONDS) && checkProcess.exitValue() == 0;
                
                if (!hping3Exists) {
                    // Try using nping instead
                    Process checkNping = new ProcessBuilder("which", "nping").start();
                    boolean npingExists = checkNping.waitFor(3, TimeUnit.SECONDS) && checkNping.exitValue() == 0;
                    
                    if (npingExists) {
                        LOGGER.info("Using nping as hping3 alternative on macOS");
                        return executeNpingAsHping3Alternative(args);
                    }
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Error checking for hping3/nping availability", e);
            }
        }
        
        // Standard hping3 execution
        List<String> command = new ArrayList<>();
        command.add(hping3Path);
        command.addAll(args);
        return executeCommand(command.toArray(new String[0]));
    }
    
    private QueryResult executeNpingAsHping3Alternative(List<String> hping3Args) {
        List<String> npingArgs = new ArrayList<>();
        npingArgs.add("nping");
        
        // Convert common hping3 arguments to nping equivalents
        for (int i = 0; i < hping3Args.size(); i++) {
            String arg = hping3Args.get(i);
            switch (arg) {
                case "--syn":
                    npingArgs.add("--tcp");
                    npingArgs.add("--flags");
                    npingArgs.add("syn");
                    break;
                case "--ack":
                    npingArgs.add("--tcp");
                    npingArgs.add("--flags");
                    npingArgs.add("ack");
                    break;
                case "--port":
                    npingArgs.add("-p");
                    if (i + 1 < hping3Args.size()) {
                        npingArgs.add(hping3Args.get(++i));
                    }
                    break;
                case "--count":
                    npingArgs.add("-c");
                    if (i + 1 < hping3Args.size()) {
                        npingArgs.add(hping3Args.get(++i));
                    }
                    break;
                default:
                    // Pass through other arguments
                    npingArgs.add(arg);
                    break;
            }
        }
        
        return executeCommand(npingArgs.toArray(new String[0]));
    }

    public QueryResult executeNmap(ScanConfiguration config) {
        // Nmap path should be configurable in a real app
        String nmapPath = "nmap"; // Placeholder
        List<String> command = new ArrayList<>();
        command.add(nmapPath);
        // ... (rest of nmap command construction logic)
        command.add(config.getTargetRange());
        return executeCommand(command.toArray(new String[0]));
    }

    public QueryResult executeIfconfig() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return executeCommand("ipconfig");
        } else {
            return executeCommand("ifconfig");
        }
    }

    public String getToolPath(String tool) {
        return toolPaths.getOrDefault(tool, tool); // Fallback to tool name in PATH
    }


    private static void initializeInstallCommands() {
        // macOS with Homebrew
        Map<String, String> macCommands = new HashMap<>();
        macCommands.put("nmap", "brew install nmap");
        macCommands.put("traceroute", ""); // traceroute is built into macOS
        macCommands.put("mtr", "brew install mtr");
        macCommands.put("hping3", ""); // hping3 is not available in Homebrew, suggest manual installation
        INSTALL_COMMANDS.put("mac", macCommands);

        // Debian/Ubuntu
        Map<String, String> debianCommands = new HashMap<>();
        debianCommands.put("nmap", "sudo apt-get install -y nmap");
        debianCommands.put("traceroute", "sudo apt-get install -y traceroute");
        debianCommands.put("mtr", "sudo apt-get install -y mtr");
        debianCommands.put("hping3", "sudo apt-get install -y hping3");
        INSTALL_COMMANDS.put("debian", debianCommands);

        // Fedora/CentOS/RHEL
        Map<String, String> fedoraCommands = new HashMap<>();
        fedoraCommands.put("nmap", "sudo dnf install -y nmap");
        fedoraCommands.put("traceroute", "sudo dnf install -y traceroute");
        fedoraCommands.put("mtr", "sudo dnf install -y mtr");
        fedoraCommands.put("hping3", "sudo dnf install -y hping3");
        INSTALL_COMMANDS.put("fedora", fedoraCommands);
    }

    private static void initializePortableToolsUrls() {
        PORTABLE_TOOLS_URLS.put("nmap_windows", "https://nmap.org/download.html");
        // Add other portable tool URLs as needed
    }

    private QueryResult executeCommand(String... command) {
        QueryResult queryResult = new QueryResult(String.join(" ", command), "");
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            // Readers for stdout and stderr
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Read output
            StringBuilder output = new StringBuilder();
            String s;
            while ((s = stdInput.readLine()) != null) {
                output.append(s).append("\n");
            }

            // Read errors
            StringBuilder errorOutput = new StringBuilder();
            while ((s = stdError.readLine()) != null) {
                errorOutput.append(s).append("\n");
            }

            boolean finished = process.waitFor(settingsService.getTimeoutSeconds(), TimeUnit.SECONDS);

            queryResult.setExitCode(process.exitValue());
            queryResult.setOutput(output.toString().trim());
            queryResult.setErrorOutput(errorOutput.toString().trim());


            if (!finished) {
                process.destroyForcibly();
                queryResult.setErrorOutput("Command timed out after " + settingsService.getTimeoutSeconds() + " seconds.");
                queryResult.setSuccess(false);
            } else {
                queryResult.setSuccess(process.exitValue() == 0);
            }

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing command: " + String.join(" ", command), e);
            queryResult.setErrorOutput(e.getMessage());
            queryResult.setSuccess(false);
        }
        return queryResult;
    }
}