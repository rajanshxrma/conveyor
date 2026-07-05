package com.conveyor.config;

import com.conveyor.batch.JobCompletionListener;
import com.conveyor.batch.RejectedRecordSkipListener;
import com.conveyor.batch.ThroughputCsvRow;
import com.conveyor.batch.ThroughputProcessor;
import com.conveyor.batch.ThroughputRecord;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.validator.ValidationException;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    /**
     * Reader is step-scoped so the input file comes from job parameters —
     * one job definition, any number of files.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<ThroughputCsvRow> throughputReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return new FlatFileItemReaderBuilder<ThroughputCsvRow>()
                .name("throughputReader")
                .resource(new FileSystemResource(inputFile))
                .linesToSkip(1)
                .delimited()
                .names("date", "airport", "checkpoint", "hour", "passengers")
                .targetType(ThroughputCsvRow.class)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<ThroughputRecord> throughputWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<ThroughputRecord>()
                .dataSource(dataSource)
                .sql("INSERT INTO throughput_records "
                        + "(record_date, airport, checkpoint, hour_of_day, passenger_count, source_file) "
                        + "VALUES (:recordDate, :airport, :checkpoint, :hourOfDay, :passengerCount, :sourceFile)")
                .beanMapped()
                .build();
    }

    @Bean
    public Step ingestStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            FlatFileItemReader<ThroughputCsvRow> throughputReader, ThroughputProcessor throughputProcessor,
            JdbcBatchItemWriter<ThroughputRecord> throughputWriter,
            RejectedRecordSkipListener skipListener) {
        return new StepBuilder("ingestStep", jobRepository)
                .<ThroughputCsvRow, ThroughputRecord>chunk(100, transactionManager)
                .reader(throughputReader)
                .processor(throughputProcessor)
                .writer(throughputWriter)
                .faultTolerant()
                .skip(ValidationException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(50)
                .listener(skipListener)
                .build();
    }

    /**
     * Warehouse step: recomputes the daily summary from staging. Deliberately
     * a full recompute — idempotent, restart-safe, and at this scale the
     * simplicity beats incremental merge logic.
     */
    @Bean
    public Step aggregateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        Tasklet recomputeSummary = (contribution, chunkContext) -> {
            jdbcTemplate.update("DELETE FROM daily_airport_summary");
            jdbcTemplate.update("INSERT INTO daily_airport_summary "
                    + "(summary_date, airport, total_passengers, checkpoints_reporting, avg_hourly_passengers) "
                    + "SELECT record_date, airport, SUM(passenger_count), COUNT(DISTINCT checkpoint), "
                    + "CAST(AVG(passenger_count) AS INT) "
                    + "FROM throughput_records GROUP BY record_date, airport");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("aggregateStep", jobRepository)
                .tasklet(recomputeSummary, transactionManager)
                .build();
    }

    @Bean
    public Job importThroughputJob(JobRepository jobRepository, Step ingestStep, Step aggregateStep,
            JobCompletionListener jobCompletionListener) {
        return new JobBuilder("importThroughputJob", jobRepository)
                .listener(jobCompletionListener)
                .start(ingestStep)
                .next(aggregateStep)
                .build();
    }

    /**
     * Async launcher for the REST trigger: the HTTP request returns 202
     * immediately with an execution id to poll. The inbox poller uses the
     * default synchronous launcher instead, because it needs the outcome to
     * decide between archive/ and failed/.
     */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}
