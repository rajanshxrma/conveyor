package com.conveyor.batch;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.validator.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where raw CSV rows either become clean records or get rejected with a
 * reason. Every throw here is a skip (within the step's skip limit), lands in
 * rejected_records via the skip listener, and never kills the whole job.
 */
@Component
@StepScope
public class ThroughputProcessor implements ItemProcessor<ThroughputCsvRow, ThroughputRecord> {

    private final String sourceFile;

    public ThroughputProcessor(@Value("#{jobParameters['inputFile']}") String sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public ThroughputRecord process(ThroughputCsvRow row) {
        LocalDate date = parseDate(row);
        String airport = normalizeAirport(row);
        String checkpoint = requireCheckpoint(row);
        int hour = parseHour(row);
        int passengers = parsePassengers(row);

        return new ThroughputRecord(date, airport, checkpoint, hour, passengers, sourceFile);
    }

    private LocalDate parseDate(ThroughputCsvRow row) {
        try {
            LocalDate date = LocalDate.parse(row.getDate());
            if (date.isAfter(LocalDate.now())) {
                throw new ValidationException("date is in the future: " + row);
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new ValidationException("unparseable date '" + row.getDate() + "': " + row);
        }
    }

    private String normalizeAirport(ThroughputCsvRow row) {
        String airport = row.getAirport() == null ? "" : row.getAirport().trim().toUpperCase();
        if (!airport.matches("[A-Z]{3}")) {
            throw new ValidationException("airport must be a 3-letter IATA code, got '"
                    + row.getAirport() + "': " + row);
        }
        return airport;
    }

    private String requireCheckpoint(ThroughputCsvRow row) {
        if (row.getCheckpoint() == null || row.getCheckpoint().isBlank()) {
            throw new ValidationException("checkpoint is blank: " + row);
        }
        return row.getCheckpoint().trim();
    }

    private int parseHour(ThroughputCsvRow row) {
        int hour = parseInt(row.getHour(), "hour", row);
        if (hour < 0 || hour > 23) {
            throw new ValidationException("hour out of range 0-23, got " + hour + ": " + row);
        }
        return hour;
    }

    private int parsePassengers(ThroughputCsvRow row) {
        int passengers = parseInt(row.getPassengers(), "passengers", row);
        if (passengers < 0) {
            throw new ValidationException("passenger count is negative: " + row);
        }
        return passengers;
    }

    private int parseInt(String value, String field, ThroughputCsvRow row) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("unparseable " + field + " '" + value + "': " + row);
        }
    }
}
