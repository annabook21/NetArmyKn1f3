package edu.au.cpsc.part1;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    // The invalid constructor that was here has been REMOVED.

    @FXML
    public void initialize() {
        setupDayToggleMap();
        clearEditor();
    }

    // ... all other methods remain the same ...

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
            setEditorState(true);
        } else {
            clearEditor();
            setEditorState(false);
        }
    }

    public void updateFlightFromFields(ScheduledFlight flight) {
        flight.setFlightDesignator(designatorField.getText());
        flight.setDepartureAirportIdent(departureAirportField.getText());
        flight.setDepartureTime(LocalTime.parse(departureTimeField.getText()));
        flight.setArrivalAirportIdent(arrivalAirportField.getText());
        flight.setArrivalTime(LocalTime.parse(arrivalTimeField.getText()));
        flight.setDaysOfWeek(getSelectedDays());
    }

    public ScheduledFlight createFlightFromFields() {
        String departureTimeText = departureTimeField.getText();
        String arrivalTimeText = arrivalTimeField.getText();
        LocalTime departureTime = LocalTime.parse(departureTimeText);
        LocalTime arrivalTime = LocalTime.parse(arrivalTimeText);
        return new ScheduledFlight(
                designatorField.getText(),
                departureAirportField.getText(),
                departureTime,
                arrivalAirportField.getText(),
                arrivalTime,
                getSelectedDays()
        );
    }

    @FXML
    private void handleAddUpdateClick() {
        if (onAddUpdateButtonClick != null) { onAddUpdateButtonClick.run(); }
    }

    @FXML
    private void handleNewClick() {
        if (onNewButtonClick != null) { onNewButtonClick.run(); }
    }

    @FXML
    private void handleDeleteClick() {
        if (onDeleteButtonClick != null) { onDeleteButtonClick.run(); }
    }

    public void setOnAddUpdateButtonClick(Runnable onAddUpdateButtonClick) { this.onAddUpdateButtonClick = onAddUpdateButtonClick; }
    public void setOnNewButtonClick(Runnable onNewButtonClick) { this.onNewButtonClick = onNewButtonClick; }
    public void setOnDeleteButtonClick(Runnable onDeleteButtonClick) { this.onDeleteButtonClick = onDeleteButtonClick; }

    private void clearEditor() {
        designatorField.clear();
        departureAirportField.clear();
        departureTimeField.clear();
        arrivalAirportField.clear();
        arrivalTimeField.clear();
        dayToggleMap.values().forEach(button -> button.setSelected(false));
    }

    private void setEditorState(boolean isItemSelected) {
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

