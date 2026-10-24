package com.nexus.integration;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Offset-based for now ({@code page}/{@code size}), not the cursor-based
 * envelope docs/api/initial-contract.md specifies for list endpoints — a
 * deliberate, scoped-down simplification for this phase (see
 * docs/build-plan.md Phase 3 notes). Revisit when a second list endpoint
 * (ingestion jobs, discrepancies) makes it worth building cursor encoding
 * once instead of ad hoc here.
 */
public record IntegrationListResponse(List<IntegrationResponse> items, int page, int size, long totalElements) {

    public static IntegrationListResponse from(Page<Integration> page) {
        Page<IntegrationResponse> mapped = page.map(IntegrationResponse::from);
        return new IntegrationListResponse(mapped.getContent(), mapped.getNumber(), mapped.getSize(),
                mapped.getTotalElements());
    }
}
