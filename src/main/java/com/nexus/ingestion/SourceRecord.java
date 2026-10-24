package com.nexus.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "source_records")
public class SourceRecord {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "ingestion_job_id", nullable = false)
    private UUID ingestionJobId;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "business_key", nullable = false)
    private String businessKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "external_record_id", nullable = false)
    private String externalRecordId;

    /** NUMERIC(18,2), never floating-point, for currency — see CLAUDE.md. */
    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "record_timestamp")
    private Instant recordTimestamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SourceRecord() {
    }

    public SourceRecord(UUID id, UUID tenantId, UUID integrationId, UUID ingestionJobId,
            String sourceSystem, String businessKey, String payload, String externalRecordId) {
        this.id = id;
        this.tenantId = tenantId;
        this.integrationId = integrationId;
        this.ingestionJobId = ingestionJobId;
        this.sourceSystem = sourceSystem;
        this.businessKey = businessKey;
        this.payload = payload;
        this.externalRecordId = externalRecordId;
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

    public UUID getIngestionJobId() {
        return ingestionJobId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getExternalRecordId() {
        return externalRecordId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getRecordTimestamp() {
        return recordTimestamp;
    }

    public void setRecordTimestamp(Instant recordTimestamp) {
        this.recordTimestamp = recordTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
