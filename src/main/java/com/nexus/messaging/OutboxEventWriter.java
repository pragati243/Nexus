package com.nexus.messaging;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only write path onto {@code outbox_events}. {@code Propagation.MANDATORY}
 * makes ADR-0003's "same transaction as the business change" requirement
 * mechanically enforced rather than a convention someone can forget — a
 * caller outside a transaction gets a hard failure here, not a silently
 * orphaned business row with no corresponding event.
 */
@Service
public class OutboxEventWriter {

    private final OutboxEventRepository repository;

    public OutboxEventWriter(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent write(UUID tenantId, String aggregateType, UUID aggregateId, String eventType,
            String payloadJson) {
        OutboxEvent entity = new OutboxEvent(
                UUID.randomUUID(), tenantId, aggregateType, aggregateId, eventType, payloadJson);
        return repository.save(entity);
    }
}
