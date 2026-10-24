package com.nexus.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Foreign keys ({@code tenantId}, {@code integrationId}) are plain
 * {@code UUID} columns, not JPA {@code @ManyToOne} associations, across
 * every entity in this module — a deliberate choice, not an oversight.
 * Mapping tenant-scoped FKs as object associations invites exactly the
 * unbounded-eager/N+1 risk CLAUDE.md and docs/build-plan.md Phase 2 warn
 * against: a lazy proxy that gets accidentally initialized outside its
 * transaction, or an eager default nobody remembered to override. A plain id
 * column can't be accidentally fetched — a caller that needs the parent
 * looks it up explicitly through its own repository.
 */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionJobStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "source_object_key")
    private String sourceObjectKey;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "valid_rows")
    private Integer validRows;

    @Column(name = "error_rows")
    private Integer errorRows;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected IngestionJob() {
    }

    public IngestionJob(UUID id, UUID tenantId, UUID integrationId, IngestionJobStatus status,
            String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.integrationId = integrationId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getIntegrationId() {
        return integrationId;
    }

    public IngestionJobStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionJobStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getSourceObjectKey() {
        return sourceObjectKey;
    }

    public void setSourceObjectKey(String sourceObjectKey) {
        this.sourceObjectKey = sourceObjectKey;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getValidRows() {
        return validRows;
    }

    public void setValidRows(Integer validRows) {
        this.validRows = validRows;
    }

    public Integer getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(Integer errorRows) {
        this.errorRows = errorRows;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
