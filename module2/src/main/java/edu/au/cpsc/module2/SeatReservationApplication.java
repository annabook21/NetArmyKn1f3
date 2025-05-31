package edu.au.cpsc.module2;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.time.LocalDate;

public class SeatReservationApplication extends Application {

    // Instance variable for SeatReservation
    private SeatReservation seatReservation;

    // Controls for the SeatReservation fields
    private TextField flightDesignatorField;
    private DatePicker flightDatePicker;
    private TextField firstNameField;
    private TextField lastNameField;
    private Spinner<Integer> numberOfBagsSpinner;
    private CheckBox flyingWithInfantCheckBox;
    private TextField numberOfPassengersField;

    // Buttons
    private Button cancelButton;
    private Button saveButton;

    @Override
    public void start(Stage stage) {
        // Create and initialize SeatReservation instance with reasonable values
        seatReservation = new SeatReservation("AA123", LocalDate.of(2025, 6, 15), "John", "Smith");
        seatReservation.setNumberOfBags(2);
        seatReservation.makeFlyingWithInfant();

        // Create the main layout
        BorderPane borderPane = new BorderPane();

        // Create the center GridPane for form controls
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        // Create labels and controls
        createFormControls(gridPane);

        // Create button panel
        HBox buttonBox = createButtonPanel();

        // Set up the BorderPane
        borderPane.setCenter(gridPane);
        borderPane.setBottom(buttonBox);

        // Create and show the scene
        Scene scene = new Scene(borderPane, 400, 350);
        stage.setTitle("Seat Reservation Editor");

        // Update UI with initial values before showing
        updateUI();

        stage.setScene(scene);
        stage.show();
    }

    private void createFormControls(GridPane gridPane) {
        int row = 0;

        // Flight Designator
        gridPane.add(new Label("Flight Designator:"), 0, row);
        flightDesignatorField = new TextField();
        gridPane.add(flightDesignatorField, 1, row++);

        // Flight Date
        gridPane.add(new Label("Flight Date:"), 0, row);
        flightDatePicker = new DatePicker();
        flightDatePicker.setValue(LocalDate.now());
        gridPane.add(flightDatePicker, 1, row++);

        // First Name
        gridPane.add(new Label("First Name:"), 0, row);
        firstNameField = new TextField();
        gridPane.add(firstNameField, 1, row++);

        // Last Name
        gridPane.add(new Label("Last Name:"), 0, row);
        lastNameField = new TextField();
        gridPane.add(lastNameField, 1, row++);

        // Number of Bags
        gridPane.add(new Label("Number of Bags:"), 0, row);
        numberOfBagsSpinner = new Spinner<>(0, 10, 0);
        numberOfBagsSpinner.setEditable(true);
        gridPane.add(numberOfBagsSpinner, 1, row++);

        // Flying with Infant
        gridPane.add(new Label("Flying with Infant:"), 0, row);
        flyingWithInfantCheckBox = new CheckBox();

        // Add event handler for CheckBox
        flyingWithInfantCheckBox.setOnAction(e -> {
            if (flyingWithInfantCheckBox.isSelected()) {
                numberOfPassengersField.setText("2");
            } else {
                numberOfPassengersField.setText("1");
            }
        });

        gridPane.add(flyingWithInfantCheckBox, 1, row++);

        // Number of Passengers (read-only)
        gridPane.add(new Label("Number of Passengers:"), 0, row);
        numberOfPassengersField = new TextField("1");
        numberOfPassengersField.setEditable(false);
        numberOfPassengersField.setStyle("-fx-background-color: #f0f0f0;");
        gridPane.add(numberOfPassengersField, 1, row++);
    }

    private HBox createButtonPanel() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.TOP_RIGHT);
        buttonBox.setPadding(new Insets(10, 20, 20, 20));

        // Create buttons
        cancelButton = new Button("Cancel");
        saveButton = new Button("Save");

        // Add button event handlers (placeholder for now)
        cancelButton.setOnAction(e -> handleCancel());
        saveButton.setOnAction(e -> handleSave());

        buttonBox.getChildren().addAll(cancelButton, saveButton);
        return buttonBox;
    }

    private void handleCancel() {
        System.out.println("Cancel clicked");
        Platform.exit();
    }

    private void handleSave() {
        try {
            // Populate the seatReservation instance variable using setter methods
            seatReservation.setFlightDesignator(flightDesignatorField.getText());
            seatReservation.setFlightDate(flightDatePicker.getValue());
            seatReservation.setFirstName(firstNameField.getText());
            seatReservation.setLastName(lastNameField.getText());
            seatReservation.setNumberOfBags(numberOfBagsSpinner.getValue());

            if (flyingWithInfantCheckBox.isSelected()) {
                seatReservation.makeFlyingWithInfant();
            } else {
                seatReservation.makeNotFlyingWithInfant();
            }

            // Display the reservation and exit
            System.out.println(seatReservation.toString());
            Platform.exit();

        } catch (IllegalArgumentException e) {
            System.out.println("Error: Flight designator must be between 4 and 6 characters long.");
        }
    }


    // Method to update UI with values from seatReservation instance
    private void updateUI() {
        flightDesignatorField.setText(seatReservation.getFlightDesignator());
        flightDatePicker.setValue(seatReservation.getFlightDate());
        firstNameField.setText(seatReservation.getFirstName());
        lastNameField.setText(seatReservation.getLastName());
        numberOfBagsSpinner.getValueFactory().setValue(seatReservation.getNumberOfBags());
        flyingWithInfantCheckBox.setSelected(seatReservation.isFlyingWithInfant());

        // Update number of passengers based on infant status
        if (seatReservation.isFlyingWithInfant()) {
            numberOfPassengersField.setText("2");
        } else {
            numberOfPassengersField.setText("1");
        }
    }

    public static void main(String[] args) {
        launch();
    }
}