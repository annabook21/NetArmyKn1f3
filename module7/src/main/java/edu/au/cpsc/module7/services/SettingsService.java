package edu.au.cpsc.module7.services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Service for managing application settings and preferences
 */
public class SettingsService {
    private static final Logger LOGGER = Logger.getLogger(SettingsService.class.getName());
    private static final String SETTINGS_DIR = ".netarmykn1f3";
    private static final String SETTINGS_FILE = "settings.properties";
    private static final String HISTORY_FILE = "domain_history.txt";
    private static final String PREF_NODE = "edu/au/cpsc/module7";
    private static final String LAST_QUERIES_KEY = "lastQueries";

    private static SettingsService instance;
    private final Path settingsPath;
    private final Path historyPath;
    private final Properties settings;
    private final Preferences prefs;

    private SettingsService() {
        // Initialize settings directory
        Path homeDir = Paths.get(System.getProperty("user.home"));
        Path settingsDir = homeDir.resolve(SETTINGS_DIR);

        if (!Files.exists(settingsDir)) {
            try {
                Files.createDirectories(settingsDir);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create settings directory", e);
            }
        }

        this.settingsPath = settingsDir.resolve(SETTINGS_FILE);
        this.historyPath = settingsDir.resolve(HISTORY_FILE);
        this.settings = new Properties();
        this.prefs = Preferences.userRoot().node(PREF_NODE);

        loadSettings();
        setDefaultSettings();
    }

    public static synchronized SettingsService getInstance() {
        if (instance == null) {
            instance = new SettingsService();
        }
        return instance;
    }

    private void loadSettings() {
        if (Files.exists(settingsPath)) {
            try (InputStream input = Files.newInputStream(settingsPath)) {
                settings.load(input);
                LOGGER.info("Settings loaded from: " + settingsPath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load settings", e);
            }
        }
    }

    private void setDefaultSettings() {
        // Set default values if not already present
        settings.putIfAbsent("python.path", "/usr/bin/python3");
        settings.putIfAbsent("timeout.seconds", "30");
        settings.putIfAbsent("max.history", "20");
        settings.putIfAbsent("auto.save.results", "false");
        settings.putIfAbsent("theme", "dark");
        settings.putIfAbsent("font.family", "Courier New");
        settings.putIfAbsent("font.size", "12");
    }

    public void saveSettings() throws IOException {
        try (OutputStream output = Files.newOutputStream(settingsPath)) {
            settings.store(output, "NetArmyKn1f3 Settings");
            LOGGER.info("Settings saved to: " + settingsPath);
        }
    }

    public String getSetting(String key) {
        return settings.getProperty(key);
    }

    public void setSetting(String key, String value) {
        settings.setProperty(key, value);
    }

    public Map<String, String> getSettings() {
        Map<String, String> result = new HashMap<>();
        for (String key : settings.stringPropertyNames()) {
            result.put(key, settings.getProperty(key));
        }
        return result;
    }

    public List<String> getDomainHistory() {
        List<String> history = new ArrayList<>();

        if (Files.exists(historyPath)) {
            try {
                List<String> lines = Files.readAllLines(historyPath);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !history.contains(line)) {
                        history.add(line);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load domain history", e);
            }
        }

        return history;
    }

    public void saveDomainHistory(List<String> history) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(historyPath))) {
            for (String domain : history) {
                if (domain != null && !domain.trim().isEmpty()) {
                    writer.println(domain.trim());
                }
            }
            LOGGER.info("Domain history saved");
        }
    }

    public void addDomainToHistory(String domain) {
        try {
            List<String> history = getDomainHistory();
            if (!history.contains(domain)) {
                history.add(0, domain);

                // Limit history size
                int maxHistory = Integer.parseInt(getSetting("max.history"));
                if (history.size() > maxHistory) {
                    history = history.subList(0, maxHistory);
                }

                saveDomainHistory(history);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update domain history", e);
        }
    }

    public void clearDomainHistory() throws IOException {
        if (Files.exists(historyPath)) {
            Files.delete(historyPath);
        }
    }

    public Path getSettingsDirectory() {
        return settingsPath.getParent();
    }

    // Specific setting getters with type conversion
    public int getTimeoutSeconds() {
        try {
            return Integer.parseInt(getSetting("timeout.seconds"));
        } catch (NumberFormatException e) {
            return 30; // default
        }
    }

    public int getMaxHistory() {
        try {
            return Integer.parseInt(getSetting("max.history"));
        } catch (NumberFormatException e) {
            return 20; // default
        }
    }

    public boolean getAutoSaveResults() {
        return Boolean.parseBoolean(getSetting("auto.save.results"));
    }

    public String getPythonPath() {
        return getSetting("python.path");
    }

    public String getTheme() {
        return getSetting("theme");
    }

    public String getFontFamily() {
        return getSetting("font.family");
    }

    public int getFontSize() {
        try {
            return Integer.parseInt(getSetting("font.size"));
        } catch (NumberFormatException e) {
            return 12; // default
        }
    }

    public void saveLastQueries(List<String> queries) {
        try {
            String serializedQueries = String.join(",", queries);
            prefs.put(LAST_QUERIES_KEY, serializedQueries);
            LOGGER.info("Last queries saved to preferences");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save last queries to preferences", e);
        }
    }

    public List<String> getLastQueries() {
        String serializedQueries = prefs.get(LAST_QUERIES_KEY, "");
        if (serializedQueries.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(serializedQueries.split(","));
    }
}