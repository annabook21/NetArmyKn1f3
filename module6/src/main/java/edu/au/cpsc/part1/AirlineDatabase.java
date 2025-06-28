package edu.au.cpsc.part1;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AirlineDatabase implements Serializable {

    // This list will hold all the flight schedules.
    private final List<ScheduledFlight> scheduledFlights;

    public AirlineDatabase() {
        this.scheduledFlights = new ArrayList<>();
    }

    /**
     * Returns a copy of the list of flights to prevent external modification.
     */
    public List<ScheduledFlight> getScheduledFlights() {
        return new ArrayList<>(scheduledFlights);
    }

    public void addScheduledFlight(ScheduledFlight sf) {
        if (sf != null) {
            scheduledFlights.add(sf);
        }
    }

    public void removeScheduledFlight(ScheduledFlight sf) {
        scheduledFlights.remove(sf);
    }

    public void updateScheduledFlight(ScheduledFlight oldSf, ScheduledFlight newSf) {
        // This is a simple way to update. Find the old one and replace it.
        int index = scheduledFlights.indexOf(oldSf);
        if (index != -1) { // -1 means the object was not found
            scheduledFlights.set(index, newSf);
        }
    }
}
