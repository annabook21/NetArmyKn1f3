package edu.au.cpsc.module4;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

public class FlightEditorViewController {

    // FXML UI Components
    @FXML private TextField designatorField;
    @FXML private TextField departureAirportField;
    @FXML private TextField departureTimeField;
    @FXML private TextField arrivalAirportField;
    @FXML private TextField arrivalTimeField;
    @FXML private ToggleButton mondayToggle, tuesdayToggle, wednesdayToggle,
            thursdayToggle, fridayToggle, saturdayToggle, sundayToggle;
    @FXML private Button addUpdateButton;
    @FXML private Button deleteButton;

    // Callbacks to communicate actions back to the main controller
    private Runnable onAddUpdateButtonClick;
    private Runnable onNewButtonClick;
    private Runnable onDeleteButtonClick;

    private EnumMap<DayOfWeek, ToggleButton> dayToggleMap;

    public FlightEditorViewController(TextField arrivalTimeField) {
        this.arrivalTimeField = arrivalTimeField;
    }

    @FXML
    public void initialize() {
        setupDayToggleMap();
        // The editor starts in a blank, disabled state
        clearEditor();
    }

    /**
     * Main method for the coordinator to show flight details in the editor.
     */
    public void showFlightDetails(ScheduledFlight flight) {
        if (flight != null) {
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            designatorField.setText(flight.getFlightDesignator());
            departureAirportField.setText(flight.getDepartureAirportIdent());
            departureTimeField.setText(flight.getDepartureTime().format(timeFormatter));
            arrivalAirportField.setText(flight.getArrivalAirportIdent());
            arrivalTimeField.setText(flight.getArrivalTime().format(timeFormatter));

            Set<DayOfWeek> activeDays = flight.getDaysOfWeek();
            dayToggleMap.forEach((day, button) -> button.setSelected(activeDays.contains(day)));

            // Enable editor and set button states for "Update" mode
            setEditorState(true);
        } else {
            // No flight selected, reset to "Add" mode
            clearEditor();
            setEditorState(false);
        }
    }

    /**
     * Reads the editor fields and USES SETTERS to update an existing flight object.
     */
    public void updateFlightFromFields(ScheduledFlight flight) {
        flight.setFlightDesignator(designatorField.getText());
        flight.setDepartureAirportIdent(departureAirportField.getText());
        flight.setDepartureTime(LocalTime.parse(departureTimeField.getText()));
        flight.setArrivalAirportIdent(arrivalAirportField.getText());
        flight.setArrivalTime(LocalTime.parse(arrivalTimeField.getText()));
        flight.setDaysOfWeek(getSelectedDays());
    }

    /**
     * Reads the editor fields and CREATES A NEW flight object.
     */
    public ScheduledFlight createFlightFromFields() {
        // Get the text from the fields
        String departureTimeText = departureTimeField.getText();
        String arrivalTimeText = arrivalTimeField.getText();

        // Parse the time strings into LocalTime objects
        LocalTime departureTime = LocalTime.parse(departureTimeText);
        LocalTime arrivalTime = LocalTime.parse(arrivalTimeText);

        return new ScheduledFlight(
                designatorField.getText(),
                departureAirportField.getText(),
                departureTime, // Pass the LocalTime object
                arrivalAirportField.getText(),
                arrivalTime,   // Pass the LocalTime object
                getSelectedDays()
        );
    }


    // --- Button Actions & Callbacks ---

    @FXML
    private void handleAddUpdateClick() {
        if (onAddUpdateButtonClick != null) {
            onAddUpdateButtonClick.run(); // Execute the action defined by the main controller
        }
    }

    @FXML
    private void handleNewClick() {
        if (onNewButtonClick != null) {
            onNewButtonClick.run();
        }
    }

    @FXML
    private void handleDeleteClick() {
        if (onDeleteButtonClick != null) {
            onDeleteButtonClick.run();
        }
    }

    // Public setters for the main controller to define button behavior
    public void setOnAddUpdateButtonClick(Runnable onAddUpdateButtonClick) { this.onAddUpdateButtonClick = onAddUpdateButtonClick; }
    public void setOnNewButtonClick(Runnable onNewButtonClick) { this.onNewButtonClick = onNewButtonClick; }
    public void setOnDeleteButtonClick(Runnable onDeleteButtonClick) { this.onDeleteButtonClick = onDeleteButtonClick; }

    // --- Helper Methods ---

    private void clearEditor() {
        designatorField.clear();
        departureAirportField.clear();
        departureTimeField.clear();
        arrivalAirportField.clear();
        arrivalTimeField.clear();
        dayToggleMap.values().forEach(button -> button.setSelected(false));
    }

    private void setEditorState(boolean isItemSelected) {
        // Toggles editability and button text/state
        designatorField.setDisable(!isItemSelected); // For update, don't allow changing the key field
        addUpdateButton.setText(isItemSelected ? "Update" : "Add");
        deleteButton.setDisable(!isItemSelected);
    }

    private Set<DayOfWeek> getSelectedDays() {
        Set<DayOfWeek> selectedDays = new HashSet<>();
        dayToggleMap.forEach((day, button) -> {
            if (button.isSelected()) {
                selectedDays.add(day);
            }
        });
        return selectedDays;
    }

    private void setupDayToggleMap() {
        dayToggleMap = new EnumMap<>(DayOfWeek.class);
        dayToggleMap.put(DayOfWeek.MONDAY, mondayToggle);
        dayToggleMap.put(DayOfWeek.TUESDAY, tuesdayToggle);
        dayToggleMap.put(DayOfWeek.WEDNESDAY, wednesdayToggle);
        dayToggleMap.put(DayOfWeek.THURSDAY, thursdayToggle);
        dayToggleMap.put(DayOfWeek.FRIDAY, fridayToggle);
        dayToggleMap.put(DayOfWeek.SATURDAY, saturdayToggle);
        dayToggleMap.put(DayOfWeek.SUNDAY, sundayToggle);
    }
}
