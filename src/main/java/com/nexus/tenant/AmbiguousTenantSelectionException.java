package com.nexus.tenant;

/**
 * The caller belongs to more than one tenant and didn't send an
 * {@code X-Tenant-Id} header to say which one this request is for. Maps to
 * 400 — this is a client bug (ambiguous request), not an authorization
 * failure.
 */
public class AmbiguousTenantSelectionException extends RuntimeException {

    public AmbiguousTenantSelectionException(String message) {
        super(message);
    }
}
