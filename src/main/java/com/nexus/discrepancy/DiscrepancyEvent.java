package com.nexus.discrepancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Append-only — no setters beyond construction; rows are never updated. */
@Entity
@Table(name = "discrepancy_events")
public class DiscrepancyEvent {

    @Id
    private UUID id;

    @Column(name = "discrepancy_id", nullable = false)
    private UUID discrepancyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private DiscrepancyEventType eventType;

    @Column(name = "actor_principal_id", nullable = false)
    private String actorPrincipalId;

    @Column
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DiscrepancyEvent() {
    }

    public DiscrepancyEvent(UUID id, UUID discrepancyId, UUID tenantId, DiscrepancyEventType eventType,
            String actorPrincipalId, String note) {
        this.id = id;
        this.discrepancyId = discrepancyId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.actorPrincipalId = actorPrincipalId;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDiscrepancyId() {
        return discrepancyId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public DiscrepancyEventType getEventType() {
        return eventType;
    }

    public String getActorPrincipalId() {
        return actorPrincipalId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
