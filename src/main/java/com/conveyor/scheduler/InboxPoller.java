package com.conveyor.scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Classic ETL inbox pattern: drop a CSV into inbox/ and it gets picked up,
 * processed, and moved to archive/ (success) or failed/ (anything else).
 * Runs synchronously on purpose — the file move depends on the job outcome.
 */
@Component
@ConditionalOnProperty(prefix = "conveyor.inbox", name = "enabled", havingValue = "true")
public class InboxPoller {

    private static final Logger log = LoggerFactory.getLogger(InboxPoller.class);

    private final JobLauncher jobLauncher;
    private final Job importThroughputJob;
    private final Path inbox;
    private final Path archive;
    private final Path failed;

    public InboxPoller(JobLauncher jobLauncher, Job importThroughputJob,
            @Value("${conveyor.inbox.directory}") String inboxDir,
            @Value("${conveyor.inbox.archive-directory}") String archiveDir,
            @Value("${conveyor.inbox.failed-directory}") String failedDir) {
        this.jobLauncher = jobLauncher;
        this.importThroughputJob = importThroughputJob;
        this.inbox = Path.of(inboxDir);
        this.archive = Path.of(archiveDir);
        this.failed = Path.of(failedDir);
    }

    @Scheduled(fixedDelayString = "${conveyor.inbox.poll-ms}")
    public void poll() throws IOException {
        Files.createDirectories(inbox);
        Files.createDirectories(archive);
        Files.createDirectories(failed);

        List<Path> files;
        try (Stream<Path> stream = Files.list(inbox)) {
            files = stream
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted()
                    .toList();
        }

        for (Path file : files) {
            processOne(file);
        }
    }

    private void processOne(Path file) {
        log.info("Picked up {}", file);
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addString("inputFile", file.toString())
                    .addLong("launchedAt", System.currentTimeMillis())
                    .toJobParameters();
            JobExecution execution = jobLauncher.run(importThroughputJob, parameters);

            if (execution.getStatus() == BatchStatus.COMPLETED) {
                move(file, archive);
            } else {
                log.error("Job for {} ended with status {}", file, execution.getStatus());
                move(file, failed);
            }
        } catch (Exception e) {
            log.error("Failed to process {}", file, e);
            move(file, failed);
        }
    }

    private void move(Path file, Path targetDir) {
        try {
            Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved {} to {}/", file.getFileName(), targetDir);
        } catch (IOException e) {
            log.error("Could not move {} to {}", file, targetDir, e);
        }
    }
}
