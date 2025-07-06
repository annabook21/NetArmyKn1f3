package edu.au.cpsc.module7.controllers

import edu.au.cpsc.module7.controllers.SettingsService;
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

    @FXML private TextField pythonPathField;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private Spinner<Integer> historySpinner;
    @FXML private CheckBox autoSaveCheckBox;
    @FXML private ComboBox<String> th
