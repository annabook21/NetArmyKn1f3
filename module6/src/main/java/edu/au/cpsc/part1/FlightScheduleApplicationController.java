package edu.au.cpsc.part1;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;

public class FlightScheduleApplicationController {

    // IMPORTANT: The fx:id in the FXML must match these variable names.
    // The "Controller" suffix is automatically added by JavaFX to find the controller class.
    @FXML private FlightTableViewController flightTableViewController;
    @FXML private FlightEditorViewController flightEditorViewController;

    private AirlineDatabase database;
    private ScheduledFlight currentSelection; // Keep track of the selected flight here

    @FXML
    public void initialize() {
        // --- This is the wiring logic ---

        // 1. Listen for selection changes in the table.
        flightTableViewController.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    this.currentSelection = newSelection;
                    // When selection changes, tell the editor to show the new flight's details.
                    flightEditorViewController.showFlightDetails(newSelection);
                }
        );

        // 2. Define what happens when the "New" button in the editor is clicked.
        flightEditorViewController.setOnNewButtonClick(() -> {
            flightTableViewController.clearSelection(); // This will trigger the listener above.
        });

        // 3. Define what happens when the "Delete" button is clicked.
        flightEditorViewController.setOnDeleteButtonClick(() -> {
            if (currentSelection != null) {
                database.removeScheduledFlight(currentSelection);
                // After changing the database, refresh the table's view of it.
                flightTableViewController.showFlights(database.getScheduledFlights());
            }
        });

        // 4. Define what happens when the "Add" or "Update" button is clicked.
        flightEditorViewController.setOnAddUpdateButtonClick(() -> {
            try {
                if (currentSelection == null) { // ADD mode
                    ScheduledFlight newFlight = flightEditorViewController.createFlightFromFields();
                    database.addScheduledFlight(newFlight);
                } else { // UPDATE mode
                    flightEditorViewController.updateFlightFromFields(currentSelection);
                }
                // After adding or updating, refresh the table to show the latest data.
                flightTableViewController.showFlights(database.getScheduledFlights());
                flightTableViewController.refresh();
            } catch (Exception e) {
                // Show an error dialog if input is invalid (e.g., bad time format)
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Input Error");
                alert.setHeaderText("Could not save flight.");
                alert.setContentText("Please check your input. " + e.getMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * This method is called by the main Application class to pass in the database.
     */
    public void setDatabase(AirlineDatabase database) {
        this.database = database;
        // Give the initial data to the table controller to display.
        flightTableViewController.showFlights(database.getScheduledFlights());
    }
}
