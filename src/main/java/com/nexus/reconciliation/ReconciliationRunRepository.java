package com.nexus.reconciliation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    Optional<ReconciliationRun> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ReconciliationRun> findByTenantIdAndStatus(UUID tenantId, ReconciliationRunStatus status, Pageable pageable);
}
