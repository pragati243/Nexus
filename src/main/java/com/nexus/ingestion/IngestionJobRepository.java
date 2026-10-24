package com.nexus.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    Optional<IngestionJob> findByIdAndTenantId(UUID id, UUID tenantId);

    /** The job-listing/dashboard query (data-model.md) — backed by the (tenant_id, status, created_at) index. */
    Page<IngestionJob> findByTenantIdAndStatus(UUID tenantId, IngestionJobStatus status, Pageable pageable);

    Optional<IngestionJob> findByTenantIdAndIntegrationIdAndIdempotencyKey(
            UUID tenantId, UUID integrationId, String idempotencyKey);
}
