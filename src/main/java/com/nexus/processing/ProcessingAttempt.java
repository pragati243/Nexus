package com.nexus.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row per consume attempt — see V11__create_processing_attempts.sql for the tenant_id note. */
@Entity
@Table(name = "processing_attempts")
public class ProcessingAttempt {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ingestion_job_id", nullable = false)
    private UUID ingestionJobId;

    @Column(name = "sqs_message_id", nullable = false)
    private String sqsMessageId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingOutcome outcome;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    protected ProcessingAttempt() {
    }

    public ProcessingAttempt(UUID id, UUID tenantId, UUID ingestionJobId, String sqsMessageId,
            int attemptNumber, ProcessingOutcome outcome, String errorDetail, Instant startedAt,
            Instant finishedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.ingestionJobId = ingestionJobId;
        this.sqsMessageId = sqsMessageId;
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.errorDetail = errorDetail;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getIngestionJobId() {
        return ingestionJobId;
    }

    public String getSqsMessageId() {
        return sqsMessageId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public ProcessingOutcome getOutcome() {
        return outcome;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
