package edu.au.cpsc.part1;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class FlightEditorViewController {

    private FlightUIModel uiModel = new FlightUIModel();

    // FXML Fields matching your FXML file
    @FXML private TextField designatorField;
    @FXML private TextField departureAirportField;
    @FXML private TextField departureTimeField;
    @FXML private TextField arrivalAirportField;
    @FXML private TextField arrivalTimeField;

    // Day toggles
    @FXML private ToggleButton mondayToggle;
    @FXML private ToggleButton tuesdayToggle;
    @FXML private ToggleButton wednesdayToggle;
    @FXML private ToggleButton thursdayToggle;
    @FXML private ToggleButton fridayToggle;
    @FXML private ToggleButton saturdayToggle;
    @FXML private ToggleButton sundayToggle;

    // Buttons
    @FXML private Button addUpdateButton;
    @FXML private Button newButton;
    @FXML private Button deleteButton;

    @FXML
    private void initialize() {
        setupBindings();
        setupValidationVisuals();
        setupButtonStates();
        uiModel.markAsNew();
    }

    private void setupBindings() {
        // Bind text fields to model properties
        designatorField.textProperty().bindBidirectional(uiModel.flightNumberProperty());
        departureAirportField.textProperty().bindBidirectional(uiModel.departureProperty());
        arrivalAirportField.textProperty().bindBidirectional(uiModel.arrivalProperty());
        departureTimeField.textProperty().bindBidirectional(uiModel.departureTimeProperty());
        arrivalTimeField.textProperty().bindBidirectional(uiModel.arrivalTimeProperty());
    }

    private void setupValidationVisuals() {
        // Red border for invalid fields
        uiModel.flightNumberValidProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !uiModel.flightNumberProperty().get().isEmpty()) {
                designatorField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            } else {
                designatorField.setStyle("");
            }
        });

        uiModel.departureValidProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !uiModel.departureProperty().get().isEmpty()) {
                departureAirportField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            } else {
                departureAirportField.setStyle("");
            }
        });

        uiModel.arrivalValidProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !uiModel.arrivalProperty().get().isEmpty()) {
                arrivalAirportField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            } else {
                arrivalAirportField.setStyle("");
            }
        });

        uiModel.departureTimeValidProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !uiModel.departureTimeProperty().get().isEmpty()) {
                departureTimeField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            } else {
                departureTimeField.setStyle("");
            }
        });

        uiModel.arrivalTimeValidProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !uiModel.arrivalTimeProperty().get().isEmpty()) {
                arrivalTimeField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            } else {
                arrivalTimeField.setStyle("");
            }
        });
    }

    private void setupButtonStates() {
        // Add/Update button: enabled only when all fields valid AND (new OR modified)
        addUpdateButton.disableProperty().bind(
                uiModel.allFieldsValidProperty().not()
                        .or(uiModel.isNewProperty().not().and(uiModel.isModifiedProperty().not()))
        );

        // Delete button: enabled when not new (i.e., editing existing)
        deleteButton.disableProperty().bind(uiModel.isNewProperty());

        // Update button text based on state
        uiModel.isNewProperty().addListener((obs, oldVal, newVal) -> {
            addUpdateButton.setText(newVal ? "Add" : "Update");
        });
    }

    @FXML
    private void handleAddUpdateClick() {
        if (uiModel.areAllFieldsValid()) {
            System.out.println("Saving flight: " + uiModel.getFlightNumber());
            uiModel.markAsExisting();
        }
    }

    @FXML
    private void handleNewClick() {
        uiModel.markAsNew();
        clearDayToggles();
    }

    @FXML
    private void handleDeleteClick() {
        System.out.println("Deleting flight: " + uiModel.getFlightNumber());
        uiModel.markAsNew();
        clearDayToggles();
    }

    private void clearDayToggles() {
        mondayToggle.setSelected(false);
        tuesdayToggle.setSelected(false);
        wednesdayToggle.setSelected(false);
        thursdayToggle.setSelected(false);
        fridayToggle.setSelected(false);
        saturdayToggle.setSelected(false);
        sundayToggle.setSelected(false);
    }

    // Method called by FlightScheduleApplicationController
    public void showFlightDetails(ScheduledFlight flight) {
        if (flight != null) {
            // Populate fields with flight data
            uiModel.flightNumberProperty().set(flight.getFlightDesignator());
            uiModel.departureProperty().set(flight.getDepartureAirportIdent());
            uiModel.arrivalProperty().set(flight.getArrivalAirportIdent());
            uiModel.departureTimeProperty().set(flight.getDepartureTime().toString());
            uiModel.arrivalTimeProperty().set(flight.getArrivalTime().toString());

            // Set days of week toggles
            setDayToggles(flight.getDaysOfWeek());

            // Mark as existing (not new)
            uiModel.markAsExisting();
        } else {
            // Clear everything for new flight
            uiModel.markAsNew();
            clearDayToggles();
        }
    }

    private void setDayToggles(java.util.Set<java.time.DayOfWeek> daysOfWeek) {
        if (daysOfWeek != null) {
            mondayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.MONDAY));
            tuesdayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.TUESDAY));
            wednesdayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.WEDNESDAY));
            thursdayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.THURSDAY));
            fridayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.FRIDAY));
            saturdayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.SATURDAY));
            sundayToggle.setSelected(daysOfWeek.contains(java.time.DayOfWeek.SUNDAY));
        }
    }

    // Methods called by FlightScheduleApplicationController
    public void setOnNewButtonClick(Runnable onNewButtonClick) {
        // Store callback if needed, or just ignore since we handle internally
    }

    public void setOnSaveButtonClick(Runnable onSaveButtonClick) {
        // Store callback if needed, or just ignore since we handle internally
    }

    public void setOnDeleteButtonClick(Runnable onDeleteButtonClick) {
        // Store callback if needed, or just ignore since we handle internally
    }

    public void newFlight() {
        // Called externally to start a new flight
        handleNewClick();
    }
}
