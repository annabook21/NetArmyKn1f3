package edu.au.cpsc.module7.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class SettingsServiceTest {

    private SettingsService settingsService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // Use a temporary directory for settings to avoid interfering with real settings
        Path settingsFile = tempDir.resolve(".netarmykn1f3/settings.properties");
        System.setProperty("user.home", tempDir.toString());
        settingsService = new SettingsService();
    }

    @Test
    void testDefaultSettingsAreLoaded() {
        assertEquals("dark", settingsService.getTheme());
        assertEquals(12, settingsService.getFontSize());
        assertEquals(30, settingsService.getTimeoutSeconds());
        assertFalse(settingsService.getAutoSaveResults());
    }

    @Test
    void testSetAndGetSetting() {
        settingsService.setSetting("theme", "light");
        assertEquals("light", settingsService.getTheme());
    }

    @Test
    void testSaveAndLoadSettings() throws IOException {
        // Change a setting
        settingsService.setSetting("font.size", "18");
        settingsService.saveSettings();

        // Create a new service instance to simulate app restart
        SettingsService newSettingsService = new SettingsService();
        assertEquals(18, newSettingsService.getFontSize());
    }
} 