package com.nexus.tenant;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns "who is this authenticated principal" into "which tenant are they
 * acting as, with what role" — the one place {@code tenant_memberships} is
 * read for this purpose (docs/threat-model.md § 2, CLAUDE.md rule 2). A
 * client-supplied {@code X-Tenant-Id} is only ever treated as a selection
 * among the principal's own memberships, never as proof of membership
 * itself.
 */
@Service
public class TenantContextResolver {

    private final TenantMembershipRepository tenantMembershipRepository;

    public TenantContextResolver(TenantMembershipRepository tenantMembershipRepository) {
        this.tenantMembershipRepository = tenantMembershipRepository;
    }

    @Transactional(readOnly = true)
    public TenantContext resolve(String principalId, String requestedTenantIdHeader) {
        List<TenantMembership> memberships = tenantMembershipRepository.findByPrincipalId(principalId);
        if (memberships.isEmpty()) {
            throw new TenantAccessDeniedException(
                    "Principal has no tenant memberships.");
        }

        if (requestedTenantIdHeader != null && !requestedTenantIdHeader.isBlank()) {
            UUID requestedTenantId = parseTenantId(requestedTenantIdHeader);
            TenantMembership selected = memberships.stream()
                    .filter(membership -> membership.getTenantId().equals(requestedTenantId))
                    .findFirst()
                    .orElseThrow(() -> new TenantAccessDeniedException(
                            "Principal is not a member of the tenant named in X-Tenant-Id."));
            return new TenantContext(selected.getTenantId(), principalId, selected.getRole());
        }

        if (memberships.size() > 1) {
            throw new AmbiguousTenantSelectionException(
                    "Principal belongs to more than one tenant; send X-Tenant-Id to select which one this request is for.");
        }

        TenantMembership only = memberships.get(0);
        return new TenantContext(only.getTenantId(), principalId, only.getRole());
    }

    private UUID parseTenantId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new TenantAccessDeniedException("X-Tenant-Id header is not a valid identifier.");
        }
    }
}
