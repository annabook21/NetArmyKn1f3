package edu.au.cpsc.part1;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class FlightEditorViewController {

    private FlightUIModel uiModel = new FlightUIModel();

    @FXML private TextField designatorField;
    @FXML private TextField departureAirportField;
    @FXML private TextField departureTimeField;
    @FXML private TextField arrivalAirportField;
    @FXML private TextField arrivalTimeField;
    @FXML private ToggleButton mondayToggle;
    @FXML private ToggleButton tuesdayToggle;
    @FXML private ToggleButton wednesdayToggle;
    @FXML private ToggleButton thursdayToggle;
    @FXML private ToggleButton fridayToggle;
    @FXML private ToggleButton saturdayToggle;
    @FXML private ToggleButton sundayToggle;
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
        designatorField.textProperty().bindBidirectional(uiModel.flightNumberProperty());
        departureAirportField.textProperty().bindBidirectional(uiModel.departureProperty());
        arrivalAirportField.textProperty().bindBidirectional(uiModel.arrivalProperty());
        departureTimeField.textProperty().bindBidirectional(uiModel.departureTimeProperty());
        arrivalTimeField.textProperty().bindBidirectional(uiModel.arrivalTimeProperty());
    }

    private void setupValidationVisuals() {
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
        addUpdateButton.disableProperty().bind(
                uiModel.allFieldsValidProperty().not()
                        .or(uiModel.isNewProperty().not().and(uiModel.isModifiedProperty().not()))
        );
        deleteButton.disableProperty().bind(uiModel.isNewProperty());
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

    public void showFlightDetails(ScheduledFlight flight) {
        if (flight != null) {
            uiModel.flightNumberProperty().set(flight.getFlightDesignator());
            uiModel.departureProperty().set(flight.getDepartureAirportIdent());
            uiModel.arrivalProperty().set(flight.getArrivalAirportIdent());
            uiModel.departureTimeProperty().set(flight.getDepartureTime().toString());
            uiModel.arrivalTimeProperty().set(flight.getArrivalTime().toString());
            setDayToggles(flight.getDaysOfWeek());
            uiModel.markAsExisting();
        } else {
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

    public ScheduledFlight createFlightFromFields() {
        try {
            String flightDesignator = uiModel.getFlightNumber();
            String departureAirportIdent = uiModel.getDeparture();
            String arrivalAirportIdent = uiModel.getArrival();
            java.time.LocalTime departureTime = java.time.LocalTime.parse(uiModel.getDepartureTime());
            java.time.LocalTime arrivalTime = java.time.LocalTime.parse(uiModel.getArrivalTime());

            java.util.Set<java.time.DayOfWeek> daysOfWeek = new java.util.HashSet<>();
            if (mondayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.MONDAY);
            if (tuesdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.TUESDAY);
            if (wednesdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.WEDNESDAY);
            if (thursdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.THURSDAY);
            if (fridayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.FRIDAY);
            if (saturdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.SATURDAY);
            if (sundayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.SUNDAY);

            return new ScheduledFlight(flightDesignator, departureAirportIdent, departureTime, arrivalAirportIdent, arrivalTime, daysOfWeek);
        } catch (Exception e) {
            return null;
        }
    }

    public void updateFlightFromFields(ScheduledFlight flight) {
        if (flight != null && uiModel.areAllFieldsValid()) {
            try {
                String flightDesignator = uiModel.getFlightNumber();
                String departureAirportIdent = uiModel.getDeparture();
                String arrivalAirportIdent = uiModel.getArrival();
                java.time.LocalTime departureTime = java.time.LocalTime.parse(uiModel.getDepartureTime());
                java.time.LocalTime arrivalTime = java.time.LocalTime.parse(uiModel.getArrivalTime());

                java.util.Set<java.time.DayOfWeek> daysOfWeek = new java.util.HashSet<>();
                if (mondayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.MONDAY);
                if (tuesdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.TUESDAY);
                if (wednesdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.WEDNESDAY);
                if (thursdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.THURSDAY);
                if (fridayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.FRIDAY);
                if (saturdayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.SATURDAY);
                if (sundayToggle.isSelected()) daysOfWeek.add(java.time.DayOfWeek.SUNDAY);

                // Update the flight object with new values
                flight.setFlightDesignator(flightDesignator);
                flight.setDepartureAirportIdent(departureAirportIdent);
                flight.setArrivalAirportIdent(arrivalAirportIdent);
                flight.setDepartureTime(departureTime);
                flight.setArrivalTime(arrivalTime);
                flight.setDaysOfWeek(daysOfWeek);
                
                uiModel.markAsExisting(); // Mark as existing after successful update
            } catch (Exception e) {
                throw new RuntimeException("Invalid input format: " + e.getMessage(), e);
            }
        }
    }

    public void setOnNewButtonClick(Runnable onNewButtonClick) {}
    public void setOnSaveButtonClick(Runnable onSaveButtonClick) {}
    public void setOnDeleteButtonClick(Runnable onDeleteButtonClick) {}
    public void setOnAddUpdateButtonClick(Runnable onAddUpdateButtonClick) {}
    public void newFlight() { handleNewClick(); }
}

