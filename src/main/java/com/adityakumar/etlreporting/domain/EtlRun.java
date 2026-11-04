package com.adityakumar.etlreporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** The record of one job execution: what ran, how it ended and how many rows it handled. */
@Entity
@Table(name = "etl_runs")
public class EtlRun {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_name", nullable = false, length = 20)
    private JobName jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private TriggerType trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "files_processed", nullable = false)
    private int filesProcessed;

    @Column(name = "files_failed", nullable = false)
    private int filesFailed;

    @Column(name = "rows_read", nullable = false)
    private int rowsRead;

    @Column(name = "rows_inserted", nullable = false)
    private int rowsInserted;

    @Column(name = "rows_updated", nullable = false)
    private int rowsUpdated;

    @Column(name = "rows_rejected", nullable = false)
    private int rowsRejected;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH)
    private String errorMessage;

    protected EtlRun() {
        // required by JPA
    }

    public EtlRun(JobName jobName, TriggerType trigger, LocalDateTime startedAt) {
        this.jobName = jobName;
        this.trigger = trigger;
        this.startedAt = startedAt;
    }

    public void addFileResult(int read, int inserted, int updated, int rejected) {
        filesProcessed++;
        rowsRead += read;
        rowsInserted += inserted;
        rowsUpdated += updated;
        rowsRejected += rejected;
    }

    public void addFileFailure(String message) {
        filesFailed++;
        appendError(message);
    }

    /** For jobs that load a batch of rows without files (the exchange-rate refresh). */
    public void addRows(int read, int inserted, int updated) {
        rowsRead += read;
        rowsInserted += inserted;
        rowsUpdated += updated;
    }

    public void fail(String message) {
        status = RunStatus.FAILED;
        appendError(message);
    }

    /** Sets the final status (unless the run already failed outright) and the finish time. */
    public void finish(LocalDateTime now) {
        if (status == RunStatus.RUNNING) {
            if (filesFailed == 0) {
                status = RunStatus.SUCCEEDED;
            } else {
                status = filesProcessed > 0 ? RunStatus.PARTIAL : RunStatus.FAILED;
            }
        }
        finishedAt = now;
    }

    private void appendError(String message) {
        String combined = errorMessage == null ? message : errorMessage + " | " + message;
        errorMessage = combined.length() <= MAX_ERROR_LENGTH ? combined : combined.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public JobName getJobName() {
        return jobName;
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    public RunStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    public int getFilesFailed() {
        return filesFailed;
    }

    public int getRowsRead() {
        return rowsRead;
    }

    public int getRowsInserted() {
        return rowsInserted;
    }

    public int getRowsUpdated() {
        return rowsUpdated;
    }

    public int getRowsRejected() {
        return rowsRejected;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
