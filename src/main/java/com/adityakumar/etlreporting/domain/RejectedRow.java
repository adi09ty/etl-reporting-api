package com.adityakumar.etlreporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A CSV row that failed validation, kept with the reason so data problems can be fixed at the source. */
@Entity
@Table(name = "rejected_rows", indexes = @Index(name = "idx_rejected_run", columnList = "run_id"))
public class RejectedRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    /** Not named "row_number": that is a reserved word (a window function) in MySQL 8. */
    @Column(name = "source_row", nullable = false)
    private long rowNumber;

    @Column(name = "raw_data", length = 1000)
    private String rawData;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RejectedRow() {
        // required by JPA
    }

    public RejectedRow(Long runId, String sourceFile, long rowNumber, String rawData, String reason,
                       LocalDateTime createdAt) {
        this.runId = runId;
        this.sourceFile = sourceFile;
        this.rowNumber = rowNumber;
        this.rawData = rawData == null || rawData.length() <= 1000 ? rawData : rawData.substring(0, 1000);
        this.reason = reason.length() <= 255 ? reason : reason.substring(0, 255);
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getRowNumber() {
        return rowNumber;
    }

    public String getRawData() {
        return rawData;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
