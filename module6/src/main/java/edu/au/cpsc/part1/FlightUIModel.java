package edu.au.cpsc.part1;

import javafx.beans.property.*;

public class FlightUIModel {

    // Properties for flight data
    private final StringProperty flightNumber = new SimpleStringProperty("");
    private final StringProperty departure = new SimpleStringProperty("");
    private final StringProperty arrival = new SimpleStringProperty("");
    private final StringProperty departureTime = new SimpleStringProperty("");
    private final StringProperty arrivalTime = new SimpleStringProperty("");

    // Validation properties
    private final BooleanProperty flightNumberValid = new SimpleBooleanProperty(false);
    private final BooleanProperty departureValid = new SimpleBooleanProperty(false);
    private final BooleanProperty arrivalValid = new SimpleBooleanProperty(false);
    private final BooleanProperty departureTimeValid = new SimpleBooleanProperty(false);
    private final BooleanProperty arrivalTimeValid = new SimpleBooleanProperty(false);

    // State properties
    private final BooleanProperty isNew = new SimpleBooleanProperty(true);
    private final BooleanProperty isModified = new SimpleBooleanProperty(false);
    private final BooleanProperty allFieldsValid = new SimpleBooleanProperty(false);

    public FlightUIModel() {
        setupValidation();
        setupModificationTracking();
    }

    private void setupValidation() {
        flightNumber.addListener((obs, oldVal, newVal) -> {
            boolean valid = newVal != null && !newVal.trim().isEmpty();
            flightNumberValid.set(valid);
            updateAllFieldsValid();
        });

        departure.addListener((obs, oldVal, newVal) -> {
            boolean valid = newVal != null && !newVal.trim().isEmpty();
            departureValid.set(valid);
            updateAllFieldsValid();
        });

        arrival.addListener((obs, oldVal, newVal) -> {
            boolean valid = newVal != null && !newVal.trim().isEmpty();
            arrivalValid.set(valid);
            updateAllFieldsValid();
        });

        departureTime.addListener((obs, oldVal, newVal) -> {
            boolean valid = isValidTimeFormat(newVal);
            departureTimeValid.set(valid);
            updateAllFieldsValid();
        });

        arrivalTime.addListener((obs, oldVal, newVal) -> {
            boolean valid = isValidTimeFormat(newVal);
            arrivalTimeValid.set(valid);
            updateAllFieldsValid();
        });
    }

    private void setupModificationTracking() {
        flightNumber.addListener((obs, oldVal, newVal) -> setModified());
        departure.addListener((obs, oldVal, newVal) -> setModified());
        arrival.addListener((obs, oldVal, newVal) -> setModified());
        departureTime.addListener((obs, oldVal, newVal) -> setModified());
        arrivalTime.addListener((obs, oldVal, newVal) -> setModified());
    }

    private void setModified() {
        if (!isNew.get()) {
            isModified.set(true);
        }
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        return time.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
    }

    private void updateAllFieldsValid() {
        boolean valid = flightNumberValid.get() &&
                departureValid.get() &&
                arrivalValid.get() &&
                departureTimeValid.get() &&
                arrivalTimeValid.get();
        allFieldsValid.set(valid);
    }

    public void markAsNew() {
        isNew.set(true);
        isModified.set(false);
        clearAllFields();
    }

    public void markAsExisting() {
        isNew.set(false);
        isModified.set(false);
    }

    public void clearAllFields() {
        flightNumber.set("");
        departure.set("");
        arrival.set("");
        departureTime.set("");
        arrivalTime.set("");
    }

    // Property getters
    public StringProperty flightNumberProperty() { return flightNumber; }
    public StringProperty departureProperty() { return departure; }
    public StringProperty arrivalProperty() { return arrival; }
    public StringProperty departureTimeProperty() { return departureTime; }
    public StringProperty arrivalTimeProperty() { return arrivalTime; }

    public BooleanProperty flightNumberValidProperty() { return flightNumberValid; }
    public BooleanProperty departureValidProperty() { return departureValid; }
    public BooleanProperty arrivalValidProperty() { return arrivalValid; }
    public BooleanProperty departureTimeValidProperty() { return departureTimeValid; }
    public BooleanProperty arrivalTimeValidProperty() { return arrivalTimeValid; }

    public BooleanProperty isNewProperty() { return isNew; }
    public BooleanProperty isModifiedProperty() { return isModified; }
    public BooleanProperty allFieldsValidProperty() { return allFieldsValid; }

    // Value getters
    public String getFlightNumber() { return flightNumber.get(); }
    public String getDeparture() { return departure.get(); }
    public String getArrival() { return arrival.get(); }
    public String getDepartureTime() { return departureTime.get(); }
    public String getArrivalTime() { return arrivalTime.get(); }

    public boolean isNew() { return isNew.get(); }
    public boolean isModified() { return isModified.get(); }
    public boolean areAllFieldsValid() { return allFieldsValid.get(); }
}