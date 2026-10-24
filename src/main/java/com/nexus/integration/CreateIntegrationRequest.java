package com.nexus.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** See docs/api/initial-contract.md § POST /api/v1/integrations. */
public record CreateIntegrationRequest(
        @NotBlank String name,
        @NotBlank String sourceSystem,
        @NotNull IntegrationConnectionType connectionType,
        @NotNull Map<String, Object> config) {
}
