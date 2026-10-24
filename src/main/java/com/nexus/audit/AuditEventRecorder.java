package com.nexus.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only write path onto {@code audit_events}. {@code Propagation.MANDATORY}
 * makes data-model.md Invariant #7 ("discrepancy resolution is auditable")
 * mechanically true rather than just documented: calling this outside an
 * existing transaction throws {@link org.springframework.transaction.IllegalTransactionStateException}
 * instead of silently committing the audit row on its own, separately from
 * the business change it's supposed to be atomic with.
 */
@Service
public class AuditEventRecorder {

    private final AuditEventRepository repository;

    public AuditEventRecorder(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(UUID tenantId, String actorPrincipalId, String action, String targetType,
            UUID targetId, String metadataJson) {
        AuditEvent entity = new AuditEvent(
                UUID.randomUUID(), tenantId, actorPrincipalId, action, targetType, targetId, metadataJson);
        return repository.save(entity);
    }
}
