package com.conveyor.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM throughput_records");
        jdbcTemplate.update("DELETE FROM daily_airport_summary");
        jdbcTemplate.update("DELETE FROM rejected_records");
    }

    @Test
    @SuppressWarnings("unchecked")
    void launchOverRestThenPollUntilComplete() throws Exception {
        ResponseEntity<Map> launched = rest.postForEntity("/api/v1/jobs/import",
                Map.of("file", "src/test/resources/data/clean_throughput.csv"), Map.class);

        assertThat(launched.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Number executionId = (Number) launched.getBody().get("executionId");
        assertThat(executionId).isNotNull();

        String status = "";
        for (int i = 0; i < 80 && !"COMPLETED".equals(status); i++) {
            Thread.sleep(250);
            ResponseEntity<Map> poll = rest.getForEntity("/api/v1/jobs/" + executionId, Map.class);
            status = String.valueOf(poll.getBody().get("status"));
            if ("FAILED".equals(status)) {
                break;
            }
        }
        assertThat(status).isEqualTo("COMPLETED");

        ResponseEntity<List> summaries = rest.getForEntity("/api/v1/summaries", List.class);
        assertThat(summaries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summaries.getBody()).isNotEmpty();

        ResponseEntity<List> filtered = rest.getForEntity("/api/v1/summaries?airport=atl", List.class);
        assertThat(filtered.getBody()).isNotEmpty();
    }

    @Test
    void missingFileIsRejectedUpFront() {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/jobs/import",
                Map.of("file", "no/such/file.csv"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void blankFileFailsValidation() {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/jobs/import",
                Map.of("file", " "), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownExecutionIdIsNotFound() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/jobs/999999", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
