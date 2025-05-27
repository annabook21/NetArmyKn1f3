import java.time.LocalDate;

public class SeatReservation {
    // Private instance variables
    private String flightDesignator;
    private LocalDate flightDate;
    private String firstName;
    private String lastName;
    
    // Default constructor
    public SeatReservation() {
    }
    
    // Constructor with parameters
    public SeatReservation(String flightDesignator, LocalDate flightDate, String firstName, String lastName) {
        this.flightDesignator = flightDesignator;
        this.flightDate = flightDate;
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    // Getter and Setter methods
    public String getFlightDesignator() {
        return flightDesignator;
    }
    
    public void setFlightDesignator(String flightDesignator) {
        if (flightDesignator == null){
            throw new IllegalArgumentException("flight designator cannot be null");
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
    
    // toString method that returns a string representation of the SeatReservation object
    public String toString() {
        return "SeatReservation{flightDesignator=" + 
               (flightDesignator != null ? flightDesignator : "null") + 
               ", flightDate=" + 
               (flightDate != null ? flightDate.toString() : "null") + 
               ", firstName=" + 
               (firstName != null ? firstName : "null") + 
               ", lastName=" + 
               (lastName != null ? lastName : "null") + "}";
    }
}
