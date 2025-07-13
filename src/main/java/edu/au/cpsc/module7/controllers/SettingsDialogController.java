package edu.au.cpsc.module7.controllers;

import com.google.inject.Inject;
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
    @FXML private TextField mtrPathField;
    @FXML private TextField hping3PathField;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private Spinner<Integer> historySpinner;
    @FXML private CheckBox autoSaveCheckBox;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private ComboBox<String> fontFamilyComboBox;
    @FXML private Spinner<Integer> fontSizeSpinner;
    @FXML private Button browsePathButton;
    @FXML private Button browseMtrPathButton;
    @FXML private Button browseHping3PathButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final SettingsService settingsService;

    @Inject
    public SettingsDialogController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupSpinners();
        setupComboBoxes();
        setupEventHandlers();
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
            browsePathButton.setOnAction(e -> browseForExecutable(pythonPathField, "Python Interpreter"));
        }
        browseMtrPathButton.setOnAction(e -> browseForExecutable(mtrPathField, "MTR Executable"));
        browseHping3PathButton.setOnAction(e -> browseForExecutable(hping3PathField, "hping3 Executable"));
        resetButton.setOnAction(e -> resetToDefaults());
        saveButton.setOnAction(e -> saveSettings());
        cancelButton.setOnAction(e -> handleCancel());
    }

    private void loadCurrentSettings() {
        if (settingsService == null) {
            LOGGER.warning("SettingsService not injected.");
            return;
        }

        try {
            if (pythonPathField != null) {
                pythonPathField.setText(settingsService.getPythonPath());
            }
            mtrPathField.setText(settingsService.getMtrPath());
            hping3PathField.setText(settingsService.getHping3Path());
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

    private void browseForExecutable(TextField pathField, String title) {
        if (pathField == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select " + title);

        // Set initial directory
        String currentPath = pathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                fileChooser.setInitialDirectory(currentFile.getParentFile());
            }
        }

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(pathField.getScene().getWindow());
        if (selectedFile != null) {
            pathField.setText(selectedFile.getAbsolutePath());
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
                mtrPathField.setText("mtr");
                hping3PathField.setText("hping3");
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
            showErrorAlert("Settings Error", "Settings service not available", null);
            return;
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
            settingsService.setSetting("mtr.path", mtrPathField.getText());
            settingsService.setSetting("hping3.path", hping3PathField.getText());
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

        if (!validateExecutablePath(pythonPathField, "Python")) return false;
        if (!validateExecutablePath(mtrPathField, "MTR")) return false;
        if (!validateExecutablePath(hping3PathField, "hping3")) return false;

        return true;
    }

    private boolean validateExecutablePath(TextField pathField, String toolName) {
        String path = pathField.getText();
        if (path == null || path.trim().isEmpty()) {
            showErrorAlert("Validation Error", toolName + " path cannot be empty", null);
            pathField.requestFocus();
            return false;
        }

        File file = new File(path);
        if (!file.exists()) {
            Alert confirmAlert = new Alert(Alert.AlertType.WARNING);
            confirmAlert.setTitle(toolName + " Path Warning");
            confirmAlert.setHeaderText(toolName + " executable not found");
            confirmAlert.setContentText("The specified " + toolName + " executable does not exist:\n" + path + "\n\nSave anyway?");

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
        boolean pythonChanged = pythonPathField != null && !pythonPathField.getText().equals(settingsService.getPythonPath());
        boolean mtrChanged = !mtrPathField.getText().equals(settingsService.getMtrPath());
        boolean hping3Changed = !hping3PathField.getText().equals(settingsService.getHping3Path());
        boolean timeoutChanged = !timeoutSpinner.getValue().equals(settingsService.getTimeoutSeconds());
        boolean historyChanged = !historySpinner.getValue().equals(settingsService.getMaxHistory());
        boolean autoSaveChanged = autoSaveCheckBox.isSelected() != settingsService.getAutoSaveResults();
        boolean themeChanged = !themeComboBox.getValue().equalsIgnoreCase(settingsService.getTheme());
        boolean fontChanged = !fontFamilyComboBox.getValue().equals(settingsService.getFontFamily());
        boolean fontSizeChanged = !fontSizeSpinner.getValue().equals(settingsService.getFontSize());

        return pythonChanged || mtrChanged || hping3Changed || timeoutChanged || historyChanged || autoSaveChanged || themeChanged || fontChanged || fontSizeChanged;
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
