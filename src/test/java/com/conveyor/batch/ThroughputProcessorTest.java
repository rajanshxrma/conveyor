package com.conveyor.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.validator.ValidationException;

class ThroughputProcessorTest {

    private final ThroughputProcessor processor = new ThroughputProcessor("unit-test.csv");

    private ThroughputCsvRow row(String date, String airport, String checkpoint, String hour,
            String passengers) {
        ThroughputCsvRow row = new ThroughputCsvRow();
        row.setDate(date);
        row.setAirport(airport);
        row.setCheckpoint(checkpoint);
        row.setHour(hour);
        row.setPassengers(passengers);
        return row;
    }

    @Test
    void validRowIsParsedAndNormalized() {
        ThroughputRecord record = processor.process(row("2026-07-01", "atl", " Main ", "6", "412"));

        assertThat(record.getRecordDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(record.getAirport()).isEqualTo("ATL");
        assertThat(record.getCheckpoint()).isEqualTo("Main");
        assertThat(record.getHourOfDay()).isEqualTo(6);
        assertThat(record.getPassengerCount()).isEqualTo(412);
        assertThat(record.getSourceFile()).isEqualTo("unit-test.csv");
    }

    @Test
    void garbageDateIsRejected() {
        assertThatThrownBy(() -> processor.process(row("07/01/2026", "ATL", "Main", "6", "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("unparseable date");
    }

    @Test
    void futureDateIsRejected() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertThatThrownBy(() -> processor.process(row(tomorrow, "ATL", "Main", "6", "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("future");
    }

    @Test
    void nonIataAirportIsRejected() {
        assertThatThrownBy(() -> processor.process(row("2026-07-01", "atlanta", "Main", "6", "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("IATA");
    }

    @Test
    void hourOutOfRangeIsRejected() {
        assertThatThrownBy(() -> processor.process(row("2026-07-01", "ATL", "Main", "30", "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void negativePassengerCountIsRejected() {
        assertThatThrownBy(() -> processor.process(row("2026-07-01", "ATL", "Main", "6", "-50")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void blankCheckpointIsRejected() {
        assertThatThrownBy(() -> processor.process(row("2026-07-01", "ATL", "  ", "6", "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("checkpoint");
    }
}
