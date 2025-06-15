package edu.au.cpsc.module4;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FlightTableViewController {

    @FXML
    private TableView<ScheduledFlight> flightTableView;
    @FXML
    private TableColumn<ScheduledFlight, String> designatorCol;
    @FXML
    private TableColumn<ScheduledFlight, String> departureCol;
    @FXML
    private TableColumn<ScheduledFlight, String> arrivalCol;
    @FXML
    private TableColumn<ScheduledFlight, String> daysCol;

    // This list holds the data currently shown in the table.
    private final ObservableList<ScheduledFlight> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Link the table to our observable list
        flightTableView.setItems(tableData);

        // Configure the columns to get data from the ScheduledFlight object's properties
        designatorCol.setCellValueFactory(new PropertyValueFactory<>("flightDesignator"));
        departureCol.setCellValueFactory(new PropertyValueFactory<>("departureAirportIdent"));
        arrivalCol.setCellValueFactory(new PropertyValueFactory<>("arrivalAirportIdent"));

        // The "days of week" column needs custom formatting
        daysCol.setCellValueFactory(cellData -> {
            Set<DayOfWeek> days = cellData.getValue().getDaysOfWeek();
            String dayString = days.stream()
                    .map(this::dayOfWeekToChar)
                    .sorted()
                    .collect(Collectors.joining(" "));
            return new javafx.beans.property.SimpleStringProperty(dayString);
        });
    }

    /**
     * Public method for the main controller to pass flight data to this table.
     */
    public void showFlights(List<ScheduledFlight> flights) {
        tableData.setAll(flights);
    }

    /**
     * Exposes the table's selection model so the main controller can listen to it.
     * This is a CRITICAL method for component communication.
     */
    public TableView.TableViewSelectionModel<ScheduledFlight> getSelectionModel() {
        return flightTableView.getSelectionModel();
    }

    /**
     * Allows the main controller to programmatically clear the selection.
     */
    public void clearSelection() {
        flightTableView.getSelectionModel().clearSelection();
    }

    /**
     * Allows the main controller to refresh the table display.
     */
    public void refresh() {
        flightTableView.refresh();
    }

    private String dayOfWeekToChar(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "M";
            case TUESDAY -> "T";
            case WEDNESDAY -> "W";
            case THURSDAY -> "R";
            case FRIDAY -> "F";
            case SATURDAY -> "S";
            case SUNDAY -> "U";
        };
    }
}
