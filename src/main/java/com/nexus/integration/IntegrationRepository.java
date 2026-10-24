package com.nexus.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    List<Integration> findByTenantIdAndStatus(UUID tenantId, IntegrationStatus status);

    /** Tenant-scoped lookup by id — never trust a bare id without this check (CLAUDE.md rule 2). */
    Optional<Integration> findByIdAndTenantId(UUID id, UUID tenantId);
}
