package com.conveyor.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        long read = 0;
        long written = 0;
        long skipped = 0;
        for (StepExecution step : jobExecution.getStepExecutions()) {
            read += step.getReadCount();
            written += step.getWriteCount();
            skipped += step.getSkipCount();
        }
        log.info("Job {} finished with status {} — read {}, written {}, rejected {} (file: {})",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                read, written, skipped,
                jobExecution.getJobParameters().getString("inputFile"));
    }
}
