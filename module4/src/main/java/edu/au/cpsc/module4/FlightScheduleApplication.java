package edu.au.cpsc.module4;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FlightScheduleApplication extends Application {

    private static final String DB_FILE_PATH = "airline.db";
    private AirlineDatabase database;

    @Override
    public void init() {
        // Load the database from a file when the app starts
        File dbFile = new File(DB_FILE_PATH);
        if (dbFile.exists()) {
            try (FileInputStream fis = new FileInputStream(dbFile)) {
                database = AirlineDatabaseIO.load(fis);
            } catch (Exception e) {
                System.err.println("Error loading database: " + e.getMessage());
                database = new AirlineDatabase(); // Start fresh on error
            }
        } else {
            database = new AirlineDatabase(); // Create new if it doesn't exist
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        // This needs to load the MAIN layout, not just the table
        FXMLLoader fxmlLoader = new FXMLLoader(FlightScheduleApplication.class.getResource("flight-schedule-app.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        // This is the critical step to pass the database to the controllers
        FlightScheduleApplicationController controller = fxmlLoader.getController();
        controller.setDatabase(database);

        stage.setTitle("Flight Schedule Application");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // Save the database to a file when the app closes
        try (FileOutputStream fos = new FileOutputStream(DB_FILE_PATH)) {
            AirlineDatabaseIO.save(database, fos);
        } catch (IOException e) {
            System.err.println("Error saving database: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
