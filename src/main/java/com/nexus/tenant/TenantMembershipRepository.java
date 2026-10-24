package com.nexus.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    /** The TenantContext resolution query — every request hits this (data-model.md). */
    List<TenantMembership> findByPrincipalId(String principalId);

    Optional<TenantMembership> findByTenantIdAndPrincipalId(UUID tenantId, String principalId);
}
