package edu.au.cpsc.module3;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import javafx.application.Platform;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AirportController implements Initializable {

    // Search fields (read/write)
    @FXML
    private TextField identField;
    @FXML
    private TextField iataCodeField;
    @FXML
    private TextField localCodeField;

    // Display fields (read-only)
    @FXML
    private TextField typeField;
    @FXML
    private TextField nameField;
    @FXML
    private TextField elevationField;
    @FXML
    private TextField countryField;
    @FXML
    private TextField regionField;
    @FXML
    private TextField municipalityField;

    // Search button
    @FXML
    private Button searchButton;

    // Map view
    @FXML
    private WebView mapWebView;

    // Data
    private List<Airport> airports;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            // Load airport data
            airports = Airport.readAll();
            System.out.println("Loaded " + airports.size() + " airports");

            // Make display fields read-only
            typeField.setEditable(false);
            nameField.setEditable(false);
            elevationField.setEditable(false);
            countryField.setEditable(false);
            regionField.setEditable(false);
            municipalityField.setEditable(false);

            // Set up event handlers
            setupEventHandlers();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load airport data: " + e.getMessage());
        }
    }


    private void setupEventHandlers() {
        // Enter key handlers for search fields
        identField.setOnAction(event -> performSearch());
        iataCodeField.setOnAction(event -> performSearch());
        localCodeField.setOnAction(event -> performSearch());

        // Search button handler
        searchButton.setOnAction(event -> performSearch());
    }

    @FXML
    private void performSearch() {
        Airport foundAirport = null;

        // Search based on first non-blank field
        String ident = identField.getText();
        String iataCode = iataCodeField.getText();
        String localCode = localCodeField.getText();

        if (ident != null && !ident.trim().isEmpty()) {
            foundAirport = Airport.findByIdent(airports, ident.trim());
        } else if (iataCode != null && !iataCode.trim().isEmpty()) {
            foundAirport = Airport.findByIataCode(airports, iataCode.trim());
        } else if (localCode != null && !localCode.trim().isEmpty()) {
            foundAirport = Airport.findByLocalCode(airports, localCode.trim());
        }

        if (foundAirport != null) {
            displayAirport(foundAirport);
            updateMap(foundAirport);
        } else {
            clearFields();
            System.out.println("Airport not found");
        }
    }

    private void displayAirport(Airport airport) {
        // Fill in all fields
        identField.setText(airport.getIdent() != null ? airport.getIdent() : "");
        iataCodeField.setText(airport.getIataCode() != null ? airport.getIataCode() : "");
        localCodeField.setText(airport.getLocalCode() != null ? airport.getLocalCode() : "");

        typeField.setText(airport.getType() != null ? airport.getType() : "");
        nameField.setText(airport.getName() != null ? airport.getName() : "");
        elevationField.setText(airport.getElevationFt() != null ? airport.getElevationFt().toString() : "");
        countryField.setText(airport.getIsoCountry() != null ? airport.getIsoCountry() : "");
        regionField.setText(airport.getIsoRegion() != null ? airport.getIsoRegion() : "");
        municipalityField.setText(airport.getMunicipality() != null ? airport.getMunicipality() : "");
    }

    private void clearFields() {
        typeField.clear();
        nameField.clear();
        elevationField.clear();
        countryField.clear();
        regionField.clear();
        municipalityField.clear();
    }

    private void updateMap(Airport airport) {
        if (airport.getLatitude() != null && airport.getLongitude() != null) {
            // Note: Assignment mentions coordinates are longitude, latitude in CSV
            // but windy.com expects latitude, longitude in URL
            double lat = airport.getLatitude();
            double lon = airport.getLongitude();

            String windyUrl = String.format("https://www.windy.com/?%.8f,%.8f,12", lat, lon);

            Platform.runLater(() -> {
                try {
                    mapWebView.getEngine().load(windyUrl);
                    System.out.println("Loading map for: " + airport.getName() + " at " + lat + ", " + lon);
                } catch (Exception e) {
                    System.err.println("Error loading map: " + e.getMessage());
                }
            });
        }
    }
}