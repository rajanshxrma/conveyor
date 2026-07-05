package com.conveyor.batch;

import java.time.LocalDate;

/**
 * Clean, typed record ready for the staging table.
 */
public class ThroughputRecord {

    private LocalDate recordDate;
    private String airport;
    private String checkpoint;
    private int hourOfDay;
    private int passengerCount;
    private String sourceFile;

    public ThroughputRecord() {
    }

    public ThroughputRecord(LocalDate recordDate, String airport, String checkpoint,
            int hourOfDay, int passengerCount, String sourceFile) {
        this.recordDate = recordDate;
        this.airport = airport;
        this.checkpoint = checkpoint;
        this.hourOfDay = hourOfDay;
        this.passengerCount = passengerCount;
        this.sourceFile = sourceFile;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
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

    public int getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(int hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = passengerCount;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }
}
