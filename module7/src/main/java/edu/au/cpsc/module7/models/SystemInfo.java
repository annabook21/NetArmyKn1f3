package edu.au.cpsc.module7.models;

/**
 * System information detection and platform utilities
 */
public class SystemInfo {
    private final String osName;
    private final String osVersion;
    private final String architecture;
    private final boolean isWindows;
    private final boolean isMacOS;
    private final boolean isLinux;
    private final String distribution;

    private SystemInfo(String osName, String osVersion, String architecture, String distribution) {
        this.osName = osName;
        this.osVersion = osVersion;
        this.architecture = architecture;
        this.distribution = distribution;

        String osLower = osName.toLowerCase();
        this.isWindows = osLower.contains("windows");
        this.isMacOS = osLower.contains("mac") || osLower.contains("darwin");
        this.isLinux = osLower.contains("linux");
    }

    /**
     * Detects current system information
     */
    public static SystemInfo detect() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String architecture = System.getProperty("os.arch");
        String distribution = detectLinuxDistribution();

        return new SystemInfo(osName, osVersion, architecture, distribution);
    }

    /**
     * Detects Linux distribution
     */
    private static String detectLinuxDistribution() {
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            return null;
        }

        // Try to read /etc/os-release
        try {
            java.nio.file.Path osRelease = java.nio.file.Paths.get("/etc/os-release");
            if (java.nio.file.Files.exists(osRelease)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(osRelease);
                for (String line : lines) {
                    if (line.startsWith("ID=")) {
                        return line.substring(3).replace("\"", "").toLowerCase();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and try other methods
        }

        // Try other common distribution detection methods
        String[] releaseFiles = {
                "/etc/redhat-release",
                "/etc/debian_version",
                "/etc/ubuntu-release",
                "/etc/fedora-release",
                "/etc/centos-release"
        };

        for (String file : releaseFiles) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(file);
                if (java.nio.file.Files.exists(path)) {
                    String content = java.nio.file.Files.readString(path).toLowerCase();
                    if (content.contains("ubuntu")) return "ubuntu";
                    if (content.contains("debian")) return "debian";
                    if (content.contains("fedora")) return "fedora";
                    if (content.contains("centos")) return "centos";
                    if (content.contains("red hat")) return "rhel";
                }
            } catch (Exception e) {
                // Continue trying other files
            }
        }

        return "unknown";
    }

    /**
     * Gets the package manager command for this system
     */
    public String getPackageManager() {
        if (isWindows) {
            // Check for common Windows package managers
            if (isCommandAvailable("choco")) return "choco";
            if (isCommandAvailable("scoop")) return "scoop";
            if (isCommandAvailable("winget")) return "winget";
            return null;
        }

        if (isMacOS) {
            if (isCommandAvailable("brew")) return "brew";
            if (isCommandAvailable("port")) return "port";
            return null;
        }

        if (isLinux) {
            switch (distribution) {
                case "ubuntu":
                case "debian":
                    return "apt";
                case "fedora":
                    return "dnf";
                case "centos":
                case "rhel":
                    if (isCommandAvailable("dnf")) return "dnf";
                    if (isCommandAvailable("yum")) return "yum";
                    return null;
                case "arch":
                    return "pacman";
                case "opensuse":
                    return "zypper";
                default:
                    // Try to detect available package managers
                    if (isCommandAvailable("apt")) return "apt";
                    if (isCommandAvailable("dnf")) return "dnf";
                    if (isCommandAvailable("yum")) return "yum";
                    if (isCommandAvailable("pacman")) return "pacman";
                    if (isCommandAvailable("zypper")) return "zypper";
                    return null;
            }
        }

        return null;
    }

    /**
     * Checks if a command is available in PATH
     */
    private static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();

            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("where", command);
            } else {
                pb.command("which", command);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the standard installation prefix for this system
     */
    public String getInstallationPrefix() {
        if (isWindows) {
            return System.getenv("ProgramFiles");
        }

        if (isMacOS) {
            return "/usr/local";
        }

        if (isLinux) {
            return "/usr/local";
        }

        return "/usr/local";
    }

    /**
     * Gets the user's local bin directory
     */
    public String getUserBinDirectory() {
        String home = System.getProperty("user.home");

        if (isWindows) {
            return home + "\\AppData\\Local\\Programs";
        }

        return home + "/.local/bin";
    }

    /**
     * Checks if the current user has administrative privileges
     */
    public boolean hasAdminPrivileges() {
        if (isWindows) {
            try {
                ProcessBuilder pb = new ProcessBuilder("net", "session");
                Process process = pb.start();
                return process.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        } else {
            return System.getProperty("user.name").equals("root");
        }
    }

    /**
     * Gets the appropriate elevation command for this system
     */
    public String getElevationCommand() {
        if (isWindows) {
            return "runas";
        }

        if (isMacOS) {
            return "sudo";
        }

        if (isLinux) {
            // Try different elevation methods in order of preference
            if (isCommandAvailable("pkexec")) return "pkexec";
            if (isCommandAvailable("sudo")) return "sudo";
            if (isCommandAvailable("su")) return "su";
            return null;
        }

        return "sudo";
    }

    // Getters
    public String getOsName() {
        return osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getArchitecture() {
        return architecture;
    }

    public boolean isWindows() {
        return isWindows;
    }

    public boolean isMacOS() {
        return isMacOS;
    }

    public boolean isLinux() {
        return isLinux;
    }

    public String getDistribution() {
        return distribution;
    }

    /**
     * Checks if this is a 64-bit system
     */
    public boolean is64Bit() {
        return architecture.contains("64") || architecture.contains("amd64") || architecture.contains("x86_64");
    }

    /**
     * Gets a human-readable description of the system
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(osName);

        if (distribution != null && !distribution.equals("unknown")) {
            sb.append(" (").append(distribution).append(")");
        }

        sb.append(" ").append(osVersion);
        sb.append(" ").append(architecture);

        return sb.toString();
    }

    @Override
    public String toString() {
        return getDescription();
    }
}