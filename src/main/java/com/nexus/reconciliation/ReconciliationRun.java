package com.nexus.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "left_integration_id", nullable = false)
    private UUID leftIntegrationId;

    @Column(name = "right_integration_id", nullable = false)
    private UUID rightIntegrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationRunStatus status;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "discrepancy_count")
    private Integer discrepancyCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tolerance_config", nullable = false, columnDefinition = "jsonb")
    private String toleranceConfig;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationRun() {
    }

    public ReconciliationRun(UUID id, UUID tenantId, UUID leftIntegrationId, UUID rightIntegrationId,
            ReconciliationRunStatus status, String toleranceConfig) {
        this.id = id;
        this.tenantId = tenantId;
        this.leftIntegrationId = leftIntegrationId;
        this.rightIntegrationId = rightIntegrationId;
        this.status = status;
        this.toleranceConfig = toleranceConfig;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getLeftIntegrationId() {
        return leftIntegrationId;
    }

    public UUID getRightIntegrationId() {
        return rightIntegrationId;
    }

    public ReconciliationRunStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationRunStatus status) {
        this.status = status;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    public Integer getDiscrepancyCount() {
        return discrepancyCount;
    }

    public void setDiscrepancyCount(Integer discrepancyCount) {
        this.discrepancyCount = discrepancyCount;
    }

    public String getToleranceConfig() {
        return toleranceConfig;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
