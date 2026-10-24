package com.nexus.discrepancy;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "discrepancies")
public class Discrepancy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reconciliation_run_id", nullable = false)
    private UUID reconciliationRunId;

    @Column(name = "business_key", nullable = false)
    private String businessKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscrepancyReason reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscrepancyStatus status;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Discrepancy() {
    }

    public Discrepancy(UUID id, UUID tenantId, UUID reconciliationRunId, String businessKey,
            DiscrepancyReason reason, String explanation, DiscrepancyStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.reconciliationRunId = reconciliationRunId;
        this.businessKey = businessKey;
        this.reason = reason;
        this.explanation = explanation;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getReconciliationRunId() {
        return reconciliationRunId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public DiscrepancyReason getReason() {
        return reason;
    }

    public String getExplanation() {
        return explanation;
    }

    public DiscrepancyStatus getStatus() {
        return status;
    }

    public void setStatus(DiscrepancyStatus status) {
        this.status = status;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
