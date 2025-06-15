package edu.au.cpsc.module4;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public class ScheduledFlight implements Serializable {

    private String flightDesignator;
    private String departureAirportIdent;
    private LocalTime departureTime; // This is a LocalTime
    private String arrivalAirportIdent;
    private LocalTime arrivalTime;   // This is also a LocalTime
    private Set<DayOfWeek> daysOfWeek;

    /**
     * This is the constructor that needs to be corrected.
     * Make sure the parameters for time are LocalTime, not String.
     */
    public ScheduledFlight(String flightDesignator, String departureAirportIdent, LocalTime departureTime,
                           String arrivalAirportIdent, LocalTime arrivalTime, Set<DayOfWeek> daysOfWeek) {

        setFlightDesignator(flightDesignator);
        setDepartureAirportIdent(departureAirportIdent);
        setDepartureTime(departureTime); // This setter expects LocalTime
        setArrivalAirportIdent(arrivalAirportIdent);
        setArrivalTime(arrivalTime);     // This setter expects LocalTime
        setDaysOfWeek(daysOfWeek);
    }

    // --- Getters ---
    public String getFlightDesignator() { return flightDesignator; }
    public String getDepartureAirportIdent() { return departureAirportIdent; }
    public LocalTime getDepartureTime() { return departureTime; }
    public String getArrivalAirportIdent() { return arrivalAirportIdent; }
    public LocalTime getArrivalTime() { return arrivalTime; }
    public Set<DayOfWeek> getDaysOfWeek() { return daysOfWeek; }

    // --- Setters with Validation ---
    public void setFlightDesignator(String flightDesignator) {
        if (flightDesignator == null || flightDesignator.isBlank()) {
            throw new IllegalArgumentException("Flight designator cannot be null or empty.");
        }
        this.flightDesignator = flightDesignator;
    }

    public void setDepartureAirportIdent(String departureAirportIdent) {
        if (departureAirportIdent == null || departureAirportIdent.isBlank()) {
            throw new IllegalArgumentException("Departure airport identifier cannot be null or empty.");
        }
        this.departureAirportIdent = departureAirportIdent;
    }

    public void setDepartureTime(LocalTime departureTime) {
        if (departureTime == null) {
            throw new IllegalArgumentException("Departure time cannot be null.");
        }
        this.departureTime = departureTime;
    }

    public void setArrivalAirportIdent(String arrivalAirportIdent) {
        if (arrivalAirportIdent == null || arrivalAirportIdent.isBlank()) {
            throw new IllegalArgumentException("Arrival airport identifier cannot be null or empty.");
        }
        this.arrivalAirportIdent = arrivalAirportIdent;
    }

    public void setArrivalTime(LocalTime arrivalTime) {
        if (arrivalTime == null) {
            throw new IllegalArgumentException("Arrival time cannot be null.");
        }
        this.arrivalTime = arrivalTime;
    }

    public void setDaysOfWeek(Set<DayOfWeek> daysOfWeek) {
        if (daysOfWeek == null) {
            throw new IllegalArgumentException("Days of week collection cannot be null.");
        }
        this.daysOfWeek = new HashSet<>(daysOfWeek);
    }
}
