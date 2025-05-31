package edu.au.cpsc.module2;

import java.time.LocalDate;

public class SeatReservation {
    private String flightDesignator;
    private LocalDate flightDate;
    private String firstName;
    private String lastName;
    private int numberOfBags;
    private boolean flyingWithInfant;

    public SeatReservation() {
    }

    public SeatReservation(String flightDesignator, LocalDate flightDate, String firstName, String lastName) {
        setFlightDesignator(flightDesignator);
        this.flightDate = flightDate;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFlightDesignator() {
        return flightDesignator;
    }

    public void setFlightDesignator(String flightDesignator) {
        if (flightDesignator == null || flightDesignator.length() < 4 || flightDesignator.length() > 6) {
            throw new IllegalArgumentException();
        }
        this.flightDesignator = flightDesignator;
    }

    public LocalDate getFlightDate() {
        return flightDate;
    }

    public void setFlightDate(LocalDate flightDate) {
        this.flightDate = flightDate;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getNumberOfBags() {
        return numberOfBags;
    }

    public void setNumberOfBags(int numberOfBags) {
        this.numberOfBags = numberOfBags;
    }

    public boolean isFlyingWithInfant() {
        return flyingWithInfant;
    }

    public void makeFlyingWithInfant() {
        this.flyingWithInfant = true;
    }

    public void makeNotFlyingWithInfant() {
        this.flyingWithInfant = false;
    }

    public String toString() {
        return "SeatReservation{flightDesignator=" + flightDesignator + ",flightDate=" + flightDate + ",firstName=" + firstName + ",lastName=" + lastName + ",numberOfBags=" + numberOfBags + ",flyingWithInfant=" + flyingWithInfant + "}";
    }
}