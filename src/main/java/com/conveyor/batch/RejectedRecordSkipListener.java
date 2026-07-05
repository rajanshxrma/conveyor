package com.conveyor.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Every skipped row is written to the rejected_records quarantine table with
 * the raw content and the reason — rejected data is auditable and replayable,
 * not just a WARN line lost in the logs.
 */
@Component
@StepScope
public class RejectedRecordSkipListener implements SkipListener<ThroughputCsvRow, ThroughputRecord> {

    private static final Logger log = LoggerFactory.getLogger(RejectedRecordSkipListener.class);
    private static final String INSERT =
            "INSERT INTO rejected_records (source_file, raw_content, reason) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final String sourceFile;

    public RejectedRecordSkipListener(JdbcTemplate jdbcTemplate,
            @Value("#{jobParameters['inputFile']}") String sourceFile) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceFile = sourceFile;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        String rawLine = null;
        String reason = t.getMessage();
        if (t instanceof FlatFileParseException parseException) {
            rawLine = parseException.getInput();
            reason = "line " + parseException.getLineNumber() + " failed to parse: "
                    + rootMessage(parseException);
        }
        jdbcTemplate.update(INSERT, sourceFile, truncate(rawLine, 1000), truncate(reason, 500));
        log.warn("Rejected unreadable line from {}: {}", sourceFile, reason);
    }

    @Override
    public void onSkipInProcess(ThroughputCsvRow item, Throwable t) {
        jdbcTemplate.update(INSERT, sourceFile, truncate(item.toString(), 1000),
                truncate(t.getMessage(), 500));
        log.warn("Rejected row from {}: {}", sourceFile, t.getMessage());
    }

    @Override
    public void onSkipInWrite(ThroughputRecord item, Throwable t) {
        jdbcTemplate.update(INSERT, sourceFile, truncate(String.valueOf(item.getRecordDate())
                + "," + item.getAirport() + "," + item.getCheckpoint(), 1000),
                truncate("write failed: " + t.getMessage(), 500));
        log.warn("Rejected record at write time from {}: {}", sourceFile, t.getMessage());
    }

    private String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
