package com.conveyor.batch;

/**
 * Raw CSV row exactly as read — everything is a String on purpose.
 * Parsing and validation happen in the processor so that every bad value
 * becomes a skippable, auditable rejection instead of a reader crash.
 */
public class ThroughputCsvRow {

    private String date;
    private String airport;
    private String checkpoint;
    private String hour;
    private String passengers;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getAirport() {
        return airport;
    }

    public void setAirport(String airport) {
        this.airport = airport;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(String checkpoint) {
        this.checkpoint = checkpoint;
    }

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    public String getPassengers() {
        return passengers;
    }

    public void setPassengers(String passengers) {
        this.passengers = passengers;
    }

    @Override
    public String toString() {
        return date + "," + airport + "," + checkpoint + "," + hour + "," + passengers;
    }
}
