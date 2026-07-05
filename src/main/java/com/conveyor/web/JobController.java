package com.conveyor.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    public record LaunchRequest(@NotBlank(message = "file is required") String file) {
    }

    public record StepSummary(String name, long read, long written, long skipped) {
    }

    public record ExecutionDto(long executionId, String jobName, String status, String exitCode,
            LocalDateTime startTime, LocalDateTime endTime, List<StepSummary> steps) {
    }

    private final JobLauncher asyncJobLauncher;
    private final Job importThroughputJob;
    private final JobExplorer jobExplorer;

    public JobController(JobLauncher asyncJobLauncher, Job importThroughputJob, JobExplorer jobExplorer) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.importThroughputJob = importThroughputJob;
        this.jobExplorer = jobExplorer;
    }

    @PostMapping("/import")
    public ResponseEntity<?> launch(@Valid @RequestBody LaunchRequest request) throws Exception {
        if (!Files.exists(Path.of(request.file()))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "file not found: " + request.file()));
        }

        JobParameters parameters = new JobParametersBuilder()
                .addString("inputFile", request.file())
                .addLong("launchedAt", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = asyncJobLauncher.run(importThroughputJob, parameters);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "executionId", execution.getId(),
                "status", execution.getStatus().name(),
                "statusUrl", "/api/v1/jobs/" + execution.getId()));
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<ExecutionDto> status(@PathVariable long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        List<StepSummary> steps = execution.getStepExecutions().stream()
                .map(this::toStepSummary)
                .toList();

        return ResponseEntity.ok(new ExecutionDto(
                execution.getId(),
                execution.getJobInstance().getJobName(),
                execution.getStatus().name(),
                execution.getExitStatus().getExitCode(),
                execution.getStartTime(),
                execution.getEndTime(),
                steps));
    }

    private StepSummary toStepSummary(StepExecution step) {
        return new StepSummary(step.getStepName(), step.getReadCount(), step.getWriteCount(),
                step.getSkipCount());
    }
}
