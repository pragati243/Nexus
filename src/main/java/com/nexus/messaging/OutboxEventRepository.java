package com.nexus.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * The Outbox Publisher's poll query (ADR-0003) — matches the partial index on
     * {@code (created_at) WHERE published_at IS NULL} defined in
     * V10__create_outbox_events.sql.
     */
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEvent> findUnpublishedOrderByCreatedAt(Pageable pageable);
}
