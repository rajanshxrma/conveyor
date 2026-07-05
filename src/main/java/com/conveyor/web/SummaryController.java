package com.conveyor.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SummaryController {

    public record SummaryDto(LocalDate summaryDate, String airport, long totalPassengers,
            int checkpointsReporting, int avgHourlyPassengers) {
    }

    public record RejectionDto(long id, String sourceFile, String rawContent, String reason,
            LocalDateTime rejectedAt) {
    }

    private final JdbcTemplate jdbcTemplate;

    public SummaryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/summaries")
    public List<SummaryDto> summaries(@RequestParam(required = false) String airport) {
        String base = "SELECT summary_date, airport, total_passengers, checkpoints_reporting, "
                + "avg_hourly_passengers FROM daily_airport_summary";

        if (airport != null && !airport.isBlank()) {
            return jdbcTemplate.query(base + " WHERE airport = ? ORDER BY summary_date, airport",
                    this::mapSummary, airport.trim().toUpperCase());
        }
        return jdbcTemplate.query(base + " ORDER BY summary_date, airport", this::mapSummary);
    }

    @GetMapping("/rejections")
    public List<RejectionDto> rejections() {
        return jdbcTemplate.query(
                "SELECT id, source_file, raw_content, reason, rejected_at FROM rejected_records "
                        + "ORDER BY id DESC LIMIT 50",
                (rs, rowNum) -> new RejectionDto(
                        rs.getLong("id"),
                        rs.getString("source_file"),
                        rs.getString("raw_content"),
                        rs.getString("reason"),
                        rs.getTimestamp("rejected_at").toLocalDateTime()));
    }

    private SummaryDto mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SummaryDto(
                rs.getDate("summary_date").toLocalDate(),
                rs.getString("airport"),
                rs.getLong("total_passengers"),
                rs.getInt("checkpoints_reporting"),
                rs.getInt("avg_hourly_passengers"));
    }
}
