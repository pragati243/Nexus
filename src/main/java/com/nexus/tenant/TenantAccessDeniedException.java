package com.nexus.tenant;

/**
 * The caller is authenticated but not authorized — either they have no
 * membership in the tenant a request's {@code X-Tenant-Id} named (or in any
 * tenant at all), or their role doesn't meet an action's minimum. Maps to
 * 403 in {@code GlobalExceptionHandler}. Deliberately not 404: unlike a
 * cross-tenant *resource* lookup (which does return 404 — see
 * docs/threat-model.md § 2), the tenant id here was supplied by the caller
 * themselves, so there's no existence fact left to leak by saying "no."
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
