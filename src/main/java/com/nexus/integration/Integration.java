package com.nexus.integration;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "integrations")
public class Integration {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false)
    private IntegrationConnectionType connectionType;

    /**
     * Connection-type-specific config ONLY — never raw credentials (see the
     * column comment in V3__create_integrations.sql and threat-model.md).
     * Stored as opaque JSON text; Hibernate binds it to {@code jsonb} as-is
     * via {@code SqlTypes.JSON}, with no Map/record round-tripping (and
     * therefore no dependency on which Jackson major version Hibernate's
     * internal format mapper would otherwise need).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String config;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Integration() {
    }

    public Integration(UUID id, UUID tenantId, String name, String sourceSystem,
            IntegrationConnectionType connectionType, String config, IntegrationStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.sourceSystem = sourceSystem;
        this.connectionType = connectionType;
        this.config = config;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public IntegrationConnectionType getConnectionType() {
        return connectionType;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public IntegrationStatus getStatus() {
        return status;
    }

    public void setStatus(IntegrationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
