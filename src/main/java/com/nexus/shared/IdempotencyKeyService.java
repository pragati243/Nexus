package com.nexus.shared;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure persistence wrapper around {@link IdempotencyKeyRepository} — the
 * actual duplicate-effect guarantee (data-model.md Invariant #5) comes from
 * the database's {@code UNIQUE (tenant_id, scope, key)} constraint, not from
 * this class. This service exists only so callers don't reach into the
 * repository directly for what's a two-step "look up, then record" pattern.
 */
@Service
public class IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyKeyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> find(UUID tenantId, String scope, String key) {
        return repository.findByTenantIdAndScopeAndKey(tenantId, scope, key);
    }

    /**
     * Records a new idempotency key. Throws the same constraint violation the
     * database would throw if a concurrent request raced this one and won —
     * callers are expected to catch that and treat it as "already recorded",
     * not retry blindly (business-logic concern, deferred to Phase 3).
     */
    @Transactional
    public IdempotencyKey record(UUID tenantId, String scope, String key, String requestFingerprint,
            int responseStatus, String responseBodyJson) {
        IdempotencyKey entity = new IdempotencyKey(
                UUID.randomUUID(), tenantId, scope, key, requestFingerprint, responseStatus, responseBodyJson);
        return repository.save(entity);
    }
}
