package com.nexus.shared;

/**
 * A resource doesn't exist — or exists in a tenant the caller can't see,
 * which must look identical to the caller (docs/threat-model.md § 2,
 * docs/api/initial-contract.md § 1: never leak cross-tenant existence via a
 * 403 instead of a 404). Every tenant-scoped "find by id" lookup across
 * every module throws this one shared exception rather than a
 * module-specific "NotFound" type, so {@code GlobalExceptionHandler} has
 * exactly one place that maps "not found" to 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
