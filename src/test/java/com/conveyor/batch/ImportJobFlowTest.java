package com.conveyor.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the real job end to end against H2: reader → processor → writer →
 * aggregation, including the skip/quarantine path with a deliberately
 * poisoned file.
 */
@SpringBootTest
@SpringBatchTest
class ImportJobFlowTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM throughput_records");
        jdbcTemplate.update("DELETE FROM daily_airport_summary");
        jdbcTemplate.update("DELETE FROM rejected_records");
    }

    private JobParameters parameters(String file) {
        return new JobParametersBuilder()
                .addString("inputFile", file)
                .addLong("launchedAt", System.nanoTime())
                .toJobParameters();
    }

    @Test
    void cleanFileLoadsStagingAndRecomputesSummary() throws Exception {
        JobExecution execution = jobLauncherTestUtils
                .launchJob(parameters("src/test/resources/data/clean_throughput.csv"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer staged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM throughput_records", Integer.class);
        assertThat(staged).isEqualTo(36);

        Integer summaries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_airport_summary", Integer.class);
        assertThat(summaries).isEqualTo(6); // 2 dates x 3 airports

        Long atlTotal = jdbcTemplate.queryForObject(
                "SELECT total_passengers FROM daily_airport_summary "
                        + "WHERE airport = 'ATL' AND summary_date = DATE '2026-07-01'",
                Long.class);
        assertThat(atlTotal).isEqualTo(2111L);

        Integer rejected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rejected_records", Integer.class);
        assertThat(rejected).isZero();
    }

    @Test
    void dirtyFileCompletesWithSkipsAndQuarantinesEveryBadRow() throws Exception {
        JobExecution execution = jobLauncherTestUtils
                .launchJob(parameters("src/test/resources/data/dirty_throughput.csv"));

        // bad rows are skipped within the limit, so the job still completes
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer staged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM throughput_records", Integer.class);
        assertThat(staged).isEqualTo(4); // only the good rows

        Integer rejected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rejected_records", Integer.class);
        assertThat(rejected).isEqualTo(6); // 5 validation failures + 1 unparseable line

        Integer negativeReason = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rejected_records WHERE reason LIKE '%negative%'",
                Integer.class);
        assertThat(negativeReason).isEqualTo(1);

        // summary only reflects clean data
        Integer summaries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_airport_summary", Integer.class);
        assertThat(summaries).isEqualTo(3); // 1 date x 3 airports from the good rows
    }
}
