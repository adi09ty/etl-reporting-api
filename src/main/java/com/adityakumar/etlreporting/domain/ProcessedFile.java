package com.adityakumar.etlreporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Remembers each file (by key and content ETag) that was loaded, in the same transaction as its rows.
 * A file that is seen again, for example because moving it failed, is skipped instead of loaded twice.
 */
@Entity
@Table(name = "processed_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_file", columnNames = {"object_key", "etag"}))
public class ProcessedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(nullable = false, length = 64)
    private String etag;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedFile() {
        // required by JPA
    }

    public ProcessedFile(String objectKey, String etag, Long runId, LocalDateTime processedAt) {
        this.objectKey = objectKey;
        this.etag = etag;
        this.runId = runId;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getEtag() {
        return etag;
    }

    public Long getRunId() {
        return runId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
