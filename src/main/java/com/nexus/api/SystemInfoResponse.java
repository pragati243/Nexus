package com.nexus.api;

import java.time.Instant;
import java.util.List;

/** Response body for {@code GET /api/v1/system/info} — see docs/api/initial-contract.md. */
public record SystemInfoResponse(
        String application,
        String version,
        Instant buildTime,
        List<String> activeProfiles,
        String javaVersion,
        Instant serverTime) {
}
