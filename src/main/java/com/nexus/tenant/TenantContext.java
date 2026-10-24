package com.nexus.tenant;

import java.util.UUID;

/**
 * Who is calling, which tenant they're acting as, and what role they hold
 * there — resolved exactly once per request by {@link TenantContextResolver},
 * never reconstructed from a client-supplied field. See
 * docs/authorization-model.md.
 */
public record TenantContext(UUID tenantId, String principalId, MembershipRole role) {

    public void require(MembershipRole minimum) {
        if (!role.atLeast(minimum)) {
            throw new TenantAccessDeniedException(
                    "Role " + role + " does not meet the required minimum of " + minimum + " for this action.");
        }
    }
}
