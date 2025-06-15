package edu.au.cpsc.module4;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

// Implement Serializable to allow objects of this class to be written to/read from files.
public class ScheduledFlight implements Serializable {

    private String flightDesignator;
    private String departureAirportIdent;
    private LocalTime departureTime;
    private String arrivalAirportIdent;
    private LocalTime arrivalTime;
    private Set<DayOfWeek> daysOfWeek;

    public ScheduledFlight(String flightDesignator, String departureAirportIdent, LocalTime departureTime,
                           String arrivalAirportIdent, LocalTime arrivalTime, Set<DayOfWeek> daysOfWeek) {
        // Use setters in the constructor to leverage validation
        setFlightDesignator(flightDesignator);
        setDepartureAirportIdent(departureAirportIdent);
        setDepartureTime(departureTime);
        setArrivalAirportIdent(arrivalAirportIdent);
        setArrivalTime(arrivalTime);
        setDaysOfWeek(daysOfWeek);
    }

    // --- Getters ---
    public String getFlightDesignator() {
        return flightDesignator;
    }

    public String getDepartureAirportIdent() {
        return departureAirportIdent;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public String getArrivalAirportIdent() {
        return arrivalAirportIdent;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public Set<DayOfWeek> getDaysOfWeek() {
        return daysOfWeek;
    }

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
        // Create a new HashSet to ensure the set is mutable and owned by this object
        this.daysOfWeek = new HashSet<>(daysOfWeek);
    }
}

