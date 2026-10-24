package com.nexus.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately excludes {@code config} — even though it never holds raw
 * credentials (threat-model.md), there's no reason to echo implementation
 * details back on every read, matching the shape already fixed in
 * docs/api/initial-contract.md.
 */
public record IntegrationResponse(
        UUID id,
        UUID tenantId,
        String name,
        String sourceSystem,
        String connectionType,
        String status,
        Instant createdAt) {

    public static IntegrationResponse from(Integration integration) {
        return new IntegrationResponse(
                integration.getId(),
                integration.getTenantId(),
                integration.getName(),
                integration.getSourceSystem(),
                integration.getConnectionType().name(),
                integration.getStatus().name(),
                integration.getCreatedAt());
    }
}
