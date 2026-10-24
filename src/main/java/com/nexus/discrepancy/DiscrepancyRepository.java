package com.nexus.discrepancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, UUID> {

    Optional<Discrepancy> findByIdAndTenantId(UUID id, UUID tenantId);

    /** The discrepancy dashboard query (data-model.md) — backed by the (tenant_id, status, created_at) index. */
    Page<Discrepancy> findByTenantIdAndStatus(UUID tenantId, DiscrepancyStatus status, Pageable pageable);

    Page<Discrepancy> findByReconciliationRunId(UUID reconciliationRunId, Pageable pageable);
}
