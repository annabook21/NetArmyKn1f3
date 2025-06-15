package edu.au.cpsc.module4;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class FlightScheduleController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}