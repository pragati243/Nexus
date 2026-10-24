package com.nexus.integration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    Page<Integration> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Integration> findByTenantIdAndStatus(UUID tenantId, IntegrationStatus status, Pageable pageable);

    /** Tenant-scoped lookup by id — never trust a bare id without this check (CLAUDE.md rule 2). */
    Optional<Integration> findByIdAndTenantId(UUID id, UUID tenantId);
}
