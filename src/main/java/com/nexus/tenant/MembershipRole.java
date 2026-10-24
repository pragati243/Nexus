package com.nexus.tenant;

/**
 * Ordered from least to most privileged within a tenant — see
 * docs/authorization-model.md for the full permission matrix.
 * {@link #atLeast(MembershipRole)} relies on enum declaration order, so the
 * order of these constants is load-bearing, not cosmetic.
 */
public enum MembershipRole {
    READ_ONLY,
    OPERATOR,
    TENANT_ADMIN;

    public boolean atLeast(MembershipRole minimum) {
        return this.ordinal() >= minimum.ordinal();
    }
}
