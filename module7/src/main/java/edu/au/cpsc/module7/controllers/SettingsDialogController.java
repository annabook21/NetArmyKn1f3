package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.services.SettingsService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Settings Dialog
 */
public class SettingsDialogController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(SettingsDialogController.class.getName());

    @FXML private TextField pythonPathField; // may be null if Python section removed from FXML
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private Spinner<Integer> historySpinner;
    @FXML private CheckBox autoSaveCheckBox;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private ComboBox<String> fontFamilyComboBox;
    @FXML private Spinner<Integer> fontSizeSpinner;
    @FXML private Button browsePathButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private SettingsService settingsService;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupSpinners();
        setupComboBoxes();
        setupEventHandlers();
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
        loadCurrentSettings();
    }

    private void setupSpinners() {
        // Timeout spinner (5-300 seconds)
        timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 300, 30, 5));
        timeoutSpinner.setEditable(true);

        // History spinner (5-100 entries)
        historySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 100, 20, 5));
        historySpinner.setEditable(true);

        // Font size spinner (8-24)
        fontSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 24, 12, 1));
        fontSizeSpinner.setEditable(true);
    }

    private void setupComboBoxes() {
        // Theme options
        themeComboBox.getItems().addAll("Dark", "Light", "System");
        themeComboBox.setValue("Dark");

        // Font family options
        fontFamilyComboBox.getItems().addAll(
                "JetBrains Mono", "Fira Code", "Consolas", "Monaco",
                "Courier New", "Monospace", "System Default"
        );
        fontFamilyComboBox.setValue("JetBrains Mono");
    }

    private void setupEventHandlers() {
        if (browsePathButton != null) {
            browsePathButton.setOnAction(e -> browsePythonPath());
        }
        resetButton.setOnAction(e -> resetToDefaults());
        saveButton.setOnAction(e -> saveSettings());
        cancelButton.setOnAction(e -> handleCancel());
    }

    private void loadCurrentSettings() {
        if (settingsService == null) {
            try {
                settingsService = new edu.au.cpsc.module7.services.SettingsService();
            } catch (Exception ignored) {
                return; // Cannot load settings
            }
        }

        try {
            if (pythonPathField != null) {
                pythonPathField.setText(settingsService.getPythonPath());
            }
            timeoutSpinner.getValueFactory().setValue(settingsService.getTimeoutSeconds());
            historySpinner.getValueFactory().setValue(settingsService.getMaxHistory());
            autoSaveCheckBox.setSelected(settingsService.getAutoSaveResults());
            themeComboBox.setValue(capitalize(settingsService.getTheme()));
            fontFamilyComboBox.setValue(settingsService.getFontFamily());
            fontSizeSpinner.getValueFactory().setValue(settingsService.getFontSize());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading settings", e);
            showErrorAlert("Settings Error", "Failed to load current settings", e.getMessage());
        }
    }

    private void browsePythonPath() {
        if (pythonPathField == null) return; // section removed
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Python Interpreter");

        // Set initial directory
        String currentPath = pythonPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                fileChooser.setInitialDirectory(currentFile.getParentFile());
            }
        }

        // Add file filters
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Python Executable", "python*", "python.exe", "python3*"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(browsePathButton.getScene().getWindow());
        if (selectedFile != null) {
            if (pythonPathField != null) {
                pythonPathField.setText(selectedFile.getAbsolutePath());
            }
        }
    }

    private void resetToDefaults() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Reset Settings");
        confirmAlert.setHeaderText("Reset to Default Values");
        confirmAlert.setContentText("This will reset all settings to their default values. Continue?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (pythonPathField != null) {
                    pythonPathField.setText("/usr/bin/python3");
                }
                timeoutSpinner.getValueFactory().setValue(30);
                historySpinner.getValueFactory().setValue(20);
                autoSaveCheckBox.setSelected(false);
                themeComboBox.setValue("Dark");
                fontFamilyComboBox.setValue("JetBrains Mono");
                fontSizeSpinner.getValueFactory().setValue(12);
            }
        });
    }

    private void saveSettings() {
        if (settingsService == null) {
            try {
                settingsService = new edu.au.cpsc.module7.services.SettingsService();
            } catch (Exception e) {
                showErrorAlert("Settings Error", "Settings service not available", null);
                return;
            }
        }

        try {
            // Validate settings
            if (!validateSettings()) {
                return;
            }

            // Save settings
            if (pythonPathField != null) {
                settingsService.setSetting("python.path", pythonPathField.getText());
            }
            settingsService.setSetting("timeout.seconds", timeoutSpinner.getValue().toString());
            settingsService.setSetting("max.history", historySpinner.getValue().toString());
            settingsService.setSetting("auto.save.results", String.valueOf(autoSaveCheckBox.isSelected()));
            settingsService.setSetting("theme", themeComboBox.getValue().toLowerCase());
            settingsService.setSetting("font.family", fontFamilyComboBox.getValue());
            settingsService.setSetting("font.size", fontSizeSpinner.getValue().toString());

            settingsService.saveSettings();

            // Show success message
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Settings Saved");
            successAlert.setHeaderText(null);
            successAlert.setContentText("Settings have been saved successfully.\n\nSome changes may require restarting the application.");
            successAlert.showAndWait();

            // Close dialog
            handleClose();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving settings", e);
            showErrorAlert("Save Error", "Failed to save settings", e.getMessage());
        }
    }

    private boolean validateSettings() {
        if (pythonPathField == null) {
            return true; // no python validation needed
        }

        String pythonPath = pythonPathField.getText();
        if (pythonPath == null || pythonPath.trim().isEmpty()) {
            showErrorAlert("Validation Error", "Python path cannot be empty", null);
            pythonPathField.requestFocus();
            return false;
        }

        File pythonFile = new File(pythonPath);
        if (!pythonFile.exists()) {
            Alert confirmAlert = new Alert(Alert.AlertType.WARNING);
            confirmAlert.setTitle("Python Path Warning");
            confirmAlert.setHeaderText("Python executable not found");
            confirmAlert.setContentText("The specified Python executable does not exist:\n" + pythonPath + "\n\nSave anyway?");

            confirmAlert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            return confirmAlert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
        }

        return true;
    }

    @FXML
    private void handleCancel() {
        // Check if there are unsaved changes
        if (hasUnsavedChanges()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Unsaved Changes");
            confirmAlert.setHeaderText("You have unsaved changes");
            confirmAlert.setContentText("Do you want to save your changes before closing?");

            ButtonType saveAndCloseButton = new ButtonType("Save & Close");
            ButtonType closeWithoutSavingButton = new ButtonType("Close Without Saving");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            confirmAlert.getButtonTypes().setAll(saveAndCloseButton, closeWithoutSavingButton, cancelButton);

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == saveAndCloseButton) {
                    saveSettings();
                } else if (response == closeWithoutSavingButton) {
                    handleClose();
                }
                // If cancel, do nothing (dialog stays open)
            });
        } else {
            handleClose();
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private boolean hasUnsavedChanges() {
        if (settingsService == null) return false;

        try {
            boolean pythonChanged = (pythonPathField != null) && !pythonPathField.getText().equals(settingsService.getPythonPath());
            return pythonChanged ||
                    !timeoutSpinner.getValue().equals(settingsService.getTimeoutSeconds()) ||
                    !historySpinner.getValue().equals(settingsService.getMaxHistory()) ||
                    autoSaveCheckBox.isSelected() != settingsService.getAutoSaveResults() ||
                    !themeComboBox.getValue().toLowerCase().equals(settingsService.getTheme()) ||
                    !fontFamilyComboBox.getValue().equals(settingsService.getFontFamily()) ||
                    !fontSizeSpinner.getValue().equals(settingsService.getFontSize());
        } catch (Exception e) {
            return false;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
